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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.Test;

import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author John Casey
 * @author Dennis Lundberg
 * @since 1.0
 *
 */
@PlexusTest
class FilteringUtilsTest {
    private static File testDirectory = new File(getBasedir(), "target/test-classes/");

    @Test
    void mSHARED1213CopyWithTargetAlreadyExisting0ByteFile() throws IOException {
        File fromFile = new File(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        File toFile = new File(testDirectory, "MSHARED-1213-enunciate.xml");
        Files.write(toFile.toPath(), "".getBytes(StandardCharsets.UTF_8));
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
                Files.readAllLines(fromFile.toPath(), StandardCharsets.UTF_8),
                Files.readAllLines(toFile.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void mSHARED1213CopyWithTargetAlreadyExistingJunkFile() throws IOException {
        File fromFile = new File(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        File toFile = new File(testDirectory, "MSHARED-1213-enunciate.xml");
        Files.write(toFile.toPath(), "junk".getBytes(StandardCharsets.UTF_8));
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
                Files.readAllLines(fromFile.toPath(), StandardCharsets.UTF_8),
                Files.readAllLines(toFile.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void mSHARED1213CopyWithTargetAlreadyExistingSameFile() throws IOException {
        File fromFile = new File(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        File toFile = new File(testDirectory, "MSHARED-1213-enunciate.xml");
        Files.copy(fromFile.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
                Files.readAllLines(fromFile.toPath(), StandardCharsets.UTF_8),
                Files.readAllLines(toFile.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void escapeWindowsPathStartingWithDrive() {
        assertEquals("C:\\\\Users\\\\Administrator", FilteringUtils.escapeWindowsPath("C:\\Users\\Administrator"));
    }

    @Test
    void escapeWindowsPathMissingDriveLetter() {
        assertEquals(":\\Users\\Administrator", FilteringUtils.escapeWindowsPath(":\\Users\\Administrator"));
    }

    @Test
    void escapeWindowsPathInvalidDriveLetter() {
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
    void escapeWindowsPathNotAtBeginning() throws Exception {
        assertEquals(
                "jdbc:derby:C:\\\\Users\\\\Administrator/test;create=true",
                FilteringUtils.escapeWindowsPath("jdbc:derby:C:\\Users\\Administrator/test;create=true"));
    }
}
