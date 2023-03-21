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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.RecursionInterceptor;
import org.codehaus.plexus.interpolation.SimpleRecursionInterceptor;

/**
 * A FilterReader implementation, that works with Interpolator interface instead of it's own interpolation
 * implementation. This implementation is heavily based on org.codehaus.plexus.util.InterpolationFilterReader.
 *
 * @author cstamas
 * @author Olivier Lamy
 * @since 1.0
 */
public class InterpolatorFilterReaderLineEnding extends AbstractFilterReaderLineEnding {

    /**
     * Interpolator used to interpolate
     */
    private Interpolator interpolator;

    private RecursionInterceptor recursionInterceptor;

    /**
     * replacement text from a token
     */
    private String replaceData = null;

    /**
     * Index into replacement data
     */
    private int replaceIndex = 0;

    /**
     * Default begin token.
     */
    public static final String DEFAULT_BEGIN_TOKEN = "${";

    /**
     * Default end token.
     */
    public static final String DEFAULT_END_TOKEN = "}";

    private String beginToken;

    private String endToken;

    /**
     * true by default to preserve backward comp
     */
    private boolean interpolateWithPrefixPattern = true;

    private boolean supportMultiLineFiltering;

    private boolean eof = false;

    /**
     * @param in reader to use
     * @param interpolator interpolator instance to use
     * @param beginToken start token to use
     * @param endToken end token to use
     * @param supportMultiLineFiltering If multi line filtering is allowed
     */
    public InterpolatorFilterReaderLineEnding(
            Reader in,
            Interpolator interpolator,
            String beginToken,
            String endToken,
            boolean supportMultiLineFiltering) {
        this(in, interpolator, beginToken, endToken, new SimpleRecursionInterceptor(), supportMultiLineFiltering);
    }

    /**
     * @param in reader to use
     * @param interpolator interpolator instance to use
     * @param beginToken start token to use
     * @param endToken end token to use
     * @param ri The {@link RecursionInterceptor} to use to prevent recursive expressions.
     * @param supportMultiLineFiltering If multi line filtering is allowed
     */
    private InterpolatorFilterReaderLineEnding(
            Reader in,
            Interpolator interpolator,
            String beginToken,
            String endToken,
            RecursionInterceptor ri,
            boolean supportMultiLineFiltering) {
        // wrap our own buffer, so we can use mark/reset safely.
        super(new BufferedReader(in));

        this.interpolator = interpolator;

        this.beginToken = beginToken;

        this.endToken = endToken;

        recursionInterceptor = ri;

        this.supportMultiLineFiltering = supportMultiLineFiltering;

        calculateMarkLength();
    }

    /**
     * Skips characters. This method will block until some characters are available, an I/O error occurs, or the end of
     * the stream is reached.
     *
     * @param n The number of characters to skip
     * @return the number of characters actually skipped
     * @throws IOException If an I/O error occurs
     */
    @Override
    public long skip(long n) throws IOException {
        if (n < 0L) {
            throw new IllegalArgumentException("skip value is negative");
        }

        for (long i = 0; i < n; i++) {
            if (read() == -1) {
                return i;
            }
        }
        return n;
    }

    /**
     * Reads characters into a portion of an array. This method will block until some input is available, an I/O error
     * occurs, or the end of the stream is reached.
     *
     * @param cbuf Destination buffer to write characters to. Must not be <code>null</code>.
     * @param off Offset at which to start storing characters.
     * @param len Maximum number of characters to read.
     * @return the number of characters read, or -1 if the end of the stream has been reached
     * @throws IOException If an I/O error occurs
     */
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            int ch = read();
            if (ch == -1) {
                if (i == 0) {
                    return -1;
                } else {
                    return i;
                }
            }
            cbuf[off + i] = (char) ch;
        }
        return len;
    }

    /**
     * Returns the next character in the filtered stream, replacing tokens from the original stream.
     *
     * @return the next character in the resulting stream, or -1 if the end of the resulting stream has been reached
     * @throws IOException if the underlying stream throws an IOException during reading
     */
    @Override
    public int read() throws IOException {
        if (replaceIndex > 0) {
            return replaceData.charAt(replaceData.length() - (replaceIndex--));
        }
        if (eof) {
            return -1;
        }

        in.mark(markLength);

        int ch = in.read();
        if (ch == -1 || (ch == '\n' && !supportMultiLineFiltering)) {
            return ch;
        }

        boolean inEscape = useEscape && ch == getEscapeString().charAt(0);

        StringBuilder key = new StringBuilder();

        // have we found an escape string?
        if (inEscape) {
            for (int i = 0; i < getEscapeString().length(); i++) {
                key.append((char) ch);

                if (ch != getEscapeString().charAt(i) || ch == -1 || (ch == '\n' && !supportMultiLineFiltering)) {
                    // mismatch, EOF or EOL, no escape string here
                    in.reset();
                    inEscape = false;
                    key.setLength(0);
                    break;
                }

                ch = in.read();
            }
        }

        // have we found a delimiter?
        boolean foundToken = false;
        for (int i = 0; i < beginToken.length(); i++) {
            if (ch != beginToken.charAt(i) || ch == -1 || (ch == '\n' && !supportMultiLineFiltering)) {
                // mismatch, EOF or EOL, no match
                break;
            }

            if (i == beginToken.length() - 1) {

                foundToken = true;
            }

            ch = in.read();
        }

        in.reset();
        in.skip(key.length());
        ch = in.read();

        // escape means no luck, prevent parsing of the escaped character, and return
        if (inEscape) {

            if (beginToken != null) {
                if (!isPreserveEscapeString()) {
                    key.setLength(0);
                }
            }

            key.append((char) ch);

            replaceData = key.toString();
            replaceIndex = key.length();

            return read();
        }

        // no match means no luck, reset and return
        if (!foundToken) {

            in.reset();
            return in.read();
        }

        // we're committed, find the end token, EOL or EOF

        key.append(beginToken);
        in.reset();
        in.skip(beginToken.length());
        ch = in.read();

        int endTokenSize = endToken.length();
        int end = endTokenSize;
        do {
            if (ch == -1) {
                break;
            } else if (ch == '\n' && !supportMultiLineFiltering) {
                // EOL
                key.append((char) ch);
                break;
            }

            key.append((char) ch);

            if (ch == this.endToken.charAt(endTokenSize - end)) {
                end--;
                if (end == 0) {
                    break;
                }
            } else {
                end = endTokenSize;
            }

            ch = in.read();
        } while (true);

        // found endtoken? interpolate our key resolved above
        String value = null;
        if (end == 0) {
            try {
                if (interpolateWithPrefixPattern) {
                    value = interpolator.interpolate(key.toString(), "", recursionInterceptor);
                } else {
                    value = interpolator.interpolate(key.toString(), recursionInterceptor);
                }
            } catch (InterpolationException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }

        // write away the value if present, otherwise the key unmodified
        if (value != null) {
            replaceData = value;
            replaceIndex = value.length();
        } else {
            replaceData = key.toString();
            replaceIndex = key.length();
        }

        if (ch == -1) {
            eof = true;
        }
        return read();
    }

    /**
     * @return current state of interpolate with prefix pattern.
     */
    public boolean isInterpolateWithPrefixPattern() {
        return interpolateWithPrefixPattern;
    }

    /**
     * @param interpolateWithPrefixPattern interpolate with prefix pattern.
     */
    public void setInterpolateWithPrefixPattern(boolean interpolateWithPrefixPattern) {
        this.interpolateWithPrefixPattern = interpolateWithPrefixPattern;
    }

    /**
     * @return {@link #recursionInterceptor}
     */
    public RecursionInterceptor getRecursionInterceptor() {
        return recursionInterceptor;
    }

    /**
     * @param theRecursionInterceptor {@link RecursionInterceptor}
     * @return {@link InterpolatorFilterReaderLineEnding}
     */
    public InterpolatorFilterReaderLineEnding setRecursionInterceptor(RecursionInterceptor theRecursionInterceptor) {
        this.recursionInterceptor = theRecursionInterceptor;
        return this;
    }
}
