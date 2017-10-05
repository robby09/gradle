/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.AbstractNativePublishingIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.junit.Assume

class CppExecutablePublishingIntegrationTest extends AbstractNativePublishingIntegrationSpec {

    private static final DEBUG = 'Debug'
    private static final RELEASE = 'Release'

    def setup() {
        // TODO - currently the customizations to the tool chains are ignored by the plugins, so skip these tests until this is fixed
        Assume.assumeTrue(toolChain.id != "mingw" && toolChain.id != "gcccygwin")
    }

    def "can publish the binaries of an application to a Maven repository"() {
        def app = new CppApp()

        given:
        buildFile << """
            apply plugin: 'cpp-executable'
            apply plugin: 'maven-publish'
            
            group = 'some.group'
            version = '1.2'
            executable {
                baseName = 'test'
            }
            publishing {
                repositories { maven { url 'repo' } }
            }
"""
        app.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(*(linkTasks(DEBUG) + linkTasks(RELEASE)), ":generatePomFileForDebugPublication", ":generateMetadataFileForDebugPublication", ":publishDebugPublicationToMavenRepository", ":generatePomFileForMainPublication", ":generateMetadataFileForMainPublication", ":publishMainPublicationToMavenRepository", ":generatePomFileForReleasePublication", ":generateMetadataFileForReleasePublication", ":publishReleasePublicationToMavenRepository", ":publish")

        def repo = new MavenFileRepository(file("repo"))

        def main = repo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2.pom", "test-1.2-module.json")
        main.parsedPom.scopes.isEmpty()
        main.parsedModuleMetadata.variants.empty

        def debug = repo.module('some.group', 'test_debug', '1.2')
        debug.assertPublished()
        debug.assertArtifactsPublished(executableName("test_debug-1.2"), "test_debug-1.2.pom", "test_debug-1.2-module.json")
        debug.artifactFile(type: executableExtension).assertIsCopyOf(executable("build/exe/main/debug/test").file)

        debug.parsedPom.scopes.isEmpty()

        def debugMetadata = debug.parsedModuleMetadata
        debugMetadata.variants.size() == 1
        debugMetadata.variant("native-runtime").files.size() == 1
        debugMetadata.variant("native-runtime").files[0].name == executableName('test')
        debugMetadata.variant("native-runtime").files[0].url == executableName("test_debug-1.2")

        def release = repo.module('some.group', 'test_release', '1.2')
        release.assertPublished()
        release.assertArtifactsPublished(executableName("test_release-1.2"), "test_release-1.2.pom", "test_release-1.2-module.json")
        release.artifactFile(type: executableExtension).assertIsCopyOf(executable("build/exe/main/release/test").file)

        release.parsedPom.scopes.isEmpty()

        def releaseMetadata = release.parsedModuleMetadata
        releaseMetadata.variants.size() == 1
        releaseMetadata.variant("native-runtime").files.size() == 1
        releaseMetadata.variant("native-runtime").files[0].name == executableName('test')
        releaseMetadata.variant("native-runtime").files[0].url == executableName("test_release-1.2")
    }

    def "can publish an executable and library to a Maven repository"() {
        def app = new CppAppWithLibrary()

        given:
        settingsFile << "include 'greeter', 'app'"
        buildFile << """
            subprojects {
                apply plugin: 'maven-publish'
                
                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '../repo' } }
                }
            }
            project(':app') { 
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') { 
                apply plugin: 'cpp-library'
            }
"""
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(file('app'))

        when:
        run('publish')

        then:
        def repo = new MavenFileRepository(file("repo"))

        def appModule = repo.module('some.group', 'app', '1.2')
        appModule.assertPublished()

        def appDebugModule = repo.module('some.group', 'app_debug', '1.2')
        appDebugModule.assertPublished()
        appDebugModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def appReleaseModule = repo.module('some.group', 'app_release', '1.2')
        appReleaseModule.assertPublished()
        appReleaseModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def greeterModule = repo.module('some.group', 'greeter', '1.2')
        greeterModule.assertPublished()

        def greeterDebugModule = repo.module('some.group', 'greeter_debug', '1.2')
        greeterDebugModule.assertPublished()

        def greeterReleaseModule = repo.module('some.group', 'greeter_release', '1.2')
        greeterReleaseModule.assertPublished()
    }

    List<String> linkTasks(String project = '', String variant) {
        ["${project}:discoverInputs${variant}", "${project}:compile${variant}Cpp", "${project}:link${variant}"]
    }

    List<String> installTasks(String project = '', String variant) {
        [*linkTasks(project, variant), "${project}:install${variant}"]
    }

}
