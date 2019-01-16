/*
 * Copyright 2011-2019 Asakusa Framework Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.asakusafw.mapreduce.gradle.plugins.internal

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.asakusafw.gradle.plugins.AsakusafwSdkExtension
import com.asakusafw.gradle.plugins.AsakusafwSdkPluginParticipant
import com.asakusafw.gradle.plugins.internal.AsakusaSdkPlugin
import com.asakusafw.gradle.plugins.internal.PluginUtils

/**
 * A base plug-in of {@link AsakusaMapReduceSdkPlugin}.
 * This only organizes dependencies and testkits.
 * @since 0.9.0
 */
class AsakusaMapReduceSdkBasePlugin implements Plugin<Project> {

    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.apply plugin: AsakusaSdkPlugin
        project.apply plugin: AsakusaMapReduceBasePlugin

        configureTestkit()
        configureConfigurations()
    }

    private void configureTestkit() {
        AsakusafwSdkExtension sdk = AsakusaSdkPlugin.get(project).sdk
        sdk.availableTestkits << new AsakusaMapReduceTestkit()
        sdk.availableTestkits << new AsakusaSimpleMapReduceTestkit()
    }

    private void configureConfigurations() {
        project.configurations {
            asakusaMapreduceCommon {
                description 'Common libraries of Asakusa DSL Compiler for MapReduce'
                exclude group: 'asm', module: 'asm'
            }
            asakusaMapreduceCompiler {
                description 'Full classpath of Asakusa DSL Compiler for MapReduce'
                extendsFrom project.configurations.compile
                extendsFrom project.configurations.asakusaMapreduceCommon
            }
            asakusaMapreduceTestkit {
                description 'Asakusa DSL testkit classpath for MapReduce'
                extendsFrom project.configurations.asakusaMapreduceCommon
            }
            asakusaMapreduceEmulationTestkit {
                description 'Asakusa DSL testkit classpath for MapReduce Emulator'
                extendsFrom project.configurations.asakusaMapreduceTestkit
            }
        }
        PluginUtils.afterEvaluate(project) {
            AsakusaMapReduceBaseExtension base = AsakusaMapReduceBasePlugin.get(project)
            AsakusafwSdkExtension features = AsakusaSdkPlugin.get(project).sdk
            project.dependencies {
                if (features.core) {
                    asakusaMapreduceCommon "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-core:${base.featureVersion}"
                    asakusaMapreduceCommon "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-extension-inspection:${base.featureVersion}"
                    asakusaMapreduceCommon "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-extension-info:${base.featureVersion}"
                    asakusaMapreduceCommon "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-extension-workflow:${base.featureVersion}"
                    asakusaMapreduceCompiler "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-cli:${base.featureVersion}"
                    if (features.yaess) {
                        asakusaMapreduceCommon "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-extension-yaess:${base.featureVersion}"
                    }
                    if (features.directio) {
                        asakusaMapreduceCommon "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-extension-directio:${base.featureVersion}"
                    }
                    if (features.windgate) {
                        asakusaMapreduceCommon "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-extension-windgate:${base.featureVersion}"
                    }
                    if (features.hive) {
                        asakusaMapreduceCommon "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-extension-hive:${base.featureVersion}"
                    }
                }
                if (features.testing) {
                    asakusaMapreduceTestkit "com.asakusafw.mapreduce.compiler:asakusa-mapreduce-compiler-test-adapter:${base.featureVersion}"

                    asakusaMapreduceEmulationTestkit "com.asakusafw:asakusa-test-inprocess:${base.featureVersion}"
                    asakusaMapreduceEmulationTestkit "com.asakusafw:asakusa-test-windows:${base.featureVersion}"
                    if (features.windgate) {
                        asakusaMapreduceEmulationTestkit "com.asakusafw:asakusa-windgate-test-inprocess:${base.featureVersion}"
                    }
                }
            }
        }
    }

    /**
     * A participant descriptor for {@link AsakusaMapReduceSdkBasePlugin}.
     * @since 0.9.0
     */
    static class Participant implements AsakusafwSdkPluginParticipant {

        @Override
        String getName() {
            return descriptor.simpleName
        }

        @Override
        Class<?> getDescriptor() {
            return AsakusaMapReduceSdkBasePlugin
        }
    }
}
