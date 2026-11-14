/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytechef.automation.configuration.gitsync.sync;

import com.bytechef.automation.configuration.gitsync.layout.ProjectGitSyncWorkflowFile;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An immutable snapshot of a published project version, captured inside the publish transaction and pushed after it
 * commits.
 */
public record ProjectGitSyncPublication(
    long projectId, String projectUuid, String projectName, @Nullable String projectDescription, int projectVersion,
    @Nullable String publishDescription, @Nullable String publishedBy, String tenantId,
    List<ProjectGitSyncWorkflowFile> workflowFiles) {

    public ProjectGitSyncPublication {
        workflowFiles = List.copyOf(workflowFiles);
    }
}
