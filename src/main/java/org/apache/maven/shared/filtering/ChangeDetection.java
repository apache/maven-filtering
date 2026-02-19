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

/**
 * Change detection strategies: to decide whether an existing target file has changed and needs to be overwritten or not.
 *
 * @since 3.5.0
 */
public enum ChangeDetection {
    /**
     * Only consider the timestamp of the file to determine if it has changed. This was default before 3.4.0.
     */
    TIMESTAMP,
    /**
     * Consider the content of the file to determine if it has changed. This is the default since 3.4.0.
     */
    CONTENT,
    /**
     * Combine timestamp and content change detection.
     */
    COMBINED,
    /**
     * Disable change detection; always overwrite.
     */
    ALWAYS;
}
