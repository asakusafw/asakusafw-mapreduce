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

import java.util.regex.Matcher
import java.util.regex.Pattern

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency

import com.asakusafw.gradle.plugins.AsakusafwBasePlugin
import com.asakusafw.gradle.plugins.AsakusafwCompilerExtension
import com.asakusafw.gradle.plugins.AsakusafwPluginConvention
import com.asakusafw.gradle.plugins.internal.AsakusaSdkPlugin
import com.asakusafw.gradle.plugins.internal.PluginUtils
import com.asakusafw.gradle.tasks.RunBatchappTask
import com.asakusafw.mapreduce.gradle.tasks.CompileBatchappTask

/**
 * A Gradle sub plug-in for Asakusa on MapReduce SDK.
 * @since 0.8.0
 * @version 0.9.0
 * @see AsakusaMapReduceSdkBasePlugin
 */
class AsakusaMapReduceSdkPlugin implements Plugin<Project> {

    /**
     * The compile task name.
     */
    public static final String TASK_COMPILE = 'mapreduceCompileBatchapps'

    private static final Pattern OPTION_PATTERN = ~/([\+\-])\s*([0-9A-Za-z_\\-]+)|(X[0-9A-Za-z_\\-]+)=([^,]*)/

    private Project project

    private AsakusafwCompilerExtension extension

    @Override
    void apply(Project project) {
        this.project = project

        project.apply plugin: AsakusaMapReduceSdkBasePlugin
        this.extension = AsakusaSdkPlugin.get(project).extensions.create('mapreduce', AsakusafwCompilerExtension)

        configureConvention()
        defineTasks()
    }

    private void configureConvention() {
        AsakusaMapReduceBaseExtension base = AsakusaMapReduceBasePlugin.get(project)
        AsakusafwPluginConvention sdk = AsakusaSdkPlugin.get(project)
        extension.conventionMapping.with {
            outputDirectory = { project.file(sdk.compiler.compiledSourceDirectory) }
            runtimeWorkingDirectory = { sdk.compiler.hadoopWorkDirectory }
            compilerProperties = { parseOptions(sdk.compiler.compilerOptions) }
            failOnError = { true }
        }
        PluginUtils.injectVersionProperty(extension, { base.featureVersion })
    }

    private Map<String, Object> parseOptions(List<String> options) {
        Map<String, Object> results = [:]
        for (String s : options) {
            if (s == null || s.trim().isEmpty()) {
                continue
            }
            String option = s.trim()
            Matcher m = OPTION_PATTERN.matcher(option)
            if (m.matches()) {
                if (m.group(1) != null) {
                    String key = m.group(2)
                    boolean value = m.group(1) == '+'
                    results[key] = value
                } else if (m.group(3) != null) {
                    String key = m.group(3)
                    String value = m.group(4)
                    results[key] = value
                } else {
                    throw new AssertionError(option)
                }
            } else {
                project.logger.warn "unrecognizable compiler option: ${option}"
            }
        }
        return results
    }

    private void defineTasks() {
        defineCompileBatchappTask()
        defineTestRunBatchappTask()
        extendVersionsTask()
    }

    private defineCompileBatchappTask() {
        AsakusaMapReduceBaseExtension base = AsakusaMapReduceBasePlugin.get(project)
        AsakusafwPluginConvention sdk = AsakusaSdkPlugin.get(project)
        project.tasks.create(TASK_COMPILE, CompileBatchappTask) { CompileBatchappTask task ->
            task.group AsakusaSdkPlugin.ASAKUSAFW_BUILD_GROUP
            task.description 'Compiles Asakusa DSL source files for MapReduce environment.'
            task.dependsOn project.tasks.classes
            task.compilerName = 'Asakusa DSL compiler for MapReduce'

            task.sourcepath << PluginUtils.getClassesDirs(project, project.sourceSets.main.output)
            task.embed << { project.sourceSets.main.output.resourcesDir }

            task.toolClasspath << project.configurations.asakusaMapreduceCompiler
            task.toolClasspath << project.sourceSets.main.output

            task.include << { extension.include }
            task.exclude << { extension.exclude }

            task.conventionMapping.with {
                logbackConf = { sdk.logbackConf ? project.file(sdk.logbackConf) : null }
                maxHeapSize = { sdk.maxHeapSize }
                frameworkVersion = { base.featureVersion }
                packageName = { sdk.compiler.compiledSourcePackage }
                compilerOptions = { restoreOptions(extension.compilerProperties) }
                workingDirectory = {
                    if (sdk.compiler.compilerWorkDirectory != null) {
                        return project.file(sdk.compiler.compilerWorkDirectory)
                    } else {
                        return null
                    }
                }
                hadoopWorkingDirectory = { extension.runtimeWorkingDirectory }
                outputDirectory = { project.file(extension.outputDirectory) }
            }
            task.doFirst {
                if (extension.batchIdPrefix != null) {
                    task.logger.warn "batchIdPrefix is not supported in ${task.compilerName}"
                }
            }
            project.tasks.compileBatchapp.dependsOn task
            project.tasks.jarBatchapp.from { task.outputDirectory }
        }
    }

    private void defineTestRunBatchappTask() {
        // NOTE: toolClasspath will be configured by SDK plug-in
        AsakusafwPluginConvention sdk = AsakusaSdkPlugin.get(project)
        project.tasks.create('testRunBatchapp', RunBatchappTask) { RunBatchappTask task ->
            task.group AsakusaSdkPlugin.ASAKUSAFW_BUILD_GROUP
            task.description 'Executes an Asakusa batch application [Experimental].'

            task.systemProperties.put 'asakusa.testdriver.batchapps', { project.tasks.getByName(TASK_COMPILE).outputDirectory }
            task.conventionMapping.with {
                logbackConf = { sdk.logbackConf ? project.file(sdk.logbackConf) : null }
                maxHeapSize = { sdk.maxHeapSize }
            }
            task.dependsOn project.tasks.getByName(TASK_COMPILE)
        }
    }

    private List<String> restoreOptions(Map<String, Object> options) {
        return options.collect { String key, Object value ->
            if (key == null || value == null) {
                return []
            } else if (key.startsWith('X')) {
                return [(String) "${key}=${value}"]
            } else {
                return [(String) "${isEnabled(value) ? '+' : '-'}${key}"]
            }
        }.flatten()
    }

    private boolean isEnabled(Object value) {
        if (value instanceof Boolean) {
            return value
        } else {
            return Boolean.parseBoolean(String.valueOf(value))
        }
    }

    private void extendVersionsTask() {
        project.tasks.getByName(AsakusafwBasePlugin.TASK_VERSIONS).doLast {
            def hadoopVersion = 'UNKNOWN'
            try {
                logger.info 'detecting Hadoop version'
                for (Configuration conf : [project.configurations.provided, project.configurations.compile]) {
                    def v = findHadoopVersion(conf.resolvedConfiguration)
                    if (v != null) {
                        hadoopVersion = v
                        break
                    }
                }
            } catch (Exception e) {
                logger.info 'failed to detect Hadoop version', e
                hadoopVersion = 'INVALID'
            }
            logger.lifecycle "Hadoop: ${hadoopVersion}"
        }
    }

    private String findHadoopVersion(ResolvedConfiguration conf) {
        LinkedList<ResolvedDependency> work = new LinkedList<>()
        work.addAll(conf.firstLevelModuleDependencies)
        Set<ResolvedDependency> saw = new HashSet<>()
        while (!work.empty) {
            ResolvedDependency d = work.removeFirst()
            if (saw.contains(d)) {
                continue
            }
            saw.add(d)
            if (d.moduleVersion != null
                    && d.moduleGroup == 'org.apache.hadoop'
                    && (d.moduleName == 'hadoop-core' || d.moduleName == 'hadoop-common')) {
                return d.moduleVersion
            }
            work.addAll(d.children)
        }
        return null
    }
}
