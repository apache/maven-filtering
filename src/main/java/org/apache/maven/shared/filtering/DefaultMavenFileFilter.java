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
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;
import org.apache.maven.shared.utils.io.FileUtils.FilterWrapper;
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
                FilterWrapper[] array = filterWrappers.toArray( new FilterWrapper[0] );
                FileUtils.copyFile( from, to, encoding, array, false );
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
}
