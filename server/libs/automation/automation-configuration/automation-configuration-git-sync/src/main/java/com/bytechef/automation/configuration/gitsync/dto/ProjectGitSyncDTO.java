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

import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncDirection;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Read model of a project's Git sync settings and last sync state. The access token is write-only: only whether one is
 * stored is exposed.
 */
public record ProjectGitSyncDTO(
    long projectId, String repositoryUrl, String branch, @Nullable String subDirectory, @Nullable String username,
    boolean accessTokenSet, boolean enabled, @Nullable String lastSyncCommitHash, @Nullable Instant lastSyncDate,
    @Nullable ProjectGitSyncDirection lastSyncDirection, @Nullable ProjectGitSyncStatus lastSyncStatus,
    @Nullable String lastSyncErrorMessage, @Nullable Integer lastPushedProjectVersion) {

    public ProjectGitSyncDTO(ProjectGitSync projectGitSync) {
        this(
            projectGitSync.getProjectId(), projectGitSync.getRepositoryUrl(), projectGitSync.getBranch(),
            projectGitSync.getSubDirectory(), projectGitSync.getUsername(), projectGitSync.hasAccessToken(),
            projectGitSync.isEnabled(), projectGitSync.getLastSyncCommitHash(), projectGitSync.getLastSyncDate(),
            projectGitSync.getLastSyncDirection(), projectGitSync.getLastSyncStatus(),
            projectGitSync.getLastSyncErrorMessage(), projectGitSync.getLastPushedProjectVersion());
    }
}
