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

import javax.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Resource;
import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Olivier Lamy
 *
 */
@PlexusTest
class MuliLinesMavenResourcesFilteringTest {

    @Inject
    MavenResourcesFiltering mavenResourcesFiltering;

    File outputDirectory = new File(getBasedir(), "target/MuliLinesMavenResourcesFilteringTest");

    @BeforeEach
    void setUp() throws Exception {
        if (outputDirectory.exists()) {
            FileUtils.deleteDirectory(outputDirectory);
        }
        outputDirectory.mkdirs();
    }

    /**
     * @throws Exception
     */
    @Test
    void filteringTokenOnce() throws Exception {
        File baseDir = new File(getBasedir());
        StubMavenProject mavenProject = new StubMavenProject(baseDir);
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        Properties projectProperties = new Properties();
        projectProperties.put("foo", "bar");
        projectProperties.put("java.version", "zloug");
        mavenProject.setProperties(projectProperties);

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
                new StubMavenSession());
        mavenResourcesExecution.setUseDefaultFilterWrappers(true);

        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        Properties result = new Properties();

        try (FileInputStream in = new FileInputStream(new File(outputDirectory, "test.properties"))) {
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
