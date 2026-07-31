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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.testing.MavenDITest;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.api.services.PathMatcherFactory;
import org.apache.maven.di.Injector;
import org.apache.maven.impl.DefaultPathMatcherFactory;
import org.apache.maven.internal.build.context.impl.DefaultBuildContext;
import org.apache.maven.internal.build.context.impl.FilesystemWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests incremental resource filtering using the Maven 4 BuildContext API.
 *
 * <p>These tests exercise the incremental code path in {@link DefaultMavenResourcesFiltering}
 * by wiring in a real {@link DefaultBuildContext} (backed by a {@link FilesystemWorkspace}).
 * This verifies:
 * <ul>
 *   <li>Initial build processes all files</li>
 *   <li>Rebuild with no changes processes nothing</li>
 *   <li>Modified input is re-processed</li>
 *   <li>Deleted input causes stale output cleanup</li>
 *   <li>Include/exclude patterns are respected</li>
 * </ul>
 */
@MavenDITest
class IncrementalResourceFilteringTest {

    @Inject
    Injector container;

    @TempDir
    Path tempDir;

    private Path sourceDir;
    private Path outputDir;
    private Path stateFile;
    private ProjectStub mavenProject;
    private PathMatcherFactory pathMatcherFactory;

    @BeforeEach
    void setUp() throws Exception {
        sourceDir = tempDir.resolve("src");
        outputDir = tempDir.resolve("output");
        stateFile = tempDir.resolve("buildstate.ctx");
        Files.createDirectories(sourceDir);
        Files.createDirectories(outputDir);

        mavenProject = new ProjectStub().setBasedir(tempDir);
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        pathMatcherFactory = new DefaultPathMatcherFactory();
    }

    @Test
    void initialBuildProcessesAllFiles() throws Exception {
        // Create two source files
        writeProperties(sourceDir.resolve("file01.txt"), "time", "initial");
        writeProperties(sourceDir.resolve("file02.txt"), "time", "initial");

        // First build — all files should be processed
        DefaultBuildContext ctx = newBuildContext();
        filterResources(ctx);
        ctx.commit(null);

        // Both outputs should exist with correct content
        assertPropertyValue("initial", outputDir.resolve("file01.txt"), "time");
        assertPropertyValue("initial", outputDir.resolve("file02.txt"), "time");
    }

    @Test
    void noChangeRebuildSkipsProcessing() throws Exception {
        // Create source files
        writeProperties(sourceDir.resolve("file01.txt"), "time", "initial");
        writeProperties(sourceDir.resolve("file02.txt"), "time", "initial");

        // Initial build
        DefaultBuildContext ctx1 = newBuildContext();
        filterResources(ctx1);
        ctx1.commit(null);

        // Record output modification times
        long mtime1 = Files.getLastModifiedTime(outputDir.resolve("file01.txt")).toMillis();
        long mtime2 = Files.getLastModifiedTime(outputDir.resolve("file02.txt")).toMillis();

        // Small delay to ensure mtime would differ if files were rewritten
        Thread.sleep(100);

        // Rebuild with no changes — outputs should NOT be rewritten
        DefaultBuildContext ctx2 = newBuildContext();
        filterResources(ctx2);
        ctx2.commit(null);

        // Outputs should still exist but NOT have been rewritten
        assertTrue(Files.exists(outputDir.resolve("file01.txt")));
        assertTrue(Files.exists(outputDir.resolve("file02.txt")));
        assertEquals(
                mtime1,
                Files.getLastModifiedTime(outputDir.resolve("file01.txt")).toMillis(),
                "file01.txt should not have been rewritten");
        assertEquals(
                mtime2,
                Files.getLastModifiedTime(outputDir.resolve("file02.txt")).toMillis(),
                "file02.txt should not have been rewritten");
    }

    @Test
    void modifiedInputIsReprocessed() throws Exception {
        // Create source files
        writeProperties(sourceDir.resolve("file01.txt"), "time", "initial");
        writeProperties(sourceDir.resolve("file02.txt"), "time", "initial");

        // Initial build
        DefaultBuildContext ctx1 = newBuildContext();
        filterResources(ctx1);
        ctx1.commit(null);

        assertPropertyValue("initial", outputDir.resolve("file01.txt"), "time");
        assertPropertyValue("initial", outputDir.resolve("file02.txt"), "time");

        // Modify only file01
        Thread.sleep(100); // ensure different mtime
        writeProperties(sourceDir.resolve("file01.txt"), "time", "modified");

        // Rebuild — only file01 should be re-processed
        DefaultBuildContext ctx2 = newBuildContext();
        filterResources(ctx2);
        ctx2.commit(null);

        assertPropertyValue("modified", outputDir.resolve("file01.txt"), "time");
        assertPropertyValue("initial", outputDir.resolve("file02.txt"), "time");
    }

    @Test
    void deletedInputCausesStaleOutputCleanup() throws Exception {
        // Create source files
        writeProperties(sourceDir.resolve("file01.txt"), "time", "initial");
        writeProperties(sourceDir.resolve("file02.txt"), "time", "initial");

        // Initial build
        DefaultBuildContext ctx1 = newBuildContext();
        filterResources(ctx1);
        ctx1.commit(null);

        assertTrue(Files.exists(outputDir.resolve("file01.txt")));
        assertTrue(Files.exists(outputDir.resolve("file02.txt")));

        // Delete file01 from source
        Files.delete(sourceDir.resolve("file01.txt"));

        // Rebuild — file01's output should be cleaned up
        DefaultBuildContext ctx2 = newBuildContext();
        filterResources(ctx2);
        ctx2.commit(null);

        assertFalse(Files.exists(outputDir.resolve("file01.txt")), "Stale output should be deleted");
        assertTrue(Files.exists(outputDir.resolve("file02.txt")), "Remaining output should still exist");
        assertPropertyValue("initial", outputDir.resolve("file02.txt"), "time");
    }

    @Test
    void includeExcludePatternsRespected() throws Exception {
        // Create source files with different extensions
        writeProperties(sourceDir.resolve("included.txt"), "key", "included");
        writeProperties(sourceDir.resolve("excluded.xml"), "key", "excluded");
        writeProperties(sourceDir.resolve("also-included.txt"), "key", "also");

        // Build with include pattern for .txt only
        DefaultBuildContext ctx = newBuildContext();

        Resource resource = new Resource();
        resource.setDirectory(sourceDir.toString());
        resource.setFiltering(false);
        resource.setIncludes(List.of("**/*.txt"));

        MavenResourcesExecution mre = createExecution(resource);
        MavenResourcesFiltering filtering = createFilteringWithBuildContext(ctx);
        filtering.filterResources(mre);
        ctx.commit(null);

        assertTrue(Files.exists(outputDir.resolve("included.txt")));
        assertTrue(Files.exists(outputDir.resolve("also-included.txt")));
        assertFalse(Files.exists(outputDir.resolve("excluded.xml")), ".xml should be excluded by pattern");
    }

    @Test
    void excludePatternsRespected() throws Exception {
        // Create source files
        writeProperties(sourceDir.resolve("keep.txt"), "key", "kept");
        Files.createDirectories(sourceDir.resolve(".git"));
        writeProperties(sourceDir.resolve(".git/config"), "key", "scm");
        writeProperties(sourceDir.resolve("skip.log"), "key", "skipped");

        // Build with exclude pattern
        DefaultBuildContext ctx = newBuildContext();

        Resource resource = new Resource();
        resource.setDirectory(sourceDir.toString());
        resource.setFiltering(false);
        resource.setExcludes(List.of("**/*.log"));

        MavenResourcesExecution mre = createExecution(resource);
        mre.setAddDefaultExcludes(true);
        MavenResourcesFiltering filtering = createFilteringWithBuildContext(ctx);
        filtering.filterResources(mre);
        ctx.commit(null);

        assertTrue(Files.exists(outputDir.resolve("keep.txt")));
        assertFalse(Files.exists(outputDir.resolve("skip.log")), ".log should be excluded");
        assertFalse(Files.exists(outputDir.resolve(".git/config")), ".git should be excluded by default");
    }

    @Test
    void newFileAddedIncrementally() throws Exception {
        // Start with one file
        writeProperties(sourceDir.resolve("file01.txt"), "time", "initial");

        // Initial build
        DefaultBuildContext ctx1 = newBuildContext();
        filterResources(ctx1);
        ctx1.commit(null);

        assertTrue(Files.exists(outputDir.resolve("file01.txt")));
        assertFalse(Files.exists(outputDir.resolve("file02.txt")));

        // Add a new file
        writeProperties(sourceDir.resolve("file02.txt"), "time", "new");

        // Rebuild — new file should be picked up
        DefaultBuildContext ctx2 = newBuildContext();
        filterResources(ctx2);
        ctx2.commit(null);

        assertTrue(Files.exists(outputDir.resolve("file01.txt")));
        assertTrue(Files.exists(outputDir.resolve("file02.txt")));
        assertPropertyValue("new", outputDir.resolve("file02.txt"), "time");
    }

    @Test
    void filteringAppliedIncrementally() throws Exception {
        // Create source file with property placeholder
        writeProperties(sourceDir.resolve("app.properties"), "version", "${project.version}");

        mavenProject.addProperty("project.version", "1.0");

        // Initial build with filtering enabled
        DefaultBuildContext ctx1 = newBuildContext();
        Resource resource = new Resource();
        resource.setDirectory(sourceDir.toString());
        resource.setFiltering(true);

        MavenResourcesExecution mre = createExecution(resource);
        mre.setUseDefaultFilterWrappers(true);
        MavenResourcesFiltering filtering = createFilteringWithBuildContext(ctx1);
        filtering.filterResources(mre);
        ctx1.commit(null);

        assertPropertyValue("1.0", outputDir.resolve("app.properties"), "version");

        // Modify the source
        Thread.sleep(100);
        writeProperties(sourceDir.resolve("app.properties"), "version", "${project.version}-SNAPSHOT");

        // Rebuild — should re-filter with the property
        DefaultBuildContext ctx2 = newBuildContext();
        mre = createExecution(resource);
        mre.setUseDefaultFilterWrappers(true);
        filtering = createFilteringWithBuildContext(ctx2);
        filtering.filterResources(mre);
        ctx2.commit(null);

        assertPropertyValue("1.0-SNAPSHOT", outputDir.resolve("app.properties"), "version");
    }

    @Test
    void subdirectoryStructurePreserved() throws Exception {
        // Create nested source files
        Files.createDirectories(sourceDir.resolve("sub/deep"));
        writeProperties(sourceDir.resolve("root.txt"), "key", "root");
        writeProperties(sourceDir.resolve("sub/nested.txt"), "key", "nested");
        writeProperties(sourceDir.resolve("sub/deep/deep.txt"), "key", "deep");

        // Build
        DefaultBuildContext ctx = newBuildContext();
        filterResources(ctx);
        ctx.commit(null);

        assertTrue(Files.exists(outputDir.resolve("root.txt")));
        assertTrue(Files.exists(outputDir.resolve("sub/nested.txt")));
        assertTrue(Files.exists(outputDir.resolve("sub/deep/deep.txt")));
        assertPropertyValue("deep", outputDir.resolve("sub/deep/deep.txt"), "key");
    }

    // --- Helpers ---

    private DefaultBuildContext newBuildContext() {
        return new DefaultBuildContext(new FilesystemWorkspace(), stateFile, new HashMap<>(), null, pathMatcherFactory);
    }

    private void filterResources(DefaultBuildContext ctx) throws MavenFilteringException {
        Resource resource = new Resource();
        resource.setDirectory(sourceDir.toString());
        resource.setFiltering(false);

        MavenResourcesExecution mre = createExecution(resource);
        MavenResourcesFiltering filtering = createFilteringWithBuildContext(ctx);
        filtering.filterResources(mre);
    }

    private MavenResourcesExecution createExecution(Resource resource) {
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);

        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setResources(resources);
        mre.setOutputDirectory(outputDir);
        mre.setEncoding("UTF-8");
        mre.setMavenProject(mavenProject);
        mre.setFilters(Collections.emptyList());
        mre.setNonFilteredFileExtensions(Collections.emptyList());
        mre.setMavenSession(new StubSession());
        return mre;
    }

    private MavenResourcesFiltering createFilteringWithBuildContext(DefaultBuildContext ctx) {
        MavenFileFilter fileFilter = container.getInstance(MavenFileFilter.class);
        return new DefaultMavenResourcesFiltering(fileFilter, ctx, pathMatcherFactory);
    }

    private static void writeProperties(Path file, String key, String value) throws IOException {
        Files.createDirectories(file.getParent());
        Properties props = new Properties();
        props.setProperty(key, value);
        try (var out = Files.newOutputStream(file)) {
            props.store(out, null);
        }
    }

    private static void assertPropertyValue(String expected, Path file, String key) throws IOException {
        assertTrue(Files.exists(file), "File should exist: " + file);
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(file)) {
            props.load(is);
        }
        assertEquals(expected, props.getProperty(key), "Property '" + key + "' in " + file.getFileName());
    }
}
