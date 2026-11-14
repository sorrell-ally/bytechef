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

package com.bytechef.automation.configuration.gitsync.listener;

import com.bytechef.automation.configuration.domain.Project;
import com.bytechef.automation.configuration.domain.ProjectVersion;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncRedactor;
import com.bytechef.automation.configuration.gitsync.service.ProjectGitSyncService;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncPublication;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncPublisher;
import com.bytechef.automation.configuration.listener.ProjectGitSyncEventListener;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.tenant.TenantContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pushes a project version to Git when it is published.
 *
 * <p>
 * The listener runs inside the publish transaction, before the project is saved. It only CAPTURES the published version
 * there (reads its workflow definitions, the tenant and the publishing user); the network push is handed to the
 * {@code workerExecutor} after the transaction commits. Rationale:
 * <ul>
 * <li>a push cannot be rolled back, so pushing inside the transaction could leave Git ahead of a publish that later
 * rolls back;</li>
 * <li>a slow or failing remote must neither hold the publish transaction (and its pooled connection) open nor delay or
 * fail the publish response. Spring runs {@code afterCommit} before the transaction's connection is released, so the
 * callback only submits the push; the push, and the {@code REQUIRES_NEW} state update it ends with, run on a worker
 * thread.</li>
 * </ul>
 * Publishing therefore always succeeds; a push failure is recorded in the project's sync state (status {@code FAILED}
 * plus a redacted message), from where the UI can show it and a manual push can retry.
 *
 * <p>
 * Only active in the CE edition, so it never coexists with another {@link ProjectGitSyncEventListener}.
 */
@Component
@ConditionalOnCEVersion
public class ProjectGitSyncPublishListener implements ProjectGitSyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectGitSyncPublishListener.class);

    private final ProjectGitSyncPublisher projectGitSyncPublisher;
    private final ProjectGitSyncService projectGitSyncService;
    private final TaskExecutor workerExecutor;

    @SuppressFBWarnings("EI")
    public ProjectGitSyncPublishListener(
        ProjectGitSyncPublisher projectGitSyncPublisher, ProjectGitSyncService projectGitSyncService,
        @Qualifier("workerExecutor") TaskExecutor workerExecutor) {

        this.projectGitSyncPublisher = projectGitSyncPublisher;
        this.projectGitSyncService = projectGitSyncService;
        this.workerExecutor = workerExecutor;
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void onBeforePublishProject(Project project) {
        long projectId = project.getId();

        Optional<ProjectGitSync> projectGitSyncOptional = projectGitSyncService.fetchProjectGitSync(projectId);

        if (projectGitSyncOptional.isEmpty() || !projectGitSyncOptional.get()
            .isEnabled()) {

            return;
        }

        // project.publish(...) has already marked the version being published as PUBLISHED (in memory)
        ProjectVersion publishedProjectVersion = project.getLastPublishedProjectVersion();

        if (publishedProjectVersion == null) {
            return;
        }

        ProjectGitSyncPublication publication;

        try {
            publication = projectGitSyncPublisher.capture(project, publishedProjectVersion.getVersion());
        } catch (RuntimeException runtimeException) {
            String description = ProjectGitSyncRedactor.describe(runtimeException, null);

            log.warn("Failed to capture project {} for Git sync: {}", projectId, description);

            projectGitSyncPublisher.recordFailure(projectId, "Failed to read the published version: " + description);

            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    submitPush(publication);
                }
            });
        } else {
            submitPush(publication);
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void submitPush(ProjectGitSyncPublication publication) {
        try {
            workerExecutor.execute(
                () -> TenantContext.runWithTenantId(
                    publication.tenantId(), () -> projectGitSyncPublisher.push(publication)));
        } catch (RuntimeException runtimeException) {
            String description = ProjectGitSyncRedactor.describe(runtimeException, null);

            log.warn("Failed to schedule the Git push of project {}: {}", publication.projectId(), description);

            projectGitSyncPublisher.recordFailure(
                publication.projectId(), "Failed to schedule the push: " + description);
        }
    }
}
