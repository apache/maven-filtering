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
import java.util.Collections;
import java.util.List;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;

/**
 * @author Kristian Rosenvold
 */
@Singleton
@Named
public class DefaultMavenReaderFilter extends BaseFilter implements MavenReaderFilter {
    @Override
    public Reader filter(
            Reader from,
            boolean filtering,
            Project mavenProject,
            List<String> filters,
            boolean escapedBackslashesInFilePath,
            Session mavenSession)
            throws MavenFilteringException {
        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setMavenProject(mavenProject);
        mre.setFileFilters(filters);
        mre.setEscapeWindowsPaths(escapedBackslashesInFilePath);
        mre.setMavenSession(mavenSession);
        mre.setInjectProjectBuildFilters(true);

        List<FilterWrapper> filterWrappers = getDefaultFilterWrappers(mre);
        return filter(from, filtering, filterWrappers);
    }

    @Override
    public Reader filter(MavenReaderFilterRequest mavenFileFilterRequest) throws MavenFilteringException {
        List<FilterWrapper> filterWrappers = getDefaultFilterWrappers(mavenFileFilterRequest);
        return filter(mavenFileFilterRequest.getFrom(), mavenFileFilterRequest.isFiltering(), filterWrappers);
    }

    @Override
    public Reader filter(Reader from, boolean filtering, List<FilterWrapper> filterWrappers) {
        return filterWrap(from, filtering ? filterWrappers : Collections.<FilterWrapper>emptyList());
    }

    private static Reader filterWrap(Reader from, Iterable<FilterWrapper> wrappers) {
        Reader reader = from;
        for (FilterWrapper wrapper : wrappers) {
            reader = wrapper.getReader(reader);
        }
        return reader;
    }
}
