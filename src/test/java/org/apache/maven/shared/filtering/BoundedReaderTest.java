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
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedReaderTest {

    private final Reader sr = new BufferedReader(new StringReader("01234567890"));

    @Test
    void readTillEnd() throws IOException {
        try (BoundedReader mr = new BoundedReader(sr, 3)) {
            mr.mark(3);
            mr.read();
            mr.read();
            mr.read();
            assertEquals(-1, mr.read());
        }
    }

    @Test
    void readMulti() throws IOException {
        char[] cbuf = new char[4];
        for (int i = 0; i < cbuf.length; i++) {
            cbuf[i] = 'X';
        }

        try (BoundedReader mr = new BoundedReader(sr, 3)) {
            final int read = mr.read(cbuf, 0, 4);
            assertEquals(3, read);
        }

        assertEquals('0', cbuf[0]);
        assertEquals('1', cbuf[1]);
        assertEquals('2', cbuf[2]);
        assertEquals('X', cbuf[3]);
    }

    @Test
    void readMultiWithOffset() throws IOException {

        char[] cbuf = new char[4];
        for (int i = 0; i < cbuf.length; i++) {
            cbuf[i] = 'X';
        }

        try (BoundedReader mr = new BoundedReader(sr, 3)) {
            final int read = mr.read(cbuf, 1, 2);
            assertEquals(2, read);
        }

        assertEquals('X', cbuf[0]);
        assertEquals('0', cbuf[1]);
        assertEquals('1', cbuf[2]);
        assertEquals('X', cbuf[3]);
    }

    @Test
    void resetWorks() throws IOException {
        try (BoundedReader mr = new BoundedReader(sr, 3)) {
            mr.read();
            mr.read();
            mr.read();
            mr.reset();
            mr.read();
            mr.read();
            mr.read();
            assertEquals(-1, mr.read());
        }
    }

    @Test
    void skipTest() throws IOException {
        try (BoundedReader mr = new BoundedReader(sr, 3)) {
            mr.skip(2);
            mr.read();
            assertEquals(-1, mr.read());
        }
    }
}
