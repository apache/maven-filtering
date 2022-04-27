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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * I added this class to support JSON files MRESOURCES-284
 * @author <a href="mailto:belmoujahid.i@gmail.com">Imad BELMOUJAHID</a> @ImadBL
 */
public class PropertiesJson
{
    /**
     * properties
     */
    private final Properties properties;

    /**
     * default constructor
     */
    public PropertiesJson ()
    {
        properties = new Properties ();
    }

    /**
     *
     * @return properties
     */
    public Properties getProperties ()
    {
        return properties;
    }

    /**
     *
     * @param fr
     * @param useThisRoot
     * @throws IOException
     */
    public void load ( File fr, String useThisRoot )
            throws IOException
    {
        try ( FileReader reader = new FileReader ( fr ) )
        {
            for ( Map.Entry<String, Object> e
                    : getPropertiesFromString( IOUtils.toString( reader ), useThisRoot ).entrySet( ) )
            {

                this.properties.put ( e.getKey (), e.getValue () );
            }
        }
    }

    /**
     *
     * @param json
     * @param useThisRoot
     * @return
     * @throws JsonProcessingException
     */
    private HashMap<String, Object> getPropertiesFromString( String json, String useThisRoot )
            throws IOException
    {
        String str = "";
        HashMap<String, Object> hashMap = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        List<String> keys = new ArrayList<>();
        JsonNode jsonNode = mapper.readTree( json );
        getPropertiesFromJsonNode( hashMap, str, jsonNode, keys, useThisRoot );
        return hashMap;
    }

    /**
     *
     * @param result
     * @param str
     * @param jsonNode
     * @param keys
     * @param useThisRoot
     */
    private void getPropertiesFromJsonNode ( HashMap<String, Object> result, String str,
                                             JsonNode jsonNode, List<String> keys, String useThisRoot )
    {
        if ( jsonNode.isObject() )
        {
            Iterator<String> fieldNames = jsonNode.fieldNames();
            while ( fieldNames.hasNext() )
            {
                String fieldName = fieldNames.next();
                keys.add ( fieldName );
                if ( jsonNode.get( fieldName ).isObject() )
                {
                    String r;
                    if ( str != null && str.isEmpty () )
                    {
                        r = str.concat( fieldName );
                    }
                    else
                    {
                        r = str.concat( "." ).concat( fieldName );
                    }
                    getPropertiesFromJsonNode ( result, r, jsonNode.get( fieldName ), keys, useThisRoot );
                }
                else
                {
                    String r;
                    if ( str != null && str.isEmpty() )
                    {
                        r = str.concat( fieldName );
                    }
                    else
                    {
                        r = str.concat( "." ).concat( fieldName );
                    }
                    getPropertiesFromJsonNode ( result, r, jsonNode.get( fieldName ), keys, useThisRoot );
                }
            }
        }
        else if ( jsonNode.isArray () )
        {
            ArrayNode arrayField = ( ArrayNode ) jsonNode;
            for ( JsonNode node : arrayField )
            {
                getPropertiesFromJsonNode ( result, str, node, keys, useThisRoot ) ;
            }
        }
        else
        {

            if ( null != useThisRoot && !useThisRoot.isEmpty() )
            {
                if ( str.startsWith( useThisRoot + "." ) )
                {
                    result.put ( str.replaceFirst( "^[a-zA-Z1-9]*.", "" ), jsonNode.textValue(  ) );
                }
            }
            else
            {
                result.put ( str, jsonNode.textValue() );
            }

        }
    }
}