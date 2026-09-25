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

import java.util.List;

/**
 * @author Olivier Lamy
 */
public interface MavenResourcesFiltering {

    /**
     * return the List of the non filtered extensions. By default this includes common binary formats:
     * images (jpg, jpeg, gif, bmp, png, ico, webp, tif, tiff), Java archives and native libraries
     * (jar, war, ear, aar, rar, jnilib, so, dll, dylib), generic archives (zip, gz, bz2, xz, zst, 7z, tar),
     * executables (exe, bin, class), documents (pdf, doc, docx, xls, xlsx, ppt, pptx),
     * audio/video (mp3, mp4, ogg, wav, avi, mov, flv, swf) and fonts (ttf, otf, woff, woff2, eot).
     *
     * @return {@link List} of {@link String}
     */
    List<String> getDefaultNonFilteredFileExtensions();

    /**
     * @param fileName the file name
     * @param userNonFilteredFileExtensions an extra list of file extensions
     * @return true if filtering can be applied to the file (means extensions.lowerCase is in the default List or in the
     *         user defined extension List)
     */
    boolean filteredFileExtension(String fileName, List<String> userNonFilteredFileExtensions);

    /**
     * @param mavenResourcesExecution {@link MavenResourcesExecution}
     * @throws MavenFilteringException in case of failure.
     */
    void filterResources(MavenResourcesExecution mavenResourcesExecution) throws MavenFilteringException;
}
