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

import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

import com.asakusafw.gradle.plugins.AsakusafwOrganizerPlugin
import com.asakusafw.gradle.plugins.AsakusafwOrganizerPluginConvention
import com.asakusafw.gradle.plugins.AsakusafwOrganizerProfile
import com.asakusafw.gradle.plugins.internal.PluginUtils
import com.asakusafw.mapreduce.gradle.plugins.AsakusafwOrganizerMapReduceExtension

/**
 * A Gradle sub plug-in for Asakusa on MapReduce project organizer.
 */
class AsakusaMapReduceOrganizerPlugin implements Plugin<Project> {

    private Project project

    private NamedDomainObjectCollection<AsakusaMapReduceOrganizer> organizers

    @Override
    void apply(Project project) {
        this.project = project
        this.organizers = project.container(AsakusaMapReduceOrganizer)

        project.apply plugin: AsakusafwOrganizerPlugin
        project.apply plugin: AsakusaMapReduceBasePlugin

        configureConvention()
        configureProfiles()
        configureTasks()
    }

    /**
     * Returns the organizers for each profile (only for testing).
     * @return the organizers for each profile
     */
    NamedDomainObjectCollection<AsakusaMapReduceOrganizer> getOrganizers() {
        return organizers
    }

    private void configureConvention() {
        AsakusaMapReduceBaseExtension base = AsakusaMapReduceBasePlugin.get(project)
        AsakusafwOrganizerPluginConvention convention = project.asakusafwOrganizer
        convention.extensions.create('mapreduce', AsakusafwOrganizerMapReduceExtension)
        convention.mapreduce.conventionMapping.with {
            enabled = { true }
        }
        PluginUtils.injectVersionProperty(convention.mapreduce, { base.featureVersion })
    }

    private void configureProfiles() {
        AsakusafwOrganizerPluginConvention convention = project.asakusafwOrganizer
        convention.profiles.all { AsakusafwOrganizerProfile profile ->
            configureProfile(profile)
        }
    }

    private void configureProfile(AsakusafwOrganizerProfile profile) {
        AsakusaMapReduceBaseExtension base = AsakusaMapReduceBasePlugin.get(project)
        profile.extensions.create('mapreduce', AsakusafwOrganizerMapReduceExtension)
        profile.mapreduce.conventionMapping.with {
            enabled = { project.asakusafwOrganizer.mapreduce.enabled }
        }
        PluginUtils.injectVersionProperty(profile.mapreduce, { base.featureVersion })

        AsakusaMapReduceOrganizer organizer = new AsakusaMapReduceOrganizer(project, profile)
        organizer.configureProfile()
        organizers << organizer
    }

    private void configureTasks() {
        defineFacadeTasks([
            attachComponentMapreduce : 'Attaches Asakusa on MapReduce components to assemblies.',
            attachMapreduceBatchapps : 'Attaches Asakusa on MapReduce batch applications to assemblies.',
        ])
    }

    private void defineFacadeTasks(Map<String, String> taskMap) {
        taskMap.each { String taskName, String desc ->
            project.task(taskName) { Task task ->
                if (desc != null) {
                    task.group AsakusafwOrganizerPlugin.ASAKUSAFW_ORGANIZER_GROUP
                    task.description desc
                }
                organizers.all { AsakusaMapReduceOrganizer organizer ->
                    task.dependsOn organizer.task(task.name)
                }
            }
        }
    }
}
