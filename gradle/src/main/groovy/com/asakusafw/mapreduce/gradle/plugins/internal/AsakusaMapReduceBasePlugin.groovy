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

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.asakusafw.gradle.plugins.AsakusafwBaseExtension
import com.asakusafw.gradle.plugins.AsakusafwBasePlugin

/**
 * A Gradle sub plug-in for Asakusa on MapReduce facilities.
 * @since 0.8.0
 * @version 0.9.0
 */
class AsakusaMapReduceBasePlugin implements Plugin<Project> {

    private Project project

    private AsakusaMapReduceBaseExtension extension

    /**
     * Applies this plug-in and returns the extension object for the project.
     * @param project the target project
     * @return the corresponded extension
     */
    static AsakusaMapReduceBaseExtension get(Project project) {
        project.apply plugin: AsakusaMapReduceBasePlugin
        return project.plugins.getPlugin(AsakusaMapReduceBasePlugin).extension
    }

    @Override
    void apply(Project project) {
        this.project = project
        project.apply plugin: AsakusafwBasePlugin

        this.extension = project.extensions.create('asakusaMapReduceBase', AsakusaMapReduceBaseExtension)
        configureExtension()
    }

    private void configureExtension() {
        AsakusafwBaseExtension base = AsakusafwBasePlugin.get(project)

        // FIXME refer asakusafw-mapreduce
        extension.featureVersion = base.frameworkVersion
    }
}
