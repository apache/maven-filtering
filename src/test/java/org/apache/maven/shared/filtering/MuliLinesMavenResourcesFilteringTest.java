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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.testing.MavenDITest;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.di.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.di.testing.MavenDIExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Olivier Lamy
 *
 */
@MavenDITest
public class MuliLinesMavenResourcesFilteringTest {

    @Inject
    Injector container;

    Path outputDirectory = Paths.get(getBasedir(), "target/MuliLinesMavenResourcesFilteringTest");

    @BeforeEach
    protected void setUp() throws Exception {
        IOUtils.deleteDirectory(outputDirectory);
        Files.createDirectories(outputDirectory);
    }

    /**
     */
    @Test
    public void testFilteringTokenOnce() throws Exception {
        Path baseDir = Paths.get(getBasedir());
        ProjectStub mavenProject = new ProjectStub().setBasedir(baseDir);
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        mavenProject.addProperty("foo", "bar");
        mavenProject.addProperty("java.version", "zloug");
        MavenResourcesFiltering mavenResourcesFiltering = container.getInstance(MavenResourcesFiltering.class);

        String unitFilesDir = getBasedir() + "/src/test/units-files/MRESOURCES-104";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitFilesDir);
        resource.setFiltering(true);

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add(getBasedir() + "/src/test/units-files/MRESOURCES-104/test.properties");

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

        Properties result = new Properties();

        try (InputStream in = Files.newInputStream(outputDirectory.resolve("test.properties"))) {
            result.load(in);
        }

        // email=foo@bar.com
        // foo=${project.version}
        // bar=@project.version@
        assertEquals("1.0", result.get("foo"));
        assertEquals("1.0", result.get("bar"));
        assertEquals("foo@bar.com", result.get("email"));
    }
}
