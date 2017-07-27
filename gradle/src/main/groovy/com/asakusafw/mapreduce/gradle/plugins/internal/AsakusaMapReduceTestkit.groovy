/*
 * Copyright 2011-2017 Asakusa Framework Team.
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

import org.gradle.api.Project

import com.asakusafw.gradle.plugins.AsakusaTestkit

/**
 * An implementation of {@link AsakusaTestkit} which uses MapReduce compiler and runtime.
 * @since 0.9.0
 */
class AsakusaMapReduceTestkit implements AsakusaTestkit {

    @Override
    String getName() {
        return 'mapreduce'
    }

    @Override
    int getPriority() {
        return 1
    }

    @Override
    void apply(Project project) {
        project.logger.info "enabling MapReduce Testkit (${name})"
        project.configurations {
            testCompile.extendsFrom asakusaMapreduceTestkit
        }
    }

    @Override
    String toString() {
        return "Testkit(${name})"
    }
}
