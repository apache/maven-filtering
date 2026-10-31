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

import java.util.Arrays;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/**
 * @author Karl Heinz Marbaise <a href="mailto:khmarbaise@apache.org">khmarbaise@apache.org</a>.
 */
class AbstractMavenFilteringRequestTest {

    private AbstractMavenFilteringRequest request = new AbstractMavenFilteringRequest();
    private LinkedHashSet<String> delimiters = new LinkedHashSet<>();

    @Test
    void setDelimitersShouldNotChangeAnythingIfUsingNull() {
        request.setDelimiters(null, false);
        assertIterableEquals(Arrays.asList("${*}", "@"), request.getDelimiters());
    }

    @Test
    void setDelimitersShouldNotChangeAnythingIfUsingEmpty() {
        request.setDelimiters(delimiters, false);
        assertIterableEquals(Arrays.asList("${*}", "@"), request.getDelimiters());
    }

    @Test
    void setDelimitersShouldAddOnlyTheGivenDelimiter() {
        delimiters.add("test");
        request.setDelimiters(delimiters, false);
        assertIterableEquals(Arrays.asList("test"), request.getDelimiters());
    }

    @Test
    void setDelimitersShouldAddDefaultDelimitersForNullElements() {
        delimiters.add("test");
        delimiters.add(null);
        delimiters.add("second");
        request.setDelimiters(delimiters, false);
        assertIterableEquals(Arrays.asList("test", "${*}", "second"), request.getDelimiters());
    }

    @Test
    void setDelimitersShouldAddDefaultDelimitersIfUseDefaultDelimitersIfNullGiven() {
        request.setDelimiters(null, true);
        assertIterableEquals(Arrays.asList("${*}", "@"), request.getDelimiters());
    }

    @Test
    void setDelimitersShouldAddDefaultDelimitersIfUseDefaultDelimitersIfNotNullGiven() {
        LinkedHashSet<String> delimiters = new LinkedHashSet<>();
        request.setDelimiters(delimiters, true);
        assertIterableEquals(Arrays.asList("${*}", "@"), request.getDelimiters());
    }

    @Test
    void setDelimitersShouldAddDefaultDelimitersIfUseDefaultDelimitersIfSingleElementIsGiven() {
        delimiters.add("test");
        request.setDelimiters(delimiters, true);
        assertIterableEquals(Arrays.asList("${*}", "@", "test"), request.getDelimiters());
    }

    @Test
    void setDelimitersShouldAddDefaultDelimitersForNullElement() {
        delimiters.add("test");
        delimiters.add(null);
        delimiters.add("second");
        request.setDelimiters(delimiters, true);
        assertIterableEquals(Arrays.asList("${*}", "@", "test", "second"), request.getDelimiters());
    }
}
