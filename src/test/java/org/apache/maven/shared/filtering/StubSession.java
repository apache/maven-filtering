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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.api.*;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.settings.Settings;

/**
 */
public class StubSession implements Session {

    private Map<String, String> userProperties;

    private Map<String, String> systemProperties;

    private final Settings settings;

    public StubSession(Settings settings) {
        this(null, null, settings);
    }

    public StubSession() {
        this(null, null, null);
    }

    public StubSession(Map<String, String> userProperties) {
        this(null, userProperties, null);
    }

    public StubSession(Map<String, String> systemProperties, Map<String, String> userProperties, Settings settings) {

        this.settings = settings;

        this.systemProperties = new HashMap<>();
        if (systemProperties != null) {
            this.systemProperties.putAll(systemProperties);
        }
        System.getProperties().forEach((k, v) -> this.systemProperties.put(k.toString(), v.toString()));

        this.userProperties = new HashMap<>();
        if (userProperties != null) {
            this.userProperties.putAll(userProperties);
        }
    }

    @Override
    public Settings getSettings() {
        return settings;
    }

    @Override
    public Map<String, String> getSystemProperties() {
        return this.systemProperties;
    }

    @Override
    public Map<String, String> getUserProperties() {
        return this.userProperties;
    }

    @Override
    public LocalRepository getLocalRepository() {
        return null;
    }

    @Override
    public Path getTopDirectory() {
        return null;
    }

    @Override
    public Path getRootDirectory() {
        return null;
    }

    @Override
    public List<RemoteRepository> getRemoteRepositories() {
        return null;
    }

    @Override
    public SessionData getData() {
        return null;
    }

    @Override
    public String getMavenVersion() {
        return null;
    }

    @Override
    public int getDegreeOfConcurrency() {
        return 0;
    }

    @Override
    public Instant getStartTime() {
        return null;
    }

    @Override
    public List<Project> getProjects() {
        return null;
    }

    @Override
    public Map<String, Object> getPluginContext(Project project) {
        return null;
    }

    @Override
    public <T extends Service> T getService(Class<T> clazz) {
        return null;
    }

    @Override
    public Session withLocalRepository(LocalRepository localRepository) {
        return null;
    }

    @Override
    public Session withRemoteRepositories(List<RemoteRepository> repositories) {
        return null;
    }

    @Override
    public void registerListener(Listener listener) {}

    @Override
    public void unregisterListener(Listener listener) {}

    @Override
    public Collection<Listener> getListeners() {
        return null;
    }

    @Override
    public LocalRepository createLocalRepository(Path path) {
        return null;
    }

    @Override
    public RemoteRepository createRemoteRepository(String id, String url) {
        return null;
    }

    @Override
    public RemoteRepository createRemoteRepository(Repository repository) {
        return null;
    }

    @Override
    public Artifact createArtifact(String groupId, String artifactId, String version, String extension) {
        return null;
    }

    @Override
    public Artifact createArtifact(
            String groupId, String artifactId, String version, String classifier, String extension, String type) {
        return null;
    }

    @Override
    public ArtifactCoordinate createArtifactCoordinate(String s, String s1, String s2, String s3) {
        return null;
    }

    @Override
    public ArtifactCoordinate createArtifactCoordinate(
            String s, String s1, String s2, String s3, String s4, String s5) {
        return null;
    }

    @Override
    public ArtifactCoordinate createArtifactCoordinate(Artifact artifact) {
        return null;
    }

    @Override
    public DependencyCoordinate createDependencyCoordinate(ArtifactCoordinate artifactCoordinate) {
        return null;
    }

    @Override
    public DependencyCoordinate createDependencyCoordinate(Dependency dependency) {
        return null;
    }

    @Override
    public Artifact resolveArtifact(Artifact artifact) {
        return null;
    }

    @Override
    public Artifact resolveArtifact(ArtifactCoordinate coordinate) {
        return null;
    }

    @Override
    public Collection<Artifact> resolveArtifacts(ArtifactCoordinate... artifactCoordinates) {
        return null;
    }

    @Override
    public Collection<Artifact> resolveArtifacts(Collection<? extends ArtifactCoordinate> collection) {
        return null;
    }

    @Override
    public Collection<Artifact> resolveArtifacts(Artifact... artifacts) {
        return null;
    }

    @Override
    public void installArtifacts(Artifact... artifacts) {}

    @Override
    public void installArtifacts(Collection<Artifact> artifacts) {}

    @Override
    public void deployArtifact(RemoteRepository repository, Artifact... artifacts) {}

    @Override
    public void setArtifactPath(Artifact artifact, Path path) {}

    @Override
    public Optional<Path> getArtifactPath(Artifact artifact) {
        return Optional.empty();
    }

    @Override
    public boolean isVersionSnapshot(String version) {
        return false;
    }

    @Override
    public Node collectDependencies(Artifact artifact) {
        return null;
    }

    @Override
    public Node collectDependencies(Project project) {
        return null;
    }

    @Override
    public Node collectDependencies(DependencyCoordinate dependencyCoordinate) {
        return null;
    }

    @Override
    public Path getPathForLocalArtifact(Artifact artifact) {
        return null;
    }

    @Override
    public Path getPathForRemoteArtifact(RemoteRepository remote, Artifact artifact) {
        return null;
    }

    @Override
    public Version parseVersion(String version) {
        return null;
    }

    @Override
    public VersionRange parseVersionRange(String versionRange) {
        return null;
    }
}
