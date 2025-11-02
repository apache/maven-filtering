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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.testing.MavenDITest;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.di.Injector;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonatype.plexus.build.incremental.ThreadBuildContext;

import static org.apache.maven.api.di.testing.MavenDIExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MavenDITest
class IncrementalResourceFilteringTest {

    final Path baseDirectory = Paths.get(getBasedir());
    final Path outputDirectory = baseDirectory.resolve("target/IncrementalResourceFilteringTest");
    final Path unitDirectory = baseDirectory.resolve("src/test/units-files/incremental");
    final Path filters = unitDirectory.resolve("filters.txt");
    final Path inputFile01 = unitDirectory.resolve("files/file01.txt");
    Path inputFile02 = unitDirectory.resolve("files/file02.txt");
    final Path outputFile01 = outputDirectory.resolve("file01.txt");
    final Path outputFile02 = outputDirectory.resolve("file02.txt");

    @Inject
    Injector container;

    @BeforeEach
    protected void setUp() throws Exception {
        FileUtils.deleteDirectory(outputDirectory.toFile());
        Files.createDirectories(outputDirectory);
    }

    @AfterEach
    protected void tearDown() {
        ThreadBuildContext.setThreadBuildContext(null);
    }

    @Test
    void simpleIncrementalFiltering() throws Exception {
        // run full build first
        filter("time");

        assertTime("time", "file01.txt");
        assertTime("time", "file02.txt");

        // only one file is expected to change
        Set<Path> changedFiles = new HashSet<>();
        changedFiles.add(inputFile01);

        TestIncrementalBuildContext ctx = new TestIncrementalBuildContext(baseDirectory, changedFiles);
        ThreadBuildContext.setThreadBuildContext(ctx);

        filter("notime");
        assertTime("notime", "file01.txt");
        assertTime("time", "file02.txt"); // this one is unchanged

        assertTrue(ctx.getRefreshFiles().contains(outputFile01));

        // only one file is expected to change
        Set<Path> deletedFiles = new HashSet<>();
        deletedFiles.add(inputFile01);

        ctx = new TestIncrementalBuildContext(baseDirectory, null, deletedFiles);
        ThreadBuildContext.setThreadBuildContext(ctx);

        filter("moretime");
        assertFalse(outputFile01.toFile().exists());
        assertTime("time", "file02.txt"); // this one is unchanged

        assertTrue(ctx.getRefreshFiles().contains(outputFile01));
    }

    @Test
    void outputChange() throws Exception {
        // run full build first
        filter("time");

        // all files are reprocessed after contents of output directory changed (e.g. was deleted)
        Set<Path> changedFiles = new HashSet<>();
        changedFiles.add(outputDirectory);
        TestIncrementalBuildContext ctx = new TestIncrementalBuildContext(baseDirectory, changedFiles);
        ThreadBuildContext.setThreadBuildContext(ctx);

        filter("notime");
        assertTime("notime", "file01.txt");
        assertTime("notime", "file02.txt");

        assertTrue(ctx.getRefreshFiles().contains(outputFile01));
        assertTrue(ctx.getRefreshFiles().contains(outputFile02));
    }

    @Test
    void filterChange() throws Exception {
        // run full build first
        filter("time");

        // all files are reprocessed after content of filters changes
        Set<Path> changedFiles = new HashSet<>();
        changedFiles.add(filters);
        TestIncrementalBuildContext ctx = new TestIncrementalBuildContext(baseDirectory, changedFiles);
        ThreadBuildContext.setThreadBuildContext(ctx);

        filter("notime");
        assertTime("notime", "file01.txt");
        assertTime("notime", "file02.txt");

        assertTrue(ctx.getRefreshFiles().contains(outputFile01));
        assertTrue(ctx.getRefreshFiles().contains(outputFile02));
    }

    @Test
    void filterDeleted() throws Exception {
        // run full build first
        filter("time");

        // all files are reprocessed after content of filters changes
        Set<Path> deletedFiles = new HashSet<>();
        deletedFiles.add(filters);
        TestIncrementalBuildContext ctx = new TestIncrementalBuildContext(unitDirectory, null, deletedFiles);
        ThreadBuildContext.setThreadBuildContext(ctx);

        filter("notime");
        assertTime("notime", "file01.txt");
        assertTime("notime", "file02.txt");

        assertTrue(ctx.getRefreshFiles().contains(outputFile01));
        assertTrue(ctx.getRefreshFiles().contains(outputFile02));
    }

    private void assertTime(String time, String relpath) throws IOException {
        Properties properties = new Properties();

        try (InputStream is = Files.newInputStream(outputDirectory.resolve(relpath))) {
            properties.load(is);
        }

        assertEquals(time, properties.getProperty("time"));
    }

    private void filter(String time) throws Exception {
        Path baseDir = Paths.get(getBasedir());
        ProjectStub mavenProject = new ProjectStub().setBasedir(baseDir);
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        mavenProject.addProperty("time", time);
        mavenProject.addProperty("java.version", "zloug");
        MavenResourcesFiltering mavenResourcesFiltering = container.getInstance(MavenResourcesFiltering.class);

        String unitFilesDir = unitDirectory.resolve("files").toString();

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(unitDirectory.resolve("filters.txt").toString());

        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setResources(resources);
        mre.setOutputDirectory(outputDirectory);
        mre.setEncoding("UTF-8");
        mre.setMavenProject(mavenProject);
        mre.setFilters(filtersFile);
        mre.setNonFilteredFileExtensions(Collections.emptyList());
        mre.setMavenSession(new StubSession());
        mre.setUseDefaultFilterWrappers(true);

        mavenResourcesFiltering.filterResources(mre);
    }
}
