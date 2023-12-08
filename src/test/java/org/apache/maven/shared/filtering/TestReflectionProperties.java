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
import java.util.Properties;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.Test;

import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Olivier Lamy
 * @since 1.0-beta-1
 *
 */
@PlexusTest
class TestReflectionProperties {

    @Inject
    MavenFileFilter mavenFileFilter;

    @Test
    void simpleFiltering() throws Exception {
        MavenProject mavenProject = new MavenProject();
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        Properties userProperties = new Properties();
        userProperties.setProperty("foo", "bar");

        File from = new File(getBasedir() + "/src/test/units-files/reflection-test.properties");
        File to = new File(getBasedir() + "/target/reflection-test.properties");

        if (to.exists()) {
            to.delete();
        }

        mavenFileFilter.copyFile(from, to, true, mavenProject, null, false, null, new StubMavenSession(userProperties));

        Properties reading = new Properties();

        try (FileInputStream readFileInputStream = new FileInputStream(to)) {
            reading.load(readFileInputStream);
        }

        assertEquals("1.0", reading.get("version"));
        assertEquals("org.apache", reading.get("groupId"));
        assertEquals("bar", reading.get("foo"));
        assertEquals("none filtered", reading.get("none"));
    }

    @Test
    void simpleNonFiltering() throws Exception {

        MavenProject mavenProject = new MavenProject();
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        Properties userProperties = new Properties();
        userProperties.setProperty("foo", "bar");

        File from = new File(getBasedir() + "/src/test/units-files/reflection-test.properties");
        File to = new File(getBasedir() + "/target/reflection-test.properties");

        if (to.exists()) {
            to.delete();
        }

        mavenFileFilter.copyFile(
                from, to, false, mavenProject, null, false, null, new StubMavenSession(userProperties));

        Properties reading = new Properties();

        try (FileInputStream readFileInputStream = new FileInputStream(to); ) {
            reading.load(readFileInputStream);
        }

        assertEquals("${pom.version}", reading.get("version"));
        assertEquals("${pom.groupId}", reading.get("groupId"));
        assertEquals("${foo}", reading.get("foo"));
        assertEquals("none filtered", reading.get("none"));
    }
}
