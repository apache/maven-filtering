package org.apache.maven.shared.filtering;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.shared.utils.PathTool;
import org.apache.maven.shared.utils.ReaderFactory;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils.FilterWrapper;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * @author Olivier Lamy
 */
@Component( role = MavenResourcesFiltering.class, hint = "default" )
public class DefaultMavenResourcesFiltering
    extends AbstractLogEnabled
    implements MavenResourcesFiltering, Initializable
{

    private static final String[] EMPTY_STRING_ARRAY = {};

    private static final String[] DEFAULT_INCLUDES = { "**/**" };

    private List<String> defaultNonFilteredFileExtensions;

    @Requirement
    private BuildContext buildContext;

    @Requirement
    private MavenFileFilter mavenFileFilter;

    // ------------------------------------------------
    // Plexus lifecycle
    // ------------------------------------------------
    @Override
    public void initialize()
        throws InitializationException
    {
        this.defaultNonFilteredFileExtensions = new ArrayList<>( 5 );
        this.defaultNonFilteredFileExtensions.add( "jpg" );
        this.defaultNonFilteredFileExtensions.add( "jpeg" );
        this.defaultNonFilteredFileExtensions.add( "gif" );
        this.defaultNonFilteredFileExtensions.add( "bmp" );
        this.defaultNonFilteredFileExtensions.add( "png" );
        this.defaultNonFilteredFileExtensions.add( "ico" );
    }

    @Override
    public boolean filteredFileExtension( String fileName, List<String> userNonFilteredFileExtensions )
    {
        List<String> nonFilteredFileExtensions = new ArrayList<>( getDefaultNonFilteredFileExtensions() );
        if ( userNonFilteredFileExtensions != null )
        {
            nonFilteredFileExtensions.addAll( userNonFilteredFileExtensions );
        }
        String extension = StringUtils.lowerCase( FilenameUtils.getExtension( fileName ) );
        boolean filteredFileExtension = !nonFilteredFileExtensions.contains( extension );
        if ( getLogger().isDebugEnabled() )
        {
            getLogger().debug( "file " + fileName + " has a" + ( filteredFileExtension ? " " : " non " )
                + "filtered file extension" );
        }
        return filteredFileExtension;
    }

    @Override
    public List<String> getDefaultNonFilteredFileExtensions()
    {
        if ( this.defaultNonFilteredFileExtensions == null )
        {
            this.defaultNonFilteredFileExtensions = new ArrayList<>();
        }
        return this.defaultNonFilteredFileExtensions;
    }

    @Override
    public void filterResources( MavenResourcesExecution mavenResourcesExecution )
        throws MavenFilteringException
    {
        if ( mavenResourcesExecution == null )
        {
            throw new MavenFilteringException( "mavenResourcesExecution cannot be null" );
        }

        if ( mavenResourcesExecution.getResources() == null )
        {
            getLogger().info( "No resources configured skip copying/filtering" );
            return;
        }

        if ( mavenResourcesExecution.getOutputDirectory() == null )
        {
            throw new MavenFilteringException( "outputDirectory cannot be null" );
        }

        if ( mavenResourcesExecution.isUseDefaultFilterWrappers() )
        {
            handleDefaultFilterWrappers( mavenResourcesExecution );
        }

        if ( mavenResourcesExecution.getEncoding() == null || mavenResourcesExecution.getEncoding().length() < 1 )
        {
            getLogger().warn( "Using platform encoding (" + ReaderFactory.FILE_ENCODING
                + " actually) to copy filtered resources, i.e. build is platform dependent!" );
        }
        else
        {
            getLogger().debug( "Using '" + mavenResourcesExecution.getEncoding()
                + "' encoding to copy filtered resources." );
        }

        if ( mavenResourcesExecution.getPropertiesEncoding() == null
            || mavenResourcesExecution.getPropertiesEncoding().length() < 1 )
        {
            getLogger().debug( "Using '" + mavenResourcesExecution.getEncoding()
                + "' encoding to copy filtered properties files." );
        }
        else
        {
            getLogger().debug( "Using '" + mavenResourcesExecution.getPropertiesEncoding()
                + "' encoding to copy filtered properties files." );
        }

        // Keep track of filtering being used and the properties files being filtered
        boolean isFilteringUsed = false;
        List<File> propertiesFiles = new ArrayList<>();

        for ( Resource resource : mavenResourcesExecution.getResources() )
        {

            if ( getLogger().isDebugEnabled() )
            {
                String ls = System.lineSeparator();
                StringBuilder debugMessage =
                    new StringBuilder( "resource with targetPath " ).append( resource.getTargetPath() ).append( ls );
                debugMessage.append( "directory " ).append( resource.getDirectory() ).append( ls );

                // @formatter:off
                debugMessage.append( "excludes " ).append( resource.getExcludes() == null ? " empty "
                                : resource.getExcludes().toString() ).append( ls );
                debugMessage.append( "includes " ).append( resource.getIncludes() == null ? " empty "
                                : resource.getIncludes().toString() );

                // @formatter:on
                getLogger().debug( debugMessage.toString() );
            }

            String targetPath = resource.getTargetPath();

            File resourceDirectory = ( resource.getDirectory() == null ) ? null : new File( resource.getDirectory() );

            if ( resourceDirectory != null && !resourceDirectory.isAbsolute() )
            {
                resourceDirectory =
                        new File( mavenResourcesExecution.getResourcesBaseDirectory(), resourceDirectory.getPath() );
            }

            if ( resourceDirectory == null || !resourceDirectory.exists() )
            {
                getLogger().info( "skip non existing resourceDirectory " + resourceDirectory );
                continue;
            }

            // this part is required in case the user specified "../something"
            // as destination
            // see MNG-1345
            File outputDirectory = mavenResourcesExecution.getOutputDirectory();
            boolean outputExists = outputDirectory.exists();
            if ( !outputExists && !outputDirectory.mkdirs() )
            {
                throw new MavenFilteringException( "Cannot create resource output directory: " + outputDirectory );
            }

            if ( resource.isFiltering() )
            {
                isFilteringUsed = true;
            }
            boolean ignoreDelta = !outputExists || buildContext.hasDelta( mavenResourcesExecution.getFileFilters() )
                || buildContext.hasDelta( getRelativeOutputDirectory( mavenResourcesExecution ) );
            getLogger().debug( "ignoreDelta " + ignoreDelta );
            Scanner scanner = buildContext.newScanner( resourceDirectory, ignoreDelta );

            setupScanner( resource, scanner, mavenResourcesExecution.isAddDefaultExcludes() );

            scanner.scan();

            if ( mavenResourcesExecution.isIncludeEmptyDirs() )
            {
                try
                {
                    File targetDirectory =
                        targetPath == null ? outputDirectory : new File( outputDirectory, targetPath );
                    copyDirectoryLayout( resourceDirectory, targetDirectory, scanner );
                }
                catch ( IOException e )
                {
                    throw new MavenFilteringException( "Cannot copy directory structure from "
                        + resourceDirectory.getPath() + " to " + outputDirectory.getPath() );
                }
            }

            List<String> includedFiles = Arrays.asList( scanner.getIncludedFiles() );

            getLogger().info( "Copying " + includedFiles.size() + " resource" + ( includedFiles.size() > 1 ? "s" : "" )
                + ( targetPath == null ? "" : " to " + targetPath ) );

            for ( String name : includedFiles )
            {

                getLogger().debug( "Copying file " + name );
                File source = new File( resourceDirectory, name );

                File destinationFile = getDestinationFile( outputDirectory, targetPath, name, mavenResourcesExecution );

                if ( mavenResourcesExecution.isFlatten() && destinationFile.exists() )
                {
                    if ( mavenResourcesExecution.isOverwrite() )
                    {
                        getLogger().warn( "existing file " + destinationFile.getName()
                                + " will be overwritten by " + name );
                    }
                    else
                    {
                        throw new MavenFilteringException( "existing file " + destinationFile.getName()
                                + " will be overwritten by " + name + " and overwrite was not set to true" );
                    }
                }
                boolean filteredExt =
                    filteredFileExtension( source.getName(), mavenResourcesExecution.getNonFilteredFileExtensions() );
                if ( resource.isFiltering() && isPropertiesFile( source ) )
                {
                    propertiesFiles.add( source );
                }

                // Determine which encoding to use when filtering this file
                String encoding = getEncoding( source, mavenResourcesExecution.getEncoding(),
                                               mavenResourcesExecution.getPropertiesEncoding() );
                getLogger().debug( "Using '" + encoding + "' encoding to copy filtered resource '"
                                       + source.getName() + "'." );
                mavenFileFilter.copyFile( source, destinationFile, resource.isFiltering() && filteredExt,
                                          mavenResourcesExecution.getFilterWrappers(),
                                          encoding,
                                          mavenResourcesExecution.isOverwrite() );
            }

            // deal with deleted source files

            scanner = buildContext.newDeleteScanner( resourceDirectory );

            setupScanner( resource, scanner, mavenResourcesExecution.isAddDefaultExcludes() );

            scanner.scan();

            List<String> deletedFiles = Arrays.asList( scanner.getIncludedFiles() );

            for ( String name : deletedFiles )
            {
                File destinationFile = getDestinationFile( outputDirectory, targetPath, name, mavenResourcesExecution );

                destinationFile.delete();

                buildContext.refresh( destinationFile );
            }

        }

        // Warn the user if all of the following requirements are met, to avoid those that are not affected
        // - the propertiesEncoding parameter has not been set
        // - properties is a filtered extension
        // - filtering is enabled for at least one resource
        // - there is at least one properties file in one of the resources that has filtering enabled
        if ( ( mavenResourcesExecution.getPropertiesEncoding() == null
            || mavenResourcesExecution.getPropertiesEncoding().length() < 1 )
            && !mavenResourcesExecution.getNonFilteredFileExtensions().contains( "properties" )
            && isFilteringUsed
            && propertiesFiles.size() > 0 )
        {
            // @todo Sometime in the future we should change this to be a warning
            getLogger().info( "The encoding used to copy filtered properties files have not been set."
                                  + " This means that the same encoding will be used to copy filtered properties files"
                                  + " as when copying other filtered resources. This might not be what you want!"
                                  + " Run your build with --debug to see which files might be affected."
                                  + " Read more at "
                                  + "https://maven.apache.org/plugins/maven-resources-plugin/"
                                  + "examples/filtering-properties-files.html" );

            StringBuilder affectedFiles = new StringBuilder();
            affectedFiles.append( "Here is a list of the filtered properties files in you project that might be"
                                      + " affected by encoding problems: " );
            for ( File propertiesFile : propertiesFiles )
            {
                affectedFiles.append( System.lineSeparator() ).append( " - " ).append( propertiesFile.getPath() );
            }
            getLogger().debug( affectedFiles.toString() );

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
    static String getEncoding( File file, String encoding, String propertiesEncoding )
    {
        if ( isPropertiesFile( file ) )
        {
            if ( propertiesEncoding == null )
            {
                // Since propertiesEncoding is a new feature, not all plugins will have implemented support for it.
                // These plugins will have propertiesEncoding set to null.
                return encoding;
            }
            else
            {
                return propertiesEncoding;
            }
        }
        else
        {
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
    static boolean isPropertiesFile( File file )
    {
        String extension = StringUtils.lowerCase( FilenameUtils.getExtension( ( file.getName() ) ) );
        return "properties".equals( extension );
    }

    private void handleDefaultFilterWrappers( MavenResourcesExecution mavenResourcesExecution )
        throws MavenFilteringException
    {
        List<FilterWrapper> filterWrappers = new ArrayList<>();
        if ( mavenResourcesExecution.getFilterWrappers() != null )
        {
            filterWrappers.addAll( mavenResourcesExecution.getFilterWrappers() );
        }
        filterWrappers.addAll( mavenFileFilter.getDefaultFilterWrappers( mavenResourcesExecution ) );
        mavenResourcesExecution.setFilterWrappers( filterWrappers );
    }

    private File getDestinationFile( File outputDirectory, String targetPath, String name,
                                     MavenResourcesExecution mavenResourcesExecution )
                                         throws MavenFilteringException
    {
        String destination;
        if ( !mavenResourcesExecution.isFlatten() )
        {
            destination = name;
        }
        else
        {
            Path path = Paths.get( name );
            Path filePath = path.getFileName();
            destination = filePath.toString();
        }

        if ( mavenResourcesExecution.isFilterFilenames() && mavenResourcesExecution.getFilterWrappers().size() > 0 )
        {
            destination = filterFileName( destination, mavenResourcesExecution.getFilterWrappers() );
        }

        if ( targetPath != null )
        {
            destination = targetPath + "/" + destination;
        }

        File destinationFile = new File( destination );
        if ( !destinationFile.isAbsolute() )
        {
            destinationFile = new File( outputDirectory, destination );
        }

        if ( !destinationFile.getParentFile().exists() )
        {
            destinationFile.getParentFile().mkdirs();
        }
        return destinationFile;
    }

    private String[] setupScanner( Resource resource, Scanner scanner, boolean addDefaultExcludes )
    {
        String[] includes = null;
        if ( resource.getIncludes() != null && !resource.getIncludes().isEmpty() )
        {
            includes = resource.getIncludes().toArray( EMPTY_STRING_ARRAY );
        }
        else
        {
            includes = DEFAULT_INCLUDES;
        }
        scanner.setIncludes( includes );

        String[] excludes = null;
        if ( resource.getExcludes() != null && !resource.getExcludes().isEmpty() )
        {
            excludes = resource.getExcludes().toArray( EMPTY_STRING_ARRAY );
            scanner.setExcludes( excludes );
        }

        if ( addDefaultExcludes )
        {
            scanner.addDefaultExcludes();
        }
        return includes;
    }

    private void copyDirectoryLayout( File sourceDirectory, File destinationDirectory, Scanner scanner )
        throws IOException
    {
        if ( sourceDirectory == null )
        {
            throw new IOException( "source directory can't be null." );
        }

        if ( destinationDirectory == null )
        {
            throw new IOException( "destination directory can't be null." );
        }

        if ( sourceDirectory.equals( destinationDirectory ) )
        {
            throw new IOException( "source and destination are the same directory." );
        }

        if ( !sourceDirectory.exists() )
        {
            throw new IOException( "Source directory doesn't exists (" + sourceDirectory.getAbsolutePath() + ")." );
        }

        List<String> includedDirectories = Arrays.asList( scanner.getIncludedDirectories() );

        for ( String name : includedDirectories )
        {
            File source = new File( sourceDirectory, name );

            if ( source.equals( sourceDirectory ) )
            {
                continue;
            }

            File destination = new File( destinationDirectory, name );
            destination.mkdirs();
        }
    }

    private String getRelativeOutputDirectory( MavenResourcesExecution execution )
    {
        String relOutDir = execution.getOutputDirectory().getAbsolutePath();

        if ( execution.getMavenProject() != null && execution.getMavenProject().getBasedir() != null )
        {
            String basedir = execution.getMavenProject().getBasedir().getAbsolutePath();
            relOutDir = PathTool.getRelativeFilePath( basedir, relOutDir );
            if ( relOutDir == null )
            {
                relOutDir = execution.getOutputDirectory().getPath();
            }
            else
            {
                relOutDir = relOutDir.replace( '\\', '/' );
            }
        }

        return relOutDir;
    }

    /*
     * Filter the name of a file using the same mechanism for filtering the content of the file.
     */
    private String filterFileName( String name, List<FilterWrapper> wrappers )
        throws MavenFilteringException
    {

        Reader reader = new StringReader( name );
        for ( FilterWrapper wrapper : wrappers )
        {
            reader = wrapper.getReader( reader );
        }

        try ( StringWriter writer = new StringWriter() )
        {
            IOUtils.copy( reader, writer );
            String filteredFilename = writer.toString();

            if ( getLogger().isDebugEnabled() )
            {
                getLogger().debug( "renaming filename " + name + " to " + filteredFilename );
            }
            return filteredFilename;
        }
        catch ( IOException e )
        {
            throw new MavenFilteringException( "Failed filtering filename" + name, e );
        }

    }

}
