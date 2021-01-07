/*
 * Copyright 2011-2021 Asakusa Framework Team.
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
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import com.asakusafw.gradle.plugins.AsakusafwOrganizerPlugin
import com.asakusafw.gradle.plugins.AsakusafwOrganizerProfile
import com.asakusafw.gradle.plugins.OrganizerTestRoot
import com.asakusafw.gradle.plugins.internal.AsakusaSdkPlugin
import com.asakusafw.mapreduce.gradle.plugins.AsakusafwOrganizerMapReduceExtension

/**
 * Test for {@link AsakusaMapReduceOrganizerPlugin}.
 */
class AsakusaMapReduceOrganizerPluginTest extends OrganizerTestRoot {

    /**
     * The test initializer.
     */
    @Rule
    public final TestRule initializer = new TestRule() {
        Statement apply(Statement stmt, Description desc) {
            project = ProjectBuilder.builder().withName(desc.methodName).build()
            project.apply plugin: AsakusaMapReduceOrganizerPlugin
            return stmt
        }
    }

    Project project

    /**
     * test for parent plug-ins.
     */
    @Test
    public void parents() {
        assert !project.plugins.hasPlugin(AsakusaSdkPlugin)
        assert project.plugins.hasPlugin(AsakusafwOrganizerPlugin)
        assert project.plugins.hasPlugin(AsakusaMapReduceBasePlugin)
    }

    /**
     * Test for {@code project.asakusafwOrganizer.mapreduce} convention default values.
     */
    @Test
    public void extension_defaults() {
        AsakusafwOrganizerMapReduceExtension extension = project.asakusafwOrganizer.mapreduce
        assert extension.enabled == true
    }

    /**
     * Test for {@code project.asakusafwOrganizer.profiles.*.mapreduce} default values.
     */
    @Test
    public void profile_defaults() {
        AsakusafwOrganizerMapReduceExtension extension = project.asakusafwOrganizer.mapreduce
        AsakusafwOrganizerMapReduceExtension profile = project.asakusafwOrganizer.profiles.testp.mapreduce
        assert profile.enabled == extension.enabled
    }

    /**
     * Test for {@code project.tasks} for organizer facade tasks.
     */
    @Test
    public void tasks_common() {
        assert project.tasks.attachComponentMapreduce
        assert project.tasks.attachMapreduceBatchapps
    }

    /**
     * Test for {@code project.tasks} for profile tasks.
     */
    @Test
    public void tasks_profile() {
        AsakusafwOrganizerProfile profile = project.asakusafwOrganizer.profiles.testp
        assert ptask(profile, 'attachComponentMapreduce')
        assert ptask(profile, 'attachMapreduceBatchapps')
    }

    /**
     * Test for dependencies of {@code project.tasks} in organizer facade tasks.
     */
    @Test
    public void tasks_dependencies() {
        checkDependencies('attachComponentMapreduce')
        checkDependencies('attachMapreduceBatchapps')
    }

    /**
     * Test for {@code project.asakusafwOrganizer.mapreduce.version}.
     */
    @Test
    void version() {
        project.asakusaMapReduceBase.featureVersion = '__VERSION__'
        assert project.asakusafwOrganizer.mapreduce.version == '__VERSION__'
        assert project.asakusafwOrganizer.profiles.dev.mapreduce.version == '__VERSION__'
        assert project.asakusafwOrganizer.profiles.prod.mapreduce.version == '__VERSION__'
        assert project.asakusafwOrganizer.profiles.other.mapreduce.version == '__VERSION__'
    }
}
