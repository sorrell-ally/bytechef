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

package com.bytechef.automation.configuration.gitsync.facade;

import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncConnectionTestResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncPullResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncSettingsDTO;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Project Git sync API: settings, connection test, manual pull into the draft and manual push of the latest published
 * version. Pushing on publish is driven by the publish listener.
 */
public interface ProjectGitSyncFacade {

    void deleteProjectGitSync(long projectId);

    Optional<ProjectGitSyncDTO> fetchProjectGitSync(long projectId);

    List<ProjectGitSyncDTO> getWorkspaceProjectGitSyncs(long workspaceId);

    ProjectGitSyncPullResultDTO pullProjectGitSync(long projectId);

    ProjectGitSyncDTO pushProjectGitSync(long projectId);

    ProjectGitSyncDTO saveProjectGitSync(long projectId, ProjectGitSyncSettingsDTO projectGitSyncSettingsDTO);

    /**
     * Lists the remote branches using the given (unsaved) settings merged over the stored ones. The stored token is
     * only used when the repository URL is unchanged.
     */
    ProjectGitSyncConnectionTestResultDTO testProjectGitSyncConnection(
        long projectId, @Nullable ProjectGitSyncSettingsDTO projectGitSyncSettingsDTO);
}
