/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.shared.filtering;

import java.io.Reader;
import java.io.StringReader;

import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.RecursionInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractInterpolatorFilterReaderLineEndingTest {

    @Mock
    private Interpolator interpolator;

    @Test
    public void defaults() throws Exception {
        when(interpolator.interpolate(eq("${a}"), eq(""), isA(RecursionInterceptor.class)))
                .thenReturn("DONE_A");

        Reader in = new StringReader("text without expression");
        Reader reader = getDollarBracesReader(in, interpolator, "\\");
        assertEquals("text without expression", IOUtils.toString(reader));

        in = new StringReader("valid expression ${a}");
        reader = getDollarBracesReader(in, interpolator, null);
        assertEquals("valid expression DONE_A", IOUtils.toString(reader));

        in = new StringReader("empty expression ${}");
        reader = getDollarBracesReader(in, interpolator, null);
        assertEquals("empty expression ${}", IOUtils.toString(reader));

        in = new StringReader("dollar space expression $ {a}");
        reader = getDollarBracesReader(in, interpolator, "\\");
        assertEquals("dollar space expression $ {a}", IOUtils.toString(reader));

        in = new StringReader("space in expression ${ a}");
        reader = getDollarBracesReader(in, interpolator, "\\");
        assertEquals("space in expression ${ a}", IOUtils.toString(reader));

        in = new StringReader("escape dollar with expression \\${a}");
        reader = getDollarBracesReader(in, interpolator, "\\");
        assertEquals("escape dollar with expression ${a}", IOUtils.toString(reader));

        //        in = new StringReader( "escape escape string before expression \\\\${a}" );
        //        reader = getDollarBracesReader( in, interpolator, "\\" );
        //        assertEquals( "escape escape string before expression \\DONE_A", toString( reader ) );
        //
        //        in = new StringReader( "escape escape string and expression \\\\\\${a}" );
        //        reader = getDollarBracesReader( in, interpolator, "\\" );
        //        assertEquals( "escape escape string before expression \\${a}", toString( reader ) );

        in = new StringReader("unknown expression ${unknown}");
        reader = getDollarBracesReader(in, interpolator, "\\");
        assertEquals("unknown expression ${unknown}", IOUtils.toString(reader));
    }

    // MSHARED-198: custom delimiters doesn't work as expected
    @Test
    public void customDelimiters() throws Exception {
        when(interpolator.interpolate(eq("aaaFILTER.a.MEaaa"), eq(""), isA(RecursionInterceptor.class)))
                .thenReturn("DONE");
        when(interpolator.interpolate(eq("abcFILTER.a.MEabc"), eq(""), isA(RecursionInterceptor.class)))
                .thenReturn("DONE");

        Reader in = new StringReader("aaaFILTER.a.MEaaa");
        Reader reader = getAaaAaaReader(in, interpolator);

        assertEquals("DONE", IOUtils.toString(reader));

        in = new StringReader("abcFILTER.a.MEabc");
        reader = getAbcAbcReader(in, interpolator);
        assertEquals("DONE", IOUtils.toString(reader));
    }

    // MSHARED-235: reader exceeds readAheadLimit
    @Test
    public void markInvalid() throws Exception {
        try (Reader reader = getAtReader(new StringReader("@\").replace(p,\"]\").replace(q,\""), interpolator, "\\")) {
            assertEquals("@\").replace(p,\"]\").replace(q,\"", IOUtils.toString(reader));
        }
    }

    protected abstract Reader getAbcAbcReader(Reader in, Interpolator interpolator);

    protected abstract Reader getAaaAaaReader(Reader in, Interpolator interpolator);

    protected abstract Reader getDollarBracesReader(Reader in, Interpolator interpolator, String escapeString);

    protected abstract Reader getAtReader(Reader in, Interpolator interpolator, String escapeString);
}
