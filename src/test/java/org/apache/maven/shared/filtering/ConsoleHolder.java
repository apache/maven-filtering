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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Helping class to capture console input and output for tests.
 *
 * @author abelsromero
 * @since 3.3.2
 */
class ConsoleHolder {

    private PrintStream originalOut;
    private PrintStream originalErr;

    private ByteArrayOutputStream newOut;
    private ByteArrayOutputStream newErr;

    private ConsoleHolder() {}

    static ConsoleHolder start() {
        final ConsoleHolder holder = new ConsoleHolder();

        holder.originalOut = System.out;
        holder.originalErr = System.err;

        holder.newOut = new DoubleOutputStream(holder.originalOut);
        holder.newErr = new DoubleOutputStream(holder.originalErr);

        System.setOut(new PrintStream(holder.newOut));
        System.setErr(new PrintStream(holder.newErr));

        return holder;
    }

    void release() {
        System.setOut(originalOut);
        System.setOut(originalErr);
    }

    String getOutput() {
        return new String(newOut.toByteArray());
    }

    String getError() {
        return new String(newErr.toByteArray());
    }

    static class DoubleOutputStream extends ByteArrayOutputStream {

        final OutputStream other;

        DoubleOutputStream(final OutputStream os) {
            other = os;
        }

        @Override
        public synchronized void write(final byte[] b, final int off, final int len) {
            try {
                other.write(b, off, len);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            super.write(b, off, len);
        }
    }
}
