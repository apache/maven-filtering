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
import java.util.regex.Pattern;

import org.codehaus.plexus.util.Os;

/**
 * @author Olivier Lamy
 * @author Dennis Lundberg
 */
public final class FilteringUtils
{
    private static final String WINDOWS_PATH_PATTERN = "^(.*)[a-zA-Z]:\\\\(.*)";

    private static final Pattern PATTERN = Pattern.compile( WINDOWS_PATH_PATTERN );

    /**
     *
     */
    private FilteringUtils()
    {
        // nothing just an util class
    }

    // TODO: Correct to handle relative windows paths. (http://jira.apache.org/jira/browse/MSHARED-121)
    // How do we distinguish a relative windows path from some other value that happens to contain backslashes??
    /**
     * @param val The value to be escaped.
     * @return Escaped value
     */
    public static String escapeWindowsPath( String val )
    {
        if ( !isEmpty( val ) && PATTERN.matcher( val ).matches() )
        {
            // Adapted from StringUtils.replace in plexus-utils to accommodate pre-escaped backslashes.
            StringBuilder buf = new StringBuilder( val.length() );
            int start = 0, end = 0;
            while ( ( end = val.indexOf( '\\', start ) ) != -1 )
            {
                buf.append( val.substring( start, end ) ).append( "\\\\" );
                start = end + 1;

                if ( val.indexOf( '\\', end + 1 ) == end + 1 )
                {
                    start++;
                }
            }

            buf.append( val.substring( start ) );

            return buf.toString();
        }
        return val;
    }


    /**
     * Resolve a file <code>filename</code> to its canonical form. If <code>filename</code> is
     * relative (doesn't start with <code>/</code>), it is resolved relative to
     * <code>baseFile</code>. Otherwise it is treated as a normal root-relative path.
     *
     * @param baseFile where to resolve <code>filename</code> from, if <code>filename</code> is relative
     * @param filename absolute or relative file path to resolve
     * @return the canonical <code>File</code> of <code>filename</code>
     */
    public static File resolveFile( final File baseFile, String filename )
    {
        String filenm = filename;
        if ( '/' != File.separatorChar )
        {
            filenm = filename.replace( '/', File.separatorChar );
        }

        if ( '\\' != File.separatorChar )
        {
            filenm = filename.replace( '\\', File.separatorChar );
        }

        // deal with absolute files
        if ( filenm.startsWith( File.separator ) || ( Os.isFamily( Os.FAMILY_WINDOWS ) && filenm.indexOf( ":" ) > 0 ) )
        {
            File file = new File( filenm );

            try
            {
                file = file.getCanonicalFile();
            }
            catch ( final IOException ioe )
            {
                // nop
            }

            return file;
        }
        // FIXME: I'm almost certain this // removal is unnecessary, as getAbsoluteFile() strips
        // them. However, I'm not sure about this UNC stuff. (JT)
        final char[] chars = filename.toCharArray();
        final StringBuilder sb = new StringBuilder();

        //remove duplicate file separators in succession - except
        //on win32 at start of filename as UNC filenames can
        //be \\AComputer\AShare\myfile.txt
        int start = 0;
        if ( '\\' == File.separatorChar )
        {
            sb.append( filenm.charAt( 0 ) );
            start++;
        }

        for ( int i = start; i < chars.length; i++ )
        {
            final boolean doubleSeparator = File.separatorChar == chars[i] && File.separatorChar == chars[i - 1];

            if ( !doubleSeparator )
            {
                sb.append( chars[i] );
            }
        }

        filenm = sb.toString();

        //must be relative
        File file = ( new File( baseFile, filenm ) ).getAbsoluteFile();

        try
        {
            file = file.getCanonicalFile();
        }
        catch ( final IOException ioe )
        {
            // nop
        }

        return file;
    }

    static boolean isEmpty( final String string )
    {
        return string == null || string.trim().isEmpty();
    }
}
