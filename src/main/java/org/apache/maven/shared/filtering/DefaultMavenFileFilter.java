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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.sonatype.plexus.build.incremental.BuildContext;

import static java.util.Objects.requireNonNull;

/**
 * @author Olivier Lamy
 */
@Singleton
@Named
public class DefaultMavenFileFilter extends BaseFilter implements MavenFileFilter {
    private final BuildContext buildContext;

    @Inject
    public DefaultMavenFileFilter(BuildContext buildContext) {
        this.buildContext = requireNonNull(buildContext);
    }

    @Override
    public void copyFile(
            Path from,
            Path to,
            boolean filtering,
            Project mavenProject,
            List<String> filters,
            boolean escapedBackslashesInFilePath,
            String encoding,
            Session mavenSession)
            throws MavenFilteringException {
        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setMavenProject(mavenProject);
        mre.setFileFilters(filters);
        mre.setEscapeWindowsPaths(escapedBackslashesInFilePath);
        mre.setMavenSession(mavenSession);
        mre.setInjectProjectBuildFilters(true);

        List<FilterWrapper> filterWrappers = getDefaultFilterWrappers(mre);
        copyFile(from, to, filtering, filterWrappers, encoding);
    }

    @Override
    public void copyFile(MavenFileFilterRequest mavenFileFilterRequest) throws MavenFilteringException {
        List<FilterWrapper> filterWrappers = getDefaultFilterWrappers(mavenFileFilterRequest);

        copyFile(
                mavenFileFilterRequest.getFrom(),
                mavenFileFilterRequest.getTo(),
                mavenFileFilterRequest.isFiltering(),
                filterWrappers,
                mavenFileFilterRequest.getEncoding());
    }

    @Override
    public void copyFile(Path from, Path to, boolean filtering, List<FilterWrapper> filterWrappers, String encoding)
            throws MavenFilteringException {
        try {
            if (filtering) {
                getLogger().debug("filtering {} to {}", from, to);
                FilterWrapper[] array = filterWrappers.toArray(new FilterWrapper[0]);
                FilteringUtils.copyFile(from, to, encoding, array, false);
            } else {
                getLogger().debug("copy {} to {}", from, to);
                FilteringUtils.copyFile(from, to, encoding, new FilterWrapper[0], false);
            }

            buildContext.refresh(to.toFile());
        } catch (IOException e) {
            throw new MavenFilteringException(
                    (filtering ? "filtering " : "copying ") + from + " to " + to + " failed with "
                            + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
        }
    }
}
