/**
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
package com.asakusafw.compiler.workflow;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.asakusafw.compiler.batch.AbstractWorkflowProcessor;
import com.asakusafw.compiler.batch.WorkDescriptionProcessor;
import com.asakusafw.compiler.batch.Workflow;
import com.asakusafw.compiler.batch.WorkflowProcessor;
import com.asakusafw.compiler.batch.processor.JobFlowWorkDescriptionProcessor;
import com.asakusafw.compiler.flow.ExternalIoCommandProvider;
import com.asakusafw.compiler.flow.ExternalIoCommandProvider.CommandContext;
import com.asakusafw.compiler.flow.Location;
import com.asakusafw.compiler.flow.jobflow.CompiledStage;
import com.asakusafw.compiler.flow.jobflow.JobflowModel;
import com.asakusafw.compiler.flow.jobflow.JobflowModel.Stage;
import com.asakusafw.utils.graph.Graph;
import com.asakusafw.utils.graph.Graphs;
import com.asakusafw.vocabulary.batch.JobFlowWorkDescription;
import com.asakusafw.workflow.model.DeleteTaskInfo;
import com.asakusafw.workflow.model.JobflowInfo;
import com.asakusafw.workflow.model.TaskInfo;
import com.asakusafw.workflow.model.basic.BasicBatchInfo;
import com.asakusafw.workflow.model.basic.BasicCommandTaskInfo;
import com.asakusafw.workflow.model.basic.BasicDeleteTaskInfo;
import com.asakusafw.workflow.model.basic.BasicHadoopTaskInfo;
import com.asakusafw.workflow.model.basic.BasicJobflowInfo;
import com.asakusafw.workflow.model.basic.BasicTaskInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An implementation of {@link WorkflowProcessor} for workflow information.
 * @since 0.10.0
 */
public class WorkflowInformationProcessor extends AbstractWorkflowProcessor {

    static final Logger LOG = LoggerFactory.getLogger(WorkflowInformationProcessor.class);

    private static final String DEFAULT_PROFILE_NAME = "default";

    /**
     * The script output path.
     */
    public static final String PATH = "etc/workflow.json"; //$NON-NLS-1$

    @Override
    public Collection<Class<? extends WorkDescriptionProcessor<?>>> getDescriptionProcessors() {
        List<Class<? extends WorkDescriptionProcessor<?>>> results = new ArrayList<>();
        results.add(JobFlowWorkDescriptionProcessor.class);
        return results;
    }

    @Override
    public void process(Workflow workflow) throws IOException {
        LOG.debug("abalyzing workflow structure"); //$NON-NLS-1$
        BasicBatchInfo info = toBatch(workflow);
        write(info);
    }

    void write(BasicBatchInfo info) {
        ObjectMapper mapper = new ObjectMapper();
        try (OutputStream output = getEnvironment().openResource(PATH)) {
            mapper.writerFor(com.asakusafw.workflow.model.BatchInfo.class).writeValue(output, info);
        } catch (IOException | RuntimeException e) {
            LOG.warn(MessageFormat.format(
                            "error occurred while writing workflow information: {0}",
                            PATH), e);
        }
    }

    private BasicBatchInfo toBatch(Workflow info) {
        Map<String, BasicJobflowInfo> elements = new LinkedHashMap<>();
        String batchId = getEnvironment().getConfiguration().getBatchId();
        for (Workflow.Unit unit : Graphs.sortPostOrder(info.getGraph())) {
            BasicJobflowInfo jobflow = toJobflow(toJobflowModel(unit));
            elements.put(jobflow.getId(), jobflow);
        }
        assert batchId != null;
        for (Graph.Vertex<Workflow.Unit> vertex : info.getGraph()) {
            BasicJobflowInfo jobflow = elements.get(vertex.getNode().getDescription().getName());
            assert jobflow != null;
            for (Workflow.Unit connected : vertex.getConnected()) {
                BasicJobflowInfo blocker = elements.get(connected.getDescription().getName());
                assert blocker != null;
                jobflow.addBlocker(blocker);
            }
        }
        BasicBatchInfo batch = new BasicBatchInfo(batchId);
        for (JobflowInfo jobflow : elements.values()) {
            batch.addElement(jobflow);
        }
        return batch;
    }

    private BasicJobflowInfo toJobflow(JobflowModel model) {
        BasicJobflowInfo result = new BasicJobflowInfo(model.getFlowId());
        processStages(result, TaskInfo.Phase.PROLOGUE, model.getCompiled().getPrologueStages());
        processMain(model, result);
        processStages(result, TaskInfo.Phase.EPILOGUE, model.getCompiled().getEpilogueStages());
        processCleanup(result);
        CommandContext context = Util.createMockCommandContext();
        for (ExternalIoCommandProvider provider : model.getCompiled().getCommandProviders()) {
            processPhase(result, TaskInfo.Phase.INITIALIZE, provider.getInitializeCommand(context));
            processPhase(result, TaskInfo.Phase.IMPORT, provider.getImportCommand(context));
            processPhase(result, TaskInfo.Phase.EXPORT, provider.getExportCommand(context));
            processPhase(result, TaskInfo.Phase.FINALIZE, provider.getFinalizeCommand(context));
        }
        return result;
    }

    private static void processPhase(
            BasicJobflowInfo result,
            TaskInfo.Phase phase, List<ExternalIoCommandProvider.Command> commands) {
        TaskInfo last = null;
        for (ExternalIoCommandProvider.Command command : commands) {
            LinkedList<String> tokens = new LinkedList<>(command.getCommandTokens());
            String file = tokens.removeFirst();
            BasicCommandTaskInfo task = new BasicCommandTaskInfo(
                    command.getModuleName(),
                    Optional.ofNullable(command.getProfileName())
                            .orElse(DEFAULT_PROFILE_NAME),
                    Util.resolveCommand(file),
                    Util.resolveArguments(tokens));
            if (last != null) {
                task.addBlocker(last);
            }
            result.addTask(phase, task);
            last = task;
        }
    }

    private static void processStages(
            BasicJobflowInfo result,
            TaskInfo.Phase phase, List<CompiledStage> stages) {
        stages.forEach(it -> result.addTask(phase, toTask(it)));
    }

    private static void processMain(JobflowModel model, BasicJobflowInfo result) {
        Graph<Stage> graph = model.getDependencyGraph();
        Map<Stage, BasicTaskInfo> tasks = new LinkedHashMap<>();
        for (Stage stage : Graphs.sortPostOrder(graph)) {
            BasicTaskInfo info = toTask(stage.getCompiled());
            result.addTask(TaskInfo.Phase.MAIN, info);
            tasks.put(stage, info);
        }
        tasks.forEach((stage, task) -> graph.getConnected(stage).stream()
                .map(tasks::get)
                .forEach(task::addBlocker));
    }

    private static BasicTaskInfo toTask(CompiledStage stage) {
        return new BasicHadoopTaskInfo(stage.getQualifiedName().toNameString());
    }

    private void processCleanup(BasicJobflowInfo result) {
        Location workingDirectory = getEnvironment().getConfiguration().getRootLocation();
        result.addTask(TaskInfo.Phase.CLEANUP, new BasicDeleteTaskInfo(
                DeleteTaskInfo.PathKind.HADOOP_FILE_SYSTEM,
                workingDirectory.toPath('/')));
    }

    private static JobflowModel toJobflowModel(Workflow.Unit unit) {
        assert unit != null;
        assert unit.getDescription() instanceof JobFlowWorkDescription;
        return (JobflowModel) unit.getProcessed();
    }
}
