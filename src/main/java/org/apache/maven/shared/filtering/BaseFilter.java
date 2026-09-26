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
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.settings.Settings;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.PrefixAwareRecursionInterceptor;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.RecursionInterceptor;
import org.codehaus.plexus.interpolation.SimpleRecursionInterceptor;
import org.codehaus.plexus.interpolation.SingleResponseValueSource;
import org.codehaus.plexus.interpolation.ValueSource;
import org.codehaus.plexus.interpolation.multi.MultiDelimiterStringSearchInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BaseFilter implements DefaultFilterInfo {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected Logger getLogger() {
        return logger;
    }

    @Override
    public List<FilterWrapper> getDefaultFilterWrappers(
            final Project mavenProject,
            List<String> filters,
            final boolean escapedBackslashesInFilePath,
            Session mavenSession,
            MavenResourcesExecution mavenResourcesExecution)
            throws MavenFilteringException {

        MavenResourcesExecution mre =
                mavenResourcesExecution == null ? new MavenResourcesExecution() : mavenResourcesExecution.copyOf();

        mre.setMavenProject(mavenProject);
        mre.setMavenSession(mavenSession);
        mre.setFilters(filters);
        mre.setEscapedBackslashesInFilePath(escapedBackslashesInFilePath);

        return getDefaultFilterWrappers(mre);
    }

    @Override
    public List<FilterWrapper> getDefaultFilterWrappers(final AbstractMavenFilteringRequest request)
            throws MavenFilteringException {
        // Here we build some properties which will be used to read some properties files
        // to interpolate the expression ${ } in this properties file

        // Take a copy of filterProperties to ensure that evaluated filterTokens are not propagated
        // to subsequent filter files. Note: this replicates current behaviour and seems to make sense.

        final Properties baseProps = new Properties();

        // Project properties
        if (request.getMavenProject() != null) {
            baseProps.putAll(request.getMavenProject().getModel().getProperties());
        }
        // TODO this is NPE free but do we consider this as normal
        // or do we have to throw an MavenFilteringException with mavenSession cannot be null
        //
        // khmarbaise: 2016-05-21:
        // If we throw an MavenFilteringException tests will fail which is
        // caused by for example:
        // void copyFile( File from, final File to, boolean filtering, List<FileUtils.FilterWrapper> filterWrappers,
        // String encoding )
        // in MavenFileFilter interface where no MavenSession is given.
        // So changing here to throw a MavenFilteringException would make
        // it necessary to change the interface or we need to find a better solution.
        //
        if (request.getMavenSession() != null) {
            // User properties have precedence over system properties
            baseProps.putAll(request.getMavenSession().getSystemProperties());
            baseProps.putAll(request.getMavenSession().getUserProperties());
        }

        // now we build properties to use for resources interpolation

        final Properties filterProperties = new Properties();

        Path basedir = Optional.ofNullable(request.getMavenProject())
                .map(Project::getBasedir)
                .orElseGet(() -> Paths.get("."));

        loadProperties(filterProperties, basedir, request.getFileFilters(), baseProps);
        if (filterProperties.isEmpty()) {
            filterProperties.putAll(baseProps);
        }

        if (request.getMavenProject() != null) {
            if (request.isInjectProjectBuildFilters()) {
                List<String> buildFilters =
                        new ArrayList<>(request.getMavenProject().getBuild().getFilters());

                // JDK-8015656: (coll) unexpected NPE from removeAll
                if (request.getFileFilters() != null) {
                    buildFilters.removeAll(request.getFileFilters());
                }

                loadProperties(filterProperties, basedir, buildFilters, baseProps);
            }

            // Project properties
            filterProperties.putAll(request.getMavenProject().getModel().getProperties());
        }
        if (request.getMavenSession() != null) {
            // User properties have precedence over system properties
            filterProperties.putAll(request.getMavenSession().getSystemProperties());
            filterProperties.putAll(request.getMavenSession().getUserProperties());
        }

        if (request.getAdditionalProperties() != null) {
            // additional properties wins
            filterProperties.putAll(request.getAdditionalProperties());
        }

        List<FilterWrapper> defaultFilterWrappers =
                new ArrayList<>(request.getDelimiters().size() + 1);

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("number of properties used for filtering: {}", filterProperties.size());
        }

        final ValueSource propertiesValueSource = new RecursivePropertiesValueSource(filterProperties, getLogger());

        FilterWrapper wrapper = new Wrapper(
                request.getDelimiters(),
                request.getMavenProject(),
                request.getMavenSession(),
                propertiesValueSource,
                request.getProjectStartExpressions(),
                request.getEscapeString(),
                request.isEscapeWindowsPaths(),
                request.isSupportMultiLineFiltering(),
                request.getInterpolatorCustomizer(),
                request.isFailOnMissingFilterValue());

        defaultFilterWrappers.add(wrapper);

        return defaultFilterWrappers;
    }

    /**
     * Returns {@code true} if the given filter path contains glob pattern characters
     * ({@code *}, {@code ?}, <code>{</code>, or {@code [}).
     */
    private static boolean isGlobPattern(String path) {
        return path.indexOf('*') >= 0 || path.indexOf('?') >= 0 || path.indexOf('{') >= 0 || path.indexOf('[') >= 0;
    }

    /**
     * Expands a glob pattern relative to {@code basedir} and returns the matched paths in sorted order.
     * The pattern must use forward slashes as path separators (as is conventional in Maven filter paths).
     */
    private List<Path> expandGlob(Path basedir, String globPattern) throws IOException {
        // Normalize to forward slashes; resolveFile accepts them on all platforms
        String normalized = globPattern.replace('\\', '/');

        // Find the deepest non-glob path prefix to use as the walk root
        String[] segments = normalized.split("/");
        StringBuilder prefix = new StringBuilder();
        for (String segment : segments) {
            if (segment.indexOf('*') >= 0
                    || segment.indexOf('?') >= 0
                    || segment.indexOf('{') >= 0
                    || segment.indexOf('[') >= 0) {
                break;
            }
            if (prefix.length() > 0) {
                prefix.append('/');
            }
            prefix.append(segment);
        }

        Path normalizedBase = basedir.toAbsolutePath().normalize();
        Path searchRoot = prefix.length() > 0 ? FilteringUtils.resolveFile(basedir, prefix.toString()) : normalizedBase;

        if (!searchRoot.startsWith(normalizedBase)) {
            throw new IOException("Filter glob pattern '" + globPattern + "' resolves outside project basedir");
        }

        if (!Files.isDirectory(searchRoot)) {
            return List.of();
        }

        // Build a relative glob pattern for the suffix beyond the prefix.
        // We match against the path relative to searchRoot to avoid OS separator issues:
        // on Windows, Path.toString() uses '\' which glob treats as an escape character.
        // Relative matching with forward-slash patterns works on all platforms because
        // Path.relativize() always returns paths comparable with '/' in glob patterns.
        String patternSuffix = prefix.length() > 0 ? normalized.substring(prefix.length() + 1) : normalized;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + patternSuffix);

        List<Path> matched = new ArrayList<>();
        int maxDepth = patternSuffix.contains("/") || patternSuffix.contains("**") ? Integer.MAX_VALUE : 1;
        try (Stream<Path> stream = Files.walk(searchRoot, maxDepth)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        // Convert relative path to forward-slash form for cross-platform matching
                        String relative = searchRoot.relativize(p).toString().replace('\\', '/');
                        return matcher.matches(Paths.get(relative));
                    })
                    .sorted()
                    .forEach(matched::add);
        }
        return matched;
    }

    /**
     * default visibility only for testing reason !
     */
    void loadProperties(
            Properties filterProperties, Path basedir, List<String> propertiesFilePaths, Properties baseProps)
            throws MavenFilteringException {
        if (propertiesFilePaths != null) {
            Properties workProperties = new Properties();
            workProperties.putAll(baseProps);

            for (String filterFile : propertiesFilePaths) {
                if (filterFile == null || filterFile.trim().isEmpty()) {
                    getLogger().warn("Skipping empty filter file entry");
                    continue;
                }
                try {
                    if (isGlobPattern(filterFile)) {
                        List<Path> matched = expandGlob(basedir, filterFile);
                        if (matched.isEmpty()) {
                            // Backward-compat fallback: if the pattern looks like a glob but a literal
                            // file exists at that exact path, load it as-is (e.g. filter[1].properties).
                            // On Windows, glob chars like * are illegal in paths, so resolveFile()
                            // will throw InvalidPathException — catch it and skip the fallback.
                            try {
                                Path literalPath = FilteringUtils.resolveFile(basedir, filterFile);
                                if (Files.isRegularFile(literalPath)) {
                                    matched = List.of(literalPath);
                                } else {
                                    getLogger().warn("Filter glob '{}' did not match any files", filterFile);
                                }
                            } catch (java.nio.file.InvalidPathException e) {
                                // Path contains OS-invalid characters (e.g. * on Windows);
                                // it cannot be a literal file, so just warn.
                                getLogger().warn("Filter glob '{}' did not match any files", filterFile);
                            }
                        }
                        for (Path propFile : matched) {
                            Properties properties =
                                    PropertyUtils.loadPropertyFile(propFile, workProperties, getLogger());
                            filterProperties.putAll(properties);
                            workProperties.putAll(properties);
                        }
                    } else {
                        Path propFile = FilteringUtils.resolveFile(basedir, filterFile);
                        Properties properties = PropertyUtils.loadPropertyFile(propFile, workProperties, getLogger());
                        filterProperties.putAll(properties);
                        workProperties.putAll(properties);
                    }
                } catch (IOException e) {
                    throw new MavenFilteringException("Error loading property file '" + filterFile + "'", e);
                }
            }
        }
    }

    private static final class Wrapper extends FilterWrapper {

        private final LinkedHashSet<String> delimiters;

        private final Project project;

        private final ValueSource propertiesValueSource;

        private final List<String> projectStartExpressions;

        private final String escapeString;

        private final boolean escapeWindowsPaths;

        private final Session mavenSession;

        private final boolean supportMultiLineFiltering;

        private final Consumer<Interpolator> interpolatorCustomizer;

        private final boolean failOnMissingFilterValue;

        Wrapper(
                LinkedHashSet<String> delimiters,
                Project project,
                Session mavenSession,
                ValueSource propertiesValueSource,
                List<String> projectStartExpressions,
                String escapeString,
                boolean escapeWindowsPaths,
                boolean supportMultiLineFiltering,
                Consumer<Interpolator> interpolatorCustomizer,
                boolean failOnMissingFilterValue) {
            super();
            this.delimiters = delimiters;
            this.project = project;
            this.mavenSession = mavenSession;
            this.propertiesValueSource = propertiesValueSource;
            this.projectStartExpressions = projectStartExpressions;
            this.escapeString = escapeString;
            this.escapeWindowsPaths = escapeWindowsPaths;
            this.supportMultiLineFiltering = supportMultiLineFiltering;
            this.interpolatorCustomizer = interpolatorCustomizer;
            this.failOnMissingFilterValue = failOnMissingFilterValue;
        }

        @Override
        public Reader getReader(Reader reader) {
            Interpolator interpolator = createInterpolator(
                    delimiters,
                    projectStartExpressions,
                    propertiesValueSource,
                    project,
                    mavenSession,
                    escapeString,
                    escapeWindowsPaths);
            if (interpolatorCustomizer != null) {
                interpolatorCustomizer.accept(interpolator);
            }

            MultiDelimiterInterpolatorFilterReaderLineEnding filterReader =
                    new MultiDelimiterInterpolatorFilterReaderLineEnding(
                            reader, interpolator, supportMultiLineFiltering);

            final RecursionInterceptor ri;
            if (projectStartExpressions != null && !projectStartExpressions.isEmpty()) {
                ri = new PrefixAwareRecursionInterceptor(projectStartExpressions, true);
            } else {
                ri = new SimpleRecursionInterceptor();
            }

            filterReader.setRecursionInterceptor(ri);
            filterReader.setDelimiterSpecs(delimiters);

            filterReader.setInterpolateWithPrefixPattern(false);
            filterReader.setEscapeString(escapeString);
            filterReader.setFailOnMissingFilterValue(failOnMissingFilterValue);

            return filterReader;
        }
    }

    private static Interpolator createInterpolator(
            LinkedHashSet<String> delimiters,
            List<String> projectStartExpressions,
            ValueSource propertiesValueSource,
            Project project,
            Session mavenSession,
            String escapeString,
            boolean escapeWindowsPaths) {
        MultiDelimiterStringSearchInterpolator interpolator = new MultiDelimiterStringSearchInterpolator();
        interpolator.setDelimiterSpecs(delimiters);

        interpolator.addValueSource(propertiesValueSource);

        if (project != null) {
            for (Object root : new Object[] {project, project.getModel()}) {
                interpolator.addValueSource(new PrefixedObjectValueSource(projectStartExpressions, root, true) {
                    @Override
                    public Object getValue(String expression) {
                        Object value = super.getValue(expression);
                        if (value instanceof Optional) {
                            //noinspection unchecked
                            value = ((Optional) value).orElse(null);
                        }
                        return value;
                    }
                });
            }
        }

        if (mavenSession != null) {
            interpolator.addValueSource(new PrefixedObjectValueSource("session", mavenSession));

            final Settings settings = mavenSession.getSettings();
            if (settings != null) {
                interpolator.addValueSource(new PrefixedObjectValueSource("settings", settings));
                interpolator.addValueSource(
                        new SingleResponseValueSource("localRepository", settings.getLocalRepository()));
            }
        }

        interpolator.setEscapeString(escapeString);

        if (escapeWindowsPaths) {
            interpolator.addPostProcessor((expression, value) ->
                    (value instanceof String) ? FilteringUtils.escapeWindowsPath((String) value) : value);
        }
        return interpolator;
    }

    /**
     * A {@link ValueSource} that resolves property expressions recursively using Maven's native
     * {@code ${...}} syntax within property values. This ensures that compound properties — where
     * one property's value references another via {@code ${key}} — are fully resolved even when
     * the user has configured custom delimiters and disabled the default {@code ${*}} delimiter.
     *
     * <p>For example, given:
     * <pre>
     *   buildNumber=42          (set by buildnumber-maven-plugin at runtime)
     *   version=1.0-${buildNumber}  (declared in the POM)
     * </pre>
     * and a resource file containing {@code @version@} with only {@code @@} as the configured
     * delimiter, this value source will resolve {@code version} to {@code 1.0-42} instead of
     * the literal string {@code 1.0-${buildNumber}}.
     */
    private static final class RecursivePropertiesValueSource implements ValueSource {

        private final Properties properties;

        private final Logger logger;

        RecursivePropertiesValueSource(Properties properties, Logger logger) {
            this.properties = properties;
            this.logger = logger;
        }

        @Override
        public Object getValue(String expression) {
            return PropertyUtils.getPropertyValue(expression, properties, logger);
        }

        @Override
        public List<String> getFeedback() {
            return Collections.emptyList();
        }

        @Override
        public void clearFeedback() {
            // nothing to clear
        }
    }
}
