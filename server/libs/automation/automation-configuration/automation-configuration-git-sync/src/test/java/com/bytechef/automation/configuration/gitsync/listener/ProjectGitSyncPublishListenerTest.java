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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.automation.configuration.domain.Project;
import com.bytechef.automation.configuration.domain.ProjectVersion;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.service.ProjectGitSyncService;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncPublication;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncPublisher;
import com.bytechef.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The listener captures inside the publish transaction and hands the push to the worker executor only after commit,
 * with the publishing tenant restored on the worker thread.
 */
class ProjectGitSyncPublishListenerTest {

    private static final long PROJECT_ID = 11L;

    private final List<Runnable> submittedTasks = new ArrayList<>();
    private final TaskExecutor recordingTaskExecutor = task -> submittedTasks.add(task);
    private final ProjectGitSyncPublisher projectGitSyncPublisher = mock(ProjectGitSyncPublisher.class);
    private final ProjectGitSyncService projectGitSyncService = mock(ProjectGitSyncService.class);
    private Project project;
    private ProjectGitSyncPublication publication;

    @BeforeEach
    void beforeEach() {
        project = new Project();

        project.setId(PROJECT_ID);
        project.setProjectVersions(
            new ArrayList<>(
                List.of(
                    new ProjectVersion(1, ProjectVersion.Status.PUBLISHED.ordinal(), null, "v1"),
                    new ProjectVersion(2))));

        publication = new ProjectGitSyncPublication(
            PROJECT_ID, "55555555-5555-5555-5555-555555555555", "Demo", null, 1, "v1", "admin", "tenant_a",
            List.of());

        ProjectGitSync projectGitSync = new ProjectGitSync(PROJECT_ID);

        projectGitSync.setEnabled(true);

        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.of(projectGitSync));
        when(projectGitSyncPublisher.capture(any(), anyInt())).thenReturn(publication);
    }

    @AfterEach
    void afterEach() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }

        TenantContext.resetCurrentTenantId();
    }

    @Test
    void testPushIsSubmittedOnlyAfterCommitWithTenant() {
        TransactionSynchronizationManager.initSynchronization();

        ProjectGitSyncPublishListener projectGitSyncPublishListener = new ProjectGitSyncPublishListener(
            projectGitSyncPublisher, projectGitSyncService, recordingTaskExecutor);

        projectGitSyncPublishListener.onBeforePublishProject(project);

        verify(projectGitSyncPublisher).capture(project, 1);
        assertThat(submittedTasks).isEmpty();

        List<TransactionSynchronization> transactionSynchronizations =
            TransactionSynchronizationManager.getSynchronizations();

        assertThat(transactionSynchronizations).hasSize(1);

        transactionSynchronizations.getFirst()
            .afterCommit();

        assertThat(submittedTasks).hasSize(1);
        verify(projectGitSyncPublisher, never()).push(any());

        AtomicReference<String> tenantIdReference = new AtomicReference<>();

        doAnswer(invocation -> {
            tenantIdReference.set(TenantContext.getCurrentTenantId());

            return null;
        }).when(projectGitSyncPublisher)
            .push(publication);

        submittedTasks.getFirst()
            .run();

        verify(projectGitSyncPublisher).push(publication);
        assertThat(tenantIdReference.get()).isEqualTo("tenant_a");
    }

    @Test
    void testRolledBackPublishPushesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        new ProjectGitSyncPublishListener(projectGitSyncPublisher, projectGitSyncService, recordingTaskExecutor)
            .onBeforePublishProject(project);

        for (TransactionSynchronization transactionSynchronization : TransactionSynchronizationManager
            .getSynchronizations()) {

            transactionSynchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertThat(submittedTasks).isEmpty();
        verify(projectGitSyncPublisher, never()).push(any());
    }

    @Test
    void testDisabledSettingsCaptureNothing() {
        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.empty());

        new ProjectGitSyncPublishListener(projectGitSyncPublisher, projectGitSyncService, recordingTaskExecutor)
            .onBeforePublishProject(project);

        verify(projectGitSyncPublisher, never()).capture(any(), anyInt());
        assertThat(submittedTasks).isEmpty();
    }

    @Test
    void testRejectedSubmissionIsRecordedAsFailure() {
        TaskExecutor rejectingTaskExecutor = task -> {
            throw new TaskRejectedException("executor shut down");
        };

        new ProjectGitSyncPublishListener(projectGitSyncPublisher, projectGitSyncService, rejectingTaskExecutor)
            .onBeforePublishProject(project);

        verify(projectGitSyncPublisher).recordFailure(eq(PROJECT_ID), anyString());
    }
}
