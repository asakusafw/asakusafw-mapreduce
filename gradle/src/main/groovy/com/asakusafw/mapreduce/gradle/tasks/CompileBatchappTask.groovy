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
package com.asakusafw.mapreduce.gradle.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.JavaExecSpec

import com.asakusafw.gradle.tasks.internal.AbstractAsakusaToolTask
import com.asakusafw.gradle.tasks.internal.ResolutionUtils

/**
 * Gradle Task for DSL Compile.
 * @since 0.5.3
 * @version 0.8.0
 */
class CompileBatchappTask extends AbstractAsakusaToolTask {

    /**
     * The compiler name.
     * @since 0.8.0
     */
    String compilerName = 'Asakusa DSL compiler'

    /**
     * The current framework version.
     */
    @Optional
    @Input
    String frameworkVersion

    /**
     * The package name for generated sources.
     */
    @Input
    String packageName

    /**
     * The Asakusa DSL compiler options
     */
    @Optional
    @Input
    List<String> compilerOptions = []

    /**
     * The compiler working directory.
     */
    File workingDirectory

    /**
     * The working directory while running the compiled batch application
     * (relative path from the original Hadoop working directory).
     */
    @Input
    String hadoopWorkingDirectory

    /**
     * The batch application output base path.
     */
    @OutputDirectory
    File outputDirectory

    /**
     * The accepting batch class name patterns ({@code "*"} as a wildcard character).
     */
    List<Object> include = []

    /**
     * The rejecting batch class name patterns ({@code "*"} as a wildcard character).
     * @since 0.8.0
     */
    List<Object> exclude = []

    /**
     * The source path to be embedded into the batch application archives.
     * @since 0.8.0
     */
    List<Object> embed = []

    /**
     * {@code true} to stop compilation immediately when detects any compilation errors.
     */
    @Input
    boolean failFast = false

    /**
     * Whether clean-up output directory before compile applications or not.
     */
    @Input
    boolean clean = true

    /**
     * Returns the application project version.
     * @return the application project version
     */
    @Input
    def getProjectVersion() {
        return project.version
    }

    /**
     * Returns the files to be embedded.
     * @return the embed files
     */
    @InputFiles
    FileCollection getEmbedFiles() {
        Object all = project.files(getEmbed()).collect { File file ->
            if (file.isFile()) {
                return file
            } else if (file.isDirectory()) {
                return project.fileTree(file)
            } else {
                return []
            }
        }
        return project.files(all)
    }

    /**
     * Returns the actual values of {@link #include}.
     * @return accepting batch class name patterns
     */
    @Input
    List<String> getResolvedInclude() {
        return ResolutionUtils.resolveToStringList(getInclude())
    }

    /**
     * Returns the actual values of {@link #exclude}.
     * @return rejecting batch class name patterns
     */
    @Input
    List<String> getResolvedExclude() {
        return ResolutionUtils.resolveToStringList(getExclude())
    }

    /**
     * Set the update target batch class name.
     * With this, the compiler only builds the target batch class,
     * and the {@link #include} will be ignored.
     * @param className the target class name pattern
     */
    @Option(option = 'update', description = 'compiles the specified batch classes only')
    void setUpdateOption(String className) {
        logger.info("update: ${className}")
        setClean(false)
        setInclude([className])
        setExclude([])
    }

    /**
     * Task Action of this task.
     */
    @TaskAction
    def compileBatchapp() {
        def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss (z)")
        if (isClean()) {
            logger.info "Cleaning ${getCompilerName()} output directory"
            project.delete getOutputDirectory()
        }
        if (getOutputDirectory().exists() == false) {
            project.mkdir getOutputDirectory()
        }

        File compilerWorkingDirectory = getWorkingDirectory() ?: new File(getTemporaryDir(), 'build')
        project.delete(compilerWorkingDirectory)
        project.javaexec { JavaExecSpec spec ->
            spec.main = 'com.asakusafw.compiler.bootstrap.AllBatchCompilerDriver'
            spec.classpath = this.getToolClasspathCollection()
            spec.jvmArgs = this.getJvmArgs()
            if (this.getMaxHeapSize()) {
                spec.maxHeapSize = this.getMaxHeapSize()
            }
            if (this.getLogbackConf()) {
                spec.systemProperties += [ 'logback.configurationFile' : this.getLogbackConf().absolutePath ]
            }
            spec.systemProperties += [ 'com.asakusafw.batchapp.project.version' : this.getProjectVersion() ]
            spec.systemProperties += [ 'com.asakusafw.batchapp.build.timestamp' : timestamp ]
            spec.systemProperties += [ 'com.asakusafw.batchapp.build.java.version' : System.properties['java.version'] ]
            if (this.getCompilerOptions()) {
                spec.systemProperties += [ 'com.asakusafw.compiler.options' : this.getCompilerOptions().join(',') ]
            }
            if (this.getFrameworkVersion()) {
                spec.systemProperties += [ 'com.asakusafw.framework.version' : this.getFrameworkVersion() ]
            }
            spec.systemProperties += this.getSystemProperties()
            spec.enableAssertions = true
            spec.args = [
                    '-output',
                    this.getOutputDirectory(),
                    '-package',
                    this.getPackageName(),
                    '-compilerwork',
                    compilerWorkingDirectory,
                    '-hadoopwork',
                    this.getHadoopWorkingDirectory(),
                    '-link',
                    project.files(this.getSourcepath(), this.getEmbed()).filter { it.exists() }.asPath,
                    '-scanpath',
                    project.files(this.getSourcepath()).filter { it.exists() }.asPath,
            ]
            if (!getResolvedInclude().isEmpty()) {
                spec.args('-include', getResolvedInclude().join(','))
            }
            if (!getResolvedExclude().isEmpty()) {
                spec.args('-exclude', getResolvedExclude().join(','))
            }
            if (!isFailFast()) {
                spec.args('-skiperror')
            }
            def plugins = this.getPluginClasspathCollection()
            if (plugins != null && !plugins.empty) {
                spec.args('-plugin', plugins.asPath)
            }
        }
    }
}
