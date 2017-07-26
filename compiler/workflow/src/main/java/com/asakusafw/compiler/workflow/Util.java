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

import java.util.ArrayList;
import java.util.List;

import com.asakusafw.compiler.flow.ExternalIoCommandProvider.CommandContext;
import com.asakusafw.workflow.model.CommandToken;

final class Util {

    private static final String PH_HOME = "{{__PH__:HOME}}"; //$NON-NLS-1$

    private static final String PH_EXECUTION_ID = "{{__PH__:EXECUTION_ID}}"; //$NON-NLS-1$

    private static final String PH_BATCH_ARGUMENTS = "{{__PH__:BATCH_ARGUMENTS}}"; //$NON-NLS-1$

    private Util() {
        return;
    }

    public static CommandContext createMockCommandContext() {
        return new CommandContext(PH_HOME, PH_EXECUTION_ID, PH_BATCH_ARGUMENTS);
    }

    public static String resolveCommand(String file) {
        if (file.startsWith(PH_HOME) == false) {
            return file;
        }
        return file.substring(PH_HOME.length());
    }

    public static List<CommandToken> resolveArguments(List<String> tokens) {
        List<CommandToken> results = new ArrayList<>();
        for (String token : tokens) {
            if (isPlaceholder(token, PH_EXECUTION_ID)) {
                results.add(CommandToken.EXECUTION_ID);
            } else if (isPlaceholder(token, PH_BATCH_ARGUMENTS)) {
                results.add(CommandToken.BATCH_ARGUMENTS);
            } else if (isPlaceholder(token, PH_HOME)) {
                throw new IllegalStateException(token);
            } else {
                results.add(CommandToken.of(token));
            }
        }
        return results;
    }

    private static boolean isPlaceholder(String token, String placeholder) {
        if (token.equals(placeholder)) {
            return true;
        }
        if (token.indexOf(placeholder) >= 0) {
            throw new IllegalStateException(token);
        }
        return false;
    }
}
