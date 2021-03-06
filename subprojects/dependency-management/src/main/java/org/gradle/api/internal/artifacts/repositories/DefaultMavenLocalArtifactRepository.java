/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.repositories;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenLocalResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import java.net.URI;

public class DefaultMavenLocalArtifactRepository extends DefaultMavenArtifactRepository implements MavenArtifactRepository {
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final FileResourceRepository fileResourceRepository;

    public DefaultMavenLocalArtifactRepository(FileResolver fileResolver, RepositoryTransportFactory transportFactory,
                                               LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder, Instantiator instantiator,
                                               FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                               MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                               ModuleMetadataParser metadataParser,
                                               AuthenticationContainer authenticationContainer,
                                               ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                               FileResourceRepository fileResourceRepository) {
        super(fileResolver, transportFactory, locallyAvailableResourceFinder, instantiator, artifactFileStore, pomParser, metadataParser, authenticationContainer, moduleIdentifierFactory, null, fileResourceRepository);
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.fileResourceRepository = fileResourceRepository;
    }

    protected MavenResolver createRealResolver() {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException("You must specify a URL for a Maven repository.");
        }

        RepositoryTransport transport = getTransport(rootUri.getScheme());
        MavenResolver resolver = new MavenLocalResolver(getName(), rootUri, transport, getLocallyAvailableResourceFinder(), getArtifactFileStore(), getPomParser(), getMetadataParser(), moduleIdentifierFactory, transport.getResourceAccessor(), fileResourceRepository, isPreferGradleMetadata());
        for (URI repoUrl : getArtifactUrls()) {
            resolver.addArtifactLocation(repoUrl);
        }
        return resolver;
    }
}
