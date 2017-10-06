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

package org.gradle.api.tasks

class LocalStateFixture {
    static String defineTaskWithLocalState(boolean useRuntimeApi) {
        if (useRuntimeApi) {
            """
                task customTask {
                    def localStateFile = file("local-state.json")
                    outputs.cacheIf { true }
                    outputs.dir("build")
                    localState.register(localStateFile)
                    doLast {
                        if (project.hasProperty("noLocalState")) {
                            assert !localStateFile.exists()
                        }
                        file("build/output.txt").text = "Output"
                        localStateFile.text = "['Some internal state']"
                    }
                }
            """
        } else {
            """
                @CacheableTask
                class CustomTask extends DefaultTask {
                    @OutputDirectory File outputDir
                    @LocalState File localStateFile
                    @TaskAction run() {
                        if (project.hasProperty("noLocalState")) {
                            assert !localStateFile.exists()
                        }
                        new File(outputDir, "output.txt").text = "Output"
                        localStateFile.text = "['Some internal state']"
                    }
                }
                
                task customTask(type: CustomTask) {
                    outputDir = file("build")
                    localStateFile = file("local-state.json")
                }
            """
        }
    }
}
