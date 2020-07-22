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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.ValueSource;

/**
 * @author Olivier Lamy
 *
 * @since 1.0-beta-1
 */
public class DefaultMavenResourcesFilteringTest
    extends PlexusTestCase
{

    private File outputDirectory = new File( getBasedir(), "target/DefaultMavenResourcesFilteringTest" );
    private File baseDir = new File( "c:\\foo\\bar" );
    private StubMavenProject mavenProject = new StubMavenProject( baseDir );


    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();
        if ( outputDirectory.exists() )
        {
            FileUtils.deleteDirectory( outputDirectory );
        }
        outputDirectory.mkdirs();
    }

    public void testSimpleFiltering()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        Properties projectProperties = new Properties();
        projectProperties.put( "foo", "bar" );
        projectProperties.put( "java.version", "zloug" );
        mavenProject.setProperties( projectProperties );
        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        File initialImageFile = new File( unitFilesDir, "happy_duke.gif" );

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        List<String> nonFilteredFileExtensions = Collections.singletonList( "gif" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", filtersFile,
                                         nonFilteredFileExtensions, new StubMavenSession() );
        mavenResourcesExecution.setUseDefaultFilterWrappers( true );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        assertFiltering( initialImageFile, false, false );
    }

    public void testSessionFiltering()
        throws Exception
    {
        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/session-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );

        List<String> filtersFile = new ArrayList<>();

        Settings settings = new Settings();
        settings.setLocalRepository( System.getProperty( "localRepository",
                                                         System.getProperty( "maven.repo.local",
                                                                             "/path/to/local/repo" ) ) );

        MavenSession session = new StubMavenSession( settings );

        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setResources( resources );
        mre.setOutputDirectory( outputDirectory );
        mre.setEncoding( "UTF-8" );
        mre.setMavenProject( mavenProject );
        mre.setFilters( filtersFile );
        mre.setNonFilteredFileExtensions( Collections.<String> emptyList() );
        mre.setMavenSession( session );
        mre.setUseDefaultFilterWrappers( true );

        mavenResourcesFiltering.filterResources( mre );

        Properties result = new Properties();
        
        try ( FileInputStream in = new FileInputStream( new File( outputDirectory, "session-filter-target.txt" ) ) )
        {
            result.load( in );
        }

        assertEquals( settings.getLocalRepository(), result.getProperty( "session.settings.local.repo" ) );
        assertEquals( settings.getLocalRepository(), result.getProperty( "settings.local.repo" ) );
        assertEquals( settings.getLocalRepository(), result.getProperty( "local.repo" ) );
    }

    public void testWithMavenResourcesExecution()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        Properties projectProperties = new Properties();
        projectProperties.put( "foo", "bar" );
        projectProperties.put( "java.version", "zloug" );
        mavenProject.setProperties( projectProperties );
        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        File initialImageFile = new File( unitFilesDir, "happy_duke.gif" );

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        List<String> nonFilteredFileExtensions = Collections.singletonList( "gif" );
        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", filtersFile,
                                         nonFilteredFileExtensions, new StubMavenSession() );
        mavenResourcesExecution.setEscapeString( "\\" );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );
        assertFiltering( initialImageFile, true, false );
    }

    public void testWithMavenResourcesExecutionWithAdditionalProperties()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        Properties projectProperties = new Properties();
        projectProperties.put( "foo", "bar" );
        projectProperties.put( "java.version", "zloug" );
        mavenProject.setProperties( projectProperties );
        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        File initialImageFile = new File( unitFilesDir, "happy_duke.gif" );

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        List<String> nonFilteredFileExtensions = Collections.singletonList( "gif" );
        Properties additionalProperties = new Properties();
        additionalProperties.put( "greatDate", "1973-06-14" );
        additionalProperties.put( "pom.version", "99.00" );
        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", filtersFile,
                                         nonFilteredFileExtensions, new StubMavenSession() );
        mavenResourcesExecution.setAdditionalProperties( additionalProperties );
        mavenResourcesExecution.setEscapeString( "\\" );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );
        assertFiltering( initialImageFile, true, true );
    }

    private void assertFiltering( File initialImageFile, boolean escapeTest,
                                  boolean additionalProperties )
                                      throws Exception
    {
        assertEquals( 7, outputDirectory.listFiles().length );
        Properties result = new Properties();
        
        try ( FileInputStream in = new FileInputStream( new File( outputDirectory, "empty-maven-resources-filtering.txt" ) ) )
        {
            result.load( in );
        }
        
        assertTrue( result.isEmpty() );

        result = new Properties();

        try ( FileInputStream in = new FileInputStream( new File( outputDirectory, "maven-resources-filtering.txt" ) ) )
        {
            result.load( in );
        }

        assertFalse( result.isEmpty() );

        if ( additionalProperties )
        {
            assertEquals( "1973-06-14", result.getProperty( "goodDate" ) );
            assertEquals( "99.00", result.get( "version" ) );
        }
        else
        {
            assertEquals( "1.0", result.get( "version" ) );
        }
        assertEquals( "org.apache", result.get( "groupId" ) );
        assertEquals( "bar", result.get( "foo" ) );
        assertEquals( "${foo.version}", result.get( "fooVersion" ) );

        assertEquals( "@@", result.getProperty( "emptyexpression" ) );
        assertEquals( "${}", result.getProperty( "emptyexpression2" ) );
        assertEquals( System.getProperty( "user.dir" ), result.getProperty( "userDir" ) );
        String userDir = result.getProperty( "userDir" );
        assertTrue( new File( userDir ).exists() );
        assertEquals( new File( System.getProperty( "user.dir" ) ), new File( userDir ) );
        assertEquals( System.getProperty( "java.version" ), result.getProperty( "javaVersion" ) );

        String userHome = result.getProperty( "userHome" );

        assertTrue( "'" + userHome + "' does not exist.", new File( userHome ).exists() );
        assertEquals( new File( System.getProperty( "user.home" ) ), new File( userHome ) );

        if ( escapeTest )
        {
            assertEquals( "${java.version}", result.getProperty( "escapeJavaVersion" ) );
            assertEquals( "@user.dir@", result.getProperty( "escapeuserDir" ) );
        }
        assertEquals( baseDir.toString(), result.get( "base" ) );
        assertEquals( new File( baseDir.toString() ).getPath(), new File( result.getProperty( "base" ) ).getPath() );

        File imageFile = new File( outputDirectory, "happy_duke.gif" );
        assertTrue( imageFile.exists() );
        // assertEquals( initialImageFile.length(), imageFile.length() );
        assertTrue( filesAreIdentical( initialImageFile, imageFile ) );
    }

    public void testAddingTokens()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        Properties projectProperties = new Properties();
        projectProperties.put( "foo", "bar" );
        projectProperties.put( "java.version", "zloug" );
        mavenProject.setProperties( projectProperties );
        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        File initialImageFile = new File( unitFilesDir, "happy_duke.gif" );

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        List<String> nonFilteredFileExtensions = Collections.singletonList( "gif" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", null,
                                         nonFilteredFileExtensions, new StubMavenSession() );

        ValueSource vs =
            new PrefixedObjectValueSource( mavenResourcesExecution.getProjectStartExpressions(), mavenProject, true );

        mavenResourcesExecution.addFilerWrapperWithEscaping( vs, "@", "@", null, false );

        mavenResourcesFiltering.filterResources( mavenResourcesExecution );
        Properties result =
            PropertyUtils.loadPropertyFile( new File( outputDirectory, "maven-resources-filtering.txt" ), null );
        assertFalse( result.isEmpty() );
        assertEquals( mavenProject.getName(), result.get( "pomName" ) );
        assertFiltering( initialImageFile, false, false );
    }

    public void testNoFiltering()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";
        File initialImageFile = new File( unitFilesDir, "happy_duke.gif" );

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );

        resource.setDirectory( unitFilesDir );
        resource.setFiltering( false );

        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setResources( resources );
        mre.setOutputDirectory( outputDirectory );
        mre.setEncoding( "UTF-8" );
        mre.setMavenProject( mavenProject );
        mre.setFilters( null );
        mre.setNonFilteredFileExtensions( Collections.<String> emptyList() );
        mre.setMavenSession( new StubMavenSession() );

        mavenResourcesFiltering.filterResources( mre );

        assertEquals( 7, outputDirectory.listFiles().length );
        Properties result =
            PropertyUtils.loadPropertyFile( new File( outputDirectory, "empty-maven-resources-filtering.txt" ), null );
        assertTrue( result.isEmpty() );

        result = PropertyUtils.loadPropertyFile( new File( outputDirectory, "maven-resources-filtering.txt" ), null );
        assertFalse( result.isEmpty() );

        assertEquals( "${pom.version}", result.get( "version" ) );
        assertEquals( "${pom.groupId}", result.get( "groupId" ) );
        assertEquals( "${foo}", result.get( "foo" ) );
        assertEquals( "@@", result.getProperty( "emptyexpression" ) );
        assertEquals( "${}", result.getProperty( "emptyexpression2" ) );
        File imageFile = new File( outputDirectory, "happy_duke.gif" );
        assertTrue( filesAreIdentical( initialImageFile, imageFile ) );
    }

    private static boolean filesAreIdentical( File expected, File current )
        throws IOException
    {
        if ( expected.length() != current.length() )
        {
            return false;
        }

        byte[] expectedBuffer = Files.readAllBytes( expected.toPath() );
        byte[] currentBuffer = Files.readAllBytes( current.toPath() );
        if ( expectedBuffer.length != currentBuffer.length )
        {
            return false;
        }
        for ( int i = 0, size = expectedBuffer.length; i < size; i++ )
        {
            if ( expectedBuffer[i] != currentBuffer[i] )
            {
                return false;
            }
        }
    return true;
    }

    public void testIncludeOneFile()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );
        resource.addInclude( "includ*" );

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", filtersFile,
                                         Collections.<String> emptyList(), new StubMavenSession() );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File[] files = outputDirectory.listFiles();
        assertEquals( 1, files.length );
        assertEquals( "includefile.txt", files[0].getName() );

    }

    public void testIncludeOneFileAndDirectory()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );
        resource.addInclude( "includ*" );
        resource.addInclude( "**/includ*" );

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", filtersFile,
                                         Collections.<String> emptyList(), new StubMavenSession() );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File[] files = outputDirectory.listFiles();
        assertNotNull( files );
        assertEquals( 2, files.length );
        File includeFile = new File( outputDirectory, "includefile.txt" );
        assertTrue( includeFile.exists() );

        includeFile = new File( new File( outputDirectory, "includedir" ), "include.txt" );
        assertTrue( includeFile.exists() );

    }

    public void testExcludeOneFile()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );
        resource.addExclude( "*.gif" );
        resource.addExclude( "**/excludedir/**" );

        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", filtersFile,
                                         Collections.<String> emptyList(), new StubMavenSession() );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File[] files = outputDirectory.listFiles();
        assertEquals( 5, files.length );
        File includeFile = new File( outputDirectory, "includefile.txt" );
        assertTrue( includeFile.exists() );

        includeFile = new File( new File( outputDirectory, "includedir" ), "include.txt" );
        assertTrue( includeFile.exists() );

        File imageFile = new File( outputDirectory, "happy_duke.gif" );
        assertFalse( imageFile.exists() );

        File excludeDir = new File( outputDirectory, "excludedir" );
        assertFalse( excludeDir.exists() );
    }

    public void testTargetAbsolutePath()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );
        resource.addInclude( "includ*" );

        String targetPath = getBasedir() + "/target/testAbsolutePath/";
        File targetPathFile = new File( targetPath );
        resource.setTargetPath( targetPathFile.getAbsolutePath() );

        if ( !targetPathFile.exists() )
        {
            targetPathFile.mkdirs();
        }
        else
        {
            FileUtils.cleanDirectory( targetPathFile );
        }
        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", filtersFile,
                                         Collections.<String> emptyList(), new StubMavenSession() );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File[] files = targetPathFile.listFiles();
        assertEquals( 1, files.length );
        assertEquals( "includefile.txt", files[0].getName() );
    }

    public void testTargetPath()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-resources-filtering";

        Resource resource = new Resource();
        List<Resource> resources = new ArrayList<>();
        resources.add( resource );
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );
        resource.addInclude( "includ*" );
        resource.setTargetPath( "testTargetPath" );
        List<String> filtersFile = new ArrayList<>();
        filtersFile.add( getBasedir()
            + "/src/test/units-files/maven-resources-filtering/empty-maven-resources-filtering.txt" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8", filtersFile,
                                         Collections.<String> emptyList(), new StubMavenSession() );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File targetPathFile = new File( outputDirectory, "testTargetPath" );

        File[] files = targetPathFile.listFiles();
        assertEquals( 1, files.length );
        assertEquals( "includefile.txt", files[0].getName() );
    }

    @SuppressWarnings( "serial" )
    public void testEmptyDirectories()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        List<Resource> resources = new ArrayList<>();
        resources.add( new Resource()
        {
            {
                setDirectory( getBasedir() + "/src/test/units-files/includeEmptyDirs" );
                setExcludes( Arrays.asList( "**/.gitignore" ) );
            }
        } );
        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8",
                                         Collections.<String> emptyList(), Collections.<String> emptyList(),
                                         new StubMavenSession() );
        mavenResourcesExecution.setIncludeEmptyDirs( true );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File[] childs = outputDirectory.listFiles();
        assertNotNull( childs );
        assertEquals( 3, childs.length );

        for ( File file : childs )
        {
            if ( file.getName().endsWith( "dir1" ) || file.getName().endsWith( "empty-directory" )
                || file.getName().endsWith( "empty-directory-child" ) )
            {
                if ( file.getName().endsWith( "dir1" ) )
                {
                    assertEquals( 1, file.list().length );
                    assertTrue( file.listFiles()[0].getName().endsWith( "foo.txt" ) );
                }
                if ( file.getName().endsWith( "empty-directory" ) )
                {
                    assertEquals( 0, file.list().length );
                }
                if ( file.getName().endsWith( "empty-directory-child" ) )
                {
                    assertEquals( 1, file.list().length );
                    assertTrue( file.listFiles()[0].isDirectory() );
                    assertEquals( 0, file.listFiles()[0].listFiles().length );
                }
            }
            else
            {
                fail( "unknow child file found " + file.getName() );
            }
        }
    }

    @SuppressWarnings( "serial" )
    public void testShouldReturnGitIgnoreFiles()
        throws Exception
    {
        createTestDataStructure();

        File outputDirectory = new File( getBasedir(), "/target/testGitIgnoreFile" );
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        List<Resource> resources = new ArrayList<>();
        resources.add( new Resource()
        {
            {
                setDirectory( getBasedir() + "/target/sourceTestGitIgnoreFile" );
                setIncludes( Arrays.asList( "**/*" ) );
            }
        } );
        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, outputDirectory, mavenProject, "UTF-8",
                                         Collections.<String> emptyList(), Collections.<String> emptyList(),
                                         new StubMavenSession() );
        mavenResourcesExecution.setIncludeEmptyDirs( true );
        mavenResourcesExecution.setAddDefaultExcludes( false );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File[] children = outputDirectory.listFiles();
        assertNotNull( children );
        assertEquals( 3, children.length );

        for ( File file : children )
        {
            if ( file.getName().endsWith( "dir1" ) || file.getName().endsWith( "empty-directory" )
                || file.getName().endsWith( "empty-directory-child" ) )
            {
                if ( file.getName().endsWith( "dir1" ) )
                {
                    assertEquals( 1, file.list().length );
                    assertTrue( file.listFiles()[0].getName().endsWith( "foo.txt" ) );
                }
                if ( file.getName().endsWith( "empty-directory" ) )
                {

                    assertEquals( 1, file.list().length );
                    assertTrue( file.listFiles()[0].getName().endsWith( ".gitignore" ) );
                }
                if ( file.getName().endsWith( "empty-directory-child" ) )
                {
                    assertEquals( 1, file.list().length );
                    assertTrue( file.listFiles()[0].isDirectory() );
                    assertEquals( 1, file.listFiles()[0].listFiles().length );

                    assertTrue( file.listFiles()[0].listFiles()[0].getName().endsWith( ".gitignore" ) );
                }
            }
            else
            {
                fail( "unknown child file found " + file.getName() );
            }
        }
    }

    /**
     * The folder and file structure will be created instead of letting this resource plugin
     * copy the structure which does not work.
     */
    private static void createTestDataStructure()
        throws IOException
    {
        File sourceDirectory = new File( getBasedir(), "/target/sourceTestGitIgnoreFile" );
        if ( sourceDirectory.exists() )
        {
            FileUtils.forceDelete( sourceDirectory );
        }

        File dir1 = new File( sourceDirectory, "dir1" );

        dir1.mkdirs();
        FileUtils.write( new File( dir1, "foo.txt" ), "This is a Test File", "UTF-8" );

        File emptyDirectory = new File( sourceDirectory, "empty-directory" );
        emptyDirectory.mkdirs();

        FileUtils.write( new File( emptyDirectory, ".gitignore" ), "# .gitignore file", "UTF-8" );

        File emptyDirectoryChild = new File( sourceDirectory, "empty-directory-child" );
        emptyDirectory.mkdirs();

        File emptyDirectoryChildEmptyChild = new File( emptyDirectoryChild, "empty-child" );
        emptyDirectoryChildEmptyChild.mkdirs();

        FileUtils.write( new File( emptyDirectoryChildEmptyChild, ".gitignore" ), "# .gitignore file", "UTF-8" );
    }

    /**
     * unit test for MSHARED-81 : https://issues.apache.org/jira/browse/MSHARED-81
     */
    @SuppressWarnings( "serial" )
    public void testMSHARED81()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );

        mavenProject.addProperty( "escaped", "this is escaped" );
        mavenProject.addProperty( "escaped.at", "this is escaped.at" );
        mavenProject.addProperty( "foo", "this is foo" );
        mavenProject.addProperty( "bar", "this is bar" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        List<Resource> resources = new ArrayList<>();
        resources.add( new Resource()
        {
            {
                setDirectory( getBasedir() + "/src/test/units-files/MSHARED-81/resources" );
                setFiltering( false );
            }
        } );
        resources.add( new Resource()
        {
            {
                setDirectory( getBasedir() + "/src/test/units-files/MSHARED-81/filtered" );
                setFiltering( true );
            }
        } );
        File output = new File( outputDirectory, "MSHARED-81" );
        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, output, mavenProject, "UTF-8", Collections.<String> emptyList(),
                                         Collections.<String> emptyList(), new StubMavenSession() );
        mavenResourcesExecution.setIncludeEmptyDirs( true );
        mavenResourcesExecution.setEscapeString( "\\" );

        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        Properties filteredResult = PropertyUtils.loadPropertyFile( new File( output, "filtered.properties" ), null );

        Properties expectedFilteredResult =
            PropertyUtils.loadPropertyFile( new File( getBasedir() + "/src/test/units-files/MSHARED-81",
                                                      "expected-filtered.properties" ),
                                            null );

        assertEquals( expectedFilteredResult, filteredResult );

        Properties nonFilteredResult =
            PropertyUtils.loadPropertyFile( new File( output, "unfiltered.properties" ), null );

        Properties expectedNonFilteredResult =
            PropertyUtils.loadPropertyFile( new File( getBasedir() + "/src/test/units-files/MSHARED-81/resources",
                                                      "unfiltered.properties" ),
                                            null );

        assertTrue( nonFilteredResult.equals( expectedNonFilteredResult ) );
    }

    /**
     * unit test for MRESOURCES-230 : https://issues.apache.org/jira/browse/MRESOURCES-230
     */
//    public void testCorrectlyEscapesEscapeString()
//        throws Exception
//    {
//
//        mavenProject.setVersion( "1.0" );
//        mavenProject.addProperty( "a", "DONE_A" );
//
//        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );
//
//        List<Resource> resources = new ArrayList<Resource>();
//        resources.add( new Resource()
//        {
//
//            {
//                setDirectory( getBasedir() + "/src/test/units-files/MRESOURCES-230" );
//                setFiltering( true );
//            }
//
//        } );
//        resources.get( 0 ).addExclude( "expected.txt" );
//
//        File output = new File( outputDirectory, "MRESOURCES-230" );
//        MavenResourcesExecution mavenResourcesExecution =
//            new MavenResourcesExecution( resources, output, mavenProject, "UTF-8", Collections.<String>emptyList(),
//                                         Collections.<String>emptyList(), new StubMavenSession() );
//        mavenResourcesExecution.setIncludeEmptyDirs( true );
//        mavenResourcesExecution.setEscapeString( "\\" );
//
//        mavenResourcesFiltering.filterResources( mavenResourcesExecution );
//
//        final String filtered = FileUtils.fileRead( new File( output, "resource.txt" ), "UTF-8" );
//        final String expected =
//            FileUtils.fileRead( new File( getBasedir() + "/src/test/units-files/MRESOURCES-230/expected.txt" ) );
//
//        assertEquals( expected, filtered );
//    }

    /**
     * unit test for edge cases : https://issues.apache.org/jira/browse/MSHARED-228
     */
    @SuppressWarnings( "serial" )
    public void testEdgeCases()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );

        mavenProject.addProperty( "escaped", "this is escaped" );
        mavenProject.addProperty( "escaped.at", "this is escaped.at" );
        mavenProject.addProperty( "foo", "this is foo" );
        mavenProject.addProperty( "bar", "this is bar" );
        mavenProject.addProperty( "domain", "this.is.domain.com" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        List<Resource> resources = new ArrayList<>();
        resources.add( new Resource()
        {
            {
                setDirectory( getBasedir() + "/src/test/units-files/edge-cases/resources" );
                setFiltering( false );
            }
        } );
        resources.add( new Resource()
        {
            {
                setDirectory( getBasedir() + "/src/test/units-files/edge-cases/filtered" );
                setFiltering( true );
            }
        } );
        File output = new File( outputDirectory, "edge-cases" );
        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( resources, output, mavenProject, "UTF-8", Collections.<String> emptyList(),
                                         Collections.<String> emptyList(), new StubMavenSession() );
        mavenResourcesExecution.setIncludeEmptyDirs( true );
        mavenResourcesExecution.setEscapeString( "\\" );

        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        Properties filteredResult = PropertyUtils.loadPropertyFile( new File( output, "filtered.properties" ), null );

        Properties expectedFilteredResult =
            PropertyUtils.loadPropertyFile( new File( getBasedir() + "/src/test/units-files/edge-cases",
                                                      "expected-filtered.properties" ),
                                            null );

        assertEquals( expectedFilteredResult, filteredResult );

        Properties nonFilteredResult =
            PropertyUtils.loadPropertyFile( new File( output, "unfiltered.properties" ), null );

        Properties expectedNonFilteredResult =
            PropertyUtils.loadPropertyFile( new File( getBasedir() + "/src/test/units-files/edge-cases/resources",
                                                      "unfiltered.properties" ),
                                            null );

        assertTrue( nonFilteredResult.equals( expectedNonFilteredResult ) );
    }

    // MSHARED-220: Apply filtering to filenames
    public void testFilterFileName()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/maven-filename-filtering";

        Resource resource = new Resource();
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );
        resource.addInclude( "${pom.version}*" );
        resource.setTargetPath( "testTargetPath" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( Collections.singletonList( resource ), outputDirectory, mavenProject, "UTF-8",
                                         Collections.<String> emptyList(), Collections.<String> emptyList(),
                                         new StubMavenSession() );
        mavenResourcesExecution.setFilterFilenames( true );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File targetPathFile = new File( outputDirectory, "testTargetPath" );

        File[] files = targetPathFile.listFiles();
        assertEquals( 1, files.length );
        assertEquals( "1.0.txt", files[0].getName() );
    }

    /**
     * MRESOURCES-171: Use correct encoding when filtering properties-files
     */
    public void testFilterPropertiesFiles()
        throws Exception
    {
        mavenProject.setVersion( "1.0" );
        mavenProject.setGroupId( "org.apache" );
        mavenProject.setName( "test project" );

        MavenResourcesFiltering mavenResourcesFiltering = lookup( MavenResourcesFiltering.class );

        String unitFilesDir = getBasedir() + "/src/test/units-files/MRESOURCES-171";

        Resource resource = new Resource();
        resource.setDirectory( unitFilesDir );
        resource.setFiltering( true );
        resource.setTargetPath( "testFilterPropertiesFiles" );

        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution( Collections.singletonList( resource ), outputDirectory, mavenProject, "UTF-8",
                                         Collections.<String> emptyList(), Collections.<String> emptyList(),
                                         new StubMavenSession() );
        mavenResourcesExecution.setPropertiesEncoding( "ISO-8859-1" );
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );

        File targetPathFile = new File( outputDirectory, "testFilterPropertiesFiles" );
        assertTrue( FileUtils.contentEquals( new File( unitFilesDir, "test.properties" ),
                                             new File( targetPathFile, "test.properties" ) ) );
        assertTrue( FileUtils.contentEquals( new File( unitFilesDir, "test.txt" ),
                                             new File( targetPathFile, "test.txt" ) ) );
    }

    public void testGetEncoding()
    {
        File propertiesFile = new File( "file.properties" );
        File regularFile = new File( "file.xml" );

        // Properties files
        assertEquals( null, DefaultMavenResourcesFiltering.getEncoding( propertiesFile, null, null ) );
        assertEquals( "UTF-8", DefaultMavenResourcesFiltering.getEncoding( propertiesFile, "UTF-8", null ) );
        assertEquals( "ISO-8859-1", DefaultMavenResourcesFiltering.getEncoding( propertiesFile, "UTF-8", "ISO-8859-1" ) );
        // Regular files
        assertEquals( null, DefaultMavenResourcesFiltering.getEncoding( regularFile, null, null ) );
        assertEquals( "UTF-8", DefaultMavenResourcesFiltering.getEncoding( regularFile, "UTF-8", null ) );
        assertEquals( "UTF-8", DefaultMavenResourcesFiltering.getEncoding( regularFile, "UTF-8", "ISO-8859-1" ) );
    }

    public void testIsPropertiesFile()
    {
        // Properties files
        assertTrue( DefaultMavenResourcesFiltering.isPropertiesFile( new File( "file.properties" ) ) );
        assertTrue( DefaultMavenResourcesFiltering.isPropertiesFile( new File( "some/parent/path",
                                                                               "file.properties" ) ) );
        // Regular files
        assertFalse( DefaultMavenResourcesFiltering.isPropertiesFile( new File( "file" ) ) );
        assertFalse( DefaultMavenResourcesFiltering.isPropertiesFile( new File( "some/parent/path", "file" ) ) );
        assertFalse( DefaultMavenResourcesFiltering.isPropertiesFile( new File( "file.xml" ) ) );
        assertFalse( DefaultMavenResourcesFiltering.isPropertiesFile( new File( "some/parent/path", "file.xml" ) ) );
    }
}
