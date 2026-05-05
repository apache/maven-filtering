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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Resource;
import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the regression first reported against
 * <a href="https://github.com/apache/maven-resources-plugin/issues/471">
 * maven-resources-plugin issue #471</a>.
 *
 * <p>When a single source directory is covered by two {@link Resource} entries — one with
 * {@code filtering=true} and a narrow {@code <include>}, the other with {@code filtering=false}
 * and a broad {@code <include>**</include>} — the file selected by the first entry must still be
 * filtered. From {@code maven-filtering 3.3.2} onwards (pulled in by
 * {@code maven-resources-plugin 3.4.0}), the placeholder is left as the literal {@code ${...}}
 * text instead of being replaced.</p>
 *
 * <p>The {@code static/keep-as-is.txt} file, only matched by the second (unfiltered) entry, is
 * expected to keep its placeholder verbatim — the test asserts both halves so a future fix
 * cannot trivially "always filter everything".</p>
 */
@PlexusTest
class OverlappingResourcesFilteringTest {

    private final File outputDirectory = new File(getBasedir(), "target/OverlappingResourcesFilteringTest");
    private final File baseDir = new File(getBasedir());
    private final StubMavenProject mavenProject = new StubMavenProject(baseDir);

    @Inject
    private MavenResourcesFiltering mavenResourcesFiltering;

    @BeforeEach
    void setUp() throws Exception {
        if (outputDirectory.exists()) {
            FileUtils.deleteDirectory(outputDirectory);
        }
        outputDirectory.mkdirs();

        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        mavenProject.setName("MRP-471 repro");
    }

    @Test
    void overlappingResourceEntriesShouldStillFilter() throws Exception {
        Properties projectProperties = new Properties();
        projectProperties.put("repro.value", "REPLACED_BY_FILTERING");
        mavenProject.setProperties(projectProperties);

        String unitFilesDir = getBasedir() + "/src/test/units-files/MRP-471";

        // Mirrors the failing POM configuration:
        //   <resource>
        //     <filtering>true</filtering>
        //     <directory>src/main/resources</directory>
        //     <includes><include>config/filtered.xml</include></includes>
        //   </resource>
        //   <resource>
        //     <filtering>false</filtering>
        //     <directory>src/main/resources</directory>
        //     <includes><include>**</include></includes>
        //   </resource>
        Resource filtered = new Resource();
        filtered.setDirectory(unitFilesDir);
        filtered.setFiltering(true);
        filtered.addInclude("config/filtered.xml");

        Resource unfiltered = new Resource();
        unfiltered.setDirectory(unitFilesDir);
        unfiltered.setFiltering(false);
        unfiltered.addInclude("**");

        List<Resource> resources = Arrays.asList(filtered, unfiltered);

        MavenResourcesExecution execution = new MavenResourcesExecution(
                resources,
                outputDirectory,
                mavenProject,
                "UTF-8",
                Collections.emptyList(),
                Collections.emptyList(),
                new StubMavenSession());
        execution.setUseDefaultFilterWrappers(true);

        mavenResourcesFiltering.filterResources(execution);

        File filteredOut = new File(outputDirectory, "config/filtered.xml");
        File unfilteredOut = new File(outputDirectory, "static/keep-as-is.txt");
        assertTrue(filteredOut.isFile(), "expected " + filteredOut + " to be copied");
        assertTrue(unfilteredOut.isFile(), "expected " + unfilteredOut + " to be copied");

        String filteredContent = new String(Files.readAllBytes(filteredOut.toPath()), StandardCharsets.UTF_8);
        String unfilteredContent = new String(Files.readAllBytes(unfilteredOut.toPath()), StandardCharsets.UTF_8);

        // The placeholder targeted by the filtered <resource> entry must be replaced.
        // Regression on maven-filtering >= 3.3.2: the literal ${repro.value} is left in place.
        assertTrue(
                filteredContent.contains("REPLACED_BY_FILTERING"),
                "config/filtered.xml should have ${repro.value} replaced; got:\n" + filteredContent);
        assertFalse(
                filteredContent.contains("${repro.value}"),
                "config/filtered.xml still contains the literal placeholder — see "
                        + "https://github.com/apache/maven-resources-plugin/issues/471\n"
                        + filteredContent);

        // The second (unfiltered) entry must leave files matched only by it untouched —
        // this guards against a fix that just turns filtering on for everything.
        assertTrue(
                unfilteredContent.contains("${repro.value}"),
                "static/keep-as-is.txt is matched by filtering=false and must keep the literal "
                        + "placeholder; got:\n" + unfilteredContent);
    }
}
