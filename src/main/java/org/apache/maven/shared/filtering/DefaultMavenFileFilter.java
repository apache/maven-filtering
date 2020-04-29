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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils;
import org.apache.maven.shared.utils.io.FileUtils.FilterWrapper;
import org.apache.maven.shared.utils.io.IOUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * @author Olivier Lamy
 */
@Component( role = org.apache.maven.shared.filtering.MavenFileFilter.class, hint = "default" )
public class DefaultMavenFileFilter
    extends BaseFilter
    implements MavenFileFilter
{

    @Requirement
    private MavenReaderFilter readerFilter;

    @Requirement
    private BuildContext buildContext;

    @Override
    public void copyFile( File from, File to, boolean filtering, MavenProject mavenProject, List<String> filters,
                          boolean escapedBackslashesInFilePath, String encoding, MavenSession mavenSession )
                              throws MavenFilteringException
    {
        MavenResourcesExecution mre = new MavenResourcesExecution();
        mre.setMavenProject( mavenProject );
        mre.setFileFilters( filters );
        mre.setEscapeWindowsPaths( escapedBackslashesInFilePath );
        mre.setMavenSession( mavenSession );
        mre.setInjectProjectBuildFilters( true );

        List<FileUtils.FilterWrapper> filterWrappers = getDefaultFilterWrappers( mre );
        copyFile( from, to, filtering, filterWrappers, encoding );
    }

    @Override
    public void copyFile( MavenFileFilterRequest mavenFileFilterRequest )
        throws MavenFilteringException
    {
        List<FilterWrapper> filterWrappers = getDefaultFilterWrappers( mavenFileFilterRequest );

        copyFile( mavenFileFilterRequest.getFrom(), mavenFileFilterRequest.getTo(),
                  mavenFileFilterRequest.isFiltering(), filterWrappers, mavenFileFilterRequest.getEncoding() );
    }

    @Override
    public void copyFile( File from, File to, boolean filtering, List<FileUtils.FilterWrapper> filterWrappers,
                          String encoding )
                              throws MavenFilteringException
    {
        // overwrite forced to false to preserve backward comp
        copyFile( from, to, filtering, filterWrappers, encoding, false );
    }

    @Override
    public void copyFile( File from, File to, boolean filtering, List<FileUtils.FilterWrapper> filterWrappers,
                          String encoding, boolean overwrite )
                              throws MavenFilteringException
    {
        try
        {
            if ( filtering )
            {
                if ( getLogger().isDebugEnabled() )
                {
                    getLogger().debug( "filtering " + from.getPath() + " to " + to.getPath() );
                }
                filterFile( from, to, encoding, filterWrappers, overwrite );
            }
            else
            {
                if ( getLogger().isDebugEnabled() )
                {
                    getLogger().debug( "copy " + from.getPath() + " to " + to.getPath() );
                }
                FileUtils.copyFile( from, to, encoding, new FileUtils.FilterWrapper[0], overwrite );
            }

            buildContext.refresh( to );
        }
        catch ( IOException e )
        {
            throw new MavenFilteringException( e.getMessage(), e );
        }

    }

    private void filterFile( @Nonnull File from, @Nonnull File to, @Nullable String encoding,
                             @Nullable List<FilterWrapper> wrappers, boolean overwrite )
                                 throws IOException, MavenFilteringException
    {
        if ( wrappers != null && wrappers.size() > 0 )
        {
            if ( overwrite )
            {
                try ( Reader fileReader = getFileReader( encoding, from );
                      Writer fileWriter = getFileWriter( encoding, to ) )
                {
                    Reader src = readerFilter.filter( fileReader, true, wrappers );

                    IOUtil.copy( src, fileWriter );
                }
            }
            else
            {
                final File dir = to.getParentFile();

                if ( !dir.isDirectory() )
                {
                    FileUtils.mkdir( dir.getPath() );
                }

                File tmp = FileUtils.createTempFile( "filter", ".tmp", dir );

                if ( getLogger().isDebugEnabled() )
                {
                    getLogger().debug( "writing to temporary file " + tmp.getPath() );
                }

                try ( Reader fileReader = getFileReader( encoding, from );
                      Writer fileWriter = getFileWriter( encoding, tmp ) )
                {
                    Reader src = readerFilter.filter( fileReader, true, wrappers );

                    IOUtil.copy( src, fileWriter );
                }

                if ( FileUtils.contentEquals( tmp, to ) )
                {
                    if ( getLogger().isDebugEnabled() )
                    {
                        getLogger().debug( "matching content, deleting " + tmp.getPath() );
                    }
                    FileUtils.delete( tmp );
                }
                else
                {
                    if ( getLogger().isDebugEnabled() )
                    {
                        getLogger().debug( "modified content, overwriting " + to.getPath() );
                    }
                    FileUtils.rename( tmp, to );
                }
            }
        }
        else
        {
            if ( overwrite || to.lastModified() < from.lastModified() )
            {
                FileUtils.copyFile( from, to );
            }
        }
    }

    private Writer getFileWriter( String encoding, File to )
        throws IOException
    {
        if ( StringUtils.isEmpty( encoding ) )
        {
            return new FileWriter( to );
        }
        else
        {
            FileOutputStream outstream = new FileOutputStream( to );

            return new OutputStreamWriter( outstream, encoding );
        }
    }

    private Reader getFileReader( String encoding, File from )
        throws FileNotFoundException, UnsupportedEncodingException
    {
        // buffer so it isn't reading a byte at a time!
        if ( StringUtils.isEmpty( encoding ) )
        {
            return new BufferedReader( new FileReader( from ) );
        }
        else
        {
            FileInputStream instream = new FileInputStream( from );
            return new BufferedReader( new InputStreamReader( instream, encoding ) );
        }
    }

}
