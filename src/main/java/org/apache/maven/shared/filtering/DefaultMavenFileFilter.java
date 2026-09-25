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
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        copyFile(from, to, filtering, filterWrappers, encoding, ChangeDetection.CONTENT);
    }

    @Override
    public void copyFile(MavenFileFilterRequest mavenFileFilterRequest) throws MavenFilteringException {
        List<FilterWrapper> filterWrappers = getDefaultFilterWrappers(mavenFileFilterRequest);

        doCopyFile(
                mavenFileFilterRequest.getFrom(),
                mavenFileFilterRequest.getTo(),
                mavenFileFilterRequest.isFiltering(),
                filterWrappers,
                mavenFileFilterRequest.getEncoding(),
                mavenFileFilterRequest.getChangeDetection(),
                mavenFileFilterRequest.isGracefulBinaryHandling());
    }

    @Deprecated
    @Override
    public void copyFile(Path from, Path to, boolean filtering, List<FilterWrapper> filterWrappers, String encoding)
            throws MavenFilteringException {
        copyFile(from, to, filtering, filterWrappers, encoding, ChangeDetection.CONTENT);
    }

    @Override
    public void copyFile(
            Path from,
            Path to,
            boolean filtering,
            List<FilterWrapper> filterWrappers,
            String encoding,
            ChangeDetection changeDetection)
            throws MavenFilteringException {
        doCopyFile(from, to, filtering, filterWrappers, encoding, changeDetection, false);
    }

    @Override
    public boolean copyFileWithResult(
            Path from,
            Path to,
            boolean filtering,
            List<FilterWrapper> filterWrappers,
            String encoding,
            ChangeDetection changeDetection)
            throws MavenFilteringException {
        return doCopyFile(from, to, filtering, filterWrappers, encoding, changeDetection, false);
    }

    @Override
    public void copyFile(
            Path from,
            Path to,
            boolean filtering,
            List<FilterWrapper> filterWrappers,
            String encoding,
            boolean gracefulBinaryHandling)
            throws MavenFilteringException {
        doCopyFile(from, to, filtering, filterWrappers, encoding, ChangeDetection.CONTENT, gracefulBinaryHandling);
    }

    private boolean doCopyFile(
            Path from,
            Path to,
            boolean filtering,
            List<FilterWrapper> filterWrappers,
            String encoding,
            ChangeDetection changeDetection,
            boolean gracefulBinaryHandling)
            throws MavenFilteringException {
        try {
            boolean copied;
            if (filtering) {
                FilterWrapper[] array = filterWrappers.toArray(new FilterWrapper[0]);
                try {
                    copied = FilteringUtils.copyFile(from, to, encoding, array, changeDetection);
                } catch (MalformedInputException e) {
                    if (!gracefulBinaryHandling) {
                        throw e;
                    }
                    getLogger()
                            .warn(
                                    "File '{}' could not be filtered (MalformedInputException) — file appears to be binary"
                                            + " and will be copied without filtering. Consider adding it to"
                                            + " <nonFilteredFiles> or <nonFilteredFileExtensions>.",
                                    from);
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                    copied = true;
                }
            } else {
                copied = FilteringUtils.copyFile(from, to, encoding, new FilterWrapper[0], changeDetection);
            }

            buildContext.refresh(to.toFile());
            return copied;
        } catch (IOException e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (e instanceof MalformedInputException) {
                String charsetName = encoding == null || encoding.isEmpty()
                        ? Charset.defaultCharset().name()
                        : encoding;
                reason += " while reading with " + charsetName + " encoding";
            }
            throw new MavenFilteringException(
                    (filtering ? "filtering " : "copying ") + from + " to " + to + " failed with " + reason, e);
        }
    }
}
