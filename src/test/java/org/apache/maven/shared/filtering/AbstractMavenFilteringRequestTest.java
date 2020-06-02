package org.apache.maven.shared.filtering;

import static org.hamcrest.MatcherAssert.assertThat;

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


import java.util.LinkedHashSet;

import org.hamcrest.Matchers;
import org.junit.Test;

/**
 * @author Karl Heinz Marbaise <a href="mailto:khmarbaise@apache.org>khmarbaise@apache.org</a>.
 */
public class AbstractMavenFilteringRequestTest
{
    
    private AbstractMavenFilteringRequest request = new AbstractMavenFilteringRequest();
    private LinkedHashSet<String> delimiters = new LinkedHashSet<>();
    
    @Test
    public void setDelimitersShouldNotChangeAnythingIfUsingNull()
    {
        request.setDelimiters( null, false );
        assertThat( request.getDelimiters(), Matchers.contains( "${*}", "@" ) );
    }

    @Test
    public void setDelimitersShouldNotChangeAnythingIfUsingEmpty()
    {
        request.setDelimiters( delimiters, false );
        assertThat( request.getDelimiters(), Matchers.contains( "${*}", "@" ) );
    }

    @Test
    public void setDelimitersShouldAddOnlyTheGivenDelimiter()
    {
        delimiters.add( "test" );
        request.setDelimiters( delimiters, false );
        assertThat( request.getDelimiters(), Matchers.contains( "test" ) );
    }

    @Test
    public void setDelimitersShouldAddDefaultDelimitersForNullElements()
    {
        delimiters.add( "test" );
        delimiters.add( null );
        delimiters.add( "second" );
        request.setDelimiters( delimiters, false );
        assertThat( request.getDelimiters(), Matchers.contains( "test", "${*}", "second" ) );
    }

    @Test
    public void setDelimitersShouldAddDefaultDelimitersIfUseDefaultDelimitersIfNullGiven()
    {
        request.setDelimiters( null, true );
        assertThat( request.getDelimiters(), Matchers.contains( "${*}", "@" ) );
    }

    @Test
    public void setDelimitersShouldAddDefaultDelimitersIfUseDefaultDelimitersIfNotNullGiven()
    {
        LinkedHashSet<String> delimiters = new LinkedHashSet<>();
        request.setDelimiters( delimiters, true );
        assertThat( request.getDelimiters(), Matchers.contains( "${*}", "@" ) );
    }

    @Test
    public void setDelimitersShouldAddDefaultDelimitersIfUseDefaultDelimitersIfSingleElementIsGiven()
    {
        LinkedHashSet<String> delimiters = new LinkedHashSet<>();
        delimiters.add( "test" );
        request.setDelimiters( delimiters, true );
        assertThat( request.getDelimiters(), Matchers.contains( "${*}", "@", "test" ) );
    }

    @Test
    public void setDelimitersShouldAddDefaultDelimitersForNullElement()
    {
        LinkedHashSet<String> delimiters = new LinkedHashSet<>();
        delimiters.add( "test" );
        delimiters.add( null );
        delimiters.add( "second" );
        request.setDelimiters( delimiters, true );
        assertThat( request.getDelimiters(), Matchers.contains( "${*}", "@", "test", "second" ) );
    }

}