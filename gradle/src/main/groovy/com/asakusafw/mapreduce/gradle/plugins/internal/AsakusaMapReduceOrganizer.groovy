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
import org.gradle.api.Task

import com.asakusafw.gradle.plugins.AsakusafwOrganizerProfile
import com.asakusafw.gradle.plugins.internal.AbstractOrganizer
import com.asakusafw.gradle.plugins.internal.PluginUtils
import com.asakusafw.mapreduce.gradle.plugins.AsakusafwOrganizerMapReduceExtension

/**
 * Processes an {@link AsakusafwOrganizerProfile} for MapReduce environment.
 * @since 0.8.0
 */
class AsakusaMapReduceOrganizer extends AbstractOrganizer {

    /**
     * Creates a new instance.
     * @param project the current project
     * @param profile the target profile
     */
    AsakusaMapReduceOrganizer(Project project, AsakusafwOrganizerProfile profile) {
        super(project, profile)
    }

    /**
     * Configures the target profile.
     */
    @Override
    void configureProfile() {
        configureTasks()
        enableTasks()
    }

    private void configureTasks() {
        createAttachComponentTasks 'attachComponent', [
            Mapreduce : {
                // no special artifacts
            },
        ]
        createAttachComponentTasks 'attach', [
            MapreduceBatchapps : {
                into('batchapps') {
                    put project.asakusafw.mapreduce.outputDirectory
                }
            },
        ]
    }

    private void enableTasks() {
        PluginUtils.afterEvaluate(project) {
            AsakusafwOrganizerMapReduceExtension extension = profile.mapreduce
            if (extension.isEnabled()) {
                project.logger.info "Enabling Asakusa on MapReduce: ${profile.name}"
                task('attachAssemble').dependsOn task('attachComponentMapreduce')
                PluginUtils.afterTaskEnabled(project, AsakusaMapReduceSdkPlugin.TASK_COMPILE) { Task compiler ->
                    task('attachMapreduceBatchapps').dependsOn compiler
                    if (profile.batchapps.isEnabled()) {
                        project.logger.info "Enabling MapReduce Batchapps: ${profile.name}"
                        task('attachAssemble') dependsOn task('attachMapreduceBatchapps')
                    }
                }
            }
        }
    }
}
