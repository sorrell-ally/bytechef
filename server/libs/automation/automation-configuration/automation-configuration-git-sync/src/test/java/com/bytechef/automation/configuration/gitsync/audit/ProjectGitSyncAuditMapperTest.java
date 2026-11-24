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

package com.bytechef.automation.configuration.gitsync.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncStatus;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncConnectionTestResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncPullResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncSettingsDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncWorkflowDTO;
import com.bytechef.automation.configuration.gitsync.service.ProjectGitSyncService;
import com.bytechef.platform.audit.AuditInvocation;
import com.bytechef.platform.audit.AuditOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProjectGitSyncAuditMapperTest {

    private static final String ACCESS_TOKEN = "super-secret-token-value";
    private static final long PROJECT_ID = 31L;
    private static final String REPOSITORY_URL = "https://git.example.com/org/repo.git";
    private static final String WORKFLOW_DEFINITION = "workflow-definition-body";

    private final ProjectGitSyncService projectGitSyncService = mock(ProjectGitSyncService.class);
    private final ProjectGitSyncAuditMapper projectGitSyncAuditMapper =
        new ProjectGitSyncAuditMapper(projectGitSyncService);

    @Test
    void testSaveOfNewSettingsRecordsTheLocationAndThatItWasCreated() {
        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.empty());

        Map<String, String> data = mapSaved(settingsWithSecret(), projectGitSyncDTO(true), captureSaved(null));

        assertThat(data).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "accessTokenConfigured", "true", "branch", "main", "created", "true", "enabled", "true", "projectId",
                String.valueOf(PROJECT_ID), "repositoryUrl", REPOSITORY_URL, "subDirectory", "projects/demo",
                "username", "bot"));
        assertDoesNotRecordSecret(data);
    }

    @Test
    void testSaveOfExistingSettingsRecordsCreatedFalse() {
        Map<String, String> data = mapSaved(settingsWithSecret(), projectGitSyncDTO(true), captureSaved(stored()));

        assertThat(data).containsEntry("created", "false");
        assertThat(data).containsEntry("accessTokenConfigured", "true");
        assertDoesNotRecordSecret(data);
    }

    @Test
    void testFailedSaveRecordsOnlyTheProject() {
        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_SAVED, arguments(settingsWithSecret()), null,
                captureSaved(stored()), AuditOutcome.ERROR));

        assertThat(data).containsExactly(Map.entry("projectId", String.valueOf(PROJECT_ID)));
        assertDoesNotRecordSecret(data);
    }

    @Test
    void testDeleteRecordsTheRemovedSettingsAndNotTheAccessToken() {
        Object captured = capture(ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED, stored());

        AuditInvocation auditInvocation = invocation(
            ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED, arguments(null), null, captured,
            AuditOutcome.SUCCESS);

        assertThat(projectGitSyncAuditMapper.shouldRecord(auditInvocation)).isTrue();

        Map<String, String> data = projectGitSyncAuditMapper.map(auditInvocation);

        assertThat(data).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "accessTokenConfigured", "true", "branch", "main", "enabled", "true", "projectId",
                String.valueOf(PROJECT_ID), "repositoryUrl", REPOSITORY_URL, "subDirectory", "projects/demo",
                "username", "bot"));
        assertDoesNotRecordSecret(data);
    }

    @Test
    void testDeleteOfMissingSettingsIsNotRecorded() {
        Object captured = capture(ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED, null);

        AuditInvocation auditInvocation = invocation(
            ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED, arguments(null), null, captured,
            AuditOutcome.SUCCESS);

        assertThat(captured).isNotNull();
        assertThat(projectGitSyncAuditMapper.shouldRecord(auditInvocation)).isFalse();
    }

    @Test
    void testDeniedDeleteOfMissingSettingsIsRecorded() {
        Object captured = capture(ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED, null);

        AuditInvocation auditInvocation = invocation(
            ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED, arguments(null), null, captured, AuditOutcome.DENIED);

        assertThat(projectGitSyncAuditMapper.shouldRecord(auditInvocation)).isTrue();
        assertThat(projectGitSyncAuditMapper.map(auditInvocation))
            .containsExactly(Map.entry("projectId", String.valueOf(PROJECT_ID)));
    }

    @Test
    void testPullRecordsCountsAndNotWorkflowDefinitions() {
        ProjectGitSyncPullResultDTO pullResultDTO = new ProjectGitSyncPullResultDTO(
            "abc123",
            List.of(new ProjectGitSyncWorkflowDTO("workflow-uuid-added", "Added", WORKFLOW_DEFINITION)),
            List.of(
                new ProjectGitSyncWorkflowDTO("workflow-uuid-updated", "Updated", null),
                new ProjectGitSyncWorkflowDTO("workflow-uuid-updated-again", "Updated again", null)),
            List.of(),
            List.of(new ProjectGitSyncWorkflowDTO("workflow-uuid-skipped", "Skipped", "not a workflow")),
            List.of(
                new ProjectGitSyncWorkflowDTO("workflow-uuid-absent", "Absent", null),
                new ProjectGitSyncWorkflowDTO("workflow-uuid-absent-two", "Absent two", null),
                new ProjectGitSyncWorkflowDTO("workflow-uuid-absent-three", "Absent three", null)));

        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_PULLED, arguments(null), pullResultDTO, null,
                AuditOutcome.SUCCESS));

        assertThat(data).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "addedCount", "1", "commitHash", "abc123", "notInGitCount", "3", "projectId",
                String.valueOf(PROJECT_ID), "skippedCount", "1", "unchangedCount", "0", "updatedCount", "2"));
        assertThat(data.values()).noneMatch(value -> value.contains(WORKFLOW_DEFINITION));
        assertThat(data.values()).noneMatch(value -> value.contains("workflow-uuid"));
    }

    @Test
    void testFailedPullRecordsOnlyTheProject() {
        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_PULLED, arguments(null), null, null, AuditOutcome.ERROR));

        assertThat(data).containsExactly(Map.entry("projectId", String.valueOf(PROJECT_ID)));
    }

    @Test
    void testPushRecordsTheGitOutcomeWithoutTheAccessToken() {
        String errorMessage = "Push failed for https://user:" + ACCESS_TOKEN + "@git.example.com/org/repo.git";

        ProjectGitSyncDTO projectGitSyncDTO = new ProjectGitSyncDTO(
            PROJECT_ID, "https://user:" + ACCESS_TOKEN + "@git.example.com/org/repo.git?token=" + ACCESS_TOKEN, "main",
            "projects/demo", "bot", true, true, null, null, null, ProjectGitSyncStatus.FAILED, errorMessage, null);

        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_PUSHED, arguments(null), projectGitSyncDTO, null,
                AuditOutcome.SUCCESS));

        assertThat(data).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "branch", "main", "lastSyncErrorMessage",
                "Push failed for https://[REDACTED]@git.example.com/org/repo.git", "lastSyncStatus", "FAILED",
                "projectId", String.valueOf(PROJECT_ID), "repositoryUrl", REPOSITORY_URL, "subDirectory",
                "projects/demo"));
        assertDoesNotRecordSecret(data);
    }

    @Test
    void testPushRejectedBeforeItStartsRecordsOnlyTheProject() {
        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_PUSHED, arguments(null), null, null, AuditOutcome.ERROR));

        assertThat(data).containsExactly(Map.entry("projectId", String.valueOf(PROJECT_ID)));
    }

    @Test
    void testSuccessfulPushRecordsTheCommitAndProjectVersion() {
        ProjectGitSyncDTO projectGitSyncDTO = new ProjectGitSyncDTO(
            PROJECT_ID, REPOSITORY_URL, "main", "projects/demo", "bot", true, true, "def456", null, null,
            ProjectGitSyncStatus.SUCCESS, null, 4);

        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_PUSHED, arguments(null), projectGitSyncDTO, null,
                AuditOutcome.SUCCESS));

        assertThat(data).containsEntry("lastSyncCommitHash", "def456");
        assertThat(data).containsEntry("lastPushedProjectVersion", "4");
        assertThat(data).containsEntry("lastSyncStatus", "SUCCESS");
        assertThat(data).doesNotContainKey("lastSyncErrorMessage");
    }

    @Test
    void testConnectionTestRedactsTheRepositoryUrlAndTheAccessToken() {
        String repositoryUrl = "https://user:" + ACCESS_TOKEN + "@git.example.com/org/repo.git?token=" + ACCESS_TOKEN +
            "#fragment";
        ProjectGitSyncSettingsDTO settingsDTO = new ProjectGitSyncSettingsDTO(
            repositoryUrl, " feature ", null, "bot", ACCESS_TOKEN, true);
        String errorMessage = "Authentication failed for " + repositoryUrl + " using " + ACCESS_TOKEN;

        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_CONNECTION_TESTED, arguments(settingsDTO),
                new ProjectGitSyncConnectionTestResultDTO(false, List.of("main", "secret-branch"), false, errorMessage),
                null, AuditOutcome.SUCCESS));

        assertThat(data).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "branch", "feature", "branchExists", "false", "errorMessage",
                "Authentication failed for https://[REDACTED]@git.example.com/org/repo.git?token=[REDACTED]#fragment using [REDACTED]",
                "projectId", String.valueOf(PROJECT_ID), "repositoryUrl", REPOSITORY_URL, "success", "false"));
        assertDoesNotRecordSecret(data);
        assertThat(data.values()).noneMatch(value -> value.contains("secret-branch"));
    }

    @Test
    void testConnectionTestUsesStoredLocationWhenTheRequestOmitsIt() {
        Object captured = capture(ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_CONNECTION_TESTED, stored());

        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_CONNECTION_TESTED,
                arguments(new ProjectGitSyncSettingsDTO(null, " ", null, null, null, null)),
                new ProjectGitSyncConnectionTestResultDTO(true, List.of("main"), true, null), captured,
                AuditOutcome.SUCCESS));

        assertThat(data).containsEntry("repositoryUrl", REPOSITORY_URL);
        assertThat(data).containsEntry("branch", "main");
        assertThat(data).containsEntry("success", "true");
        assertThat(data).containsEntry("branchExists", "true");
        assertThat(data).doesNotContainKeys("errorMessage", "username", "branches");
    }

    @Test
    void testUnparseableRepositoryUrlIsOmitted() {
        ProjectGitSyncSettingsDTO settingsDTO = new ProjectGitSyncSettingsDTO(
            "not a url " + ACCESS_TOKEN, "main", null, null, ACCESS_TOKEN, null);

        Map<String, String> data = projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_CONNECTION_TESTED, arguments(settingsDTO),
                new ProjectGitSyncConnectionTestResultDTO(false, List.of(), false, null), null,
                AuditOutcome.SUCCESS));

        assertThat(data).doesNotContainKey("repositoryUrl");
        assertDoesNotRecordSecret(data);
    }

    private static void assertDoesNotRecordSecret(Map<String, String> data) {
        assertThat(data).doesNotContainKey("accessToken");
        assertThat(data.values()).noneMatch(value -> value.contains(ACCESS_TOKEN));
        assertThat(String.valueOf(data)).doesNotContain(ACCESS_TOKEN);
    }

    private Map<String, @Nullable Object> arguments(@Nullable ProjectGitSyncSettingsDTO settingsDTO) {
        Map<String, @Nullable Object> arguments = new LinkedHashMap<>();

        arguments.put("projectId", PROJECT_ID);
        arguments.put("projectGitSyncSettingsDTO", settingsDTO);

        return arguments;
    }

    private Object capture(String event, @Nullable ProjectGitSync projectGitSync) {
        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.ofNullable(projectGitSync));

        return projectGitSyncAuditMapper.capture(
            invocation(event, arguments(null), null, null, AuditOutcome.SUCCESS));
    }

    private Object captureSaved(@Nullable ProjectGitSync projectGitSync) {
        return capture(ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_SAVED, projectGitSync);
    }

    private static AuditInvocation invocation(
        String event, Map<String, @Nullable Object> arguments, @Nullable Object result, @Nullable Object captured,
        AuditOutcome auditOutcome) {

        Class<? extends Throwable> errorClass = auditOutcome == AuditOutcome.ERROR ? IllegalStateException.class : null;

        return new AuditInvocation(event, arguments, result, captured, auditOutcome, errorClass);
    }

    private Map<String, String> mapSaved(
        ProjectGitSyncSettingsDTO settingsDTO, ProjectGitSyncDTO projectGitSyncDTO, Object captured) {

        return projectGitSyncAuditMapper.map(
            invocation(
                ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_SAVED, arguments(settingsDTO), projectGitSyncDTO, captured,
                AuditOutcome.SUCCESS));
    }

    private static ProjectGitSyncDTO projectGitSyncDTO(boolean accessTokenSet) {
        ProjectGitSync projectGitSync = stored();

        projectGitSync.setAccessToken(accessTokenSet ? "enc(" + ACCESS_TOKEN + ")" : null);

        return new ProjectGitSyncDTO(projectGitSync);
    }

    private static ProjectGitSyncSettingsDTO settingsWithSecret() {
        return new ProjectGitSyncSettingsDTO(
            "https://user:" + ACCESS_TOKEN + "@git.example.com/org/repo.git?token=" + ACCESS_TOKEN, "main",
            "projects/demo", "bot", ACCESS_TOKEN, true);
    }

    private static ProjectGitSync stored() {
        ProjectGitSync projectGitSync = new ProjectGitSync(PROJECT_ID);

        projectGitSync.setRepositoryUrl(REPOSITORY_URL);
        projectGitSync.setBranch("main");
        projectGitSync.setSubDirectory("projects/demo");
        projectGitSync.setUsername("bot");
        projectGitSync.setEnabled(true);
        projectGitSync.setAccessToken("enc(" + ACCESS_TOKEN + ")");

        return projectGitSync;
    }
}
