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
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.maven.api.di.testing.MavenDITest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import static org.apache.maven.api.di.testing.MavenDIExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Olivier Lamy
 * @since 1.0-beta-1
 */
@MavenDITest
public class PropertyUtilsTest {
    private static Path testDirectory = Paths.get(getBasedir(), "target/test-classes/");

    @Test
    public void testBasic() throws Exception {
        Path basicProp = testDirectory.resolve("basic.properties");

        Files.deleteIfExists(basicProp);

        try (Writer writer = Files.newBufferedWriter(basicProp)) {
            writer.write("ghost=${non_existent}\n");
            writer.write("key=${untat_na_damgo}\n");
            writer.write("untat_na_damgo=gani_man\n");
            writer.flush();
        }

        Properties prop = PropertyUtils.loadPropertyFile(basicProp, false, false);
        assertEquals("gani_man", prop.getProperty("key"));
        assertEquals("${non_existent}", prop.getProperty("ghost"));
    }

    @Test
    public void testSystemProperties() throws Exception {
        Path systemProp = testDirectory.resolve("system.properties");

        Files.deleteIfExists(systemProp);

        try (Writer writer = Files.newBufferedWriter(systemProp)) {
            writer.write("key=${user.dir}");
            writer.flush();
        }

        Properties prop = PropertyUtils.loadPropertyFile(systemProp, false, true);
        assertEquals(prop.getProperty("key"), System.getProperty("user.dir"));
    }

    @Test
    public void testException() throws Exception {
        Path nonExistent = testDirectory.resolve("not_existent_file");

        assertFalse(Files.exists(nonExistent), "property file exist: " + nonExistent);

        assertThrows(Exception.class, () -> PropertyUtils.loadPropertyFile(nonExistent, true, false));
    }

    @Test
    public void testloadpropertiesFile() throws Exception {
        Path propertyFile = Paths.get(getBasedir() + "/src/test/units-files/propertyutils-test.properties");
        Properties baseProps = new Properties();
        baseProps.put("pom.version", "realVersion");

        Properties interpolated = PropertyUtils.loadPropertyFile(propertyFile, baseProps);
        assertEquals("realVersion", interpolated.get("version"));
        assertEquals("${foo}", interpolated.get("foo"));
        assertEquals("realVersion", interpolated.get("bar"));
        assertEquals("none filtered", interpolated.get("none"));
    }

    /**
     * Test case to reproduce MSHARED-417
     *
     * @throws IOException if problem writing file
     */
    @Test
    public void testCircularReferences() throws IOException {
        Path basicProp = testDirectory.resolve("circular.properties");

        Files.deleteIfExists(basicProp);

        try (Writer writer = Files.newBufferedWriter(basicProp)) {
            writer.write("test=${test2}\n");
            writer.write("test2=${test2}\n");
            writer.flush();
        }

        Logger logger = mock(Logger.class);

        Properties prop = PropertyUtils.loadPropertyFile(basicProp, null, logger);
        assertEquals("${test2}", prop.getProperty("test"));
        assertEquals("${test2}", prop.getProperty("test2"));
        assertWarn(
                logger,
                "Circular reference between properties detected: test2 => test2",
                "Circular reference between properties detected: test => test2 => test2");
    }

    /**
     * Test case to reproduce MSHARED-417
     *
     * @throws IOException if problem writing file
     */
    @Test
    public void testCircularReferences3Vars() throws IOException {
        Path basicProp = testDirectory.resolve("circular.properties");

        Files.deleteIfExists(basicProp);

        try (Writer writer = Files.newBufferedWriter(basicProp)) {
            writer.write("test=${test2}\n");
            writer.write("test2=${test3}\n");
            writer.write("test3=${test}\n");
            writer.flush();
        }

        Logger logger = mock(Logger.class);

        Properties prop = PropertyUtils.loadPropertyFile(basicProp, null, logger);
        assertEquals("${test2}", prop.getProperty("test"));
        assertEquals("${test3}", prop.getProperty("test2"));
        assertEquals("${test}", prop.getProperty("test3"));
        assertWarn(
                logger,
                "Circular reference between properties detected: test3 => test => test2 => test3",
                "Circular reference between properties detected: test2 => test3 => test => test2",
                "Circular reference between properties detected: test => test2 => test3 => test");
    }

    private void assertWarn(Logger mock, String... expected) {
        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(mock, times(expected.length)).warn(argument.capture());
        List<String> messages = argument.getAllValues();
        for (String str : expected) {
            assertTrue(messages.contains(str));
        }
    }
}
