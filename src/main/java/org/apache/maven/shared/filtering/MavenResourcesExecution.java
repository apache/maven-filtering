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
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.interpolation.ValueSource;

/**
 * A bean to configure a resources filtering execution.
 *
 * @author Olivier Lamy
 */
public class MavenResourcesExecution extends AbstractMavenFilteringRequest {

    private List<Resource> resources;

    private File outputDirectory;

    private List<String> nonFilteredFileExtensions;

    private List<FilterWrapper> filterWrappers;

    private File resourcesBaseDirectory;

    private boolean useDefaultFilterWrappers = false;

    private boolean filterFilenames = false;

    private String encoding;

    /**
     * @since 3.2.0
     */
    private String propertiesEncoding;

    /**
     * By default files like {@code .gitignore}, {@code .cvsignore} etc. are excluded which means they will not being
     * copied. If you need them for a particular reason you can do that by settings this to {@code false}. This means
     * all files like the following will be copied.
     * <ul>
     * <li>Misc: &#42;&#42;/&#42;~, &#42;&#42;/#&#42;#, &#42;&#42;/.#&#42;, &#42;&#42;/%&#42;%, &#42;&#42;/._&#42;</li>
     * <li>CVS: &#42;&#42;/CVS, &#42;&#42;/CVS/&#42;&#42;, &#42;&#42;/.cvsignore</li>
     * <li>RCS: &#42;&#42;/RCS, &#42;&#42;/RCS/&#42;&#42;</li>
     * <li>SCCS: &#42;&#42;/SCCS, &#42;&#42;/SCCS/&#42;&#42;</li>
     * <li>VSSercer: &#42;&#42;/vssver.scc</li>
     * <li>MKS: &#42;&#42;/project.pj</li>
     * <li>SVN: &#42;&#42;/.svn, &#42;&#42;/.svn/&#42;&#42;</li>
     * <li>GNU: &#42;&#42;/.arch-ids, &#42;&#42;/.arch-ids/&#42;&#42;</li>
     * <li>Bazaar: &#42;&#42;/.bzr, &#42;&#42;/.bzr/&#42;&#42;</li>
     * <li>SurroundSCM: &#42;&#42;/.MySCMServerInfo</li>
     * <li>Mac: &#42;&#42;/.DS_Store</li>
     * <li>Serena Dimension: &#42;&#42;/.metadata, &#42;&#42;/.metadata/&#42;&#42;</li>
     * <li>Mercurial: &#42;&#42;/.hg, &#42;&#42;/.hg/&#42;&#42;, &#42;&#42;/.hgignore,</li>
     * <li>GIT: &#42;&#42;/.git, &#42;&#42;/.gitignore, &#42;&#42;/.gitattributes, &#42;&#42;/.git/&#42;&#42;</li>
     * <li>Bitkeeper: &#42;&#42;/BitKeeper, &#42;&#42;/BitKeeper/&#42;&#42;, &#42;&#42;/ChangeSet,
     * &#42;&#42;/ChangeSet/&#42;&#42;</li>
     * <li>Darcs: &#42;&#42;/_darcs, &#42;&#42;/_darcs/&#42;&#42;, &#42;&#42;/.darcsrepo,
     * &#42;&#42;/.darcsrepo/&#42;&#42;&#42;&#42;/-darcs-backup&#42;, &#42;&#42;/.darcs-temp-mail
     * </ul>
     *
     * @since 3.1.0
     */
    private boolean addDefaultExcludes = true;

    /**
     * Overwrite existing files even if the destination files are newer. <code>false</code> by default.
     *
     * @since 1.0-beta-2
     */
    private boolean overwrite = false;

    /**
     * Copy any empty directories included in the Resources.
     *
     * @since 1.0-beta-2
     */
    private boolean includeEmptyDirs = false;

    /**
     * Do not stop trying to filter tokens when reaching EOL.
     *
     * @since 1.0
     */
    private boolean supportMultiLineFiltering;

    /**
     * Write resources to a flattened directory structure.
     *
     */
    private boolean flatten = false;

    /**
     * Do nothing.
     */
    public MavenResourcesExecution() {
        // no op
    }

    /**
     * As we use a Maven project <code>useDefaultFilterWrappers</code> will be set to <code>true</code>. The
     * {@code useDefaultExcludes} is set to {@code true}.
     *
     * @param resources The list of resources.
     * @param outputDirectory The output directory.
     * @param mavenProject The maven project.
     * @param encoding The given encoding.
     * @param fileFilters The file filters.
     * @param nonFilteredFileExtensions The extensions which should not being filtered.
     * @param mavenSession The maven session.
     */
    public MavenResourcesExecution(
            List<Resource> resources,
            File outputDirectory,
            MavenProject mavenProject,
            String encoding,
            List<String> fileFilters,
            List<String> nonFilteredFileExtensions,
            MavenSession mavenSession) {
        super(mavenProject, fileFilters, mavenSession);
        this.encoding = encoding;
        this.resources = resources;
        this.outputDirectory = outputDirectory;
        this.nonFilteredFileExtensions = nonFilteredFileExtensions;
        this.useDefaultFilterWrappers = true;
        this.addDefaultExcludes = true;
        this.resourcesBaseDirectory = mavenProject.getBasedir();
    }

    /**
     * @param resources The list of resources.
     * @param outputDirectory The output directory.
     * @param encoding The given encoding.
     * @param filterWrappers The list of filter wrappers.
     * @param resourcesBaseDirectory The resources base directory.
     * @param nonFilteredFileExtensions The list of extensions which should not being filtered.
     */
    public MavenResourcesExecution(
            List<Resource> resources,
            File outputDirectory,
            String encoding,
            List<FilterWrapper> filterWrappers,
            File resourcesBaseDirectory,
            List<String> nonFilteredFileExtensions) {
        this();
        this.resources = resources;
        this.outputDirectory = outputDirectory;
        this.filterWrappers = filterWrappers;
        this.nonFilteredFileExtensions = nonFilteredFileExtensions;
        this.resourcesBaseDirectory = resourcesBaseDirectory;
        this.useDefaultFilterWrappers = false;
        setEncoding(encoding);
    }

    /**
     * Return the encoding.
     *
     * @return Current encoding.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Set the value for encoding.
     *
     * @param encoding Give the new value for encoding.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Return the encoding of properties files.
     *
     * @return Current encoding of properties files.
     * @since 3.2.0
     */
    public String getPropertiesEncoding() {
        return propertiesEncoding;
    }

    /**
     * Set the value for encoding of properties files.
     *
     * @param propertiesEncoding Give the new value for encoding of properties files.
     * @since 3.2.0
     */
    public void setPropertiesEncoding(String propertiesEncoding) {
        this.propertiesEncoding = propertiesEncoding;
    }

    /**
     * @return List of {@link org.apache.maven.model.Resource}
     */
    public List<Resource> getResources() {
        return resources;
    }

    /**
     * @param resources List of {@link org.apache.maven.model.Resource}
     */
    public void setResources(List<Resource> resources) {
        this.resources = resources;
    }

    /**
     * @return The output directory.
     */
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * @param outputDirectory The output directory.
     */
    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * @return List of {@link String} file extensions not to filter
     */
    public List<String> getNonFilteredFileExtensions() {
        return nonFilteredFileExtensions;
    }

    /**
     * @param nonFilteredFileExtensions List of {@link String} file extensions to not filter
     */
    public void setNonFilteredFileExtensions(List<String> nonFilteredFileExtensions) {
        this.nonFilteredFileExtensions = nonFilteredFileExtensions;
    }

    /**
     * @return List of {@link FilterWrapper}
     */
    public List<FilterWrapper> getFilterWrappers() {
        return filterWrappers;
    }

    /**
     * @param filterWrappers List of {@link FilterWrapper}
     */
    public void setFilterWrappers(List<FilterWrapper> filterWrappers) {
        this.filterWrappers = filterWrappers;
    }

    /**
     * @param filterWrapper The filter wrapper which should be added.
     */
    public void addFilterWrapper(FilterWrapper filterWrapper) {
        if (this.filterWrappers == null) {
            this.filterWrappers = new ArrayList<>();
        }
        this.filterWrappers.add(filterWrapper);
    }

    /**
     * @param valueSource {@link ValueSource}
     * @param startExp start token like <code>${</code>
     * @param endExp endToken <code>}</code>
     * @param escapeString The escape string.
     * @param multiLineFiltering do we support or use filtering on multi lines with start and endtoken on multi lines
     * @since 1.0
     */
    public void addFilerWrapperWithEscaping(
            final ValueSource valueSource,
            final String startExp,
            final String endExp,
            final String escapeString,
            final boolean multiLineFiltering) {
        addFilterWrapper(new FilterWrapper() {
            @Override
            public Reader getReader(Reader reader) {
                StringSearchInterpolator propertiesInterpolator = new StringSearchInterpolator(startExp, endExp);
                propertiesInterpolator.addValueSource(valueSource);
                propertiesInterpolator.setEscapeString(escapeString);
                InterpolatorFilterReaderLineEnding interpolatorFilterReader = new InterpolatorFilterReaderLineEnding(
                        reader, propertiesInterpolator, startExp, endExp, multiLineFiltering);
                interpolatorFilterReader.setInterpolateWithPrefixPattern(false);
                return interpolatorFilterReader;
            }
        });
    }

    /**
     * @return The resource base directory.
     */
    public File getResourcesBaseDirectory() {
        return resourcesBaseDirectory;
    }

    /**
     * @param resourcesBaseDirectory Set the resource base directory.
     */
    public void setResourcesBaseDirectory(File resourcesBaseDirectory) {
        this.resourcesBaseDirectory = resourcesBaseDirectory;
    }

    /**
     * @return use default filter wrapper
     */
    public boolean isUseDefaultFilterWrappers() {
        return useDefaultFilterWrappers;
    }

    /**
     * @param useDefaultFilterWrappers {@link #useDefaultFilterWrappers}
     */
    public void setUseDefaultFilterWrappers(boolean useDefaultFilterWrappers) {
        this.useDefaultFilterWrappers = useDefaultFilterWrappers;
    }

    /**
     * @return add the default excludes.
     */
    public boolean isAddDefaultExcludes() {
        return addDefaultExcludes;
    }

    /**
     * @param addDefaultExcludes {@link #addDefaultExcludes}
     */
    public void setAddDefaultExcludes(boolean addDefaultExcludes) {
        this.addDefaultExcludes = addDefaultExcludes;
    }

    /**
     * Overwrite existing files even if the destination files are newer.
     *
     * @return {@link #overwrite}
     * @since 1.0-beta-2
     */
    public boolean isOverwrite() {
        return overwrite;
    }

    /**
     * Overwrite existing files even if the destination files are newer.
     *
     * @param overwrite overwrite true or false.
     * @since 1.0-beta-2
     */
    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    /**
     * Write to flattened directory structure.
     *
     * @return {@link #flatten}
     */
    public boolean isFlatten() {
        return flatten;
    }

    /**
     * Write to flattened directory structure.
     *
     * @param flatten flatten true or false.
     */
    public void setFlatten(boolean flatten) {
        this.flatten = flatten;
    }

    /**
     * Copy any empty directories included in the Resources.
     *
     * @return {@link #includeEmptyDirs}
     * @since 1.0-beta-2
     */
    public boolean isIncludeEmptyDirs() {
        return includeEmptyDirs;
    }

    /**
     * Copy any empty directories included in the Resources.
     *
     * @param includeEmptyDirs {@code true} to include empty directories, otherwise {@code false}.
     * @since 1.0-beta-2
     */
    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        this.includeEmptyDirs = includeEmptyDirs;
    }

    /**
     * @return {@code true} if filenames are filtered, otherwise {@code false}
     * @since 1.2
     */
    public boolean isFilterFilenames() {
        return filterFilenames;
    }

    /**
     * @param filterFilenames {@code true} if filenames should be filtered, otherwise {@code false}
     * @since 1.2
     */
    public void setFilterFilenames(boolean filterFilenames) {
        this.filterFilenames = filterFilenames;
    }

    /**
     * @return {@link MavenResourcesExecution}
     */
    public MavenResourcesExecution copyOf() {
        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setAdditionalProperties(this.getAdditionalProperties());
        mre.setEncoding(this.getEncoding());
        mre.setEscapedBackslashesInFilePath(this.isEscapedBackslashesInFilePath());
        mre.setEscapeString(this.getEscapeString());
        mre.setFileFilters(copyList(this.getFileFilters()));
        mre.setFilterWrappers(copyList(this.getFilterWrappers()));
        mre.setIncludeEmptyDirs(this.isIncludeEmptyDirs());
        mre.setInjectProjectBuildFilters(this.isInjectProjectBuildFilters());
        mre.setMavenProject(this.getMavenProject());
        mre.setMavenSession(this.getMavenSession());
        mre.setNonFilteredFileExtensions(copyList(this.getNonFilteredFileExtensions()));
        mre.setOutputDirectory(this.getOutputDirectory());
        mre.setOverwrite(this.isOverwrite());
        mre.setProjectStartExpressions(copyList(this.getProjectStartExpressions()));
        mre.setResources(copyList(this.getResources()));
        mre.setResourcesBaseDirectory(this.getResourcesBaseDirectory());
        mre.setUseDefaultFilterWrappers(this.isUseDefaultFilterWrappers());
        mre.setAddDefaultExcludes(this.isAddDefaultExcludes());
        mre.setSupportMultiLineFiltering(this.isSupportMultiLineFiltering());
        return mre;
    }

    private <T> List<T> copyList(List<T> lst) {
        if (lst == null) {
            return null;
        } else if (lst.isEmpty()) {
            return new ArrayList<>();
        } else {
            return new ArrayList<>(lst);
        }
    }

    @Override
    public boolean isSupportMultiLineFiltering() {
        return supportMultiLineFiltering;
    }

    @Override
    public void setSupportMultiLineFiltering(boolean supportMultiLineFiltering) {
        this.supportMultiLineFiltering = supportMultiLineFiltering;
    }
}
