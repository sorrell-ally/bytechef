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

import com.bytechef.automation.configuration.domain.Project;
import com.bytechef.automation.configuration.domain.ProjectVersion;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncDirection;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncStatus;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncConnectionTestResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncPullResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncSettingsDTO;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncClient;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncException;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncPaths;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncRedactor;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncRemote;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncSnapshot;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncUrlValidator;
import com.bytechef.automation.configuration.gitsync.layout.ProjectGitSyncLayout;
import com.bytechef.automation.configuration.gitsync.service.ProjectGitSyncService;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncImporter;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncLocks;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncPublication;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncPublisher;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncRemoteFactory;
import com.bytechef.automation.configuration.service.ProjectService;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Remote Git I/O never runs inside a database transaction: settings are read, the remote is contacted, and only the
 * import (pull) or the state update then opens its own transaction.
 */
@Service
@ConditionalOnCEVersion
public class ProjectGitSyncFacadeImpl implements ProjectGitSyncFacade {

    private final ProjectGitSyncClient projectGitSyncClient;
    private final ProjectGitSyncImporter projectGitSyncImporter;
    private final ProjectGitSyncLocks projectGitSyncLocks;
    private final ProjectGitSyncPublisher projectGitSyncPublisher;
    private final ProjectGitSyncRemoteFactory projectGitSyncRemoteFactory;
    private final ProjectGitSyncService projectGitSyncService;
    private final ProjectGitSyncUrlValidator projectGitSyncUrlValidator;
    private final ProjectService projectService;

    @SuppressFBWarnings("EI")
    public ProjectGitSyncFacadeImpl(
        ProjectGitSyncClient projectGitSyncClient, ProjectGitSyncImporter projectGitSyncImporter,
        ProjectGitSyncLocks projectGitSyncLocks, ProjectGitSyncPublisher projectGitSyncPublisher,
        ProjectGitSyncRemoteFactory projectGitSyncRemoteFactory, ProjectGitSyncService projectGitSyncService,
        ProjectGitSyncUrlValidator projectGitSyncUrlValidator, ProjectService projectService) {

        this.projectGitSyncClient = projectGitSyncClient;
        this.projectGitSyncImporter = projectGitSyncImporter;
        this.projectGitSyncLocks = projectGitSyncLocks;
        this.projectGitSyncPublisher = projectGitSyncPublisher;
        this.projectGitSyncRemoteFactory = projectGitSyncRemoteFactory;
        this.projectGitSyncService = projectGitSyncService;
        this.projectGitSyncUrlValidator = projectGitSyncUrlValidator;
        this.projectService = projectService;
    }

    @Override
    @PreAuthorize("hasPermission(#projectId, 'Project', 'PROJECT_SETTINGS')")
    public void deleteProjectGitSync(long projectId) {
        projectGitSyncService.deleteByProjectId(projectId);
    }

    @Override
    @PreAuthorize("hasPermission(#projectId, 'Project', 'WORKFLOW_VIEW')")
    public Optional<ProjectGitSyncDTO> fetchProjectGitSync(long projectId) {
        return projectGitSyncService.fetchProjectGitSync(projectId)
            .map(ProjectGitSyncDTO::new);
    }

    @Override
    @PreAuthorize("hasPermission(#workspaceId, 'Workspace', 'WORKFLOW_VIEW')")
    public List<ProjectGitSyncDTO> getWorkspaceProjectGitSyncs(long workspaceId) {
        List<Long> projectIds = projectService.getWorkspaceProjectIds(workspaceId);

        return projectGitSyncService.getProjectGitSyncs(projectIds)
            .stream()
            .map(ProjectGitSyncDTO::new)
            .toList();
    }

    @Override
    @PreAuthorize("hasPermission(#projectId, 'Project', 'WORKFLOW_EDIT')")
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public ProjectGitSyncPullResultDTO pullProjectGitSync(long projectId) {
        ProjectGitSync projectGitSync = getEnabledProjectGitSync(projectId);

        try {
            return projectGitSyncLocks.withLock(projectId, () -> pullLocked(projectId, projectGitSync));
        } catch (RuntimeException runtimeException) {
            String errorMessage = runtimeException instanceof ProjectGitSyncException ||
                runtimeException instanceof IllegalArgumentException
                    ? runtimeException.getMessage()
                    : ProjectGitSyncRedactor.describe(runtimeException, null);

            projectGitSyncService.updateSyncState(
                projectId, null, ProjectGitSyncDirection.PULL, ProjectGitSyncStatus.FAILED, errorMessage, null);

            throw runtimeException;
        }
    }

    @Override
    @PreAuthorize("hasPermission(#projectId, 'Project', 'WORKFLOW_EDIT') and " +
        "hasPermission(#projectId, 'Project', 'DEPLOYMENT_PUSH')")
    public ProjectGitSyncDTO pushProjectGitSync(long projectId) {
        getEnabledProjectGitSync(projectId);

        Project project = projectService.getProject(projectId);

        ProjectVersion lastPublishedProjectVersion = project.getLastPublishedProjectVersion();

        if (lastPublishedProjectVersion == null) {
            throw new IllegalArgumentException("The project has no published version to push");
        }

        ProjectGitSyncPublication publication = projectGitSyncPublisher.capture(
            project, lastPublishedProjectVersion.getVersion());

        projectGitSyncPublisher.push(publication);

        return new ProjectGitSyncDTO(getProjectGitSync(projectId));
    }

    @Override
    @PreAuthorize("hasPermission(#projectId, 'Project', 'PROJECT_SETTINGS')")
    public ProjectGitSyncDTO saveProjectGitSync(long projectId, ProjectGitSyncSettingsDTO projectGitSyncSettingsDTO) {
        projectService.getProject(projectId);

        ProjectGitSync existingProjectGitSync = projectGitSyncService.fetchProjectGitSync(projectId)
            .orElse(null);

        ProjectGitSync projectGitSync = existingProjectGitSync == null
            ? new ProjectGitSync(projectId) : existingProjectGitSync;

        String repositoryUrl = projectGitSyncSettingsDTO.repositoryUrl() == null
            ? projectGitSync.getRepositoryUrl()
            : projectGitSyncUrlValidator.validate(projectGitSyncSettingsDTO.repositoryUrl());

        if (repositoryUrl == null) {
            throw new IllegalArgumentException("Repository URL is required");
        }

        String branch = ProjectGitSyncPaths.validateBranch(
            projectGitSyncSettingsDTO.branch() == null ? projectGitSync.getBranch()
                : projectGitSyncSettingsDTO.branch());

        String subDirectory = projectGitSyncSettingsDTO.subDirectory() == null
            ? projectGitSync.getSubDirectory()
            : ProjectGitSyncPaths.normalizeSubDirectory(projectGitSyncSettingsDTO.subDirectory());

        String accessToken = projectGitSyncSettingsDTO.accessToken();
        boolean repositoryUrlChanged = existingProjectGitSync != null &&
            !Objects.equals(existingProjectGitSync.getRepositoryUrl(), repositoryUrl);

        if (repositoryUrlChanged && accessToken == null && existingProjectGitSync.hasAccessToken()) {
            // the stored token must never be sent to a host it was not entered for
            throw new IllegalArgumentException(
                "Re-enter the access token (or send an empty one to clear it) when changing the repository URL");
        }

        checkLocationNotUsedByAnotherProject(projectId, repositoryUrl, branch, subDirectory);

        projectGitSync.setRepositoryUrl(repositoryUrl);
        projectGitSync.setBranch(branch);
        projectGitSync.setSubDirectory(emptyToNull(subDirectory));

        if (projectGitSyncSettingsDTO.username() != null) {
            projectGitSync.setUsername(emptyToNull(projectGitSyncSettingsDTO.username()));
        }

        if (accessToken != null) {
            projectGitSync.setAccessToken(
                accessToken.isBlank() ? null : projectGitSyncRemoteFactory.encrypt(accessToken.trim()));
        }

        Boolean enabled = projectGitSyncSettingsDTO.enabled();

        if (enabled != null) {
            projectGitSync.setEnabled(enabled);
        }

        return new ProjectGitSyncDTO(projectGitSyncService.save(projectGitSync));
    }

    @Override
    @PreAuthorize("hasPermission(#projectId, 'Project', 'PROJECT_SETTINGS')")
    public ProjectGitSyncConnectionTestResultDTO testProjectGitSyncConnection(
        long projectId, @Nullable ProjectGitSyncSettingsDTO projectGitSyncSettingsDTO) {

        ProjectGitSync storedProjectGitSync = projectGitSyncService.fetchProjectGitSync(projectId)
            .orElse(null);

        ProjectGitSyncSettingsDTO settingsDTO = projectGitSyncSettingsDTO == null
            ? new ProjectGitSyncSettingsDTO(null, null, null, null, null, null) : projectGitSyncSettingsDTO;

        String rawRepositoryUrl = settingsDTO.repositoryUrl() == null && storedProjectGitSync != null
            ? storedProjectGitSync.getRepositoryUrl() : settingsDTO.repositoryUrl();

        String repositoryUrl = projectGitSyncUrlValidator.validate(rawRepositoryUrl);

        String rawBranch = settingsDTO.branch() == null && storedProjectGitSync != null
            ? storedProjectGitSync.getBranch() : settingsDTO.branch();

        String branch = rawBranch == null || rawBranch.isBlank() ? null : ProjectGitSyncPaths.validateBranch(rawBranch);

        String username = settingsDTO.username() == null && storedProjectGitSync != null
            ? storedProjectGitSync.getUsername() : emptyToNull(settingsDTO.username());

        String accessToken = settingsDTO.accessToken();

        if (accessToken == null && storedProjectGitSync != null &&
            Objects.equals(storedProjectGitSync.getRepositoryUrl(), repositoryUrl)) {

            accessToken = projectGitSyncRemoteFactory.decrypt(storedProjectGitSync.getAccessToken());
        }

        ProjectGitSyncRemote remote = new ProjectGitSyncRemote(
            repositoryUrl, branch == null ? "" : branch, username, emptyToNull(accessToken));

        try {
            List<String> branches = projectGitSyncClient.listBranches(remote);

            return new ProjectGitSyncConnectionTestResultDTO(
                true, branches, branch != null && branches.contains(branch), null);
        } catch (ProjectGitSyncException projectGitSyncException) {
            return new ProjectGitSyncConnectionTestResultDTO(
                false, List.of(), false, projectGitSyncException.getMessage());
        }
    }

    private void checkLocationNotUsedByAnotherProject(
        long projectId, String repositoryUrl, String branch, @Nullable String subDirectory) {

        String normalizedSubDirectory = ProjectGitSyncPaths.normalizeSubDirectory(subDirectory);

        for (ProjectGitSync otherProjectGitSync : projectGitSyncService.getProjectGitSyncs(repositoryUrl, branch)) {
            if (otherProjectGitSync.getProjectId() == projectId) {
                continue;
            }

            String otherSubDirectory = ProjectGitSyncPaths.normalizeSubDirectory(
                otherProjectGitSync.getSubDirectory());

            if (normalizedSubDirectory.equals(otherSubDirectory)) {
                throw new IllegalArgumentException(
                    "Another project already syncs to this repository, branch and directory");
            }
        }
    }

    private ProjectGitSync getEnabledProjectGitSync(long projectId) {
        ProjectGitSync projectGitSync = getProjectGitSync(projectId);

        if (!projectGitSync.isEnabled()) {
            throw new IllegalArgumentException("Git sync is disabled for this project");
        }

        return projectGitSync;
    }

    private ProjectGitSync getProjectGitSync(long projectId) {
        return projectGitSyncService.fetchProjectGitSync(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Git sync is not configured for this project"));
    }

    private ProjectGitSyncPullResultDTO pullLocked(long projectId, ProjectGitSync projectGitSync) {
        ProjectGitSyncRemote remote = projectGitSyncRemoteFactory.create(projectGitSync);

        String projectDirectory = ProjectGitSyncPaths.normalizeSubDirectory(projectGitSync.getSubDirectory());

        ProjectGitSyncSnapshot snapshot = projectGitSyncClient.read(
            remote, projectDirectory, ProjectGitSyncLayout::isWorkflowFile);

        if (!snapshot.directoryFound()) {
            throw new ProjectGitSyncException(
                "Directory '" + projectDirectory + "' does not exist on branch '" + remote.branch() + "'");
        }

        ProjectGitSyncPullResultDTO pullResultDTO = projectGitSyncImporter.importSnapshot(projectId, snapshot);

        projectGitSyncService.updateSyncState(
            projectId, snapshot.commitHash(), ProjectGitSyncDirection.PULL,
            pullResultDTO.hasChanges() ? ProjectGitSyncStatus.SUCCESS : ProjectGitSyncStatus.NO_CHANGES, null, null);

        return pullResultDTO;
    }

    private static @Nullable String emptyToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
