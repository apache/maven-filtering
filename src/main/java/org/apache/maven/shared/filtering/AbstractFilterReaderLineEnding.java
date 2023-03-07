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

import java.io.FilterReader;
import java.io.Reader;
import java.util.LinkedHashSet;

import org.codehaus.plexus.interpolation.multi.DelimiterSpecification;

/**
 * @author Karl Heinz Marbaise <a href="mailto:khmarbaise@apache.org">khmarbaise@apache.org</a>
 */
public abstract class AbstractFilterReaderLineEnding extends FilterReader {

    private String escapeString;

    /**
     * using escape or not.
     */
    protected boolean useEscape = false;

    /**
     * if true escapeString will be preserved \{foo} -> \{foo}
     */
    private boolean preserveEscapeString = false;

    protected LinkedHashSet<DelimiterSpecification> delimiters = new LinkedHashSet<>();

    /**
     * must always be bigger than escape string plus delimiters, but doesn't need to be exact
     */
    // CHECKSTYLE_OFF: MagicNumber
    protected int markLength = 255;
    // CHECKSTYLE_ON: MagicNumber

    protected AbstractFilterReaderLineEnding(Reader in) {
        super(in);
    }

    /**
     * @return the escapce string.
     */
    public String getEscapeString() {
        return escapeString;
    }

    /**
     * @param escapeString Set the value of the escape string.
     */
    public void setEscapeString(String escapeString) {
        // TODO NPE if escapeString is null ?
        if (escapeString != null && escapeString.length() >= 1) {
            this.escapeString = escapeString;
            this.useEscape = true;
            calculateMarkLength();
        }
    }

    /**
     * @return state of preserve escape string.
     */
    public boolean isPreserveEscapeString() {
        return preserveEscapeString;
    }

    /**
     * @param preserveEscapeString preserve escape string {@code true} or {@code false}.
     */
    public void setPreserveEscapeString(boolean preserveEscapeString) {
        this.preserveEscapeString = preserveEscapeString;
    }

    protected void calculateMarkLength() {
        // CHECKSTYLE_OFF: MagicNumber
        markLength = 255;
        // CHECKSTYLE_ON: MagicNumber

        if (escapeString != null) {
            markLength += escapeString.length();
        }
        for (DelimiterSpecification spec : delimiters) {
            markLength += spec.getBegin().length();
            markLength += spec.getEnd().length();
        }
    }
}
