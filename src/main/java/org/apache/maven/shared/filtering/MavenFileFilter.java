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

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;

/**
 * @author Olivier Lamy
 */
public interface MavenFileFilter extends DefaultFilterInfo {

    /**
     * Will copy a file with some filtering using defaultFilterWrappers.
     *
     * @param from file to copy/filter
     * @param to destination file
     * @param filtering enable or not filtering
     * @param mavenProject {@link Project}
     * @param mavenSession {@link Session}
     * @param escapedBackslashesInFilePath escape backslashes in file path.
     * @param filters {@link List} of String which are path to a Property file
     * @param encoding The encoding which is used during the filtering process.
     * @throws MavenFilteringException in case of failure.
     * @see DefaultFilterInfo#getDefaultFilterWrappers(Project, List, boolean, Session, MavenResourcesExecution)
     */
    void copyFile(
            Path from,
            Path to,
            boolean filtering,
            Project mavenProject,
            List<String> filters,
            boolean escapedBackslashesInFilePath,
            String encoding,
            Session mavenSession)
            throws MavenFilteringException;

    /**
     * @param mavenFileFilterRequest the request
     * @throws MavenFilteringException in case of failure.
     * @since 1.0-beta-3
     */
    void copyFile(MavenFileFilterRequest mavenFileFilterRequest) throws MavenFilteringException;

    /**
     * @param from The source file
     * @param to The target file
     * @param filtering true to apply filtering
     * @param filterWrappers {@link List} of FileUtils.FilterWrapper
     * @param encoding The encoding used during the filtering.
     * @throws MavenFilteringException In case of an error.
     */
    void copyFile(Path from, Path to, boolean filtering, List<FilterWrapper> filterWrappers, String encoding)
            throws MavenFilteringException;
}
