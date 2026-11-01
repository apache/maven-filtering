---
title: Usage
author: 
  - Olivier Lamy
date: 2011-01-05
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Usage

## Filter a `List` of `org.apache.maven.model.Resource`

Lookup the component in your Mojo:

```java
    @Inject
    private MavenResourcesFiltering mavenResourcesFiltering;
```

Apply filtering on your `List` of resources, see [Introduction](./index.html) for the default `FilterWrappers` that are used.

```java
        MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution ( resources, outputDirectory, mavenProject,
                                          encoding, fileFilters,
                                          nonFilteredFileExtensions, mavenSession );

        mavenResourcesFiltering.filterResources( mavenResourcesExecution );
```

## Add a new filtering token

You must use the other methods from the `MavenResourcesFiltering` component and construct your own `List` of `FilterWrapper`s. The following example adds interpolation for the token @ @ using values coming from reflection with the Maven Project.

```java
        // Create your FilterWrapper
        FilterWrapper filterWrapper = new FilterWrapper() {
            @Override
            public Reader getReader(Reader reader) {
                Interpolator interpolator = new RegexBasedInterpolator("\\@", "(.+?)\\@");
                interpolator.addValueSource(
                        new PrefixedObjectValueSource(Arrays.asList("project", "pom"), mavenProject, true));
                return new InterpolatorFilterReader(reader, interpolator, "@", "@");
            }
        };

        // Add the new filterWrapper to your MavenResourcesExecution instance
        mavenResourcesExecution.addFilterWrapper(filterWrapper);
```

`FilterWrapper` is `org.apache.maven.shared.filtering.FilterWrapper`; the interpolation classes come from `org.codehaus.plexus.interpolation`.

There is a helper method to simplify this. It takes literal tokens rather than regular expressions, and an escape string (`null` for none):

```java
        mavenResourcesExecution.addFilerWrapperWithEscaping(
                new PrefixedObjectValueSource(Arrays.asList("project", "pom"), mavenProject, true),
                "@", "@", null, false);
```

**Note:** If `mavenResourcesExecution.useDefaultFilterWrappers` is set to `true`, the default `FilterWrapper`s will be added first.

Now it's time to filter the resources:

```java
        // Apply filtering on your resources
        mavenResourcesFiltering.filterResources( mavenResourcesExecution );
```

**Note:** Maven Filtering uses the [plexus-interpolation component](https://codehaus-plexus.github.io/plexus-interpolation/).
