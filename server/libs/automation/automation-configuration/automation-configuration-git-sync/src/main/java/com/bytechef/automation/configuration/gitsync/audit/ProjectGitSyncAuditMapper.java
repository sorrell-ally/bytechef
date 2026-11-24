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

import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncStatus;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncConnectionTestResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncPullResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncSettingsDTO;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncRedactor;
import com.bytechef.automation.configuration.gitsync.service.ProjectGitSyncService;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.audit.AuditInvocation;
import com.bytechef.platform.audit.AuditMapper;
import com.bytechef.platform.audit.AuditOutcome;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Records which project changed and the Git location that was saved, removed, pulled, pushed or tested. The access
 * token is never included. Repository URLs are stored without user info, query or fragment, and a deletion that removes
 * nothing is not recorded.
 */
@Component
@ConditionalOnCEVersion
public class ProjectGitSyncAuditMapper implements AuditMapper {

    private final ProjectGitSyncService projectGitSyncService;

    @SuppressFBWarnings("EI")
    public ProjectGitSyncAuditMapper(ProjectGitSyncService projectGitSyncService) {
        this.projectGitSyncService = projectGitSyncService;
    }

    @Override
    public @Nullable Object capture(AuditInvocation auditInvocation) {
        String event = auditInvocation.event();

        if (!capturesBeforeState(event)) {
            return null;
        }

        long projectId = projectId(auditInvocation);

        StoredProjectGitSync storedProjectGitSync = projectGitSyncService.fetchProjectGitSync(projectId)
            .map(ProjectGitSyncAuditMapper::toStoredProjectGitSync)
            .orElse(null);

        return new BeforeState(storedProjectGitSync);
    }

    @Override
    public Map<String, String> map(AuditInvocation auditInvocation) {
        String event = auditInvocation.event();

        if (ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_SAVED.equals(event)) {
            return mapSaved(auditInvocation);
        }

        if (ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED.equals(event)) {
            return mapDeleted(auditInvocation);
        }

        if (ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_PULLED.equals(event)) {
            return mapPulled(auditInvocation);
        }

        if (ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_PUSHED.equals(event)) {
            return mapPushed(auditInvocation);
        }

        if (ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_CONNECTION_TESTED.equals(event)) {
            return mapConnectionTested(auditInvocation);
        }

        throw new IllegalArgumentException("Unsupported Git sync audit event " + event);
    }

    @Override
    public boolean shouldRecord(AuditInvocation auditInvocation) {
        if (!ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED.equals(auditInvocation.event())) {
            return true;
        }

        if (auditInvocation.outcome() != AuditOutcome.SUCCESS) {
            return true;
        }

        return stored(auditInvocation) != null;
    }

    private static boolean capturesBeforeState(String event) {
        return ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_SAVED.equals(event) ||
            ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_DELETED.equals(event) ||
            ProjectGitSyncAuditEvents.PROJECT_GIT_SYNC_CONNECTION_TESTED.equals(event);
    }

    private static Map<String, String> mapConnectionTested(AuditInvocation auditInvocation) {
        Map<String, String> data = new LinkedHashMap<>();

        putProjectId(data, auditInvocation);

        ProjectGitSyncSettingsDTO settingsDTO = settings(auditInvocation);
        StoredProjectGitSync storedProjectGitSync = stored(auditInvocation);

        putLocation(data, repositoryUrl(settingsDTO, storedProjectGitSync), branch(settingsDTO, storedProjectGitSync));

        if (auditInvocation.result() instanceof ProjectGitSyncConnectionTestResultDTO connectionTestResultDTO) {
            data.put("success", String.valueOf(connectionTestResultDTO.success()));
            data.put("branchExists", String.valueOf(connectionTestResultDTO.branchExists()));
            putRedactedMessage(
                data, "errorMessage", connectionTestResultDTO.errorMessage(), accessToken(settingsDTO));
        }

        return data;
    }

    private static Map<String, String> mapDeleted(AuditInvocation auditInvocation) {
        Map<String, String> data = new LinkedHashMap<>();

        putProjectId(data, auditInvocation);

        StoredProjectGitSync storedProjectGitSync = stored(auditInvocation);

        if (storedProjectGitSync == null) {
            return data;
        }

        putSettings(data, storedProjectGitSync);

        return data;
    }

    private static Map<String, String> mapPulled(AuditInvocation auditInvocation) {
        Map<String, String> data = new LinkedHashMap<>();

        putProjectId(data, auditInvocation);

        if (!(auditInvocation.result() instanceof ProjectGitSyncPullResultDTO pullResultDTO)) {
            return data;
        }

        put(data, "commitHash", pullResultDTO.commitHash());
        data.put("addedCount", String.valueOf(pullResultDTO.added()
            .size()));
        data.put("updatedCount", String.valueOf(pullResultDTO.updated()
            .size()));
        data.put("unchangedCount", String.valueOf(pullResultDTO.unchanged()
            .size()));
        data.put("skippedCount", String.valueOf(pullResultDTO.skipped()
            .size()));
        data.put("notInGitCount", String.valueOf(pullResultDTO.notInGit()
            .size()));

        return data;
    }

    private static Map<String, String> mapPushed(AuditInvocation auditInvocation) {
        Map<String, String> data = new LinkedHashMap<>();

        putProjectId(data, auditInvocation);

        if (!(auditInvocation.result() instanceof ProjectGitSyncDTO projectGitSyncDTO)) {
            return data;
        }

        putLocation(data, projectGitSyncDTO.repositoryUrl(), projectGitSyncDTO.branch(),
            projectGitSyncDTO.subDirectory());

        ProjectGitSyncStatus lastSyncStatus = projectGitSyncDTO.lastSyncStatus();

        if (lastSyncStatus != null) {
            data.put("lastSyncStatus", lastSyncStatus.name());
        }

        put(data, "lastSyncCommitHash", projectGitSyncDTO.lastSyncCommitHash());

        Integer lastPushedProjectVersion = projectGitSyncDTO.lastPushedProjectVersion();

        if (lastPushedProjectVersion != null) {
            data.put("lastPushedProjectVersion", String.valueOf(lastPushedProjectVersion));
        }

        putRedactedMessage(data, "lastSyncErrorMessage", projectGitSyncDTO.lastSyncErrorMessage(), null);

        return data;
    }

    private static Map<String, String> mapSaved(AuditInvocation auditInvocation) {
        Map<String, String> data = new LinkedHashMap<>();

        putProjectId(data, auditInvocation);

        if (!(auditInvocation.result() instanceof ProjectGitSyncDTO projectGitSyncDTO)) {
            return data;
        }

        BeforeState beforeState = beforeState(auditInvocation);

        if (beforeState != null) {
            data.put("created", String.valueOf(beforeState.storedProjectGitSync() == null));
        }

        putSettings(
            data, projectGitSyncDTO.repositoryUrl(), projectGitSyncDTO.branch(), projectGitSyncDTO.subDirectory(),
            projectGitSyncDTO.username(), projectGitSyncDTO.enabled(), projectGitSyncDTO.accessTokenSet());

        return data;
    }

    private static @Nullable String accessToken(@Nullable ProjectGitSyncSettingsDTO settingsDTO) {
        if (settingsDTO == null) {
            return null;
        }

        return settingsDTO.accessToken();
    }

    private static @Nullable BeforeState beforeState(AuditInvocation auditInvocation) {
        if (auditInvocation.captured() instanceof BeforeState beforeState) {
            return beforeState;
        }

        return null;
    }

    private static @Nullable String branch(
        @Nullable ProjectGitSyncSettingsDTO settingsDTO, @Nullable StoredProjectGitSync storedProjectGitSync) {

        String requestedBranch = settingsDTO == null ? null : settingsDTO.branch();

        if (requestedBranch != null && !requestedBranch.isBlank()) {
            return requestedBranch.trim();
        }

        if (storedProjectGitSync == null) {
            return null;
        }

        return storedProjectGitSync.branch();
    }

    private static long projectId(AuditInvocation auditInvocation) {
        return (Long) auditInvocation.requireArgument("projectId");
    }

    private static void put(Map<String, String> data, String key, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        data.put(key, value);
    }

    private static void putLocation(
        Map<String, String> data, @Nullable String repositoryUrl, @Nullable String branch,
        @Nullable String subDirectory) {

        put(data, "repositoryUrl", redactRepositoryUrl(repositoryUrl));
        put(data, "branch", branch);
        put(data, "subDirectory", subDirectory);
    }

    private static void putLocation(
        Map<String, String> data, @Nullable String repositoryUrl, @Nullable String branch) {

        putLocation(data, repositoryUrl, branch, null);
    }

    private static void putProjectId(Map<String, String> data, AuditInvocation auditInvocation) {
        data.put("projectId", String.valueOf(projectId(auditInvocation)));
    }

    private static void putRedactedMessage(
        Map<String, String> data, String key, @Nullable String message, @Nullable String secret) {

        if (message == null) {
            return;
        }

        put(data, key, ProjectGitSyncRedactor.redact(message, secret));
    }

    private static void putSettings(Map<String, String> data, StoredProjectGitSync storedProjectGitSync) {
        putSettings(
            data, storedProjectGitSync.repositoryUrl(), storedProjectGitSync.branch(),
            storedProjectGitSync.subDirectory(), storedProjectGitSync.username(), storedProjectGitSync.enabled(),
            storedProjectGitSync.accessTokenConfigured());
    }

    private static void putSettings(
        Map<String, String> data, @Nullable String repositoryUrl, @Nullable String branch,
        @Nullable String subDirectory, @Nullable String username, boolean enabled, boolean accessTokenConfigured) {

        putLocation(data, repositoryUrl, branch, subDirectory);
        put(data, "username", username);
        data.put("enabled", String.valueOf(enabled));
        data.put("accessTokenConfigured", String.valueOf(accessTokenConfigured));
    }

    private static @Nullable String redactRepositoryUrl(@Nullable String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return null;
        }

        URI uri;

        try {
            uri = new URI(repositoryUrl.strip());
        } catch (URISyntaxException uriSyntaxException) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return null;
        }

        try {
            URI redactedUri = new URI(scheme, null, host, uri.getPort(), uri.getPath(), null, null);

            return redactedUri.toString();
        } catch (URISyntaxException uriSyntaxException) {
            return null;
        }
    }

    private static @Nullable String repositoryUrl(
        @Nullable ProjectGitSyncSettingsDTO settingsDTO, @Nullable StoredProjectGitSync storedProjectGitSync) {

        String requestedRepositoryUrl = settingsDTO == null ? null : settingsDTO.repositoryUrl();

        if (requestedRepositoryUrl != null && !requestedRepositoryUrl.isBlank()) {
            return requestedRepositoryUrl;
        }

        if (storedProjectGitSync == null) {
            return null;
        }

        return storedProjectGitSync.repositoryUrl();
    }

    private static @Nullable ProjectGitSyncSettingsDTO settings(AuditInvocation auditInvocation) {
        if (auditInvocation.argument(
            "projectGitSyncSettingsDTO") instanceof ProjectGitSyncSettingsDTO projectGitSyncSettingsDTO) {

            return projectGitSyncSettingsDTO;
        }

        return null;
    }

    private static @Nullable StoredProjectGitSync stored(AuditInvocation auditInvocation) {
        BeforeState beforeState = beforeState(auditInvocation);

        if (beforeState == null) {
            return null;
        }

        return beforeState.storedProjectGitSync();
    }

    private static StoredProjectGitSync toStoredProjectGitSync(ProjectGitSync projectGitSync) {
        return new StoredProjectGitSync(
            projectGitSync.getProjectId(), projectGitSync.getRepositoryUrl(), projectGitSync.getBranch(),
            projectGitSync.getSubDirectory(), projectGitSync.getUsername(), projectGitSync.isEnabled(),
            projectGitSync.hasAccessToken());
    }

    private record BeforeState(@Nullable StoredProjectGitSync storedProjectGitSync) {
    }

    private record StoredProjectGitSync(
        long projectId, String repositoryUrl, String branch, @Nullable String subDirectory, @Nullable String username,
        boolean enabled, boolean accessTokenConfigured) {
    }
}
