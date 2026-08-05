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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.testing.MavenDITest;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.di.Injector;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.di.testing.MavenDIExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Olivier Lamy
 *
 * @since 1.0-beta-1
 */
@MavenDITest
class DefaultMavenResourcesFilteringTest {

    @Inject
    Injector container;

    private final Path outputDirectory = Paths.get(getBasedir(), "target/DefaultMavenResourcesFilteringTest");
    private final Path baseDir = Paths.get(getBasedir());
    private final ProjectStub mavenProject = new ProjectStub().setBasedir(baseDir);
    private MavenResourcesFiltering mavenResourcesFiltering;

    @BeforeEach
    protected void setUp() throws Exception {
        IOUtils.deleteDirectory(outputDirectory);
        Files.createDirectories(outputDirectory);

        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        mavenResourcesFiltering = container.getInstance(MavenResourcesFiltering.class);
    }

    @Test
    void simpleFiltering() throws Exception {
        mavenProject.addProperty("foo", "bar");
        mavenProject.addProperty("java.version", "zloug");

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        Path initialImageFile = Paths.get(unitFilesDir, "happy_duke.gif");

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        List<String> nonFilteredFileExtensions = Collections.singletonList("gif");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                nonFilteredFileExtensions,
                new StubSession());
        mavenResourcesExecution.setUseDefaultFilterWrappers(true);
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        assertFiltering(initialImageFile, false, false);
    }

    @Test
    void sessionFiltering() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/session-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);

        List<String> filtersFile = new ArrayList<>();

        Settings settings = Settings.newBuilder()
                .localRepository(System.getProperty(
                        "localRepository", System.getProperty("maven.repo.local", "/path/to/local/repo")))
                .build();

        StubSession session = new StubSession(settings);

        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setResources(resources);
        mre.setOutputDirectory(outputDirectory);
        mre.setEncoding("UTF-8");
        mre.setMavenProject(mavenProject);
        mre.setFilters(filtersFile);
        mre.setNonFilteredFileExtensions(Collections.emptyList());
        mre.setMavenSession(session);
        mre.setUseDefaultFilterWrappers(true);

        mavenResourcesFiltering.filterResources(mre);

        Properties result = new Properties();

        try (InputStream in = Files.newInputStream(outputDirectory.resolve("session-filter-target.txt"))) {
            result.load(in);
        }

        assertEquals(settings.getLocalRepository(), result.getProperty("session.settings.local.repo"));
        assertEquals(settings.getLocalRepository(), result.getProperty("settings.local.repo"));
        assertEquals(settings.getLocalRepository(), result.getProperty("local.repo"));
    }

    @Test
    void withMavenResourcesExecution() throws Exception {
        mavenProject.addProperty("foo", "bar");
        mavenProject.addProperty("java.version", "zloug");

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        Path initialImageFile = Paths.get(unitFilesDir, "happy_duke.gif");

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        List<String> nonFilteredFileExtensions = Collections.singletonList("gif");
        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                nonFilteredFileExtensions,
                new StubSession());
        mavenResourcesExecution.setEscapeString("\\");
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);
        assertFiltering(initialImageFile, true, false);
    }

    @Test
    void withMavenResourcesExecutionWithAdditionalProperties() throws Exception {
        mavenProject.addProperty("foo", "bar");
        mavenProject.addProperty("java.version", "zloug");

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        Path initialImageFile = Paths.get(unitFilesDir, "happy_duke.gif");

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        List<String> nonFilteredFileExtensions = Collections.singletonList("gif");
        Properties additionalProperties = new Properties();
        additionalProperties.put("greatDate", "1973-06-14");
        additionalProperties.put("pom.version", "99.00");
        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                nonFilteredFileExtensions,
                new StubSession());
        mavenResourcesExecution.setAdditionalProperties(additionalProperties);
        mavenResourcesExecution.setEscapeString("\\");
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);
        assertFiltering(initialImageFile, true, true);
    }

    private void assertFiltering(Path initialImageFile, boolean escapeTest, boolean additionalProperties)
            throws Exception {
        assertEquals(7, list(outputDirectory).size());
        Properties result = new Properties();

        try (InputStream in = Files.newInputStream(outputDirectory.resolve("empty-maven-resources-filtering.txt"))) {
            result.load(in);
        }

        assertTrue(result.isEmpty());

        result = new Properties();

        try (InputStream in = Files.newInputStream(outputDirectory.resolve("maven-resources-filtering.txt"))) {
            result.load(in);
        }

        assertFalse(result.isEmpty());

        if (additionalProperties) {
            assertEquals("1973-06-14", result.getProperty("goodDate"));
            assertEquals("99.00", result.get("version"));
        } else {
            assertEquals("1.0", result.get("version"));
        }
        assertEquals("org.apache", result.get("groupId"));
        assertEquals("bar", result.get("foo"));
        assertEquals("${foo.version}", result.get("fooVersion"));

        assertEquals("@@", result.getProperty("emptyexpression"));
        assertEquals("${}", result.getProperty("emptyexpression2"));
        assertEquals(System.getProperty("user.dir"), result.getProperty("userDir"));
        String userDir = result.getProperty("userDir");
        assertTrue(Files.exists(Paths.get(userDir)));
        assertEquals(Paths.get(System.getProperty("user.dir")), Paths.get(userDir));
        assertEquals(System.getProperty("java.version"), result.getProperty("javaVersion"));

        String userHome = result.getProperty("userHome");

        assertTrue(Files.exists(Paths.get(userHome)), "'" + userHome + "' does not exist.");
        assertEquals(Paths.get(System.getProperty("user.home")), Paths.get(userHome));

        if (escapeTest) {
            assertEquals("${java.version}", result.getProperty("escapeJavaVersion"));
            assertEquals("@user.dir@", result.getProperty("escapeuserDir"));
        }
        assertEquals(baseDir.toString(), result.getProperty("base"));
        assertEquals(Paths.get(baseDir.toString()), Paths.get(result.getProperty("base")));

        Path imageFile = outputDirectory.resolve("happy_duke.gif");
        assertTrue(Files.exists(imageFile));
        // assertEquals( initialImageFile.length(), imageFile.length() );
        assertTrue(filesAreIdentical(initialImageFile, imageFile));
    }

    @Test
    void addingTokens() throws Exception {
        mavenProject.addProperty("foo", "bar");
        mavenProject.addProperty("java.version", "zloug");

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        Path initialImageFile = Paths.get(unitFilesDir, "happy_duke.gif");

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        List<String> nonFilteredFileExtensions = Collections.singletonList("gif");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources, outputDirectory, mavenProject, "UTF-8", null, nonFilteredFileExtensions, new StubSession());

        ValueSource vs =
                new PrefixedObjectValueSource(mavenResourcesExecution.getProjectStartExpressions(), mavenProject, true);

        mavenResourcesExecution.addFilerWrapperWithEscaping(vs, "@", "@", null, false);

        mavenResourcesFiltering.filterResources(mavenResourcesExecution);
        Properties result =
                PropertyUtils.loadPropertyFile(outputDirectory.resolve("maven-resources-filtering.txt"), null);
        assertFalse(result.isEmpty());
        assertEquals(mavenProject.getName(), result.get("pomName"));
        assertFiltering(initialImageFile, false, false);
    }

    @Test
    void noFiltering() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        Path initialImageFile = Paths.get(unitFilesDir, "happy_duke.gif");

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);

        resource.setDirectory(unitFilesDir);
        resource.setFiltering(false);

        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setResources(resources);
        mre.setOutputDirectory(outputDirectory);
        mre.setEncoding("UTF-8");
        mre.setMavenProject(mavenProject);
        mre.setFilters(null);
        mre.setNonFilteredFileExtensions(Collections.emptyList());
        mre.setMavenSession(new StubSession());

        mavenResourcesFiltering.filterResources(mre);

        assertEquals(7, list(outputDirectory).size());
        Properties result =
                PropertyUtils.loadPropertyFile(outputDirectory.resolve("empty-maven-resources-filtering.txt"), null);
        assertTrue(result.isEmpty());

        result = PropertyUtils.loadPropertyFile(outputDirectory.resolve("maven-resources-filtering.txt"), null);
        assertFalse(result.isEmpty());

        assertEquals("${pom.version}", result.get("version"));
        assertEquals("${pom.groupId}", result.get("groupId"));
        assertEquals("${foo}", result.get("foo"));
        assertEquals("@@", result.getProperty("emptyexpression"));
        assertEquals("${}", result.getProperty("emptyexpression2"));
        Path imageFile = outputDirectory.resolve("happy_duke.gif");
        assertTrue(filesAreIdentical(initialImageFile, imageFile));
    }

    private static boolean filesAreIdentical(Path expected, Path current) throws IOException {
        if (Files.size(expected) != Files.size(current)) {
            return false;
        }

        byte[] expectedBuffer = Files.readAllBytes(expected);
        byte[] currentBuffer = Files.readAllBytes(current);
        return Arrays.equals(expectedBuffer, currentBuffer);
    }

    @Test
    void includeOneFile() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addInclude("includ*");

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                Collections.emptyList(),
                new StubSession());
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        List<Path> files = list(outputDirectory);
        assertEquals(1, files.size());
        assertEquals("includefile.txt", filename(files.get(0)));
    }

    @Test
    void includeOneFileAndDirectory() throws Exception {
        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addInclude("includ*");
        resource.addInclude("**/includ*");

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                Collections.emptyList(),
                new StubSession());
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        List<Path> files = list(outputDirectory);
        assertNotNull(files);
        assertEquals(2, files.size());
        Path includeFile = outputDirectory.resolve("includefile.txt");
        assertTrue(Files.exists(includeFile));

        includeFile = outputDirectory.resolve("includedir/include.txt");
        assertTrue(Files.exists(includeFile));
    }

    @Test
    void flattenDirectoryStructure() throws Exception {
        Path baseDir = Paths.get(getBasedir());
        ProjectStub mavenProject = new ProjectStub().setBasedir(baseDir);
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        MavenResourcesFiltering mavenResourcesFiltering = container.getInstance(MavenResourcesFiltering.class);

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addInclude("includ*");
        resource.addInclude("**/includ*");

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                Collections.emptyList(),
                new StubSession());
        mavenResourcesExecution.setFlatten(true);
        mavenResourcesExecution.setOverwrite(true);
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        List<Path> files = list(outputDirectory);
        assertNotNull(files);
        assertEquals(2, files.size());
        Path includeFile = outputDirectory.resolve("includefile.txt");
        assertTrue(Files.exists(includeFile));

        includeFile = outputDirectory.resolve("include.txt");
        assertTrue(Files.exists(includeFile));
    }

    @Test
    void flattenDirectoryStructureWithoutOverride() {
        Path baseDir = Paths.get(getBasedir());
        ProjectStub mavenProject = new ProjectStub().setBasedir(baseDir);
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        MavenResourcesFiltering mavenResourcesFiltering = container.getInstance(MavenResourcesFiltering.class);

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addInclude("includ*");
        resource.addInclude("**/includ*");

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                Collections.emptyList(),
                new StubSession());
        mavenResourcesExecution.setFlatten(true);
        mavenResourcesExecution.setOverwrite(false);
        try {
            mavenResourcesFiltering.filterResources(mavenResourcesExecution);
        } catch (MavenFilteringException e) {
            return;
        }
        fail("Copying directory structure with duplicate filename includefile.txt should have failed with overwrite");
    }

    @Test
    void excludeOneFile() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addExclude("*.gif");
        resource.addExclude("**/excludedir/**");

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                Collections.emptyList(),
                new StubSession());
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        List<Path> files = list(outputDirectory);
        assertEquals(5, files.size());
        Path includeFile = outputDirectory.resolve("includefile.txt");
        assertTrue(Files.exists(includeFile));

        includeFile = outputDirectory.resolve("includedir/include.txt");
        assertTrue(Files.exists(includeFile));

        Path imageFile = outputDirectory.resolve("happy_duke.gif");
        assertFalse(Files.exists(imageFile));

        Path excludeDir = outputDirectory.resolve("excludedir");
        assertFalse(Files.exists(excludeDir));
    }

    @Test
    void targetAbsolutePath() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addInclude("includ*");

        String targetPath = getBasedir() + "/target/testAbsolutePath/";
        Path targetPathFile = Paths.get(targetPath);
        resource.setTargetPath(targetPathFile.toAbsolutePath().toString());

        IOUtils.deleteDirectory(targetPathFile);
        Files.createDirectories(targetPathFile);

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                Collections.emptyList(),
                new StubSession());
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        List<Path> files = list(targetPathFile);
        assertEquals(1, files.size());
        assertEquals("includefile.txt", filename(files.get(0)));
    }

    @Test
    void targetPath() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addInclude("includ*");
        resource.setTargetPath("testTargetPath");
        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(
                getBasedir() + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                filtersFile,
                Collections.emptyList(),
                new StubSession());
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        Path targetPathFile = outputDirectory.resolve("testTargetPath");

        List<Path> files = list(targetPathFile);
        assertEquals(1, files.size());
        assertEquals("includefile.txt", filename(files.get(0)));
    }

    @Test
    void emptyDirectories() throws Exception {

        List<Resource> resources = new ArrayList<>();
        resources.add(new Resource() {
            {
                setDirectory(getBasedir() + "/src/test/units-files/includeEmptyDirs");
                setExcludes(List.of("**/.gitignore"));
            }
        });
        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                Collections.emptyList(),
                Collections.emptyList(),
                new StubSession());
        mavenResourcesExecution.setIncludeEmptyDirs(true);
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        List<Path> childs = list(outputDirectory);
        assertNotNull(childs);
        assertEquals(3, childs.size());

        for (Path file : childs) {
            String filename = filename(file);
            if (filename.endsWith("dir1")
                    || filename.endsWith("empty-directory")
                    || filename.endsWith("empty-directory-child")) {
                if (filename.endsWith("dir1")) {
                    assertEquals(1, list(file).size());
                    assertTrue(filename(list(file).get(0)).endsWith("foo.txt"));
                }
                if (filename.endsWith("empty-directory")) {
                    assertEquals(0, list(file).size());
                }
                if (filename.endsWith("empty-directory-child")) {
                    assertEquals(1, list(file).size());
                    assertTrue(Files.isDirectory(list(file).get(0)));
                    assertEquals(0, list(list(file).get(0)).size());
                }
            } else {
                fail("unknow child file found " + file.getFileName());
            }
        }
    }

    @Test
    void shouldReturnGitIgnoreFiles() throws Exception {
        createTestDataStructure();

        Path outputDirectory = Paths.get(getBasedir(), "target/testGitIgnoreFile");

        IOUtils.deleteDirectory(outputDirectory);
        Files.createDirectories(outputDirectory);

        List<Resource> resources = new ArrayList<>();
        resources.add(new Resource() {
            {
                setDirectory(getBasedir() + "/target/sourceTestGitIgnoreFile");
                setIncludes(List.of("**/*"));
            }
        });
        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                Collections.emptyList(),
                Collections.emptyList(),
                new StubSession());
        mavenResourcesExecution.setIncludeEmptyDirs(true);
        mavenResourcesExecution.setAddDefaultExcludes(false);
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        List<Path> children = list(outputDirectory);
        assertNotNull(children);
        assertEquals(3, children.size());

        for (Path file : children) {
            String filename = filename(file);
            if (filename.endsWith("dir1")
                    || filename.endsWith("empty-directory")
                    || filename.endsWith("empty-directory-child")) {
                if (filename.endsWith("dir1")) {
                    assertEquals(1, list(file).size());
                    assertTrue(filename(list(file).get(0)).endsWith("foo.txt"));
                }
                if (filename.endsWith("empty-directory")) {

                    assertEquals(1, list(file).size());
                    assertTrue(filename(list(file).get(0)).endsWith(".gitignore"));
                }
                if (filename.endsWith("empty-directory-child")) {
                    assertEquals(1, list(file).size());
                    assertTrue(Files.isDirectory(list(file).get(0)));
                    assertEquals(1, list(list(file).get(0)).size());

                    assertTrue(filename(list(list(file).get(0)).get(0)).endsWith(".gitignore"));
                }
            } else {
                fail("unknown child file found " + file.getFileName());
            }
        }
    }

    /**
     * The folder and file structure will be created instead of letting this resource plugin
     * copy the structure which does not work.
     */
    private static void createTestDataStructure() throws IOException {
        Path sourceDirectory = Paths.get(getBasedir(), "/target/sourceTestGitIgnoreFile");
        if (Files.exists(sourceDirectory)) {
            IOUtils.deleteDirectory(sourceDirectory);
        }

        Path dir1 = sourceDirectory.resolve("dir1");

        Files.createDirectories(dir1);
        Files.writeString(dir1.resolve("foo.txt"), "This is a Test Path", StandardCharsets.UTF_8);

        Path emptyDirectory = sourceDirectory.resolve("empty-directory");
        Files.createDirectories(emptyDirectory);

        Files.writeString(emptyDirectory.resolve(".gitignore"), "# .gitignore file", StandardCharsets.UTF_8);

        Path emptyDirectoryChild = sourceDirectory.resolve("empty-directory-child");
        Files.createDirectories(emptyDirectory);

        Path emptyDirectoryChildEmptyChild = emptyDirectoryChild.resolve("empty-child");
        Files.createDirectories(emptyDirectoryChildEmptyChild);

        Files.writeString(
                emptyDirectoryChildEmptyChild.resolve(".gitignore"), "# .gitignore file", StandardCharsets.UTF_8);
    }

    /**
     * unit test for MSHARED-81 : https://issues.apache.org/jira/browse/MSHARED-81
     */
    @Test
    void mshared81() throws Exception {
        mavenProject.addProperty("escaped", "this is escaped");
        mavenProject.addProperty("escaped.at", "this is escaped.at");
        mavenProject.addProperty("foo", "this is foo");
        mavenProject.addProperty("bar", "this is bar");

        List<Resource> resources = new ArrayList<>();
        resources.add(new Resource() {
            {
                setDirectory(getBasedir() + "/src/test/units-files/MSHARED-81/resources");
                setFiltering(false);
            }
        });
        resources.add(new Resource() {
            {
                setDirectory(getBasedir() + "/src/test/units-files/MSHARED-81/filtered");
                setFiltering(true);
            }
        });
        Path output = outputDirectory.resolve("MSHARED-81");
        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                output,
                mavenProject,
                "UTF-8",
                Collections.emptyList(),
                Collections.emptyList(),
                new StubSession());
        mavenResourcesExecution.setIncludeEmptyDirs(true);
        mavenResourcesExecution.setEscapeString("\\");

        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        Properties filteredResult = PropertyUtils.loadPropertyFile(output.resolve("filtered.properties"), null);

        Properties expectedFilteredResult = PropertyUtils.loadPropertyFile(
                Paths.get(getBasedir() + "/src/test/units-files/MSHARED-81", "expected-filtered.properties"), null);

        assertEquals(expectedFilteredResult, filteredResult);

        Properties nonFilteredResult = PropertyUtils.loadPropertyFile(output.resolve("unfiltered.properties"), null);

        Properties expectedNonFilteredResult = PropertyUtils.loadPropertyFile(
                Paths.get(getBasedir() + "/src/test/units-files/MSHARED-81/resources", "unfiltered.properties"), null);

        assertEquals(nonFilteredResult, expectedNonFilteredResult);
    }

    /**
     * unit test for MRESOURCES-230 : https://issues.apache.org/jira/browse/MRESOURCES-230
     */
    //    @Test
    //    public void testCorrectlyEscapesEscapeString()
    //        throws Exception
    //    {
    //        mavenProject.addProperty( "a", "DONE_A" );
    //
    ////
    //        List<Resource> resources = new ArrayList<Resource>();
    //        resources.add( new Resource()
    //        {
    //
    //            {
    //                setDirectory( getBasedir() + "/src/test/units-files/MRESOURCES-230" );
    //                setFiltering( true );
    //            }
    //
    //        } );
    //        resources.get( 0 ).addExclude( "expected.txt" );
    //
    //        Path output = outputDirectory.resolve( "MRESOURCES-230" );
    //        MavenResourcesExecution mavenResourcesExecution =
    //            new MavenResourcesExecution( resources, output, mavenProject, "UTF-8",
    // Collections.<String>emptyList(),
    //                                         Collections.<String>emptyList(), new StubSession() );
    //        mavenResourcesExecution.setIncludeEmptyDirs( true );
    //        mavenResourcesExecution.setEscapeString( "\\" );
    //
    //        mavenResourcesFiltering.filterResources( mavenResourcesExecution );
    //
    //        final String filtered = FileUtils.fileRead( output.resolve( "resource.txt" ), "UTF-8" );
    //        final String expected =
    //            FileUtils.fileRead( Paths.get( getBasedir() + "/src/test/units-files/MRESOURCES-230/expected.txt" ) );
    //
    //        assertEquals( expected, filtered );
    //    }

    /**
     * unit test for edge cases : https://issues.apache.org/jira/browse/MSHARED-228
     */
    @Test
    void edgeCases() throws Exception {
        mavenProject.addProperty("escaped", "this is escaped");
        mavenProject.addProperty("escaped.at", "this is escaped.at");
        mavenProject.addProperty("foo", "this is foo");
        mavenProject.addProperty("bar", "this is bar");
        mavenProject.addProperty("domain", "this.is.domain.com");
        mavenProject.addProperty(
                "com.xxxxxxx.xxxx.root.build.environment.CLOUD_AZURE_AKS_KUBERNETES_NODE_LABEL_AGENTPOOL_VALUE_PRODUCTION_XXXXXXXXXXX_NODE_IMPL_PRODUCT_SEGMENT_PROCESSOR",
                "longpropvalue");

        List<Resource> resources = new ArrayList<>();
        resources.add(new Resource() {
            {
                setDirectory(getBasedir() + "/src/test/units-files/edge-cases/resources");
                setFiltering(false);
            }
        });
        resources.add(new Resource() {
            {
                setDirectory(getBasedir() + "/src/test/units-files/edge-cases/filtered");
                setFiltering(true);
            }
        });
        Path output = outputDirectory.resolve("edge-cases");
        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                output,
                mavenProject,
                "UTF-8",
                Collections.emptyList(),
                Collections.emptyList(),
                new StubSession());
        mavenResourcesExecution.setIncludeEmptyDirs(true);
        mavenResourcesExecution.setEscapeString("\\");

        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        Properties filteredResult = PropertyUtils.loadPropertyFile(output.resolve("filtered.properties"), null);

        Properties expectedFilteredResult = PropertyUtils.loadPropertyFile(
                Paths.get(getBasedir() + "/src/test/units-files/edge-cases", "expected-filtered.properties"), null);

        assertEquals(expectedFilteredResult, filteredResult);

        Properties nonFilteredResult = PropertyUtils.loadPropertyFile(output.resolve("unfiltered.properties"), null);

        Properties expectedNonFilteredResult = PropertyUtils.loadPropertyFile(
                Paths.get(getBasedir() + "/src/test/units-files/edge-cases/resources", "unfiltered.properties"), null);

        assertEquals(nonFilteredResult, expectedNonFilteredResult);
    }

    // MSHARED-220: Apply filtering to filenames
    @Test
    void filterFileName() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-filename-filtering";

        Resource resource = new Resource();
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addInclude("**/${pom.version}*");
        resource.setTargetPath("testTargetPath");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                Collections.singletonList(resource),
                outputDirectory,
                mavenProject,
                "UTF-8",
                Collections.emptyList(),
                Collections.emptyList(),
                new StubSession());
        mavenResourcesExecution.setFilterFilenames(true);
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        Path targetPathFile = outputDirectory.resolve("testTargetPath");

        List<Path> files = list(targetPathFile);
        assertEquals(1, files.size());
        assertEquals("subfolder", filename(files.get(0)));
        assertTrue(Files.isDirectory(files.get(0)));

        files = list(files.get(0));
        assertEquals(1, files.size());
        assertEquals("1.0.txt", filename(files.get(0)));
    }

    @Test
    public void testFilterFileNameWithFileSeparatorAsEscape() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-filename-filtering";

        Resource resource = new Resource();
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.addInclude("**/${pom.version}*");
        resource.setTargetPath("testTargetPath");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                Collections.singletonList(resource),
                outputDirectory,
                mavenProject,
                "UTF-8",
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                new StubSession());
        mavenResourcesExecution.setFilterFilenames(true);

        // more likely to occur on windows, where the file
        // separator is the same as the common escape string "\"
        mavenResourcesExecution.setEscapeString(FileSystems.getDefault().getSeparator());
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        Path targetPathFile = outputDirectory.resolve("testTargetPath");

        List<Path> files = list(targetPathFile);
        assertEquals(1, files.size());
        assertEquals("subfolder", filename(files.get(0)));
        assertTrue(Files.isDirectory(files.get(0)));

        files = list(files.get(0));
        assertEquals(1, files.size());
        assertEquals("1.0.txt", filename(files.get(0)));
    }

    /**
     * MRESOURCES-171: Use correct encoding when filtering properties-files
     */
    @Test
    void filterPropertiesFiles() throws Exception {

        String unitFilesDir = getBasedir() + "/src/test/units-files/MRESOURCES-171";

        Resource resource = new Resource();
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);
        resource.setTargetPath("testFilterPropertiesFiles");

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                Collections.singletonList(resource),
                outputDirectory,
                mavenProject,
                "UTF-8",
                Collections.emptyList(),
                Collections.emptyList(),
                new StubSession());
        mavenResourcesExecution.setPropertiesEncoding("ISO-8859-1");
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        Path targetPathFile = outputDirectory.resolve("testFilterPropertiesFiles");
        assertTrue(
                contentEquals(Paths.get(unitFilesDir, "test.properties"), targetPathFile.resolve("test.properties")));
        assertTrue(contentEquals(Paths.get(unitFilesDir, "test.txt"), targetPathFile.resolve("test.txt")));
    }

    @Test
    void getEncoding() {
        Path propertiesFile = Paths.get("file.properties");
        Path regularFile = Paths.get("file.xml");

        // Properties files
        assertNull(DefaultMavenResourcesFiltering.getEncoding(propertiesFile, null, null));
        assertEquals("UTF-8", DefaultMavenResourcesFiltering.getEncoding(propertiesFile, "UTF-8", null));
        assertEquals("ISO-8859-1", DefaultMavenResourcesFiltering.getEncoding(propertiesFile, "UTF-8", "ISO-8859-1"));
        // Regular files
        assertNull(DefaultMavenResourcesFiltering.getEncoding(regularFile, null, null));
        assertEquals("UTF-8", DefaultMavenResourcesFiltering.getEncoding(regularFile, "UTF-8", null));
        assertEquals("UTF-8", DefaultMavenResourcesFiltering.getEncoding(regularFile, "UTF-8", "ISO-8859-1"));
    }

    @Test
    void isPropertiesFile() {
        // Properties files
        assertTrue(DefaultMavenResourcesFiltering.isPropertiesFile(Paths.get("file.properties")));
        assertTrue(DefaultMavenResourcesFiltering.isPropertiesFile(Paths.get("some/parent/path", "file.properties")));
        // Regular files
        assertFalse(DefaultMavenResourcesFiltering.isPropertiesFile(Paths.get("file")));
        assertFalse(DefaultMavenResourcesFiltering.isPropertiesFile(Paths.get("some/parent/path", "file")));
        assertFalse(DefaultMavenResourcesFiltering.isPropertiesFile(Paths.get("file.xml")));
        assertFalse(DefaultMavenResourcesFiltering.isPropertiesFile(Paths.get("some/parent/path", "file.xml")));
    }

    private String filename(Path file) {
        return file.getFileName().toString();
    }

    private List<Path> list(Path file) throws IOException {
        return Files.list(file).collect(Collectors.toList());
    }

    private boolean contentEquals(Path p1, Path p2) throws IOException {
        return Files.mismatch(p1, p2) < 0;
    }
}
