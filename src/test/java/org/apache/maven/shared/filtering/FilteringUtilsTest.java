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
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.maven.api.di.testing.MavenDIExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // --- ChangeDetection strategy tests (MRESOURCES-397) ---

    private static final FilterWrapper[] NO_WRAPPERS = null;

    private static final FilterWrapper IDENTITY_WRAPPER = new FilterWrapper() {
        @Override
        public Reader getReader(Reader fileReader) {
            return fileReader;
        }
    };

    /**
     * ALWAYS: always copies, even when destination already exists with identical content.
     */
    @Test
    void changeDetectionAlwaysCopiesEvenWhenDestinationExists() throws Exception {
        Path from = Files.createTempFile("cd-always-src", ".txt");
        Path to = Files.createTempFile("cd-always-dst", ".txt");
        try {
            Files.writeString(from, "hello");
            Files.writeString(to, "hello");

            boolean copied = FilteringUtils.copyFile(
                    from, to, "UTF-8", new FilterWrapper[] {IDENTITY_WRAPPER}, ChangeDetection.ALWAYS);
            assertTrue(copied, "ALWAYS should always report copied=true");
            assertEquals("hello", Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * ALWAYS: copies when destination does not exist.
     */
    @Test
    void changeDetectionAlwaysCopiesNewFile() throws Exception {
        Path from = Files.createTempFile("cd-always-new-src", ".txt");
        Path to = TEST_DIRECTORY.resolve("cd-always-new-dst.txt");
        try {
            Files.writeString(from, "content");
            Files.deleteIfExists(to);

            boolean copied = FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.ALWAYS);
            assertTrue(copied);
            assertEquals("content", Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * NEVER: skips copy when destination already exists (even if content differs).
     */
    @Test
    void changeDetectionNeverSkipsExistingFile() throws Exception {
        Path from = Files.createTempFile("cd-never-src", ".txt");
        Path to = Files.createTempFile("cd-never-dst", ".txt");
        try {
            Files.writeString(from, "new-content");
            Files.writeString(to, "old-content");

            boolean copied = FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.NEVER);
            assertFalse(copied, "NEVER should not copy when destination already exists");
            assertEquals("old-content", Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * NEVER: creates file when destination does not exist.
     */
    @Test
    void changeDetectionNeverCreatesNewFile() throws Exception {
        Path from = Files.createTempFile("cd-never-new-src", ".txt");
        Path to = TEST_DIRECTORY.resolve("cd-never-new-dst.txt");
        try {
            Files.writeString(from, "content");
            Files.deleteIfExists(to);

            boolean copied = FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.NEVER);
            assertTrue(copied, "NEVER should copy when destination does not exist");
            assertEquals("content", Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * TIMESTAMP: copies when source is newer than destination.
     */
    @Test
    void changeDetectionTimestampCopiesWhenSourceIsNewer() throws Exception {
        Path from = Files.createTempFile("cd-ts-src", ".txt");
        Path to = Files.createTempFile("cd-ts-dst", ".txt");
        try {
            Files.writeString(from, "updated");
            Files.writeString(to, "stale");
            // Make destination older than source
            Files.setLastModifiedTime(to, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
            Files.setLastModifiedTime(from, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));

            boolean copied = FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.TIMESTAMP);
            assertTrue(copied, "TIMESTAMP should copy when source is newer");
            assertEquals("updated", Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * TIMESTAMP: skips copy when destination is newer or equal.
     */
    @Test
    void changeDetectionTimestampSkipsWhenDestinationIsNewer() throws Exception {
        Path from = Files.createTempFile("cd-ts-old-src", ".txt");
        Path to = Files.createTempFile("cd-ts-new-dst", ".txt");
        try {
            Files.writeString(from, "old-content");
            Files.writeString(to, "new-content");
            // Make source older than destination
            Files.setLastModifiedTime(from, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
            Files.setLastModifiedTime(to, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));

            boolean copied = FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.TIMESTAMP);
            assertFalse(copied, "TIMESTAMP should skip when destination is newer");
            assertEquals("new-content", Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * TIMESTAMP: copies when destination does not exist.
     */
    @Test
    void changeDetectionTimestampCopiesNewFile() throws Exception {
        Path from = Files.createTempFile("cd-ts-new-src", ".txt");
        Path to = TEST_DIRECTORY.resolve("cd-ts-new-dst.txt");
        try {
            Files.writeString(from, "content");
            Files.deleteIfExists(to);

            boolean copied = FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.TIMESTAMP);
            assertTrue(copied, "TIMESTAMP should copy when destination does not exist");
            assertEquals("content", Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * CONTENT: skips copy when content is identical (uses CachingOutputStream/CachingWriter).
     */
    @Test
    void changeDetectionContentSkipsWhenContentIdentical() throws Exception {
        Path from = Files.createTempFile("cd-content-src", ".txt");
        Path to = Files.createTempFile("cd-content-dst", ".txt");
        try {
            Files.writeString(from, "same");
            Files.writeString(to, "same");

            boolean copied = FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.CONTENT);
            assertFalse(copied, "CONTENT should skip when contents are identical");
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * TIMESTAMP_AND_CONTENT: when source is newer and content differs, copies.
     * Uses large payload (&gt; 32 KB CachingOutputStream buffer) so the flush triggers
     * and {@code isModified()} returns the correct value before {@code close()}.
     */
    @Test
    void changeDetectionTimestampAndContentCopiesWhenNewerAndDifferent() throws Exception {
        Path from = Files.createTempFile("cd-tsc-src", ".txt");
        Path to = Files.createTempFile("cd-tsc-dst", ".txt");
        try {
            // 40 KB of different data to exceed CachingOutputStream's 32 KB buffer
            String srcData = "A".repeat(40_000);
            String dstData = "B".repeat(40_000);
            Files.writeString(from, srcData);
            Files.writeString(to, dstData);
            Files.setLastModifiedTime(to, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
            Files.setLastModifiedTime(from, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));

            boolean copied =
                    FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.TIMESTAMP_AND_CONTENT);
            assertTrue(copied, "TIMESTAMP_AND_CONTENT should copy when source is newer and content differs");
            assertEquals(srcData, Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * TIMESTAMP_AND_CONTENT: when source is newer but content is identical, does not modify.
     */
    @Test
    void changeDetectionTimestampAndContentSkipsWhenNewerButSameContent() throws Exception {
        Path from = Files.createTempFile("cd-tsc-same-src", ".txt");
        Path to = Files.createTempFile("cd-tsc-same-dst", ".txt");
        try {
            Files.writeString(from, "same");
            Files.writeString(to, "same");
            Files.setLastModifiedTime(to, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
            Files.setLastModifiedTime(from, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));

            boolean copied =
                    FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.TIMESTAMP_AND_CONTENT);
            assertFalse(copied, "TIMESTAMP_AND_CONTENT should not report modified when content is identical");
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * TIMESTAMP_AND_CONTENT: skips entirely when destination is newer (timestamp gate).
     */
    @Test
    void changeDetectionTimestampAndContentSkipsWhenDestinationIsNewer() throws Exception {
        Path from = Files.createTempFile("cd-tsc-old-src", ".txt");
        Path to = Files.createTempFile("cd-tsc-new-dst", ".txt");
        try {
            Files.writeString(from, "different");
            Files.writeString(to, "original");
            Files.setLastModifiedTime(from, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
            Files.setLastModifiedTime(to, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));

            boolean copied =
                    FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.TIMESTAMP_AND_CONTENT);
            assertFalse(copied, "TIMESTAMP_AND_CONTENT should skip when destination is newer");
            assertEquals("original", Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * CONTENT: copies when content differs. Uses a large payload (&gt; CachingOutputStream buffer)
     * so that the internal buffer is flushed and {@code isModified()} returns the correct value
     * before {@code close()}.
     */
    @Test
    void changeDetectionContentCopiesWhenContentDiffers() throws Exception {
        Path from = Files.createTempFile("cd-content-diff-src", ".txt");
        Path to = Files.createTempFile("cd-content-diff-dst", ".txt");
        try {
            // 40 KB of different data to exceed CachingOutputStream's 32 KB buffer
            String srcData = "A".repeat(40_000);
            String dstData = "B".repeat(40_000);
            Files.writeString(from, srcData);
            Files.writeString(to, dstData);

            boolean copied = FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.CONTENT);
            assertTrue(copied, "CONTENT should copy when contents differ");
            assertEquals(srcData, Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }
    }

    /**
     * TIMESTAMP_AND_CONTENT: copies when destination does not exist.
     * Uses large payload (&gt; 32 KB buffer) so {@code isModified()} returns the correct value.
     */
    @Test
    void changeDetectionTimestampAndContentCopiesNewFile() throws Exception {
        Path from = Files.createTempFile("cd-tsc-new-src", ".txt");
        Path to = TEST_DIRECTORY.resolve("cd-tsc-new-dst.txt");
        try {
            String srcData = "C".repeat(40_000);
            Files.writeString(from, srcData);
            Files.deleteIfExists(to);

            boolean copied =
                    FilteringUtils.copyFile(from, to, "UTF-8", NO_WRAPPERS, ChangeDetection.TIMESTAMP_AND_CONTENT);
            assertTrue(copied, "TIMESTAMP_AND_CONTENT should copy when destination does not exist");
            assertEquals(srcData, Files.readString(to, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(from);
            Files.deleteIfExists(to);
        }

    }
}
