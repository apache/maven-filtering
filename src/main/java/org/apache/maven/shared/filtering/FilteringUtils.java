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
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.util.Os;

/**
 * @author Olivier Lamy
 * @author Dennis Lundberg
 */
public final class FilteringUtils
{
    /**
     * The number of bytes in a kilobyte.
     */
    private static final int ONE_KB = 1024;

    /**
     * The number of bytes in a megabyte.
     */
    private static final int ONE_MB = ONE_KB * ONE_KB;

    /**
     * The file copy buffer size (30 MB)
     */
    private static final int FILE_COPY_BUFFER_SIZE = ONE_MB * 30;

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


    /**
     * <p>This method can calculate the relative path between two paths on a file system.</p>
     * <pre>
     * PathTool.getRelativeFilePath( null, null )                                   = ""
     * PathTool.getRelativeFilePath( null, "/usr/local/java/bin" )                  = ""
     * PathTool.getRelativeFilePath( "/usr/local", null )                           = ""
     * PathTool.getRelativeFilePath( "/usr/local", "/usr/local/java/bin" )          = "java/bin"
     * PathTool.getRelativeFilePath( "/usr/local", "/usr/local/java/bin/" )         = "java/bin"
     * PathTool.getRelativeFilePath( "/usr/local/java/bin", "/usr/local/" )         = "../.."
     * PathTool.getRelativeFilePath( "/usr/local/", "/usr/local/java/bin/java.sh" ) = "java/bin/java.sh"
     * PathTool.getRelativeFilePath( "/usr/local/java/bin/java.sh", "/usr/local/" ) = "../../.."
     * PathTool.getRelativeFilePath( "/usr/local/", "/bin" )                        = "../../bin"
     * PathTool.getRelativeFilePath( "/bin", "/usr/local/" )                        = "../usr/local"
     * </pre>
     * Note: On Windows based system, the <code>/</code> character should be replaced by <code>\</code> character.
     *
     * @param oldPath old path
     * @param newPath new path
     * @return a relative file path from <code>oldPath</code>.
     */
    public static String getRelativeFilePath( final String oldPath, final String newPath )
    {
        if ( isEmpty( oldPath ) || isEmpty( newPath ) )
        {
            return "";
        }

        // normalise the path delimiters
        String fromPath = new File( oldPath ).getPath();
        String toPath = new File( newPath ).getPath();

        // strip any leading slashes if its a windows path
        if ( toPath.matches( "^\\[a-zA-Z]:" ) )
        {
            toPath = toPath.substring( 1 );
        }
        if ( fromPath.matches( "^\\[a-zA-Z]:" ) )
        {
            fromPath = fromPath.substring( 1 );
        }

        // lowercase windows drive letters.
        if ( fromPath.startsWith( ":", 1 ) )
        {
            fromPath = Character.toLowerCase( fromPath.charAt( 0 ) ) + fromPath.substring( 1 );
        }
        if ( toPath.startsWith( ":", 1 ) )
        {
            toPath = Character.toLowerCase( toPath.charAt( 0 ) ) + toPath.substring( 1 );
        }

        // check for the presence of windows drives. No relative way of
        // traversing from one to the other.
        if ( ( toPath.startsWith( ":", 1 ) && fromPath.startsWith( ":", 1 ) )
                && ( !toPath.substring( 0, 1 ).equals( fromPath.substring( 0, 1 ) ) ) )
        {
            // they both have drive path element but they dont match, no
            // relative path
            return null;
        }

        if ( ( toPath.startsWith( ":", 1 ) && !fromPath.startsWith( ":", 1 ) )
                || ( !toPath.startsWith( ":", 1 ) && fromPath.startsWith( ":", 1 ) ) )
        {
            // one has a drive path element and the other doesnt, no relative
            // path.
            return null;
        }

        String resultPath = buildRelativePath( toPath, fromPath, File.separatorChar );

        if ( newPath.endsWith( File.separator ) && !resultPath.endsWith( File.separator ) )
        {
            return resultPath + File.separator;
        }

        return resultPath;
    }

    private static String buildRelativePath( String toPath, String fromPath, final char separatorChar )
    {
        // use tokeniser to traverse paths and for lazy checking
        StringTokenizer toTokeniser = new StringTokenizer( toPath, String.valueOf( separatorChar ) );
        StringTokenizer fromTokeniser = new StringTokenizer( fromPath, String.valueOf( separatorChar ) );

        int count = 0;

        // walk along the to path looking for divergence from the from path
        while ( toTokeniser.hasMoreTokens() && fromTokeniser.hasMoreTokens() )
        {
            if ( separatorChar == '\\' )
            {
                if ( !fromTokeniser.nextToken().equalsIgnoreCase( toTokeniser.nextToken() ) )
                {
                    break;
                }
            }
            else
            {
                if ( !fromTokeniser.nextToken().equals( toTokeniser.nextToken() ) )
                {
                    break;
                }
            }

            count++;
        }

        // reinitialise the tokenisers to count positions to retrieve the
        // gobbled token

        toTokeniser = new StringTokenizer( toPath, String.valueOf( separatorChar ) );
        fromTokeniser = new StringTokenizer( fromPath, String.valueOf( separatorChar ) );

        while ( count-- > 0 )
        {
            fromTokeniser.nextToken();
            toTokeniser.nextToken();
        }

        StringBuilder relativePath = new StringBuilder();

        // add back refs for the rest of from location.
        while ( fromTokeniser.hasMoreTokens() )
        {
            fromTokeniser.nextToken();

            relativePath.append( ".." );

            if ( fromTokeniser.hasMoreTokens() )
            {
                relativePath.append( separatorChar );
            }
        }

        if ( relativePath.length() != 0 && toTokeniser.hasMoreTokens() )
        {
            relativePath.append( separatorChar );
        }

        // add fwd fills for whatevers left of newPath.
        while ( toTokeniser.hasMoreTokens() )
        {
            relativePath.append( toTokeniser.nextToken() );

            if ( toTokeniser.hasMoreTokens() )
            {
                relativePath.append( separatorChar );
            }
        }
        return relativePath.toString();
    }

    static boolean isEmpty( final String string )
    {
        return string == null || string.trim().isEmpty();
    }

    /**
     * <b>If wrappers is null or empty, the file will be copy only if to.lastModified() &lt; from.lastModified() or if
     * overwrite is true</b>
     *
     * @param from the file to copy
     * @param to the destination file
     * @param encoding the file output encoding (only if wrappers is not empty)
     * @param wrappers array of {@link FilterWrapper}
     * @param overwrite if true and wrappers is null or empty, the file will be copied even if
     *         to.lastModified() &lt; from.lastModified()
     * @throws IOException if an IO error occurs during copying or filtering
     */
    public static void copyFile( File from, File to, String encoding,
                                 FilterWrapper[] wrappers, boolean overwrite )
            throws IOException
    {
        if ( wrappers == null || wrappers.length == 0 )
        {
            if ( overwrite || to.lastModified() < from.lastModified() )
            {
                Files.copy( from.toPath(), to.toPath(), LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.REPLACE_EXISTING );
            }
        }
        else
        {
            Charset charset = charset( encoding );

            // buffer so it isn't reading a byte at a time!
            try ( Reader fileReader = Files.newBufferedReader( from.toPath(), charset ) )
            {
                Reader wrapped = fileReader;
                for ( FilterWrapper wrapper : wrappers )
                {
                    wrapped = wrapper.getReader( wrapped );
                }

                if ( overwrite || !to.exists() )
                {
                    try ( Writer fileWriter = Files.newBufferedWriter( to.toPath(), charset ) )
                    {
                        IOUtils.copy( wrapped, fileWriter );
                    }
                }
                else
                {
                    CharsetEncoder encoder = charset.newEncoder();

                    int totalBufferSize = FILE_COPY_BUFFER_SIZE;

                    int charBufferSize = ( int ) Math.floor( totalBufferSize / ( 2 + 2 * encoder.maxBytesPerChar() ) );
                    int byteBufferSize = ( int ) Math.ceil( charBufferSize * encoder.maxBytesPerChar() );

                    CharBuffer newChars = CharBuffer.allocate( charBufferSize );
                    ByteBuffer newBytes = ByteBuffer.allocate( byteBufferSize );
                    ByteBuffer existingBytes = ByteBuffer.allocate( byteBufferSize );

                    CoderResult coderResult;
                    int existingRead;
                    boolean writing = false;

                    try ( RandomAccessFile existing = new RandomAccessFile( to, "rw" ) )
                    {
                        int n;
                        while ( -1 != ( n = wrapped.read( newChars ) ) )
                        {
                            ( (Buffer) newChars ).flip();

                            coderResult = encoder.encode( newChars, newBytes, n != 0 );
                            if ( coderResult.isError() )
                            {
                                coderResult.throwException();
                            }

                            ( ( Buffer ) newBytes ).flip();

                            if ( !writing )
                            {
                                existingRead = existing.read( existingBytes.array(), 0, newBytes.remaining() );
                                ( ( Buffer ) existingBytes ).position( existingRead );
                                ( ( Buffer ) existingBytes ).flip();

                                if ( newBytes.compareTo( existingBytes ) != 0 )
                                {
                                    writing = true;
                                    if ( existingRead > 0 )
                                    {
                                        existing.seek( existing.getFilePointer() - existingRead );
                                    }
                                }
                            }

                            if ( writing )
                            {
                                existing.write( newBytes.array(), 0, newBytes.remaining() );
                            }

                            ( ( Buffer ) newChars ).clear();
                            ( ( Buffer ) newBytes ).clear();
                            ( ( Buffer ) existingBytes ).clear();
                        }

                        if ( existing.length() > existing.getFilePointer() )
                        {
                            existing.setLength( existing.getFilePointer() );
                        }
                    }
                }
            }
        }

        copyFilePermissions( from, to );
    }


    /**
     * Attempts to copy file permissions from the source to the destination file.
     * Initially attempts to copy posix file permissions, assuming that the files are both on posix filesystems.
     * If the initial attempts fail then a second attempt using less precise permissions model.
     * Note that permissions are copied on a best-efforts basis,
     * failure to copy permissions will not result in an exception.
     *
     * @param source the file to copy permissions from.
     * @param destination the file to copy permissions to.
     */
    private static void copyFilePermissions( File source, File destination )
            throws IOException
    {
        try
        {
            // attempt to copy posix file permissions
            Files.setPosixFilePermissions(
                    destination.toPath(),
                    Files.getPosixFilePermissions( source.toPath() )
            );
        }
        catch ( NoSuchFileException nsfe )
        {
            // ignore if destination file or symlink does not exist
        }
        catch ( UnsupportedOperationException e )
        {
            // fallback to setting partial permissions
            destination.setExecutable( source.canExecute() );
            destination.setReadable( source.canRead() );
            destination.setWritable( source.canWrite() );
        }
    }

    private static Charset charset( String encoding )
    {
        if ( encoding == null || encoding.isEmpty() )
        {
            return Charset.defaultCharset();
        }
        else
        {
            return Charset.forName( encoding );
        }
    }

}
