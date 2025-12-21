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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

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
class MultiDelimiterInterpolatorFilterReaderLineEndingTest extends AbstractInterpolatorFilterReaderLineEndingTest {

    @Mock
    private Interpolator interpolator;

    @Override
    protected Reader getAaaAaaReader(Reader in, Interpolator interpolator) {
        MultiDelimiterInterpolatorFilterReaderLineEnding reader =
                new MultiDelimiterInterpolatorFilterReaderLineEnding(in, interpolator, true);
        reader.setDelimiterSpecs(Collections.singleton("aaa*aaa"));
        return reader;
    }

    @Override
    protected Reader getAbcAbcReader(Reader in, Interpolator interpolator) {
        MultiDelimiterInterpolatorFilterReaderLineEnding reader =
                new MultiDelimiterInterpolatorFilterReaderLineEnding(in, interpolator, true);
        reader.setDelimiterSpecs(Collections.singleton("abc*abc"));
        return reader;
    }

    @Override
    protected Reader getDollarBracesReader(Reader in, Interpolator interpolator, String escapeString) {
        MultiDelimiterInterpolatorFilterReaderLineEnding reader =
                new MultiDelimiterInterpolatorFilterReaderLineEnding(in, interpolator, true);
        reader.setDelimiterSpecs(Collections.singleton("${*}"));
        reader.setEscapeString(escapeString);
        return reader;
    }

    @Override
    protected Reader getAtReader(Reader in, Interpolator interpolator, String escapeString) {
        MultiDelimiterInterpolatorFilterReaderLineEnding reader =
                new MultiDelimiterInterpolatorFilterReaderLineEnding(in, interpolator, true);
        reader.setDelimiterSpecs(Collections.singleton("@"));
        reader.setEscapeString(escapeString);
        return reader;
    }

    // MSHARED-199: Filtering doesn't work if 2 delimiters are used on the same line, the first one being left open
    @Test
    void lineWithSingleAtAndExpression() throws Exception {
        when(interpolator.interpolate(eq("${foo}"), eq(""), isA(RecursionInterceptor.class)))
                .thenReturn("bar");

        Reader in = new StringReader("toto@titi.com ${foo}");
        MultiDelimiterInterpolatorFilterReaderLineEnding reader =
                new MultiDelimiterInterpolatorFilterReaderLineEnding(in, interpolator, true);
        reader.setDelimiterSpecs(new HashSet<>(Arrays.asList("${*}", "@")));

        assertEquals("toto@titi.com bar", IOUtils.toString(reader));
    }

    // http://stackoverflow.com/questions/21786805/maven-war-plugin-customize-filter-delimitters-in-webresources/
    @Test
    void atDollarExpression() throws Exception {
        when(interpolator.interpolate(eq("${db.server}"), eq(""), isA(RecursionInterceptor.class)))
                .thenReturn("DB_SERVER");
        when(interpolator.interpolate(eq("${db.port}"), eq(""), isA(RecursionInterceptor.class)))
                .thenReturn("DB_PORT");
        when(interpolator.interpolate(eq("${db.name}"), eq(""), isA(RecursionInterceptor.class)))
                .thenReturn("DB_NAME");

        Reader in = new StringReader("  url=\"jdbc:oracle:thin:\\@${db.server}:${db.port}:${db.name}\"");
        MultiDelimiterInterpolatorFilterReaderLineEnding reader =
                new MultiDelimiterInterpolatorFilterReaderLineEnding(in, interpolator, true);
        reader.setEscapeString("\\");
        reader.setDelimiterSpecs(new HashSet<>(Arrays.asList("${*}", "@")));

        assertEquals("  url=\"jdbc:oracle:thin:@DB_SERVER:DB_PORT:DB_NAME\"", IOUtils.toString(reader));
    }
}
