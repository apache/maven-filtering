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
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.apache.maven.api.annotations.Nullable;
import org.apache.maven.api.build.context.BuildContext;
import org.apache.maven.api.build.context.Input;
import org.apache.maven.api.build.context.Status;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.services.PathMatcherFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * @author Olivier Lamy
 */
@Singleton
@Named
public class DefaultMavenResourcesFiltering implements MavenResourcesFiltering {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMavenResourcesFiltering.class);

    // DEFAULT_INCLUDES is no longer needed — includes are passed directly to BuildContext.registerAndProcessInputs()
    private static final int BUFFER_LENGTH = 8192;

    private final List<String> defaultNonFilteredFileExtensions;

    private final MavenFileFilter mavenFileFilter;

    private final BuildContext buildContext;

    private final PathMatcherFactory pathMatcherFactory;

    @Inject
    public DefaultMavenResourcesFiltering(
            MavenFileFilter mavenFileFilter,
            @Nullable BuildContext buildContext,
            @Nullable PathMatcherFactory pathMatcherFactory) {
        this.mavenFileFilter = requireNonNull(mavenFileFilter);
        this.buildContext = buildContext; // null when running without incremental support (e.g. tests)
        this.pathMatcherFactory = pathMatcherFactory; // null when running outside Maven 4 DI
        this.defaultNonFilteredFileExtensions = new ArrayList<>(5);
        this.defaultNonFilteredFileExtensions.add("jpg");
        this.defaultNonFilteredFileExtensions.add("jpeg");
        this.defaultNonFilteredFileExtensions.add("gif");
        this.defaultNonFilteredFileExtensions.add("bmp");
        this.defaultNonFilteredFileExtensions.add("png");
        this.defaultNonFilteredFileExtensions.add("ico");
    }

    @Override
    public boolean filteredFileExtension(String fileName, List<String> userNonFilteredFileExtensions) {
        List<String> nonFilteredFileExtensions = new ArrayList<>(getDefaultNonFilteredFileExtensions());
        if (userNonFilteredFileExtensions != null) {
            nonFilteredFileExtensions.addAll(userNonFilteredFileExtensions);
        }
        String extension = getExtension(fileName);
        boolean filteredFileExtension = !nonFilteredFileExtensions.contains(extension);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("file " + fileName + " has a" + (filteredFileExtension ? " " : " non ")
                    + "filtered file extension");
        }
        return filteredFileExtension;
    }

    private static String getExtension(String fileName) {
        final int extensionPos = fileName.lastIndexOf('.');
        final int lastUnixPos = fileName.lastIndexOf('/');
        final int lastWindowsPos = fileName.lastIndexOf('\\');
        final int lastSeparator = Math.max(lastUnixPos, lastWindowsPos);
        return lastSeparator > extensionPos
                ? ""
                : fileName.substring(extensionPos + 1).toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> getDefaultNonFilteredFileExtensions() {
        return this.defaultNonFilteredFileExtensions;
    }

    @Override
    public void filterResources(MavenResourcesExecution mavenResourcesExecution) throws MavenFilteringException {
        if (mavenResourcesExecution == null) {
            throw new MavenFilteringException("mavenResourcesExecution cannot be null");
        }

        if (mavenResourcesExecution.getResources() == null) {
            LOGGER.info("No resources configured skip copying/filtering");
            return;
        }

        if (mavenResourcesExecution.getOutputDirectory() == null) {
            throw new MavenFilteringException("outputDirectory cannot be null");
        }

        if (mavenResourcesExecution.isUseDefaultFilterWrappers()) {
            handleDefaultFilterWrappers(mavenResourcesExecution);
        }

        if (mavenResourcesExecution.getEncoding() == null
                || mavenResourcesExecution.getEncoding().isEmpty()) {
            LOGGER.warn("Using platform encoding (" + Charset.defaultCharset().displayName()
                    + " actually) to copy filtered resources, i.e. build is platform dependent!");
        } else {
            LOGGER.debug("Using '" + mavenResourcesExecution.getEncoding() + "' encoding to copy filtered resources.");
        }

        if (mavenResourcesExecution.getPropertiesEncoding() == null
                || mavenResourcesExecution.getPropertiesEncoding().isEmpty()) {
            LOGGER.debug("Using '" + mavenResourcesExecution.getEncoding()
                    + "' encoding to copy filtered properties files.");
        } else {
            LOGGER.debug("Using '" + mavenResourcesExecution.getPropertiesEncoding()
                    + "' encoding to copy filtered properties files.");
        }

        // Keep track of filtering being used and the properties files being filtered
        boolean isFilteringUsed = false;
        List<Path> propertiesFiles = new ArrayList<>();

        for (Resource resource : mavenResourcesExecution.getResources()) {

            if (LOGGER.isDebugEnabled()) {
                String ls = System.lineSeparator();
                StringBuilder debugMessage = new StringBuilder("resource with targetPath ")
                        .append(resource.getTargetPath())
                        .append(ls);
                debugMessage
                        .append("directory ")
                        .append(resource.getDirectory())
                        .append(ls);

                // @formatter:off
                debugMessage
                        .append("excludes ")
                        .append(
                                resource.getExcludes() == null
                                        ? " empty "
                                        : resource.getExcludes().toString())
                        .append(ls);
                debugMessage
                        .append("includes ")
                        .append(
                                resource.getIncludes() == null
                                        ? " empty "
                                        : resource.getIncludes().toString());

                // @formatter:on
                LOGGER.debug(debugMessage.toString());
            }

            String targetPath = resource.getTargetPath();

            Path resourceDirectory = (resource.getDirectory() == null) ? null : Paths.get(resource.getDirectory());

            if (resourceDirectory != null && !resourceDirectory.isAbsolute()) {
                resourceDirectory =
                        mavenResourcesExecution.getResourcesBaseDirectory().resolve(resourceDirectory);
            }

            if (resourceDirectory == null || !Files.exists(resourceDirectory)) {
                LOGGER.info("skip non existing resourceDirectory " + resourceDirectory);
                continue;
            }

            // Normalize to absolute path for consistent path operations
            // (BuildContext canonicalizes paths, so relativize() would fail on mixed relative/absolute)
            resourceDirectory = resourceDirectory.toAbsolutePath().normalize();

            // this part is required in case the user specified "../something"
            // as destination
            // see MNG-1345
            Path outputDirectory = mavenResourcesExecution.getOutputDirectory();
            boolean outputExists = Files.exists(outputDirectory);
            if (!outputExists) {
                try {
                    Files.createDirectories(outputDirectory);
                } catch (IOException e) {
                    throw new MavenFilteringException("Cannot create resource output directory: " + outputDirectory, e);
                }
            }

            if (resource.isFiltering()) {
                isFilteringUsed = true;
            }

            // Resolve include/exclude patterns for this resource
            List<String> includes = resource.getIncludes();
            if (includes == null || includes.isEmpty()) {
                includes = List.of("**/**");
            }
            List<String> excludes = resource.getExcludes();
            if (excludes == null) {
                excludes = List.of();
            }
            boolean addDefaultExcludes = mavenResourcesExecution.isAddDefaultExcludes();

            // Register inputs with the BuildContext and get per-file change status.
            // registerAndProcessInputs() returns ALL matching inputs (including UNMODIFIED)
            // and internally marks NEW/MODIFIED ones as "processed".
            // For REMOVED inputs, the BuildContext automatically cleans up associated outputs.
            Collection<? extends Input> allInputs;
            if (buildContext != null) {
                // BuildContext needs default excludes in the excludes list directly
                List<String> effectiveExcludes = excludes;
                if (addDefaultExcludes) {
                    effectiveExcludes = new ArrayList<>(excludes);
                    addDefaultExcludes(effectiveExcludes);
                }
                allInputs = buildContext.registerAndProcessInputs(resourceDirectory, includes, effectiveExcludes);
            } else {
                allInputs = null;
            }

            // Separate changed inputs from unchanged ones
            List<Input> changedInputs;
            List<String> allFiles;
            if (allInputs != null) {
                final Path resDirFinal = resourceDirectory;
                changedInputs = new ArrayList<>();
                allFiles = new ArrayList<>();
                for (Input input : allInputs) {
                    allFiles.add(resDirFinal.relativize(input.getPath()).toString());
                    if (input.getStatus() != Status.UNMODIFIED) {
                        changedInputs.add(input);
                    }
                }
            } else {
                // No build context — scan directory with PathMatcherFactory (handles
                // Ant-style patterns and default excludes) and process everything
                changedInputs = null;
                allFiles = scanDirectory(resourceDirectory, includes, excludes, addDefaultExcludes);
            }

            if (mavenResourcesExecution.isIncludeEmptyDirs()) {
                try {
                    Path targetDirectory = targetPath == null ? outputDirectory : outputDirectory.resolve(targetPath);
                    copyDirectoryLayout(resourceDirectory, targetDirectory);
                } catch (IOException e) {
                    throw new MavenFilteringException(
                            "Cannot copy directory structure from " + resourceDirectory + " to " + outputDirectory);
                }
            }

            // Determine which files to actually process
            boolean incremental = changedInputs != null;
            int totalCount = allFiles.size();
            int processCount = incremental ? changedInputs.size() : totalCount;

            // Log the processing summary
            try {
                Path basedir =
                        mavenResourcesExecution.getMavenProject().getBasedir().toAbsolutePath();
                Path destination = getDestinationFile(outputDirectory, targetPath, "", mavenResourcesExecution)
                        .toAbsolutePath();
                if (incremental && processCount < totalCount) {
                    LOGGER.info("Copying " + processCount + " of " + totalCount + " resource"
                            + (totalCount > 1 ? "s" : "") + " from "
                            + basedir.relativize(resourceDirectory.toAbsolutePath()) + " to "
                            + basedir.relativize(destination)
                            + " (" + (totalCount - processCount) + " unchanged)");
                } else {
                    LOGGER.info("Copying " + totalCount + " resource" + (totalCount > 1 ? "s" : "") + " from "
                            + basedir.relativize(resourceDirectory.toAbsolutePath()) + " to "
                            + basedir.relativize(destination));
                }
            } catch (Exception e) {
                // be foolproof: if for ANY reason throws, do not abort, just fall back to old message
                LOGGER.info("Copying " + processCount + " resource" + (processCount > 1 ? "s" : "")
                        + (targetPath == null ? "" : " to " + targetPath));
            }

            // Process only changed files (or all files when no BuildContext is available)
            if (incremental) {
                // Incremental mode: only copy/filter files with status NEW or MODIFIED,
                // then register input→output associations for stale output cleanup
                for (Input input : changedInputs) {
                    Path source = input.getPath();
                    String name = resourceDirectory.relativize(source).toString();

                    Path destinationFile =
                            getDestinationFile(outputDirectory, targetPath, name, mavenResourcesExecution);

                    if (mavenResourcesExecution.isFlatten() && Files.exists(destinationFile)) {
                        if (mavenResourcesExecution.isOverwrite()) {
                            LOGGER.warn("existing file " + destinationFile.getFileName() + " will be overwritten by "
                                    + name);
                        } else {
                            throw new MavenFilteringException("existing file " + destinationFile.getFileName()
                                    + " will be overwritten by " + name + " and overwrite was not set to true");
                        }
                    }

                    boolean filteredExt = filteredFileExtension(
                            source.getFileName().toString(), mavenResourcesExecution.getNonFilteredFileExtensions());
                    if (resource.isFiltering() && isPropertiesFile(source)) {
                        propertiesFiles.add(source);
                    }

                    String encoding = getEncoding(
                            source,
                            mavenResourcesExecution.getEncoding(),
                            mavenResourcesExecution.getPropertiesEncoding());
                    LOGGER.debug("Using '" + encoding + "' encoding to copy filtered resource '" + source.getFileName()
                            + "'.");
                    mavenFileFilter.copyFile(
                            source,
                            destinationFile,
                            resource.isFiltering() && filteredExt,
                            mavenResourcesExecution.getFilterWrappers(),
                            encoding);

                    // Register the input→output association so the BuildContext can:
                    // 1. Track which outputs belong to which inputs
                    // 2. Automatically delete stale outputs when their input is removed
                    // Skip when flattening: multiple inputs may map to the same output file,
                    // which violates the BuildContext's one-input-to-one-output constraint.
                    if (!mavenResourcesExecution.isFlatten()) {
                        input.associateOutput(destinationFile);
                    }
                }
            } else {
                // Non-incremental mode: process all files
                for (String name : allFiles) {
                    LOGGER.debug("Copying file " + name);
                    Path source = resourceDirectory.resolve(name);
                    Path destinationFile =
                            getDestinationFile(outputDirectory, targetPath, name, mavenResourcesExecution);

                    if (mavenResourcesExecution.isFlatten() && Files.exists(destinationFile)) {
                        if (mavenResourcesExecution.isOverwrite()) {
                            LOGGER.warn("existing file " + destinationFile.getFileName() + " will be overwritten by "
                                    + name);
                        } else {
                            throw new MavenFilteringException("existing file " + destinationFile.getFileName()
                                    + " will be overwritten by " + name + " and overwrite was not set to true");
                        }
                    }

                    boolean filteredExt = filteredFileExtension(
                            source.getFileName().toString(), mavenResourcesExecution.getNonFilteredFileExtensions());
                    if (resource.isFiltering() && isPropertiesFile(source)) {
                        propertiesFiles.add(source);
                    }

                    String encoding = getEncoding(
                            source,
                            mavenResourcesExecution.getEncoding(),
                            mavenResourcesExecution.getPropertiesEncoding());
                    LOGGER.debug("Using '" + encoding + "' encoding to copy filtered resource '" + source.getFileName()
                            + "'.");
                    mavenFileFilter.copyFile(
                            source,
                            destinationFile,
                            resource.isFiltering() && filteredExt,
                            mavenResourcesExecution.getFilterWrappers(),
                            encoding);
                }
            }
        }

        // Warn the user if all of the following requirements are met, to avoid those that are not affected
        // - the propertiesEncoding parameter has not been set
        // - properties is a filtered extension
        // - filtering is enabled for at least one resource
        // - there is at least one properties file in one of the resources that has filtering enabled
        if ((mavenResourcesExecution.getPropertiesEncoding() == null
                        || mavenResourcesExecution.getPropertiesEncoding().isEmpty())
                && !mavenResourcesExecution.getNonFilteredFileExtensions().contains("properties")
                && isFilteringUsed
                && !propertiesFiles.isEmpty()) {
            // @todo Sometime in the future we should change this to be a warning
            LOGGER.info("The encoding used to copy filtered properties files has not been set."
                    + " This means that the same encoding will be used to copy filtered properties files"
                    + " as when copying other filtered resources. This might not be what you want!"
                    + " Run your build with --debug to see which files might be affected."
                    + " Read more at "
                    + "https://maven.apache.org/plugins/maven-resources-plugin/"
                    + "examples/filtering-properties-files.html");

            StringBuilder affectedFiles = new StringBuilder();
            affectedFiles.append("Here is a list of the filtered properties files in your project that might be"
                    + " affected by encoding problems: ");
            for (Path propertiesFile : propertiesFiles) {
                affectedFiles.append(System.lineSeparator()).append(" - ").append(propertiesFile);
            }
            LOGGER.debug(affectedFiles.toString());
        }
    }

    /**
     * Get the encoding to use when filtering the specified file. Properties files can be configured to use a different
     * encoding than regular files.
     *
     * @param file The file to check
     * @param encoding The encoding to use for regular files
     * @param propertiesEncoding The encoding to use for properties files
     * @return The encoding to use when filtering the specified file
     * @since 3.2.0
     */
    static String getEncoding(Path file, String encoding, String propertiesEncoding) {
        if (isPropertiesFile(file)) {
            if (propertiesEncoding == null) {
                // Since propertiesEncoding is a new feature, not all plugins will have implemented support for it.
                // These plugins will have propertiesEncoding set to null.
                return encoding;
            } else {
                return propertiesEncoding;
            }
        } else {
            return encoding;
        }
    }

    /**
     * Determine whether a file is a properties file or not.
     *
     * @param file The file to check
     * @return <code>true</code> if the file name has an extension of "properties", otherwise <code>false</code>
     * @since 3.2.0
     */
    static boolean isPropertiesFile(Path file) {
        return "properties".equals(getExtension(file.getFileName().toString()));
    }

    private void handleDefaultFilterWrappers(MavenResourcesExecution mavenResourcesExecution)
            throws MavenFilteringException {
        List<FilterWrapper> filterWrappers = new ArrayList<>();
        if (mavenResourcesExecution.getFilterWrappers() != null) {
            filterWrappers.addAll(mavenResourcesExecution.getFilterWrappers());
        }
        filterWrappers.addAll(mavenFileFilter.getDefaultFilterWrappers(mavenResourcesExecution));
        mavenResourcesExecution.setFilterWrappers(filterWrappers);
    }

    private Path getDestinationFile(
            Path outputDirectory, String targetPath, String name, MavenResourcesExecution mavenResourcesExecution)
            throws MavenFilteringException {
        String destination;
        if (!mavenResourcesExecution.isFlatten()) {
            destination = name;
        } else {
            Path path = Paths.get(name);
            Path filePath = path.getFileName();
            destination = filePath.toString();
        }

        if (mavenResourcesExecution.isFilterFilenames()
                && !mavenResourcesExecution.getFilterWrappers().isEmpty()) {
            destination = filterFileName(destination, mavenResourcesExecution.getFilterWrappers());
        }

        if (targetPath != null) {
            destination = targetPath + "/" + destination;
        }

        Path destinationFile = outputDirectory.resolve(destination);

        try {
            Files.createDirectories(destinationFile.getParent());
        } catch (IOException e) {
            throw new MavenFilteringException("Unable to create directory " + destinationFile.getParent(), e);
        }

        return destinationFile;
    }

    /**
     * Scans a directory for files matching include/exclude patterns.
     * Uses {@link PathMatcherFactory} when available (Maven 4 API — handles Ant-style
     * patterns correctly), otherwise falls back to a simple unfiltered walk.
     */
    private List<String> scanDirectory(
            Path baseDir, List<String> includes, List<String> excludes, boolean addDefaultExcludes)
            throws MavenFilteringException {
        List<String> result = new ArrayList<>();
        try {
            PathMatcher matcher;
            if (pathMatcherFactory != null) {
                matcher = pathMatcherFactory.createPathMatcher(baseDir, includes, excludes, addDefaultExcludes);
            } else {
                matcher = null; // no filtering available — include everything
            }
            Files.walk(baseDir).filter(Files::isRegularFile).forEach(path -> {
                if (matcher == null || matcher.matches(path)) {
                    String relative = baseDir.relativize(path).toString().replace('\\', '/');
                    result.add(relative);
                }
            });
        } catch (IOException e) {
            throw new MavenFilteringException("Failed to scan directory: " + baseDir, e);
        }
        return result;
    }

    /**
     * Adds the standard SCM and IDE default excludes to the given list.
     */
    private static void addDefaultExcludes(List<String> excludes) {
        // Standard default excludes (SCM dirs, IDE files, OS files)
        excludes.add("**/.git/**");
        excludes.add("**/.svn/**");
        excludes.add("**/.hg/**");
        excludes.add("**/.bzr/**");
        excludes.add("**/CVS/**");
        excludes.add("**/.gitignore");
        excludes.add("**/.cvsignore");
        excludes.add("**/.DS_Store");
        excludes.add("**/Thumbs.db");
    }

    private void copyDirectoryLayout(Path sourceDirectory, Path destinationDirectory) throws IOException {
        if (sourceDirectory == null) {
            throw new IOException("source directory can't be null.");
        }

        if (destinationDirectory == null) {
            throw new IOException("destination directory can't be null.");
        }

        if (sourceDirectory.equals(destinationDirectory)) {
            throw new IOException("source and destination are the same directory.");
        }

        if (!Files.exists(sourceDirectory)) {
            throw new IOException("Source directory doesn't exists (" + sourceDirectory.toAbsolutePath() + ").");
        }

        Files.walk(sourceDirectory)
                .filter(Files::isDirectory)
                .filter(p -> !p.equals(sourceDirectory))
                .forEach(dir -> {
                    Path destination = destinationDirectory.resolve(sourceDirectory.relativize(dir));
                    try {
                        Files.createDirectories(destination);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
    }

    // getRelativeOutputDirectory was only used for old BuildContext.hasDelta() — no longer needed

    /*
     * Filter the name of a file using the same mechanism for filtering the content of the file.
     */
    private String filterFileName(String name, List<FilterWrapper> wrappers) throws MavenFilteringException {

        Reader reader = new StringReader(name);
        for (FilterWrapper wrapper : wrappers) {
            reader = wrapper.getReader(reader);
        }

        try (StringWriter writer = new StringWriter()) {
            char[] buffer = new char[BUFFER_LENGTH];
            int nRead;
            while ((nRead = reader.read(buffer, 0, buffer.length)) >= 0) {
                writer.write(buffer, 0, nRead);
            }

            String filteredFilename = writer.toString();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("renaming filename " + name + " to " + filteredFilename);
            }
            return filteredFilename;
        } catch (IOException e) {
            throw new MavenFilteringException("Failed filtering filename" + name, e);
        }
    }
}
