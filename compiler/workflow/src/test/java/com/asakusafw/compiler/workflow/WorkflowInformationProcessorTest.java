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
package com.asakusafw.compiler.workflow;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.asakusafw.compiler.flow.FlowCompilerOptions;
import com.asakusafw.compiler.flow.Location;
import com.asakusafw.compiler.testing.DirectBatchCompiler;
import com.asakusafw.compiler.yaess.testing.batch.ComplexBatch;
import com.asakusafw.compiler.yaess.testing.batch.DiamondBatch;
import com.asakusafw.compiler.yaess.testing.batch.SimpleBatch;
import com.asakusafw.vocabulary.batch.BatchDescription;
import com.asakusafw.workflow.model.BatchInfo;
import com.asakusafw.workflow.model.CommandTaskInfo;
import com.asakusafw.workflow.model.HadoopTaskInfo;
import com.asakusafw.workflow.model.JobflowInfo;
import com.asakusafw.workflow.model.TaskInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test for {@link WorkflowInformationProcessor}.
 * @since 0.10.0
 */
public class WorkflowInformationProcessorTest {

    /**
     * Temporary folder.
     */
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    /**
     * simple case.
     */
    @Test
    public void simple() {
        BatchInfo info = compile(SimpleBatch.class);

        assertThat(info.getId(), is("simple"));
        assertThat(info.getElements(), hasSize(1));

        JobflowInfo f0 = info.findElement("first").get();

        assertThat(f0.getTasks(TaskInfo.Phase.INITIALIZE), hasCommands("initialize"));
        assertThat(f0.getTasks(TaskInfo.Phase.IMPORT), hasCommands("import"));
        assertThat(f0.getTasks(TaskInfo.Phase.PROLOGUE).size(), is(1));
        assertThat(f0.getTasks(TaskInfo.Phase.MAIN).size(), is(1));
        assertThat(f0.getTasks(TaskInfo.Phase.EPILOGUE).size(), is(1));
        assertThat(f0.getTasks(TaskInfo.Phase.EXPORT), hasCommands("export"));
        assertThat(f0.getTasks(TaskInfo.Phase.FINALIZE), hasCommands("finalize"));
        assertThat(f0.getTasks(TaskInfo.Phase.CLEANUP).size(), is(1));
    }

    /**
     * w/ multiple stages.
     */
    @Test
    public void stages() {
        BatchInfo info = compile(ComplexBatch.class);

        assertThat(info.getId(), is("complex"));
        assertThat(info.getElements(), hasSize(1));

        JobflowInfo f0 = info.findElement("last").get();
        Collection<? extends TaskInfo> main = f0.getTasks(TaskInfo.Phase.MAIN);
        assertThat(main, hasSize(3));

        TaskInfo s0 = main.stream()
            .filter(it -> it.getBlockers().isEmpty())
            .findFirst()
            .get();

        TaskInfo s1 = main.stream()
                .filter(it -> it.getBlockers().contains(s0))
                .findFirst()
                .get();

        TaskInfo s2 = main.stream()
                .filter(it -> it.getBlockers().contains(s1))
                .findFirst()
                .get();

        assertThat(s0, is(instanceOf(HadoopTaskInfo.class)));
        assertThat(s1, is(instanceOf(HadoopTaskInfo.class)));
        assertThat(s2, is(instanceOf(HadoopTaskInfo.class)));
    }

    /**
     * w/ multiple flow.
     */
    @Test
    public void flows() {
        BatchInfo info = compile(DiamondBatch.class);

        assertThat(info.getId(), is("diamond"));
        assertThat(info.getElements(), hasSize(4));

        JobflowInfo f0 = info.findElement("first").get();
        JobflowInfo f1 = info.findElement("left").get();
        JobflowInfo f2 = info.findElement("right").get();
        JobflowInfo f3 = info.findElement("last").get();

        assertThat(f0.getBlockers(), hasSize(0));
        assertThat(f1.getBlockers(), containsInAnyOrder(f0));
        assertThat(f2.getBlockers(), containsInAnyOrder(f0));
        assertThat(f3.getBlockers(), containsInAnyOrder(f1, f2));
    }

    private BatchInfo compile(Class<? extends BatchDescription> batchClass) {
        try {
            File output = folder.newFolder("output");
            DirectBatchCompiler.compile(
                    batchClass,
                    "com.example",
                    Location.fromPath("testing", '/'),
                    output,
                    folder.newFolder("working"),
                    Collections.emptyList(),
                    getClass().getClassLoader(),
                    new FlowCompilerOptions());
            File script = new File(output, WorkflowInformationProcessor.PATH);
            return new ObjectMapper().readValue(script, BatchInfo.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private Matcher<Collection<? extends TaskInfo>> hasCommands(String... executables) {
        return new BaseMatcher<Collection<? extends TaskInfo>>() {
            @Override
            public boolean matches(Object target) {
                if ((target instanceof Collection<?>) == false) {
                    return false;
                }
                Collection<?> tasks = (Collection<?>) target;
                List<String> commands = tasks.stream()
                    .filter(it -> it instanceof CommandTaskInfo)
                    .map(it -> (CommandTaskInfo) it)
                    .map(it -> it.getCommand())
                    .sorted()
                    .collect(Collectors.toList());
                if (tasks.size() != commands.size()) {
                    return false;
                }
                return commands.equals(Arrays.stream(executables)
                        .sorted()
                        .collect(Collectors.toList()));
            }
            @Override
            public void describeTo(Description desc) {
                desc.appendText("executables of ");
                desc.appendValue(Arrays.toString(executables));
            }
        };
    }
}
