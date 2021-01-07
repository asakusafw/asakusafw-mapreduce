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
package com.asakusafw.compiler.info;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.asakusafw.compiler.batch.AbstractWorkflowProcessor;
import com.asakusafw.compiler.batch.WorkDescriptionProcessor;
import com.asakusafw.compiler.batch.Workflow;
import com.asakusafw.compiler.batch.Workflow.Unit;
import com.asakusafw.compiler.batch.WorkflowProcessor;
import com.asakusafw.compiler.batch.processor.JobFlowWorkDescriptionProcessor;
import com.asakusafw.compiler.flow.ExternalIoCommandProvider;
import com.asakusafw.compiler.flow.ExternalIoCommandProvider.Command;
import com.asakusafw.compiler.flow.ExternalIoCommandProvider.CommandContext;
import com.asakusafw.compiler.flow.jobflow.CompiledStage;
import com.asakusafw.compiler.flow.jobflow.JobflowModel;
import com.asakusafw.compiler.flow.jobflow.JobflowModel.Stage;
import com.asakusafw.info.BatchInfo;
import com.asakusafw.info.JobflowInfo;
import com.asakusafw.info.ParameterInfo;
import com.asakusafw.info.ParameterListAttribute;
import com.asakusafw.info.task.TaskInfo;
import com.asakusafw.info.task.TaskListAttribute;
import com.asakusafw.utils.graph.Graph;
import com.asakusafw.utils.graph.Graphs;
import com.asakusafw.vocabulary.batch.Batch;
import com.asakusafw.vocabulary.batch.BatchDescription;
import com.asakusafw.vocabulary.batch.JobFlowWorkDescription;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An implementation of {@link WorkflowProcessor} for DSL information.
 * @since 0.10.0
 */
public class DslInformationProcessor extends AbstractWorkflowProcessor {

    static final Logger LOG = LoggerFactory.getLogger(DslInformationProcessor.class);

    private static final String HADOOP_MODULE_NAME = "hadoop";

    private static final String DEFAULT_PROFILE_NAME = "default";

    /**
     * The script output path.
     */
    public static final String PATH = "etc/batch-info.json"; //$NON-NLS-1$

    @Override
    public Collection<Class<? extends WorkDescriptionProcessor<?>>> getDescriptionProcessors() {
        List<Class<? extends WorkDescriptionProcessor<?>>> results = new ArrayList<>();
        results.add(JobFlowWorkDescriptionProcessor.class);
        return results;
    }

    @Override
    public void process(Workflow workflow) throws IOException {
        BatchInfo info = toBatch(workflow);
        write(info);
    }

    void write(BatchInfo info) {
        ObjectMapper mapper = new ObjectMapper();
        try (OutputStream output = getEnvironment().openResource(PATH)) {
            mapper.writerFor(BatchInfo.class).writeValue(output, info);
        } catch (IOException | RuntimeException e) {
            LOG.warn(MessageFormat.format(
                    "error occurred while writing DSL information: {0}",
                    PATH), e);
        }
    }

    private BatchInfo toBatch(Workflow info) {
        Graph<Unit> graph = info.getGraph();
        Class<? extends BatchDescription> desc = info.getDescription().getClass();
        Batch annotation = desc.getAnnotation(Batch.class);
        BatchInfo batch = new BatchInfo(
                getEnvironment().getConfiguration().getBatchId(),
                desc.getName(),
                Optional.ofNullable(annotation)
                        .map(Batch::comment)
                        .filter(it -> it.isEmpty() == false)
                        .orElse(null),
                Graphs.sortPostOrder(graph).stream()
                        .map(unit -> toJobflow(graph, unit))
                        .collect(Collectors.toList()),
                Collections.singleton(Optional.ofNullable(annotation)
                        .map(it -> collectParameters(it))
                        .orElseGet(() -> new ParameterListAttribute(Collections.emptyList(), false))));
        return batch;
    }

    private static JobflowInfo toJobflow(Graph<Workflow.Unit> graph, Workflow.Unit unit) {
        return new JobflowInfo(
                unit.getDescription().getName(),
                unit.getDescription().getClass().getName(),
                graph.getConnected(unit).stream()
                        .map(it -> it.getDescription().getName())
                        .sorted()
                        .collect(Collectors.toList()),
                Collections.singletonList(collectTasks(unit)));
    }

    private static TaskListAttribute collectTasks(Workflow.Unit unit) {
        JobflowModel model = toJobflowModel(unit);
        CommandContext context = new CommandContext("A", "E", Collections.emptyMap());
        List<TaskInfo> results = new ArrayList<>();
        for (ExternalIoCommandProvider provider : model.getCompiled().getCommandProviders()) {
            results.addAll(collectPhase(TaskInfo.Phase.INITIALIZE, provider.getInitializeCommand(context)));
            results.addAll(collectPhase(TaskInfo.Phase.IMPORT, provider.getImportCommand(context)));
        }
        results.addAll(collectStages(TaskInfo.Phase.PROLOGUE, model.getCompiled().getPrologueStages()));
        results.addAll(collectMain(model));
        results.addAll(collectStages(TaskInfo.Phase.EPILOGUE, model.getCompiled().getEpilogueStages()));
        for (ExternalIoCommandProvider provider : model.getCompiled().getCommandProviders()) {
            results.addAll(collectPhase(TaskInfo.Phase.EXPORT, provider.getExportCommand(context)));
            results.addAll(collectPhase(TaskInfo.Phase.FINALIZE, provider.getFinalizeCommand(context)));
        }
        return new TaskListAttribute(results);
    }

    private static List<TaskInfo> collectMain(JobflowModel model) {
        Graph<Stage> graph = model.getDependencyGraph();
        return graph.getNodeSet().stream()
                .sorted(Comparator.comparingInt(Stage::getNumber))
                .map(stage -> new TaskInfo(
                    stage.getCompiled().getStageId(),
                    TaskInfo.Phase.MAIN,
                    HADOOP_MODULE_NAME,
                    DEFAULT_PROFILE_NAME,
                    graph.getConnected(stage).stream()
                            .sorted(Comparator.comparingInt(Stage::getNumber))
                            .map(blocker -> blocker.getCompiled().getStageId())
                            .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    private static List<TaskInfo> collectStages(TaskInfo.Phase phase, List<CompiledStage> stages) {
        return stages.stream()
                .map(it -> new TaskInfo(
                        it.getStageId(),
                        phase,
                        HADOOP_MODULE_NAME,
                        DEFAULT_PROFILE_NAME,
                        Collections.emptyList()))
                .collect(Collectors.toList());
    }

    private static List<TaskInfo> collectPhase(TaskInfo.Phase phase, List<Command> commands) {
        List<TaskInfo> results = new ArrayList<>();
        for (ExternalIoCommandProvider.Command command : commands) {
            results.add(new TaskInfo(
                    String.valueOf(results.size()),
                    phase,
                    command.getModuleName(),
                    Optional.ofNullable(command.getProfileName())
                            .orElse(DEFAULT_PROFILE_NAME),
                    Collections.emptyList()));
        }
        return results;
    }

    private static ParameterListAttribute collectParameters(Batch batch) {
        return new ParameterListAttribute(
                Arrays.stream(batch.parameters())
                        .map(it -> new ParameterInfo(
                                it.key(),
                                Optional.of(it.comment())
                                        .filter(s -> s.isEmpty() == false)
                                        .orElse(null),
                                it.required(),
                                Optional.of(it.pattern())
                                        .filter(Predicate.isEqual(Batch.DEFAULT_PARAMETER_VALUE_PATTERN).negate())
                                        .orElse(null)))
                        .collect(Collectors.toList()),
                batch.strict());
    }

    private static JobflowModel toJobflowModel(Workflow.Unit unit) {
        assert unit != null;
        assert unit.getDescription() instanceof JobFlowWorkDescription;
        return (JobflowModel) unit.getProcessed();
    }
}
