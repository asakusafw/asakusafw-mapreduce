/*
 * Copyright 2011-2018 Asakusa Framework Team.
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
 * An implementation of {@link AsakusaTestkit} which uses MapReduce compiler and emulation mode runtime.
 * @since 0.9.0
 */
class AsakusaSimpleMapReduceTestkit implements AsakusaTestkit {

    private static final AsakusaTestkit NORMAL = new AsakusaMapReduceTestkit()

    @Override
    public String getName() {
        return "${NORMAL.name}-emulation"
    }

    @Override
    public int getPriority() {
        return NORMAL.priority + 1
    }

    @Override
    public void apply(Project project) {
        project.logger.info "enabling MapReduce (emulation mode) Testkit (${name})"
        project.configurations {
            testCompile.extendsFrom asakusaMapreduceEmulationTestkit
        }
    }

    @Override
    String toString() {
        return "Testkit(${name})"
    }
}
