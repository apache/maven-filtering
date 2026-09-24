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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Olivier Lamy
 * @since 2.3
 *
 */
public class Resource {

    List<String> includes;
    List<String> excludes;
    List<String> nonFilteredFiles;
    String directory;
    String targetPath;
    boolean filtering;
    String mergeId;

    public Resource() {}

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    /**
     * Returns the list of Ant-style glob patterns that should be copied as-is (without filtering),
     * even when this resource has {@code <filtering>true</filtering>}. Patterns are matched
     * against the relative file path within the resource directory.
     *
     * @return the list of non-filtered file glob patterns, or {@code null} if none are configured
     * @since 3.4.0
     */
    public List<String> getNonFilteredFiles() {
        return nonFilteredFiles;
    }

    /**
     * Sets the list of Ant-style glob patterns for files that should be copied without filtering.
     *
     * @param nonFilteredFiles the Ant-style glob patterns, e.g. {@code **&#47;*.p12} or {@code **&#47;certs/**}
     * @since 3.4.0
     */
    public void setNonFilteredFiles(List<String> nonFilteredFiles) {
        this.nonFilteredFiles = nonFilteredFiles;
    }

    /**
     * Adds a single Ant-style glob pattern to the list of non-filtered files.
     *
     * @param nonFilteredFile the glob pattern to add
     * @since 3.4.0
     */
    public void addNonFilteredFile(String nonFilteredFile) {
        if (nonFilteredFiles == null) {
            nonFilteredFiles = new ArrayList<>();
        }
        nonFilteredFiles.add(nonFilteredFile);
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public boolean isFiltering() {
        return filtering;
    }

    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
    }

    public String getMergeId() {
        return mergeId;
    }

    public void setMergeId(String mergeId) {
        this.mergeId = mergeId;
    }

    public void addInclude(String include) {
        if (includes == null) {
            includes = new ArrayList<>();
        }
        includes.add(include);
    }

    public void addExclude(String exclude) {
        if (excludes == null) {
            excludes = new ArrayList<>();
        }
        excludes.add(exclude);
    }
}
