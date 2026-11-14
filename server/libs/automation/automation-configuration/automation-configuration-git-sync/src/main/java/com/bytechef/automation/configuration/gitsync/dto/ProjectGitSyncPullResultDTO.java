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

package com.bytechef.automation.configuration.gitsync.dto;

import java.util.List;

/**
 * Summary of a pull into the project's draft version.
 *
 * @param commitHash the commit that was read
 * @param added      workflows created in the draft (uuid kept from the repository)
 * @param updated    draft workflows whose definition was replaced
 * @param unchanged  draft workflows whose definition already matched
 * @param skipped    repository workflows that were not imported, with the reason
 * @param notInGit   draft workflows absent from the repository; they are left untouched
 */
public record ProjectGitSyncPullResultDTO(
    String commitHash, List<ProjectGitSyncWorkflowDTO> added, List<ProjectGitSyncWorkflowDTO> updated,
    List<ProjectGitSyncWorkflowDTO> unchanged, List<ProjectGitSyncWorkflowDTO> skipped,
    List<ProjectGitSyncWorkflowDTO> notInGit) {

    public ProjectGitSyncPullResultDTO {
        added = List.copyOf(added);
        updated = List.copyOf(updated);
        unchanged = List.copyOf(unchanged);
        skipped = List.copyOf(skipped);
        notInGit = List.copyOf(notInGit);
    }

    public boolean hasChanges() {
        return !added.isEmpty() || !updated.isEmpty();
    }
}
