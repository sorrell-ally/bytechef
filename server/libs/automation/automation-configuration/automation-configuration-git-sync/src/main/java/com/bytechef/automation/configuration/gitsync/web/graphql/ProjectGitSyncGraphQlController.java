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

package com.bytechef.automation.configuration.gitsync.web.graphql;

import com.bytechef.atlas.coordinator.annotation.ConditionalOnCoordinator;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncConnectionTestResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncPullResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncSettingsDTO;
import com.bytechef.automation.configuration.gitsync.facade.ProjectGitSyncFacade;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL API of project Git sync ({@code graphql/project-git-sync.graphqls}). Authorization is enforced by
 * {@link ProjectGitSyncFacade}.
 */
@Controller
@ConditionalOnCEVersion
@ConditionalOnCoordinator
public class ProjectGitSyncGraphQlController {

    private final ProjectGitSyncFacade projectGitSyncFacade;

    @SuppressFBWarnings("EI")
    public ProjectGitSyncGraphQlController(ProjectGitSyncFacade projectGitSyncFacade) {
        this.projectGitSyncFacade = projectGitSyncFacade;
    }

    @MutationMapping(name = "deleteProjectGitSync")
    public boolean deleteProjectGitSync(@Argument long projectId) {
        projectGitSyncFacade.deleteProjectGitSync(projectId);

        return true;
    }

    @QueryMapping(name = "projectGitSync")
    public @Nullable ProjectGitSyncDTO projectGitSync(@Argument long projectId) {
        return projectGitSyncFacade.fetchProjectGitSync(projectId)
            .orElse(null);
    }

    @MutationMapping(name = "pullProjectGitSync")
    public ProjectGitSyncPullResultDTO pullProjectGitSync(@Argument long projectId) {
        return projectGitSyncFacade.pullProjectGitSync(projectId);
    }

    @MutationMapping(name = "pushProjectGitSync")
    public ProjectGitSyncDTO pushProjectGitSync(@Argument long projectId) {
        return projectGitSyncFacade.pushProjectGitSync(projectId);
    }

    @MutationMapping(name = "saveProjectGitSync")
    public ProjectGitSyncDTO saveProjectGitSync(
        @Argument long projectId, @Argument ProjectGitSyncSettingsDTO input) {

        return projectGitSyncFacade.saveProjectGitSync(projectId, input);
    }

    @MutationMapping(name = "testProjectGitSyncConnection")
    public ProjectGitSyncConnectionTestResultDTO testProjectGitSyncConnection(
        @Argument long projectId, @Argument @Nullable ProjectGitSyncSettingsDTO input) {

        return projectGitSyncFacade.testProjectGitSyncConnection(projectId, input);
    }

    @QueryMapping(name = "workspaceProjectGitSyncs")
    public List<ProjectGitSyncDTO> workspaceProjectGitSyncs(@Argument long workspaceId) {
        return projectGitSyncFacade.getWorkspaceProjectGitSyncs(workspaceId);
    }
}
