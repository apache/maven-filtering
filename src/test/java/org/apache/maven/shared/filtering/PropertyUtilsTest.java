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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author Olivier Lamy
 * @since 1.0-beta-1
 */
public class PropertyUtilsTest extends TestSupport {
    private static File testDirectory = new File(getBasedir(), "target/test-classes/");

    public void testBasic() throws Exception {
        File basicProp = new File(testDirectory, "basic.properties");

        if (basicProp.exists()) {
            basicProp.delete();
        }

        basicProp.createNewFile();
        try (FileWriter writer = new FileWriter(basicProp)) {
            writer.write("ghost=${non_existent}\n");
            writer.write("key=${untat_na_damgo}\n");
            writer.write("untat_na_damgo=gani_man\n");
            writer.flush();
        }

        Logger logger = mock(Logger.class);
        Properties prop = PropertyUtils.loadPropertyFile(basicProp, false, false, logger);
        assertTrue(prop.getProperty("key").equals("gani_man"));
        assertTrue(prop.getProperty("ghost").equals("${non_existent}"));
        verifyNoInteractions(logger);
    }

    public void testSystemProperties() throws Exception {
        File systemProp = new File(testDirectory, "system.properties");

        if (systemProp.exists()) {
            systemProp.delete();
        }

        systemProp.createNewFile();
        try (FileWriter writer = new FileWriter(systemProp)) {
            writer.write("key=${user.dir}");
            writer.flush();
        }

        Logger logger = mock(Logger.class);
        Properties prop = PropertyUtils.loadPropertyFile(systemProp, false, true, logger);
        assertTrue(prop.getProperty("key").equals(System.getProperty("user.dir")));
        verifyNoInteractions(logger);
    }

    public void testException() throws Exception {
        File nonExistent = new File(testDirectory, "not_existent_file");

        assertFalse("property file exist: " + nonExistent.toString(), nonExistent.exists());

        try {
            PropertyUtils.loadPropertyFile(nonExistent, true, false);
            assertTrue("Exception failed", false);
        } catch (Exception ex) {
            // exception ok
        }
    }

    public void testLoadPropertiesFile() throws Exception {
        File propertyFile = new File(getBasedir() + "/src/test/units-files/propertyutils-test.properties");
        Properties baseProps = new Properties();
        baseProps.put("pom.version", "realVersion");

        Logger logger = mock(Logger.class);
        Properties interpolated = PropertyUtils.loadPropertyFile(propertyFile, baseProps, logger);
        assertEquals("realVersion", interpolated.get("version"));
        assertEquals("${foo}", interpolated.get("foo"));
        assertEquals("realVersion", interpolated.get("bar"));
        assertEquals("none filtered", interpolated.get("none"));
        assertWarn(logger, "Circular reference between properties detected: foo => foo");
    }

    /**
     * Test case to reproduce MSHARED-417
     *
     * @throws IOException if problem writing file
     */
    public void testCircularReferences() throws IOException {
        File basicProp = new File(testDirectory, "circular.properties");

        if (basicProp.exists()) {
            basicProp.delete();
        }

        basicProp.createNewFile();
        try (FileWriter writer = new FileWriter(basicProp)) {
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
    public void testCircularReferences3Vars() throws IOException {
        File basicProp = new File(testDirectory, "circular.properties");

        if (basicProp.exists()) {
            basicProp.delete();
        }

        basicProp.createNewFile();
        try (FileWriter writer = new FileWriter(basicProp)) {
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

    public void testNonCircularReferences1Var3Times() throws IOException {
        File basicProp = new File(testDirectory, "non-circular.properties");

        if (basicProp.exists()) {
            basicProp.delete();
        }

        basicProp.createNewFile();
        try (FileWriter writer = new FileWriter(basicProp)) {
            writer.write("depends=p1 >= ${version}, p2 >= ${version}, p3 >= ${version}\n");
            writer.write("version=1.2.3\n");
            writer.flush();
        }

        Logger logger = mock(Logger.class);
        Properties prop = PropertyUtils.loadPropertyFile(basicProp, null, logger);
        assertEquals("p1 >= 1.2.3, p2 >= 1.2.3, p3 >= 1.2.3", prop.getProperty("depends"));
        assertEquals("1.2.3", prop.getProperty("version"));
        verifyNoInteractions(logger);
    }

    public void testNonCircularReferences2Vars2Times() throws IOException {
        File basicProp = new File(testDirectory, "non-circular.properties");

        if (basicProp.exists()) {
            basicProp.delete();
        }

        basicProp.createNewFile();
        try (FileWriter writer = new FileWriter(basicProp)) {
            writer.write("test=${test2} ${test3} ${test2} ${test3}\n");
            writer.write("test2=${test3} ${test3}\n");
            writer.write("test3=test\n");
            writer.flush();
        }

        Logger logger = mock(Logger.class);
        Properties prop = PropertyUtils.loadPropertyFile(basicProp, null, logger);
        assertEquals("test test test test test test", prop.getProperty("test"));
        assertEquals("test test", prop.getProperty("test2"));
        assertEquals("test", prop.getProperty("test3"));
        verifyNoInteractions(logger);
    }
}
