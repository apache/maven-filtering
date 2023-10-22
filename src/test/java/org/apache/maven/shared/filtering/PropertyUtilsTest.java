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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;

import static org.mockito.Mockito.mock;

/**
 * @author Olivier Lamy
 * @since 1.0-beta-1
 */
public class PropertyUtilsTest extends TestSupport {
    private static File testDirectory = new File(getBasedir(), "target/test-classes/");

    public void testBasic() throws Exception {
        File basicProp = File.createTempFile("basic", ".properties");

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(basicProp), StandardCharsets.UTF_8)) {
                writer.write("ghost=${non_existent}\n");
                writer.write("key=${untat_na_damgo}\n");
                writer.write("untat_na_damgo=gani_man\n");
                writer.flush();
            }

            Logger logger = mock(Logger.class);
            Properties prop = PropertyUtils.loadPropertyFile(basicProp, false, false, logger);
            assertTrue(prop.getProperty("key").equals("gani_man"));
            assertTrue(prop.getProperty("ghost").equals("${non_existent}"));
        } finally {
            basicProp.delete();
        }
    }

    public void testSystemProperties() throws Exception {
        File systemProp = File.createTempFile("system", ".properties");

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(systemProp), StandardCharsets.UTF_8)) {
                writer.write("key=${user.dir}");
                writer.flush();
            }

            Logger logger = mock(Logger.class);
            Properties prop = PropertyUtils.loadPropertyFile(systemProp, false, true, logger);
            assertTrue(prop.getProperty("key").equals(System.getProperty("user.dir")));
        } finally {
            systemProp.delete();
        }
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
    }

    /**
     * Test case to reproduce MSHARED-417
     *
     * @throws IOException if problem writing file
     */
    public void testCircularReferences() throws IOException {
        File circularProp = File.createTempFile("circular", ".properties");

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(circularProp), StandardCharsets.UTF_8)) {
                writer.write("test=${test2}\n");
                writer.write("test2=${test2}\n");
                writer.flush();
            }

            Logger logger = mock(Logger.class);
            Properties prop = PropertyUtils.loadPropertyFile(circularProp, null, logger);
            assertEquals("${test2}", prop.getProperty("test"));
            assertEquals("${test2}", prop.getProperty("test2"));
        } finally {
            circularProp.delete();
        }
    }

    /**
     * Test case to reproduce MSHARED-417
     *
     * @throws IOException if problem writing file
     */
    public void testCircularReferences3Vars() throws IOException {
        File circularProp = File.createTempFile("circular", ".properties");

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(circularProp), StandardCharsets.UTF_8)) {
                writer.write("test=${test2}\n");
                writer.write("test2=${test3}\n");
                writer.write("test3=${test}\n");
                writer.flush();
            }

            Logger logger = mock(Logger.class);
            Properties prop = PropertyUtils.loadPropertyFile(circularProp, null, logger);
            assertEquals("${test2}", prop.getProperty("test"));
            assertEquals("${test3}", prop.getProperty("test2"));
            assertEquals("${test}", prop.getProperty("test3"));
        } finally {
            circularProp.delete();
        }
    }

    public void testNonCircularReferences1Var3Times() throws IOException {
        File circularProp = File.createTempFile("non-circular", ".properties");

        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(circularProp), StandardCharsets.UTF_8)) {
                writer.write("depends=p1 >= ${version}, p2 >= ${version}, p3 >= ${version}\n");
                writer.write("version=1.2.3\n");
                writer.flush();
            }

            Logger logger = mock(Logger.class);
            Properties prop = PropertyUtils.loadPropertyFile(circularProp, null, logger);
            assertEquals("p1 >= 1.2.3, p2 >= 1.2.3, p3 >= 1.2.3", prop.getProperty("depends"));
            assertEquals("1.2.3", prop.getProperty("version"));
        } finally {
            circularProp.delete();
        }
    }

    public void testNonCircularReferences2Vars2Times() throws IOException {
        File nonCircularProp = File.createTempFile("non-circular", ".properties");

        try {
            try (Writer writer =
                    new OutputStreamWriter(new FileOutputStream(nonCircularProp), StandardCharsets.UTF_8)) {
                writer.write("test=${test2} ${test3} ${test2} ${test3}\n");
                writer.write("test2=${test3} ${test3}\n");
                writer.write("test3=test\n");
                writer.flush();
            }

            Logger logger = mock(Logger.class);
            Properties prop = PropertyUtils.loadPropertyFile(nonCircularProp, null, logger);
            assertEquals("test test test test test test", prop.getProperty("test"));
            assertEquals("test test", prop.getProperty("test2"));
            assertEquals("test", prop.getProperty("test3"));
        } finally {
            nonCircularProp.delete();
        }
    }
}
