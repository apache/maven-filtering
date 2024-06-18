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

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;

import static org.apache.maven.api.di.testing.MavenDIExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author John Casey
 * @author Dennis Lundberg
 * @since 1.0
 *
 */
public class FilteringUtilsTest {
    private static Path testDirectory = Paths.get(getBasedir(), "target/test-classes/");

    @Test
    public void testMSHARED1213CopyWithTargetAlreadyExisting0ByteFile() throws IOException {
        Path fromFile = Paths.get(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        Path toFile = testDirectory.resolve("MSHARED-1213-enunciate.xml");
        Files.write(toFile, "".getBytes(StandardCharsets.UTF_8));
        FilteringUtils.copyFile(
                fromFile,
                toFile,
                "UTF-8",
                new FilterWrapper[] {
                    new FilterWrapper() {
                        @Override
                        public Reader getReader(Reader fileReader) {
                            return fileReader;
                        }
                    }
                },
                false);
        assertEquals(
                Files.readAllLines(fromFile, StandardCharsets.UTF_8),
                Files.readAllLines(toFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testMSHARED1213CopyWithTargetAlreadyExistingJunkFile() throws IOException {
        Path fromFile = Paths.get(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        Path toFile = testDirectory.resolve("MSHARED-1213-enunciate.xml");
        Files.write(toFile, "junk".getBytes(StandardCharsets.UTF_8));
        FilteringUtils.copyFile(
                fromFile,
                toFile,
                "UTF-8",
                new FilterWrapper[] {
                    new FilterWrapper() {
                        @Override
                        public Reader getReader(Reader fileReader) {
                            return fileReader;
                        }
                    }
                },
                false);
        assertEquals(
                Files.readAllLines(fromFile, StandardCharsets.UTF_8),
                Files.readAllLines(toFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testMSHARED1213CopyWithTargetAlreadyExistingSameFile() throws IOException {
        Path fromFile = Paths.get(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        Path toFile = testDirectory.resolve("MSHARED-1213-enunciate.xml");
        Files.copy(fromFile, toFile, StandardCopyOption.REPLACE_EXISTING);
        FilteringUtils.copyFile(
                fromFile,
                toFile,
                "UTF-8",
                new FilterWrapper[] {
                    new FilterWrapper() {
                        @Override
                        public Reader getReader(Reader fileReader) {
                            return fileReader;
                        }
                    }
                },
                false);
        assertEquals(
                Files.readAllLines(fromFile, StandardCharsets.UTF_8),
                Files.readAllLines(toFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testEscapeWindowsPathStartingWithDrive() {
        assertEquals("C:\\\\Users\\\\Administrator", FilteringUtils.escapeWindowsPath("C:\\Users\\Administrator"));
    }

    @Test
    public void testEscapeWindowsPathMissingDriveLetter() {
        assertEquals(":\\Users\\Administrator", FilteringUtils.escapeWindowsPath(":\\Users\\Administrator"));
    }

    @Test
    public void testEscapeWindowsPathInvalidDriveLetter() {
        assertEquals("4:\\Users\\Administrator", FilteringUtils.escapeWindowsPath("4:\\Users\\Administrator"));
    }

    // This doesn't work, see MSHARED-121
    /*
     * public void testEscapeWindowsPathStartingWithDrivelessAbsolutePath()
     * {
     * assertEquals( "\\\\Users\\\\Administrator", FilteringUtils.escapeWindowsPath( "\\Users\\Administrator" ) );
     * }
     */

    // This doesn't work, see MSHARED-121
    /*
     * public void testEscapeWindowsPathStartingWithExpression()
     * {
     * assertEquals( "${pathExpr}\\\\Documents", FilteringUtils.escapeWindowsPath( "${pathExpr}\\Documents" ) );
     * }
     */

    // MSHARED-179
    @Test
    public void testEscapeWindowsPathNotAtBeginning() throws Exception {
        assertEquals(
                "jdbc:derby:C:\\\\Users\\\\Administrator/test;create=true",
                FilteringUtils.escapeWindowsPath("jdbc:derby:C:\\Users\\Administrator/test;create=true"));
    }
}
