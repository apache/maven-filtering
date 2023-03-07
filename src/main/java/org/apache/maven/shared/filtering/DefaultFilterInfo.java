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

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * @author Kristian Rosenvold
 */
public interface DefaultFilterInfo {

    /**
     * @param mavenProject The maven project
     * @param filters The filters to get
     * @param escapedBackslashesInFilePath escape backslashes ?
     * @param mavenSession The maven session
     * @param mavenResourcesExecution The filtering configuration
     * @throws MavenFilteringException in case of failure.
     * @return {@link java.util.List} of FileUtils.FilterWrapper
     * @since 1.0-beta-2
     */
    List<FilterWrapper> getDefaultFilterWrappers(
            MavenProject mavenProject,
            List<String> filters,
            boolean escapedBackslashesInFilePath,
            MavenSession mavenSession,
            MavenResourcesExecution mavenResourcesExecution)
            throws MavenFilteringException;

    /**
     * @param request The filtering request
     * @throws org.apache.maven.shared.filtering.MavenFilteringException in case of failure.
     * @return {@link java.util.List} of FileUtils.FilterWrapper
     * @since 1.0-beta-3
     */
    List<FilterWrapper> getDefaultFilterWrappers(AbstractMavenFilteringRequest request) throws MavenFilteringException;
}
