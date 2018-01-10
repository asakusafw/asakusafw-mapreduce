/**
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
package com.asakusafw.compiler.info;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.asakusafw.compiler.flow.FlowCompilerOptions;
import com.asakusafw.compiler.flow.Location;
import com.asakusafw.compiler.testing.DirectBatchCompiler;
import com.asakusafw.compiler.yaess.testing.batch.ComplexBatch;
import com.asakusafw.compiler.yaess.testing.batch.DiamondBatch;
import com.asakusafw.compiler.yaess.testing.batch.SimpleBatch;
import com.asakusafw.info.BatchInfo;
import com.asakusafw.info.JobflowInfo;
import com.asakusafw.info.task.TaskInfo;
import com.asakusafw.info.task.TaskListAttribute;
import com.asakusafw.vocabulary.batch.BatchDescription;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test for {@link DslInformationProcessor}.
 */
public class DslInformationProcessorTest {

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
        assertThat(info.getJobflows(), hasSize(1));

        JobflowInfo f0 = jobflow(info, "first");
        assertThat(f0.getBlockerIds(), hasSize(0));

        assertThat(tasks(f0, TaskInfo.Phase.INITIALIZE), hasSize(1));
        assertThat(tasks(f0, TaskInfo.Phase.IMPORT), hasSize(1));
        assertThat(tasks(f0, TaskInfo.Phase.PROLOGUE), hasSize(1));
        assertThat(tasks(f0, TaskInfo.Phase.MAIN), hasSize(1));
        assertThat(tasks(f0, TaskInfo.Phase.EPILOGUE), hasSize(1));
        assertThat(tasks(f0, TaskInfo.Phase.EXPORT), hasSize(1));
        assertThat(tasks(f0, TaskInfo.Phase.FINALIZE), hasSize(1));
    }

    /**
     * w/ multiple stages.
     */
    @Test
    public void stages() {
        BatchInfo info = compile(ComplexBatch.class);

        assertThat(info.getId(), is("complex"));
        assertThat(info.getJobflows(), hasSize(1));

        JobflowInfo f0 = jobflow(info, "last");
        List<TaskInfo> main = tasks(f0, TaskInfo.Phase.MAIN);

        assertThat(main, hasSize(3));

        TaskInfo s0 = main.stream()
            .filter(it -> it.getBlockers().isEmpty())
            .findFirst()
            .get();

        TaskInfo s1 = main.stream()
                .filter(it -> it.getBlockers().contains(s0.getId()))
                .findFirst()
                .get();

        main.stream()
                .filter(it -> it.getBlockers().contains(s1.getId()))
                .findFirst()
                .get();
    }

    /**
     * w/ multiple flow.
     */
    @Test
    public void flows() {
        BatchInfo info = compile(DiamondBatch.class);

        assertThat(info.getId(), is("diamond"));
        assertThat(info.getJobflows(), hasSize(4));

        JobflowInfo f0 = jobflow(info, "first");
        JobflowInfo f1 = jobflow(info, "left");
        JobflowInfo f2 = jobflow(info, "right");
        JobflowInfo f3 = jobflow(info, "last");

        assertThat(f0.getBlockerIds(), hasSize(0));
        assertThat(f1.getBlockerIds(), containsInAnyOrder(f0.getId()));
        assertThat(f2.getBlockerIds(), containsInAnyOrder(f0.getId()));
        assertThat(f3.getBlockerIds(), containsInAnyOrder(f1.getId(), f2.getId()));
    }

    private static JobflowInfo jobflow(BatchInfo parent, String id) {
        return parent.getJobflows().stream()
                .filter(it -> it.getId().equals(id))
                .findFirst()
                .get();
    }

    private List<TaskInfo> tasks(JobflowInfo jobflow, TaskInfo.Phase phase) {
        return jobflow.findAttribute(TaskListAttribute.class)
                .map(it -> it.getPhases().getOrDefault(phase, Collections.emptyList()))
                .orElse(Collections.emptyList());
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
            File script = new File(output, DslInformationProcessor.PATH);
            return new ObjectMapper().readValue(script, BatchInfo.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

}
