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

import java.util.concurrent.Callable

import org.gradle.api.Buildable
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import com.asakusafw.gradle.plugins.AsakusafwCompilerExtension
import com.asakusafw.gradle.plugins.AsakusafwOrganizerPlugin
import com.asakusafw.gradle.plugins.AsakusafwPluginConvention
import com.asakusafw.gradle.plugins.internal.AsakusaSdkPlugin
import com.asakusafw.gradle.tasks.RunBatchappTask
import com.asakusafw.gradle.tasks.internal.ResolutionUtils
import com.asakusafw.mapreduce.gradle.tasks.CompileBatchappTask

/**
 * Test for {@link AsakusaMapReduceSdkPlugin}.
 */
class AsakusaMapReduceSdkPluginTest {

    /**
     * The test initializer.
     */
    @Rule
    public final TestRule initializer = new TestRule() {
        Statement apply(Statement stmt, Description desc) {
            project = ProjectBuilder.builder().withName(desc.methodName).build()
            project.apply plugin: AsakusaMapReduceSdkPlugin
            project.asakusafwBase.frameworkVersion = '0.0.0'
            return stmt
        }
    }

    Project project

    /**
     * test for parent plug-ins.
     */
    @Test
    public void parents() {
        assert project.plugins.hasPlugin(AsakusaSdkPlugin)
        assert !project.plugins.hasPlugin(AsakusafwOrganizerPlugin)
        assert project.plugins.hasPlugin(AsakusaMapReduceBasePlugin)
    }

    /**
     * test for {@code project.asakusafw.mapreduce}.
     */
    @Test
    public void extension_defaults() {
        AsakusafwPluginConvention sdk = project.asakusafw
        sdk.compiler.compiledSourceDirectory = 'compiler'
        sdk.compiler.hadoopWorkDirectory = 'hadoop'
        sdk.compiler.compilerOptions '+testing'

        AsakusafwCompilerExtension extension = sdk.mapreduce
        assert project.relativePath(extension.outputDirectory) == 'compiler'
        assert !extension.include
        assert !extension.exclude
        assert extension.runtimeWorkingDirectory == 'hadoop'
        assert ResolutionUtils.resolveToStringMap(extension.compilerProperties) == ['testing': 'true']
        assert extension.batchIdPrefix == null
        assert extension.failOnError == true
    }

    /**
     * Test for {@code project.asakusafw.mapreduce.compilerProperties}.
     */
    @Test
    void extension_compilerProperties() {
        project.asakusafw.compiler.compilerOptions('+a, -b, Xc=d')

        Map<String, String> map = ResolutionUtils.resolveToStringMap(project.asakusafw.mapreduce.compilerProperties)
        assert map == ['a': 'true', 'b': 'false', 'Xc': 'd']
    }

    /**
     * Test for {@code project.asakusafw.mapreduce.version}.
     */
    @Test
    void extension_version() {
        project.asakusaMapReduceBase.featureVersion = '__VERSION__'
        assert project.asakusafw.mapreduce.version == '__VERSION__'
    }

    /**
     * Test for {@code project.asakusafw.mapreduce.options}.
     */
    @Test
    void extension_options() {
        project.asakusafw.mapreduce.options = [:]
        project.asakusafw.mapreduce.options 'a': true, 'b': false, 'Xc': 'd'
        project.asakusafw.mapreduce.options 'e': true
        project.asakusafw.mapreduce.option 'f', false

        Map<String, String> map = ResolutionUtils.resolveToStringMap(project.asakusafw.mapreduce.options)
        assert map == ['a': 'true', 'b': 'false', 'Xc': 'd', 'e': 'true', 'f': 'false']
        assert map == ResolutionUtils.resolveToStringMap(project.asakusafw.mapreduce.compilerProperties)
    }

    /**
     * Test for {@code project.tasks.mapreduceCompileBatchapps}.
     */
    @Test
    void tasks_mapreduceCompileBatchapps() {
        AsakusaMapReduceBaseExtension base = AsakusaMapReduceBasePlugin.get(project)
        base.featureVersion = '__VERSION__'

        AsakusafwPluginConvention sdk = project.asakusafw
        sdk.logbackConf 'testing/logback'
        sdk.maxHeapSize '1G'

        sdk.compiler.compiledSourcePackage 'testing.batchapps'
        sdk.compiler.compilerOptions = ['Xtesting=true', '+e', '-d']
        sdk.compiler.compilerWorkDirectory 'testing/work'
        sdk.compiler.hadoopWorkDirectory 'testing/hadoop'
        sdk.compiler.compiledSourceDirectory 'testing/compiled'

        AsakusafwCompilerExtension extension = sdk.mapreduce
        extension.include = ['I1', 'I2']
        extension.exclude = ['X1', 'X2']

        CompileBatchappTask task = project.tasks.mapreduceCompileBatchapps
        assert task.logbackConf == project.file(sdk.logbackConf)
        assert task.maxHeapSize == sdk.maxHeapSize
        assert project.files(task.sourcepath).contains(project.sourceSets.main.output.classesDir)
        assert project.files(task.embed).contains(project.sourceSets.main.output.resourcesDir)
        assert task.systemProperties.isEmpty()
        assert task.jvmArgs.isEmpty()

        assert task.frameworkVersion == base.featureVersion
        assert task.packageName == sdk.compiler.compiledSourcePackage
        assert task.workingDirectory == project.file(sdk.compiler.compilerWorkDirectory)
        assert task.hadoopWorkingDirectory == sdk.compiler.hadoopWorkDirectory
        assert task.outputDirectory == project.file(sdk.compiler.compiledSourceDirectory)

        Set<String> options = ResolutionUtils.resolveToStringList(task.compilerOptions).toSet()
        Set<String> include = ResolutionUtils.resolveToStringList(task.include).toSet()
        Set<String> exclude = ResolutionUtils.resolveToStringList(task.exclude).toSet()
        assert options == ['Xtesting=true', '+e', '-d'] as Set
        assert include == ['I1', 'I2'] as Set
        assert exclude == ['X1', 'X2'] as Set
    }

    /**
     * Test for {@code project.tasks.testRunBatchapp}.
     */
    @Test
    void tasks_testRunBatchapp() {
        AsakusafwPluginConvention sdk = project.asakusafw
        sdk.logbackConf 'testing/logback'
        sdk.maxHeapSize '1G'

        sdk.compiler.compiledSourceDirectory 'testing/compiled'

        RunBatchappTask task = project.tasks.testRunBatchapp
        assert task.logbackConf == project.file(sdk.logbackConf)
        assert task.maxHeapSize == sdk.maxHeapSize
        assert project.file(task.systemProperties['asakusa.testdriver.batchapps']) == project.file(sdk.compiler.compiledSourceDirectory)
        assert task.jvmArgs.isEmpty()

        assert task.batchId == null
        assert task.batchArguments.isEmpty()
    }

    /**
     * Test for {@code project.tasks.compileBatchapp}.
     */
    @Test
    void tasks_compileBatchapp() {
        Task task = project.tasks.findByName('compileBatchapp')
        assert task != null
        assert dependencies(task).contains('mapreduceCompileBatchapps')
    }

    Set<String> dependencies(Task task) {
        return task.getDependsOn().collect { toTaskNames(task, it) }.flatten().toSet()
    }

    Collection<String> toTaskNames(Task origin, Object value) {
        if (value instanceof Task) {
            return [ value.name ]
        } else if (value instanceof Callable<?>) {
            return toTaskNames(origin, value.call() ?: [])
        } else if (value instanceof TaskDependency) {
            return value.getDependencies(origin).collect { it.name }
        } else if (value instanceof Buildable) {
            return toTaskNames(origin, value.buildDependencies)
        } else if (value instanceof Collection<?> || value instanceof Object[]) {
            return value.collect { toTaskNames(origin, it) }.flatten()
        } else {
            return [ String.valueOf(value) ]
        }
    }
}
