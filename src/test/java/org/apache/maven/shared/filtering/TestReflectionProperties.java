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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.testing.MavenDITest;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.di.Injector;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.di.testing.MavenDIExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Olivier Lamy
 * @since 1.0-beta-1
 *
 */
@MavenDITest
public class TestReflectionProperties {

    @Inject
    Injector container;

    @Test
    public void testSimpleFiltering() throws Exception {
        ProjectStub mavenProject = new ProjectStub();
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        Map<String, String> userProperties = new HashMap<>();
        userProperties.put("foo", "bar");
        MavenFileFilter mavenFileFilter = container.getInstance(MavenFileFilter.class);

        Path from = Paths.get(getBasedir() + "/src/test/units-files/reflection-test.properties");
        Path to = Paths.get(getBasedir() + "/target/reflection-test.properties");

        Files.deleteIfExists(to);

        mavenFileFilter.copyFile(from, to, true, mavenProject, null, false, null, new StubSession(userProperties));

        Properties reading = new Properties();

        try (InputStream readFileInputStream = Files.newInputStream(to)) {
            reading.load(readFileInputStream);
        }

        assertEquals("1.0", reading.get("version"));
        assertEquals("org.apache", reading.get("groupId"));
        assertEquals("bar", reading.get("foo"));
        assertEquals("none filtered", reading.get("none"));
    }

    @Test
    public void testSimpleNonFiltering() throws Exception {

        ProjectStub mavenProject = new ProjectStub();
        mavenProject.setVersion("1.0");
        mavenProject.setGroupId("org.apache");
        Map<String, String> userProperties = new HashMap<>();
        userProperties.put("foo", "bar");
        MavenFileFilter mavenFileFilter = container.getInstance(MavenFileFilter.class);

        Path from = Paths.get(getBasedir() + "/src/test/units-files/reflection-test.properties");
        Path to = Paths.get(getBasedir() + "/target/reflection-test.properties");

        Files.deleteIfExists(to);

        mavenFileFilter.copyFile(from, to, false, mavenProject, null, false, null, new StubSession(userProperties));

        Properties reading = new Properties();

        try (InputStream readFileInputStream = Files.newInputStream(to)) {
            reading.load(readFileInputStream);
        }

        assertEquals("${pom.version}", reading.get("version"));
        assertEquals("${pom.groupId}", reading.get("groupId"));
        assertEquals("${foo}", reading.get("foo"));
        assertEquals("none filtered", reading.get("none"));
    }
}
