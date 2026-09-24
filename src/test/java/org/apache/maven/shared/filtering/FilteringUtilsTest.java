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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.maven.api.di.testing.MavenDIExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author John Casey
 * @author Dennis Lundberg
 * @since 1.0
 *
 */
class FilteringUtilsTest {
    private static final Path TEST_DIRECTORY = Paths.get(getBasedir(), "target/test-classes/");

    @TempDir
    Path tempDir;

    @Test
    void mshared1213CopyWithTargetAlreadyExisting0ByteFile() throws Exception {
        Path fromFile = Paths.get(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        Path toFile = TEST_DIRECTORY.resolve("MSHARED-1213-enunciate.xml");
        Files.writeString(toFile, "");
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
    void mshared1213CopyWithTargetAlreadyExistingJunkFile() throws Exception {
        Path fromFile = Paths.get(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        Path toFile = TEST_DIRECTORY.resolve("MSHARED-1213-enunciate.xml");
        Files.writeString(toFile, "junk");
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
    void mshared1213CopyWithTargetAlreadyExistingSameFile() throws Exception {
        Path fromFile = Paths.get(getBasedir() + "/src/test/units-files/MSHARED-1213/enunciate.xml");
        Path toFile = TEST_DIRECTORY.resolve("MSHARED-1213-enunciate.xml");
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
    void escapeWindowsPathStartingWithDrive() {
        assertEquals("C:\\\\Users\\\\Administrator", FilteringUtils.escapeWindowsPath("C:\\Users\\Administrator"));
    }

    @Test
    void escapeWindowsPathRelative() {
        assertEquals("src\\\\main\\\\java", FilteringUtils.escapeWindowsPath("src\\main\\java"));
    }

    @Test
    void escapeWindowsPathPreservesRepeatedBackslashes() {
        // Already-escaped backslash pairs (\\) are preserved as-is (idempotent guard)
        assertEquals("C:\\\\Users", FilteringUtils.escapeWindowsPath("C:\\\\Users"));
    }

    @Test
    void escapeWindowsPathMissingDriveLetter() {
        assertEquals(":\\Users\\Administrator", FilteringUtils.escapeWindowsPath(":\\Users\\Administrator"));
    }

    @Test
    void escapeWindowsPathInvalidDriveLetter() {
        assertEquals("4:\\Users\\Administrator", FilteringUtils.escapeWindowsPath("4:\\Users\\Administrator"));
    }

    @Test
    void escapeWindowsPathStartingWithDrivelessAbsolutePath() {
        assertEquals("\\\\Users\\\\Administrator", FilteringUtils.escapeWindowsPath("\\Users\\Administrator"));
    }

    @Test
    void escapeWindowsPathStartingWithExpression() {
        assertEquals("${pathExpr}\\\\Documents", FilteringUtils.escapeWindowsPath("${pathExpr}\\Documents"));
    }

    // MSHARED-179
    @Test
    void escapeWindowsPathNotAtBeginning() {
        assertEquals(
                "jdbc:derby:C:\\\\Users\\\\Administrator/test;create=true",
                FilteringUtils.escapeWindowsPath("jdbc:derby:C:\\Users\\Administrator/test;create=true"));
    }

    @Test
    void relativeFilePathStripsLeadingSeparatorFromWindowsDrivePath() {
        assertEquals("file.txt", FilteringUtils.getRelativeFilePath("C:/base", "/C:/base/file.txt"));
        assertEquals("../other/file.txt", FilteringUtils.getRelativeFilePath("/C:/base/dir", "C:/base/other/file.txt"));
    }

    @Test
    void relativeFilePathUnixStylePaths() {
        assertEquals("java/bin", FilteringUtils.getRelativeFilePath("/usr/local", "/usr/local/java/bin"));
        assertEquals("../../bin", FilteringUtils.getRelativeFilePath("/usr/local/", "/bin"));
        assertEquals("../usr/local/", FilteringUtils.getRelativeFilePath("/bin", "/usr/local/"));
    }

    // MSHARED-1004: symbolic links in resources

    /**
     * A file-symlink in the source directory must be followed: the target file's content is
     * copied to the destination as a regular file (not as a symlink).
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void copyFileSymlinkIsFollowedAndWrittenAsRegularFile() throws Exception {
        // real file
        Path realFile = tempDir.resolve("real.txt");
        Files.writeString(realFile, "symlink content");

        // symlink → real file
        Path symlink = tempDir.resolve("link.txt");
        Files.createSymbolicLink(symlink, realFile.getFileName()); // relative: ../real.txt

        Path toFile = tempDir.resolve("output.txt");
        FilteringUtils.copyFile(symlink, toFile, "UTF-8", new FilterWrapper[0], false);

        assertFalse(Files.isSymbolicLink(toFile), "destination must be a regular file, not a symlink");
        assertEquals("symlink content", Files.readString(toFile));
    }

    /**
     * When the destination is a dangling symlink left by a previous build (e.g. from an older
     * version of maven-filtering that copied symlinks verbatim), copyFile must replace it with a
     * regular file instead of failing with NoSuchFileException.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void copyFileReplacesDanglingSymlinkAtDestination() throws Exception {
        Path realFile = tempDir.resolve("real.txt");
        Files.writeString(realFile, "hello");

        Path toFile = tempDir.resolve("output.txt");
        // Simulate a dangling symlink left by old code
        Files.createSymbolicLink(toFile, Path.of("../nonexistent/B"));

        // Must succeed even though the symlink is dangling
        FilteringUtils.copyFile(realFile, toFile, "UTF-8", new FilterWrapper[0], false);

        assertFalse(Files.isSymbolicLink(toFile), "dangling symlink must be replaced by a regular file");
        assertEquals("hello", Files.readString(toFile));
    }

    /**
     * Same as above but with filtering active (wrappers path through CachingWriter).
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void copyFileWithFilteringReplacesDanglingSymlinkAtDestination() throws Exception {
        Path realFile = tempDir.resolve("real.txt");
        Files.writeString(realFile, "hello");

        Path toFile = tempDir.resolve("output.txt");
        Files.createSymbolicLink(toFile, Path.of("../nonexistent/B"));

        FilteringUtils.copyFile(
                realFile,
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

        assertFalse(Files.isSymbolicLink(toFile), "dangling symlink must be replaced by a regular file");
        assertEquals("hello", Files.readString(toFile));
    }
}
