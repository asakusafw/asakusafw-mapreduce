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
package com.asakusafw.compiler.directio.hive;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.asakusafw.compiler.directio.hive.testing.DualBatch;
import com.asakusafw.compiler.directio.hive.testing.MockInputDescription;
import com.asakusafw.compiler.directio.hive.testing.MockOutputDescription;
import com.asakusafw.compiler.directio.hive.testing.OtherBatch;
import com.asakusafw.compiler.directio.hive.testing.SimpleBatch;
import com.asakusafw.compiler.flow.FlowCompilerOptions;
import com.asakusafw.compiler.flow.Location;
import com.asakusafw.compiler.testing.BatchInfo;
import com.asakusafw.compiler.testing.DirectBatchCompiler;
import com.asakusafw.info.hive.HiveInputInfo;
import com.asakusafw.info.hive.HiveOutputInfo;
import com.asakusafw.info.hive.TableInfo;
import com.asakusafw.vocabulary.batch.BatchDescription;

/**
 * Test for {@link HiveSchemaCollectorProcessor}.
 */
public class HiveSchemaCollectorProcessorTest {

    /**
     * temporary folder for testing.
     */
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    /**
     * simple case.
     * @throws Exception if failed
     */
    @Test
    public void simple() throws Exception {
        File dir = compile(SimpleBatch.class);
        check(dir, new HiveInputInfo[] {
                new MockInputDescription.A().toInfo("i0"),
        });
        check(dir, new HiveOutputInfo[] {
                new MockOutputDescription.A().toInfo("o0"),
        });
    }

    /**
     * multiple inputs/outputs.
     * @throws Exception if failed
     */
    @Test
    public void multiple() throws Exception {
        File dir = compile(DualBatch.class);
        check(dir, new HiveInputInfo[] {
                new MockInputDescription.A().toInfo("i0"),
                new MockInputDescription.B().toInfo("i1"),
        });
        check(dir, new HiveOutputInfo[] {
                new MockOutputDescription.C().toInfo("o0"),
                new MockOutputDescription.D().toInfo("o1"),
        });
    }

    /**
     * other inputs/outputs.
     * @throws Exception if failed
     */
    @Test
    public void other() throws Exception {
        File dir = compile(OtherBatch.class);
        check(dir, new HiveInputInfo[0]);
        check(dir, new HiveOutputInfo[0]);
    }

    private File compile(Class<? extends BatchDescription> batch) throws IOException {
        File output = temporary.newFolder();
        File working = temporary.newFolder();
        BatchInfo info = DirectBatchCompiler.compile(
                batch,
                "com.example.asakusafw.testing",
                Location.fromPath("target/testing", '/'),
                output,
                working,
                Collections.emptyList(),
                HiveSchemaCollectorProcessorTest.class.getClassLoader(),
                new FlowCompilerOptions());
        return info.getOutputDirectory();
    }

    private void check(File base, HiveInputInfo[] elements) throws IOException {
        check(new File(base, HiveSchemaCollectorProcessor.PATH_INPUT), HiveInputInfo.class, Arrays.asList(elements));
    }

    private void check(File base, HiveOutputInfo[] elements) throws IOException {
        check(new File(base, HiveSchemaCollectorProcessor.PATH_OUTPUT), HiveOutputInfo.class, Arrays.asList(elements));
    }

    private <T extends TableInfo.Provider> void check(File file, Class<T> type, List<T> elements) throws IOException {
        List<T> results;
        try (InputStream input = new FileInputStream(file)) {
            results = Persistent.read(type, input);
        }
        Collections.sort(results, (o1, o2) -> o1.getSchema().getName().compareTo(o2.getSchema().getName()));
        assertThat(results, equalTo(elements));
    }
}
