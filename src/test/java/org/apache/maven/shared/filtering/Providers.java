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

import java.util.HashMap;

import org.apache.maven.api.build.context.BuildContext;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.services.PathMatcherFactory;
import org.apache.maven.impl.DefaultPathMatcherFactory;
import org.apache.maven.internal.build.context.impl.DefaultBuildContext;
import org.apache.maven.internal.build.context.impl.FilesystemWorkspace;

/**
 * DI provider for test-scoped bindings.
 *
 * <p>Provides a real {@link BuildContext} backed by a {@link FilesystemWorkspace} so that
 * tests running with {@code @MavenDITest} can inject {@link DefaultMavenResourcesFiltering}
 * without requiring the full Maven runtime (which needs mojo execution scope infrastructure).
 * The BuildContext created here has no state file, so it treats every build as a full build —
 * all files are reported as {@code NEW}.</p>
 *
 * <p>The {@code @Priority(10)} annotation ensures this binding wins over the auto-discovered
 * {@code MavenBuildContext} from maven-core on the test classpath.</p>
 */
@Named
class Providers {

    @Provides
    @Priority(10)
    PathMatcherFactory pathMatcherFactory() {
        return new DefaultPathMatcherFactory();
    }

    @Provides
    @Priority(10)
    BuildContext buildContext() {
        // No state file (null) → every invocation is a full build (all files are NEW).
        // This matches the behavior expected by the existing non-incremental tests.
        return new DefaultBuildContext(
                new FilesystemWorkspace(), null, new HashMap<>(), null, new DefaultPathMatcherFactory());
    }
}
