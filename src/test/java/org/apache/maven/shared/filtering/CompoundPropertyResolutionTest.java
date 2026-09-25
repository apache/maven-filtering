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

import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.testing.MavenDITest;
import org.apache.maven.di.Injector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for MRESOURCES-254: filtering of compound properties using properties added by other
 * plugins fails when the default {@code ${*}} delimiter is disabled.
 *
 * <p>A "compound property" is a POM property whose value references another property using
 * Maven's native {@code ${key}} syntax, e.g.:
 * <pre>
 *   &lt;properties&gt;
 *     &lt;version&gt;1.0-${buildNumber}&lt;/version&gt;
 *   &lt;/properties&gt;
 * </pre>
 * where {@code buildNumber} is injected at runtime by a plugin (e.g. buildnumber-maven-plugin).
 * Because the property is set after model interpolation, its value is stored as the literal
 * string {@code "1.0-${buildNumber}"}.  When the user configures only a custom delimiter
 * (e.g. {@code @@}) and disables the default {@code ${}}, the compound value must still be
 * fully resolved.
 */
@MavenDITest
class CompoundPropertyResolutionTest {

    @Inject
    Injector container;

    /**
     * Baseline: with default delimiters enabled, compound properties are resolved correctly.
     * This test exercises the existing behaviour and must continue to pass.
     */
    @Test
    void compoundPropertyResolvesWithDefaultDelimiters() throws Exception {
        MavenFileFilter filter = container.getInstance(MavenFileFilter.class);

        // "runtimeProp" simulates a runtime plugin property (e.g. buildnumber).
        // "compound" simulates a POM property that references "runtimeProp" via ${}.
        Properties additionalProperties = new Properties();
        additionalProperties.setProperty("runtimeProp", "42");
        additionalProperties.setProperty("compound", "prefix-${runtimeProp}");

        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        req.setAdditionalProperties(additionalProperties);
        // Default delimiters include ${*}

        List<FilterWrapper> wrappers = filter.getDefaultFilterWrappers(req);

        try (Reader reader = wrappers.get(0).getReader(new StringReader("${compound}"))) {
            assertEquals("prefix-42", IOUtils.toString(reader));
        }
    }

    /**
     * MRESOURCES-254: compound property must also resolve when only a custom delimiter is
     * configured and {@code useDefaultDelimiters} is {@code false}.
     *
     * <p>The resource file uses {@code @compound@}; the expected result is {@code prefix-42},
     * not the literal {@code prefix-${runtimeProp}}.
     */
    @Test
    void compoundPropertyResolvesWithCustomDelimiterOnly() throws Exception {
        MavenFileFilter filter = container.getInstance(MavenFileFilter.class);

        Properties additionalProperties = new Properties();
        additionalProperties.setProperty("runtimeProp", "42");
        additionalProperties.setProperty("compound", "prefix-${runtimeProp}");

        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        req.setAdditionalProperties(additionalProperties);
        // Custom delimiter only — default ${*} is NOT added.
        req.setDelimiters(new LinkedHashSet<>(List.of("@")));

        List<FilterWrapper> wrappers = filter.getDefaultFilterWrappers(req);

        try (Reader reader = wrappers.get(0).getReader(new StringReader("@compound@"))) {
            assertEquals("prefix-42", IOUtils.toString(reader));
        }
    }

    /**
     * Variant: three-level chain — A references B which references C.
     * Verifies that recursive ${} resolution works transitively.
     */
    @Test
    void deeplyCompoundPropertyResolvesWithCustomDelimiterOnly() throws Exception {
        MavenFileFilter filter = container.getInstance(MavenFileFilter.class);

        Properties additionalProperties = new Properties();
        additionalProperties.setProperty("base", "hello");
        additionalProperties.setProperty("mid", "${base}-world");
        additionalProperties.setProperty("top", "say:${mid}");

        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        req.setAdditionalProperties(additionalProperties);
        req.setDelimiters(new LinkedHashSet<>(List.of("@")));

        List<FilterWrapper> wrappers = filter.getDefaultFilterWrappers(req);

        try (Reader reader = wrappers.get(0).getReader(new StringReader("@top@"))) {
            assertEquals("say:hello-world", IOUtils.toString(reader));
        }
    }

    /**
     * Cycle guard: a property that references itself must not loop infinitely.
     * The unresolved expression is left as-is, matching the behaviour of
     * {@link PropertyUtils} for circular references.
     */
    @Test
    void circularPropertyReferenceDoesNotLoop() throws Exception {
        MavenFileFilter filter = container.getInstance(MavenFileFilter.class);

        Properties additionalProperties = new Properties();
        additionalProperties.setProperty("loop", "${loop}");

        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        req.setAdditionalProperties(additionalProperties);
        req.setDelimiters(new LinkedHashSet<>(List.of("@")));

        List<FilterWrapper> wrappers = filter.getDefaultFilterWrappers(req);

        // Must return within a reasonable time and not throw.
        try (Reader reader = wrappers.get(0).getReader(new StringReader("@loop@"))) {
            // Circular reference: value is left as the original raw value.
            assertEquals("${loop}", IOUtils.toString(reader));
        }
    }

    /**
     * Compound property resolution must also work when both custom and default delimiters
     * are active simultaneously (regression guard).
     */
    @Test
    void compoundPropertyResolvesWithBothCustomAndDefaultDelimiters() throws Exception {
        MavenFileFilter filter = container.getInstance(MavenFileFilter.class);

        Properties additionalProperties = new Properties();
        additionalProperties.setProperty("runtimeProp", "42");
        additionalProperties.setProperty("compound", "prefix-${runtimeProp}");

        AbstractMavenFilteringRequest req = new AbstractMavenFilteringRequest();
        req.setAdditionalProperties(additionalProperties);
        req.setDelimiters(new LinkedHashSet<>(Arrays.asList("@", "${*}")));

        List<FilterWrapper> wrappers = filter.getDefaultFilterWrappers(req);

        try (Reader reader = wrappers.get(0).getReader(new StringReader("@compound@ ${compound}"))) {
            assertEquals("prefix-42 prefix-42", IOUtils.toString(reader));
        }
    }
}
