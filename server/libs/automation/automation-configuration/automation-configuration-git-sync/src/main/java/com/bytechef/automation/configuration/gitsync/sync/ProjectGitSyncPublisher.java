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

import com.bytechef.atlas.configuration.domain.Workflow;
import com.bytechef.atlas.configuration.service.WorkflowService;
import com.bytechef.automation.configuration.domain.Project;
import com.bytechef.automation.configuration.domain.ProjectVersion;
import com.bytechef.automation.configuration.domain.ProjectWorkflow;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncDirection;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncStatus;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncClient;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncException;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncPaths;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncPushResult;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncRedactor;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncRemote;
import com.bytechef.automation.configuration.gitsync.layout.ProjectGitSyncLayout;
import com.bytechef.automation.configuration.gitsync.layout.ProjectGitSyncWorkflowFile;
import com.bytechef.automation.configuration.gitsync.service.ProjectGitSyncService;
import com.bytechef.automation.configuration.service.ProjectWorkflowService;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.security.util.SecurityUtils;
import com.bytechef.tenant.TenantContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures a published project version and pushes it to the project's configured branch.
 */
@Component
@ConditionalOnCEVersion
public class ProjectGitSyncPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProjectGitSyncPublisher.class);

    private final String authorEmail;
    private final String authorName;
    private final ProjectGitSyncClient projectGitSyncClient;
    private final ProjectGitSyncLocks projectGitSyncLocks;
    private final ProjectGitSyncRemoteFactory projectGitSyncRemoteFactory;
    private final ProjectGitSyncService projectGitSyncService;
    private final ProjectWorkflowService projectWorkflowService;
    private final WorkflowService workflowService;

    @SuppressFBWarnings("EI")
    public ProjectGitSyncPublisher(
        @Value("${bytechef.project-git-sync.author-email:noreply@bytechef.io}") String authorEmail,
        @Value("${bytechef.project-git-sync.author-name:ByteChef}") String authorName,
        ProjectGitSyncClient projectGitSyncClient, ProjectGitSyncLocks projectGitSyncLocks,
        ProjectGitSyncRemoteFactory projectGitSyncRemoteFactory, ProjectGitSyncService projectGitSyncService,
        ProjectWorkflowService projectWorkflowService, WorkflowService workflowService) {

        this.authorEmail = authorEmail;
        this.authorName = authorName;
        this.projectGitSyncClient = projectGitSyncClient;
        this.projectGitSyncLocks = projectGitSyncLocks;
        this.projectGitSyncRemoteFactory = projectGitSyncRemoteFactory;
        this.projectGitSyncService = projectGitSyncService;
        this.projectWorkflowService = projectWorkflowService;
        this.workflowService = workflowService;
    }

    /**
     * Reads the workflows of a project version. Called inside the publish transaction (or a read transaction for a
     * manual push), so the snapshot is consistent with what was published.
     */
    @Transactional(readOnly = true)
    public ProjectGitSyncPublication capture(Project project, int projectVersion) {
        List<ProjectGitSyncWorkflowFile> workflowFiles = new ArrayList<>();

        for (ProjectWorkflow projectWorkflow : projectWorkflowService.getProjectWorkflows(
            project.getId(), projectVersion)) {

            Workflow workflow = workflowService.getWorkflow(projectWorkflow.getWorkflowId());

            workflowFiles.add(
                new ProjectGitSyncWorkflowFile(
                    projectWorkflow.getUuidAsString(), workflow.getLabel(), workflow.getDefinition()));
        }

        Optional<ProjectVersion> projectVersionOptional = project.fetchProjectVersion(projectVersion);

        String publishDescription = projectVersionOptional.map(ProjectVersion::getDescription)
            .orElse(null);

        return new ProjectGitSyncPublication(
            project.getId(), project.getUuid(), project.getName(), project.getDescription(), projectVersion,
            publishDescription, SecurityUtils.fetchCurrentUserLogin()
                .orElse(null),
            TenantContext.getCurrentTenantId(), workflowFiles);
    }

    /**
     * Pushes a captured version. Never throws: the outcome, including any failure, is recorded as the project's sync
     * state, because this runs after the publish transaction committed and must not surface as a publish error.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void push(ProjectGitSyncPublication publication) {
        long projectId = publication.projectId();

        try {
            projectGitSyncLocks.withLock(projectId, () -> {
                pushLocked(publication);

                return null;
            });
        } catch (ProjectGitSyncException | IllegalArgumentException exception) {
            log.warn("Git push of project {} failed: {}", projectId, exception.getMessage());

            recordFailure(projectId, exception.getMessage());
        } catch (RuntimeException runtimeException) {
            String description = ProjectGitSyncRedactor.describe(runtimeException, null);

            log.warn("Git push of project {} failed unexpectedly: {}", projectId, description);

            recordFailure(projectId, "Unexpected error: " + description);
        }
    }

    /**
     * Records a failure to capture or push a version as the project's sync state.
     */
    public void recordFailure(long projectId, @Nullable String errorMessage) {
        projectGitSyncService.updateSyncState(
            projectId, null, ProjectGitSyncDirection.PUSH, ProjectGitSyncStatus.FAILED, errorMessage, null);
    }

    private void pushLocked(ProjectGitSyncPublication publication) {
        long projectId = publication.projectId();

        Optional<ProjectGitSync> projectGitSyncOptional = projectGitSyncService.fetchProjectGitSync(projectId);

        if (projectGitSyncOptional.isEmpty() || !projectGitSyncOptional.get()
            .isEnabled()) {

            return;
        }

        ProjectGitSync projectGitSync = projectGitSyncOptional.get();

        Integer lastPushedProjectVersion = projectGitSync.getLastPushedProjectVersion();

        if (lastPushedProjectVersion != null && publication.projectVersion() < lastPushedProjectVersion) {
            projectGitSyncService.updateSyncState(
                projectId, null, ProjectGitSyncDirection.PUSH, ProjectGitSyncStatus.SKIPPED,
                "Version " + publication.projectVersion() + " was not pushed because version " +
                    lastPushedProjectVersion + " was already pushed",
                null);

            return;
        }

        ProjectGitSyncRemote remote = projectGitSyncRemoteFactory.create(projectGitSync);

        String projectDirectory = ProjectGitSyncPaths.normalizeSubDirectory(projectGitSync.getSubDirectory());

        Map<String, String> files = ProjectGitSyncLayout.toFiles(
            publication.projectUuid(), publication.projectName(), publication.projectDescription(),
            publication.workflowFiles());

        ProjectGitSyncPushResult pushResult = projectGitSyncClient.push(
            remote, projectDirectory, files, ProjectGitSyncLayout::isManagedFile, buildCommitMessage(publication),
            new PersonIdent(authorName, authorEmail));

        projectGitSyncService.updateSyncState(
            projectId, pushResult.commitHash(), ProjectGitSyncDirection.PUSH,
            pushResult.changed() ? ProjectGitSyncStatus.SUCCESS : ProjectGitSyncStatus.NO_CHANGES, null,
            publication.projectVersion());

        log.info(
            "Project {} version {} synced to Git (commit {}, changed={})", projectId, publication.projectVersion(),
            pushResult.commitHash(), pushResult.changed());
    }

    static String buildCommitMessage(ProjectGitSyncPublication publication) {
        StringBuilder message = new StringBuilder();

        message.append("Publish ")
            .append(singleLine(publication.projectName()))
            .append(" version ")
            .append(publication.projectVersion())
            .append('\n');

        String publishDescription = publication.publishDescription();

        if (publishDescription != null && !publishDescription.isBlank()) {
            message.append('\n')
                .append(publishDescription.strip())
                .append('\n');
        }

        message.append('\n')
            .append("Project-Uuid: ")
            .append(publication.projectUuid())
            .append('\n')
            .append("Project-Version: ")
            .append(publication.projectVersion())
            .append('\n');

        String publishedBy = publication.publishedBy();

        if (publishedBy != null && !publishedBy.isBlank()) {
            message.append("Published-By: ")
                .append(singleLine(publishedBy))
                .append('\n');
        }

        return message.toString();
    }

    private static String singleLine(String text) {
        return text.replaceAll("[\\r\\n]+", " ")
            .strip();
    }
}
