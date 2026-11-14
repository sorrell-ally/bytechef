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

package com.bytechef.automation.configuration.gitsync.service;

import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncDirection;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncStatus;
import com.bytechef.automation.configuration.gitsync.repository.ProjectGitSyncRepository;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence of project Git sync settings and sync state. Tokens arrive here already encrypted.
 */
@Service
@ConditionalOnCEVersion
@Transactional
public class ProjectGitSyncService {

    private final ProjectGitSyncRepository projectGitSyncRepository;

    @SuppressFBWarnings("EI")
    public ProjectGitSyncService(ProjectGitSyncRepository projectGitSyncRepository) {
        this.projectGitSyncRepository = projectGitSyncRepository;
    }

    public void deleteByProjectId(long projectId) {
        projectGitSyncRepository.findByProjectId(projectId)
            .ifPresent(projectGitSyncRepository::delete);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectGitSync> fetchProjectGitSync(long projectId) {
        return projectGitSyncRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectGitSync> getProjectGitSyncs(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }

        return projectGitSyncRepository.findAllByProjectIdIn(projectIds);
    }

    @Transactional(readOnly = true)
    public List<ProjectGitSync> getProjectGitSyncs(String repositoryUrl, String branch) {
        return projectGitSyncRepository.findAllByRepositoryUrlAndBranch(repositoryUrl, branch);
    }

    public ProjectGitSync save(ProjectGitSync projectGitSync) {
        return projectGitSyncRepository.save(projectGitSync);
    }

    /**
     * Records the outcome of a sync in its own transaction, so it is persisted even when called after the publish
     * transaction has committed or when the surrounding operation rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSyncState(
        long projectId, @Nullable String commitHash, ProjectGitSyncDirection direction, ProjectGitSyncStatus status,
        @Nullable String errorMessage, @Nullable Integer pushedProjectVersion) {

        projectGitSyncRepository.updateSyncState(
            projectId, commitHash, Instant.now(), direction.ordinal(), status.ordinal(), errorMessage,
            pushedProjectVersion);
    }
}
