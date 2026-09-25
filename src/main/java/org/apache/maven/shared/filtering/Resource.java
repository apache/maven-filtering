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
import java.util.Collections;
import java.util.List;

/**
 * @author Olivier Lamy
 * @since 2.3
 *
 */
public class Resource {

    List<String> includes = new ArrayList<>();
    List<String> excludes = new ArrayList<>();
    List<String> nonFilteredFiles = new ArrayList<>();
    String directory;
    String targetPath;
    boolean filtering;
    String mergeId;
    ChangeDetection changeDetection;

    public Resource() {}

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes != null ? includes : new ArrayList<>();
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes != null ? excludes : new ArrayList<>();
    }

    /**
     * Returns the list of Ant-style glob patterns that should be copied as-is (without filtering),
     * even when this resource has {@code <filtering>true</filtering>}. Patterns are matched
     * against the relative file path within the resource directory.
     *
     * @return the list of non-filtered file glob patterns (never {@code null}, may be empty)
     * @since 3.4.0
     */
    public List<String> getNonFilteredFiles() {
        return Collections.unmodifiableList(nonFilteredFiles);
    }

    /**
     * Sets the list of Ant-style glob patterns for files that should be copied without filtering.
     *
     * @param nonFilteredFiles the Ant-style glob patterns, e.g. {@code **&#47;*.p12} or {@code **&#47;certs/**}
     * @since 3.4.0
     */
    public void setNonFilteredFiles(List<String> nonFilteredFiles) {
        this.nonFilteredFiles = nonFilteredFiles != null ? nonFilteredFiles : new ArrayList<>();
    }

    /**
     * Adds a single Ant-style glob pattern to the list of non-filtered files.
     *
     * @param nonFilteredFile the glob pattern to add
     * @since 3.4.0
     */
    public void addNonFilteredFile(String nonFilteredFile) {
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
        includes.add(include);
    }

    public void addExclude(String exclude) {
        excludes.add(exclude);
    }

    /**
     * Returns the change detection strategy to apply when copying files from this resource.
     * When set, this overrides the request-level {@link AbstractMavenFilteringRequest#getChangeDetection()}
     * for files belonging to this resource.
     *
     * @return the per-resource change detection strategy, or {@code null} if not set (falls back to the request level)
     * @since 4.0.0-beta-2
     */
    public ChangeDetection getChangeDetection() {
        return changeDetection;
    }

    /**
     * Sets the change detection strategy to apply when copying files from this resource.
     *
     * @param changeDetection the strategy, or {@code null} to inherit from the request level
     * @since 4.0.0-beta-2
     */
    public void setChangeDetection(ChangeDetection changeDetection) {
        this.changeDetection = changeDetection;
    }
}
