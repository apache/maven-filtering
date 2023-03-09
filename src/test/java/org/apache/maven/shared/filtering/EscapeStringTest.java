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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Resource;
import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Olivier Lamy
 */
@PlexusTest
public class EscapeStringTest extends TestSupport {

    Path outputDirectory = Paths.get(getBasedir(), "target/EscapeStringTest");

    Path unitDirectory = Paths.get(getBasedir(), "src/test/units-files/escape-remove-char");

    @BeforeEach
    protected void setUp() throws Exception {
        FileUtils.deleteDirectory(outputDirectory.toFile());
        Files.createDirectories(outputDirectory);
    }

    @Test
    public void testEscape() throws Exception {
        Path baseDir = Paths.get("c:\\foo\\bar");
        StubProject mavenProject = new StubProject(baseDir);
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("test project");

        Map<String, String> projectProperties = new HashMap<>();
        projectProperties.put("foo", "bar");
        projectProperties.put("java.version", "zloug");
        projectProperties.put("replaceThis", "I am the replacement");
        mavenProject.setProperties(projectProperties);
        MavenResourcesFiltering mavenResourcesFiltering = lookup(MavenResourcesFiltering.class);

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        resource.setDirectory(unitDirectory.toString());
        resource.setFiltering(true);

        List<String> filtersFile = new ArrayList<>();

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

        mavenResourcesExecution.setEscapeString("!");

        mavenResourcesFiltering.filterResources(mavenResourcesExecution);

        Path file = outputDirectory.resolve("content.xml");
        String content = FileUtils.readFileToString(file.toFile(), StandardCharsets.UTF_8);
        assertTrue(content.contains("<broken-tag>Content with replacement: I am the replacement !</broken-tag>"));
        assertTrue(
                content.contains("<broken-tag>Content with escaped replacement: Do not ${replaceThis} !</broken-tag>"));
    }
}
