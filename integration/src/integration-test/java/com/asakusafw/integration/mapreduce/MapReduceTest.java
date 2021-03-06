/**
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
package com.asakusafw.integration.mapreduce;

import static com.asakusafw.integration.mapreduce.Util.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.asakusafw.integration.AsakusaConfigurator;
import com.asakusafw.integration.AsakusaConstants;
import com.asakusafw.integration.AsakusaProject;
import com.asakusafw.integration.AsakusaProjectProvider;
import com.asakusafw.integration.PlatformUtil;
import com.asakusafw.utils.gradle.Bundle;
import com.asakusafw.utils.gradle.ContentsConfigurator;
import com.asakusafw.utils.gradle.PropertyConfigurator;

/**
 * Test for {@code asakusafw-mapreduce}.
 */
@RunWith(Parameterized.class)
public class MapReduceTest {

    /**
     * Return the test parameters.
     * @return the test parameters
     */
    @Parameters(name = "use-hadoop:{0}")
    public static Object[][] getTestParameters() {
        return new Object[][] {
            { false },
            { true },
        };
    }

    /**
     * project provider.
     */
    @Rule
    public final AsakusaProjectProvider provider = new AsakusaProjectProvider()
            .withProject(ContentsConfigurator.copy(data("mapreduce")))
            .withProject(ContentsConfigurator.copy(data("ksv")))
            .withProject(ContentsConfigurator.copy(data("logback-test")))
            .withProject(AsakusaConfigurator.projectHome());

    /**
     * Creates a new instance.
     * @param useHadoop whether or not the test uses hadoop command
     */
    public MapReduceTest(boolean useHadoop) {
        if (useHadoop) {
            provider.withProject(AsakusaConfigurator.hadoop(AsakusaConfigurator.Action.SKIP_IF_UNDEFINED));
        } else {
            provider.withProject(AsakusaConfigurator.hadoop(AsakusaConfigurator.Action.UNSET_ALWAYS));
        }
    }

    /**
     * help.
     */
    @Test
    public void help() {
        AsakusaProject project = provider.newInstance("prj");
        project.gradle("help");
    }

    /**
     * version.
     */
    @Test
    public void version() {
        AsakusaProject project = provider.newInstance("prj");
        project.gradle("asakusaVersions");
    }

    /**
     * upgrade.
     */
    @Test
    public void upgrade() {
        AsakusaProject project = provider.newInstance("prj");
        project.gradle("asakusaUpgrade");
        Bundle contents = project.getContents();
        assertThat(contents.find("gradlew"), is(not(Optional.empty())));
        assertThat(contents.find("gradlew.bat"), is(not(Optional.empty())));
    }

    /**
     * {@code assemble}.
     */
    @Test
    public void assemble() {
        AsakusaProject project = provider.newInstance("prj");
        project.gradle("assemble");
        Bundle contents = project.getContents();
        assertThat(contents.find("build/asakusafw-prj.tar.gz"), is(not(Optional.empty())));
    }

    /**
     * {@code installAsakusafw}.
     */
    @Test
    public void installAsakusafw() {
        AsakusaProject project = provider.newInstance("prj");
        project.gradle("installAsakusafw");
    }

    /**
     * {@code test}.
     */
    @Test
    public void test() {
        AsakusaProject project = provider.newInstance("prj");
        project.gradle("installAsakusafw", "test");
    }

    /**
     * {@code test} with {@code 'mapreduce'} testkit.
     */
    @Test
    public void test_mapreduce() {
        PlatformUtil.skipIfWindows();

        AsakusaProject project = provider.newInstance("prj")
                .with(PropertyConfigurator.of("sdk.testkit", "mapreduce"));
        project.gradle("installAsakusafw", "test");
    }

    /**
     * {@code runTest} - test w/ test-tools.
     */
    @Test
    public void test_tools() {
        AsakusaProject project = provider.newInstance("prj");
        project.gradle("attachMapreduceBatchapps", "installAsakusafw", "runTest");
    }

    /**
     * YAESS.
     */
    @Test
    public void yaess() {
        PlatformUtil.skipIfWindows();

        AsakusaProject project = provider.newInstance("prj");
        project.gradle("attachMapreduceBatchapps", "installAsakusafw");

        String[] csv = new String[] {
                "1,1.0,A",
                "2,2.0,B",
                "3,3.0,C",
        };
        project.getContents().put("var/data/input/file.csv", f -> {
            Files.write(f, Arrays.asList(csv), StandardCharsets.UTF_8);
        });

        project.getFramework().withLaunch(
                AsakusaConstants.CMD_YAESS, "perf.average.sort",
                "-A", "input=input", "-A", "output=output");

        project.getContents().get("var/data/output", dir -> {
            List<String> results = Files.list(dir)
                .flatMap(Util::lines)
                .sorted()
                .collect(Collectors.toList());
            assertThat(results, containsInAnyOrder(csv));
        });
    }

    /**
     * YAESS w/ WindGate tasks.
     */
    @Test
    public void yaess_windgate() {
        PlatformUtil.skipIfWindows();

        AsakusaProject project = provider.newInstance("prj");

        project.gradle("attachMapreduceBatchapps", "installAsakusafw");

        String[] csv = new String[] {
                "1,1.0,A",
                "2,2.0,B",
                "3,3.0,C",
        };

        project.getContents().put("var/windgate/input.csv", f -> {
            Files.write(f, Arrays.asList(csv), StandardCharsets.UTF_8);
        });

        project.getFramework().withLaunch(
                AsakusaConstants.CMD_YAESS, "wg.perf.average.sort",
                "-A", "input=input.csv", "-A", "output=output.csv");

        project.getContents().get("var/windgate/output.csv", file -> {
            List<String> results = lines(file)
                .collect(Collectors.toList());
            assertThat(results, containsInAnyOrder(csv));
        });
    }

    /**
     * {@code run}.
     */
    @Test
    public void run() {
        AsakusaProject project = provider.newInstance("prj");
        project.gradle("attachMapreduceBatchapps", "installAsakusafw");

        String[] csv = new String[] {
                "1,1.0,A",
                "2,2.0,B",
                "3,3.0,C",
        };
        project.getContents().put("var/data/input/file.csv", f -> {
            Files.write(f, Arrays.asList(csv), StandardCharsets.UTF_8);
        });

        project.getFramework().withLaunch(
                AsakusaConstants.CMD_PORTAL, "run", "perf.average.sort",
                "-Ainput=input", "-Aoutput=output");

        project.getContents().get("var/data/output", dir -> {
            List<String> results = Files.list(dir)
                .flatMap(Util::lines)
                .sorted()
                .collect(Collectors.toList());
            assertThat(results, containsInAnyOrder(csv));
        });
    }

    /**
     * {@code run} w/ WindGate tasks.
     */
    @Test
    public void run_windgate() {
        AsakusaProject project = provider.newInstance("prj");

        project.gradle("attachMapreduceBatchapps", "installAsakusafw");

        String[] csv = new String[] {
                "1,1.0,A",
                "2,2.0,B",
                "3,3.0,C",
        };

        project.getContents().put("var/windgate/input.csv", f -> {
            Files.write(f, Arrays.asList(csv), StandardCharsets.UTF_8);
        });

        project.getFramework().withLaunch(
                AsakusaConstants.CMD_PORTAL, "run", "wg.perf.average.sort",
                "-Ainput=input.csv", "-Aoutput=output.csv");

        project.getContents().get("var/windgate/output.csv", file -> {
            List<String> results = lines(file)
                .collect(Collectors.toList());
            assertThat(results, containsInAnyOrder(csv));
        });
    }
}
