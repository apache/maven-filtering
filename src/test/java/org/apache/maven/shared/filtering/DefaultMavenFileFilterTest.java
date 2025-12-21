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
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author Olivier Lamy
 *
 */
@PlexusTest
class DefaultMavenFileFilterTest {

    @Inject
    MavenFileFilter mavenFileFilter;

    File to = new File(getBasedir(), "target/reflection-test.properties");

    @BeforeEach
    void setUp() throws Exception {
        Files.deleteIfExists(to.toPath());
    }

    @Test
    void overwriteFile() throws Exception {
        File from = new File(getBasedir(), "src/test/units-files/reflection-test.properties");

        mavenFileFilter.copyFile(from, to, false, null, null);

        from = new File(getBasedir(), "src/test/units-files/reflection-test-older.properties");

        // very old file :-)
        from.setLastModified(1);

        to.setLastModified(System.currentTimeMillis());

        mavenFileFilter.copyFile(from, to, false, null, null);

        Properties properties = PropertyUtils.loadPropertyFile(to, null);
        assertEquals("older file", properties.getProperty("version"));
    }

    @Test
    void nullSafeDefaultFilterWrappers() throws Exception {
        mavenFileFilter.getDefaultFilterWrappers(null, null, false, null, null);
        // shouldn't fail
    }

    @Test
    void multiFilterFileInheritance() throws Exception {
        DefaultMavenFileFilter mavenFileFilter = new DefaultMavenFileFilter(mock(BuildContext.class));

        File testDir = new File(getBasedir(), "src/test/units-files/MSHARED-177");

        List<String> filters = new ArrayList<>();

        filters.add(new File(testDir, "first_filter_file.properties").getAbsolutePath());
        filters.add(new File(testDir, "second_filter_file.properties").getAbsolutePath());
        filters.add(new File(testDir, "third_filter_file.properties").getAbsolutePath());

        final Properties filterProperties = new Properties();

        mavenFileFilter.loadProperties(filterProperties, new File(getBasedir()), filters, new Properties());

        assertEquals("first and second", filterProperties.getProperty("third_filter_key"));
    }

    // MSHARED-161: DefaultMavenFileFilter.getDefaultFilterWrappers loads
    // filters from the current directory instead of using basedir
    @Test
    void mavenBasedir() throws Exception {
        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        req.setFileFilters(Collections.singletonList("src/main/filters/filefilter.properties"));

        MavenProject mavenProject = new StubMavenProject(new File("src/test/units-files/MSHARED-161"));
        mavenProject.getBuild().setFilters(Collections.singletonList("src/main/filters/buildfilter.properties"));
        req.setMavenProject(mavenProject);
        req.setInjectProjectBuildFilters(true);

        List<FilterWrapper> wrappers = mavenFileFilter.getDefaultFilterWrappers(req);

        try (Reader reader = wrappers.get(0).getReader(new StringReader("${filefilter} ${buildfilter}"))) {
            assertEquals("true true", IOUtils.toString(reader));
        }
    }

    // MSHARED-198: custom delimiters doesn't work as expected
    @Test
    void customDelimiters() throws Exception {
        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        Properties additionalProperties = new Properties();
        additionalProperties.setProperty("FILTER.a.ME", "DONE");
        req.setAdditionalProperties(additionalProperties);
        req.setDelimiters(new LinkedHashSet<>(Arrays.asList("aaa*aaa", "abc*abc")));

        List<FilterWrapper> wrappers = mavenFileFilter.getDefaultFilterWrappers(req);

        Reader reader = wrappers.get(0).getReader(new StringReader("aaaFILTER.a.MEaaa"));
        assertEquals("DONE", IOUtils.toString(reader));

        reader = wrappers.get(0).getReader(new StringReader("abcFILTER.a.MEabc"));
        assertEquals("DONE", IOUtils.toString(reader));
    }

    // MSHARED-199: Filtering doesn't work if 2 delimiters are used on the same line, the first one being left open
    @Test
    void lineWithSingleAtAndExpression() throws Exception {
        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        Properties additionalProperties = new Properties();
        additionalProperties.setProperty("foo", "bar");
        req.setAdditionalProperties(additionalProperties);

        List<FilterWrapper> wrappers = mavenFileFilter.getDefaultFilterWrappers(req);

        try (Reader reader = wrappers.get(0).getReader(new StringReader("toto@titi.com ${foo}"))) {
            assertEquals("toto@titi.com bar", IOUtils.toString(reader));
        }
    }

    @Test
    void interpolatorCustomizer() throws Exception {
        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        req.setInterpolatorCustomizer(i -> {
            i.addValueSource(new AbstractValueSource(false) {

                @Override
                public Object getValue(String expression) {
                    if (expression.equals("foo")) {
                        return "bar";
                    }
                    return null;
                }
            });
        });

        List<FilterWrapper> wrappers = mavenFileFilter.getDefaultFilterWrappers(req);

        try (Reader reader = wrappers.get(0).getReader(new StringReader("toto@titi.com ${foo}"))) {
            assertEquals("toto@titi.com bar", IOUtils.toString(reader));
        }
    }
}
