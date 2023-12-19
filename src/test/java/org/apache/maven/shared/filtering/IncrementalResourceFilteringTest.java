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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Resource;
import org.sonatype.plexus.build.incremental.ThreadBuildContext;

public class IncrementalResourceFilteringTest extends TestSupport {

    Path baseDirectory = new File(getBasedir()).toPath();
    Path outputDirectory = baseDirectory.resolve("target/IncrementalResourceFilteringTest");
    Path unitDirectory = baseDirectory.resolve("src/test/units-files/incremental");
    Path filters = unitDirectory.resolve("filters.txt");
    Path inputFile01 = unitDirectory.resolve("files/file01.txt");
    Path inputFile02 = unitDirectory.resolve("files/file02.txt");
    Path outputFile01 = outputDirectory.resolve("file01.txt");
    Path outputFile02 = outputDirectory.resolve("file02.txt");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (outputDirectory.toFile().exists()) {
            FileUtils.deleteDirectory(outputDirectory.toFile());
        }
        outputDirectory.toFile().mkdirs();
    }

    public void testSimpleIncrementalFiltering() throws Exception {
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

    public void testOutputChange() throws Exception {
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

    public void testFilterChange() throws Exception {
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

    /**
     * Check that missing targets are rebuilt even if source is not changed (MSHARED-1285)
     */
    public void testMissingTarget() throws Exception {
        // run full build first
        filter("time");

        // erase target files
        outputFile01.toFile().delete();
        outputFile02.toFile().delete();
        // report change only on one file
        Set<Path> changedFiles = new HashSet<>();
        changedFiles.add(inputFile01);
        TestIncrementalBuildContext ctx = new TestIncrementalBuildContext(baseDirectory, changedFiles);
        ThreadBuildContext.setThreadBuildContext(ctx);

        filter("time");

        assertTrue(ctx.getRefreshFiles().contains(outputFile01));
        assertTrue(ctx.getRefreshFiles().contains(outputFile02));
        assertTrue(outputFile01.toFile().exists());
        assertTrue(outputFile02.toFile().exists());
    }

    /**
     * Check that updated targets with unchanged sources are updated (MSHARED-1285)
     */
    public void testUpdatedTarget() throws Exception {
        // run full build first
        filter("time");

        // touch target file02
        FileUtils.touch(outputFile02.toFile());
        Set<Path> changedFiles = new HashSet<>();
        changedFiles.add(inputFile01);
        // report change only on target file
        changedFiles.add(outputFile02);
        TestIncrementalBuildContext ctx = new TestIncrementalBuildContext(baseDirectory, changedFiles);
        ThreadBuildContext.setThreadBuildContext(ctx);

        // both files are updated
        filter("notime");
        assertTime("notime", "file01.txt");
        assertTime("notime", "file02.txt");

        assertTrue(ctx.getRefreshFiles().contains(outputFile01));
        assertTrue(ctx.getRefreshFiles().contains(outputFile02));
    }

    public void testFilterDeleted() throws Exception {
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

        try (InputStream is =
                new FileInputStream(outputDirectory.resolve(relpath).toFile())) {
            properties.load(is);
        }

        assertEquals(time, properties.getProperty("time"));
    }

    private void filter(String time) throws Exception {
        File baseDir = new File(getBasedir());
        StubMavenProject mavenProject = new StubMavenProject(baseDir);
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        Properties projectProperties = new Properties();
        projectProperties.put("time", time);
        projectProperties.put("java.version", "zloug");
        mavenProject.setProperties(projectProperties);
        MavenResourcesFiltering mavenResourcesFiltering = lookup(MavenResourcesFiltering.class);

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
        mre.setOutputDirectory(outputDirectory.toFile());
        mre.setEncoding("UTF-8");
        mre.setMavenProject(mavenProject);
        mre.setFilters(filtersFile);
        mre.setNonFilteredFileExtensions(Collections.<String>emptyList());
        mre.setMavenSession(new StubMavenSession());
        mre.setUseDefaultFilterWrappers(true);

        mavenResourcesFiltering.filterResources(mre);
    }
}
