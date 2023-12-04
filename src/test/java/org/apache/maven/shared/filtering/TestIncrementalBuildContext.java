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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * {@link TestIncrementalBuildContext} mock for testing purpose. It allows to
 * check build behavior based on {@link #isUptodate(File, File)} return value.
 *
 * Constructor parameters allow to indicate folder/files to declare files as
 * modified / deleted.
 *
 * hasDelta, isUptodate, newScanner, newDeleteScanner methods output consistent
 * values based upon changedFiles / deletedFiles inputs.
 *
 * getRefreshFiles method allows to check files modified by build.
 */
public class TestIncrementalBuildContext implements BuildContext {

    private final Path basedir;
    private final Set<Path> refresh = new HashSet<Path>();
    private final Set<Path> changedFiles = new HashSet<Path>();
    private final Set<Path> deletedFiles = new HashSet<Path>();
    private final Map<String, Object> context = new HashMap<>();

    public TestIncrementalBuildContext(Path basedir, Set<Path> changedFiles) {
        this(basedir, changedFiles, null);
    }

    public TestIncrementalBuildContext(Path basedir, Set<Path> changedFiles, Set<Path> deletedFiles) {
        checkPath(basedir);
        this.basedir = basedir;
        Optional.ofNullable(changedFiles).ifPresent(this.changedFiles::addAll);
        Optional.ofNullable(deletedFiles).ifPresent(this.deletedFiles::addAll);
        this.changedFiles.forEach(TestIncrementalBuildContext::checkPath);
        this.deletedFiles.forEach(TestIncrementalBuildContext::checkPath);
    }

    public static void checkPath(Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalStateException(String.format("Absolute path are expected. Failing path %s" + path));
        }
    }

    public Set<Path> getRefreshFiles() {
        return Collections.unmodifiableSet(refresh);
    }

    /**
     * Check that relpath or parent folders of relpath is not listed in modified /
     * deleted files.
     */
    @Override
    public boolean hasDelta(String relpath) {
        Path resolved = basedir.resolve(relpath);
        Path candidate = resolved;
        boolean changed = false;
        while (candidate != null) {
            changed = changedFiles.contains(candidate) || deletedFiles.contains(candidate);
            if (changed || candidate.equals(basedir)) {
                break;
            }
            candidate = candidate.getParent();
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean hasDelta(@SuppressWarnings("rawtypes") List relpaths) {
        return ((List<String>) relpaths).stream().anyMatch(this::hasDelta);
    }

    @Override
    public boolean hasDelta(File file) {
        return hasDelta(file.getAbsolutePath());
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public Scanner newDeleteScanner(File basedir) {
        return new TestScanner(basedir, deletedFiles);
    }

    @Override
    public OutputStream newFileOutputStream(File file) throws IOException {
        refresh(file);
        return new FileOutputStream(file);
    }

    @Override
    public Scanner newScanner(final File basedir) {
        return new TestScanner(basedir, changedFiles);
    }

    @Override
    public Scanner newScanner(File basedir, boolean ignoreDelta) {
        if (ignoreDelta) {
            DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setBasedir(basedir);
            return directoryScanner;
        }

        return newScanner(basedir);
    }

    @Override
    public void refresh(File file) {
        refresh.add(file.toPath());
    }

    @Override
    public Object getValue(String key) {
        return context.get(key);
    }

    @Override
    public void setValue(String key, Object value) {
        context.put(key, value);
    }

    @Override
    public void addError(File file, int line, int column, String message, Throwable cause) {}

    @Override
    public void addWarning(File file, int line, int column, String message, Throwable cause) {}

    @Override
    public void addMessage(File file, int line, int column, String message, int severity, Throwable cause) {}

    @Override
    public void removeMessages(File file) {}

    @Override
    public boolean isUptodate(File target, File source) {
        return target != null
                && target.exists()
                && !hasDelta(target)
                && source != null
                && source.exists()
                && !hasDelta(source)
                && target.lastModified() > source.lastModified();
    }

    private static final class TestScanner implements Scanner {
        private final Path basedir;
        private final Set<Path> files = new HashSet<>();

        private TestScanner(File basedir, Set<Path> files) {
            this.basedir = basedir.toPath().toAbsolutePath();
            Optional.ofNullable(files).ifPresent(this.files::addAll);
        }

        @Override
        public void addDefaultExcludes() {}

        @Override
        public String[] getIncludedDirectories() {
            return new String[0];
        }

        @Override
        public String[] getIncludedFiles() {
            return files.stream()
                    .filter(p -> p.startsWith(basedir))
                    .map(p -> basedir.relativize(p))
                    .map(Path::toString)
                    .toArray(i -> new String[i]);
        }

        @Override
        public void scan() {}

        @Override
        public void setExcludes(String[] excludes) {}

        @Override
        public void setIncludes(String[] includes) {}

        @Override
        public File getBasedir() {
            return basedir.toFile();
        }

        @Override
        public void setFilenameComparator(Comparator<String> filenameComparator) {}
    }
}
