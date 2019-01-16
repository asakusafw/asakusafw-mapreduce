/**
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
package com.asakusafw.integration.mapreduce;

import static com.asakusafw.integration.mapreduce.Util.*;

import org.junit.ClassRule;
import org.junit.Test;

import com.asakusafw.integration.AsakusaConfigurator;
import com.asakusafw.integration.AsakusaConstants;
import com.asakusafw.integration.AsakusaProjectProvider;
import com.asakusafw.utils.gradle.Bundle;
import com.asakusafw.utils.gradle.ContentsConfigurator;

/**
 * Test for the portal command.
 */
public class PortalTest {

    /**
     * project provider.
     */
    @ClassRule
    public static final AsakusaProjectProvider PROVIDER = new AsakusaProjectProvider()
            .withProject(ContentsConfigurator.copy(data("mapreduce")))
            .withProject(ContentsConfigurator.copy(data("ksv")))
            .withProject(ContentsConfigurator.copy(data("logback-test")))
            .withProject(AsakusaConfigurator.projectHome())
            .withProvider(provider -> {
                // install framework only once
                framework = provider.newInstance("inf")
                        .gradle("attachMapreduceBatchapps", "installAsakusafw")
                        .getFramework();
            });

    static Bundle framework;

    /**
     * {@code list parameter}.
     */
    @Test
    public void list_parameter() {
        framework.withLaunch(AsakusaConstants.CMD_PORTAL, "list", "parameter", "-v",
                "perf.average.sort");
    }

    /**
     * {@code list jobflow}.
     */
    @Test
    public void list_jobflow() {
        framework.withLaunch(AsakusaConstants.CMD_PORTAL, "list", "jobflow", "-v",
                "perf.average.sort");
    }

    /**
     * {@code generate dot jobflow}.
     */
    @Test
    public void generate_dot_jobflow() {
        framework.withLaunch(AsakusaConstants.CMD_PORTAL, "generate", "dot", "jobflow", "-v",
                "perf.average.sort");
    }
}
