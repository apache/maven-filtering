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

import java.io.File;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Resource;

/**
 * @author Mikolaj Izdebski
 */
public class InvalidMarkTest extends TestSupport {
    File outputDirectory = new File(getBasedir(), "target/LongLineTest");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (outputDirectory.exists()) {
            FileUtils.deleteDirectory(outputDirectory);
        }
        outputDirectory.mkdirs();
    }

    public void testEscape() throws Exception {
        MavenResourcesFiltering mavenResourcesFiltering = lookup(MavenResourcesFiltering.class);

        Resource resource = new Resource();
        resource.setDirectory("src/test/units-files/MSHARED-325");
        resource.setFiltering(true);

        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                Collections.singletonList(resource),
                outputDirectory,
                new StubMavenProject(new File(".")),
                "UTF-8",
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                new StubMavenSession());

        try {
            mavenResourcesFiltering.filterResources(mavenResourcesExecution);
        } catch (MavenFilteringException e) {
            fail();
        }
    }
}
