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

import java.util.LinkedHashSet;
import java.util.function.Consumer;

import org.codehaus.plexus.interpolation.Interpolator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MavenResourcesExecution#copyOf()}.
 */
class MavenResourcesExecutionTest {

    @Test
    void copyOfShouldPreserveFlatten() {
        MavenResourcesExecution original = new MavenResourcesExecution();
        original.setFlatten(true);

        MavenResourcesExecution copy = original.copyOf();

        Assertions.assertTrue(copy.isFlatten(), "flatten should be copied");
    }

    @Test
    void copyOfShouldPreservePropertiesEncoding() {
        MavenResourcesExecution original = new MavenResourcesExecution();
        original.setPropertiesEncoding("ISO-8859-1");

        MavenResourcesExecution copy = original.copyOf();

        Assertions.assertEquals("ISO-8859-1", copy.getPropertiesEncoding());
    }

    @Test
    void copyOfShouldPreserveDelimiters() {
        MavenResourcesExecution original = new MavenResourcesExecution();
        LinkedHashSet<String> delimiters = new LinkedHashSet<>();
        delimiters.add("@{*@}");
        delimiters.add("${*}");
        original.setDelimiters(delimiters);

        MavenResourcesExecution copy = original.copyOf();

        Assertions.assertEquals(original.getDelimiters(), copy.getDelimiters());
    }

    @Test
    void copyOfShouldPreserveInterpolatorCustomizer() {
        Consumer<Interpolator> customizer = interpolator -> {};
        MavenResourcesExecution original = new MavenResourcesExecution();
        original.setInterpolatorCustomizer(customizer);

        MavenResourcesExecution copy = original.copyOf();

        Assertions.assertSame(customizer, copy.getInterpolatorCustomizer());
    }

    @Test
    void copyOfShouldCopyAllFourMissingFields() {
        Consumer<Interpolator> customizer = interpolator -> {};
        LinkedHashSet<String> delimiters = new LinkedHashSet<>();
        delimiters.add("@{*@}");

        MavenResourcesExecution original = new MavenResourcesExecution();
        original.setFlatten(true);
        original.setPropertiesEncoding("UTF-16");
        original.setDelimiters(delimiters);
        original.setInterpolatorCustomizer(customizer);

        MavenResourcesExecution copy = original.copyOf();

        Assertions.assertTrue(copy.isFlatten());
        Assertions.assertEquals("UTF-16", copy.getPropertiesEncoding());
        Assertions.assertEquals(delimiters, copy.getDelimiters());
        Assertions.assertSame(customizer, copy.getInterpolatorCustomizer());
    }
}
