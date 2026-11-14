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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncSettingsDTO;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncClient;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncLimits;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncUrlValidator;
import com.bytechef.automation.configuration.gitsync.service.ProjectGitSyncService;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncImporter;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncLocks;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncPublisher;
import com.bytechef.automation.configuration.gitsync.sync.ProjectGitSyncRemoteFactory;
import com.bytechef.automation.configuration.service.ProjectService;
import com.bytechef.encryption.Encryption;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Settings rules: validation, write-only encrypted token, token re-entry on URL change, location uniqueness.
 */
class ProjectGitSyncFacadeTest {

    private static final long PROJECT_ID = 31L;
    private static final String URL = "https://93.184.216.34/org/repo.git";

    private final Encryption encryption = mock(Encryption.class);
    private final ProjectGitSyncService projectGitSyncService = mock(ProjectGitSyncService.class);
    private ProjectGitSyncFacade projectGitSyncFacade;

    @BeforeEach
    void beforeEach() {
        when(encryption.encrypt(anyString())).thenAnswer(invocation -> "enc(" + invocation.getArgument(0) + ")");
        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.empty());
        when(projectGitSyncService.getProjectGitSyncs(anyString(), anyString())).thenReturn(List.of());
        when(projectGitSyncService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectGitSyncUrlValidator projectGitSyncUrlValidator = new ProjectGitSyncUrlValidator(false);

        projectGitSyncFacade = new ProjectGitSyncFacadeImpl(
            new ProjectGitSyncClient(ProjectGitSyncLimits.DEFAULT), mock(ProjectGitSyncImporter.class),
            new ProjectGitSyncLocks(), mock(ProjectGitSyncPublisher.class),
            new ProjectGitSyncRemoteFactory(encryption, projectGitSyncUrlValidator), projectGitSyncService,
            projectGitSyncUrlValidator, mock(ProjectService.class));
    }

    @Test
    void testSaveEncryptsTokenAndNeverExposesIt() {
        ProjectGitSyncDTO projectGitSyncDTO = projectGitSyncFacade.saveProjectGitSync(
            PROJECT_ID, new ProjectGitSyncSettingsDTO(URL, "main", "/projects/demo/", "bot", "secret-token", true));

        assertThat(projectGitSyncDTO.accessTokenSet()).isTrue();
        assertThat(projectGitSyncDTO.subDirectory()).isEqualTo("projects/demo");
        assertThat(projectGitSyncDTO.toString()).doesNotContain("secret-token");

        verify(projectGitSyncService).save(
            argThat(
                projectGitSync -> "enc(secret-token)".equals(projectGitSync.getAccessToken()) &&
                    !projectGitSync.toString()
                        .contains("secret-token")));
    }

    @Test
    void testPartialUpdateKeepsStoredValues() {
        ProjectGitSync storedProjectGitSync = storedProjectGitSync();

        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.of(storedProjectGitSync));

        ProjectGitSyncDTO projectGitSyncDTO = projectGitSyncFacade.saveProjectGitSync(
            PROJECT_ID, new ProjectGitSyncSettingsDTO(null, "release", null, null, null, false));

        assertThat(projectGitSyncDTO.branch()).isEqualTo("release");
        assertThat(projectGitSyncDTO.enabled()).isFalse();
        assertThat(projectGitSyncDTO.repositoryUrl()).isEqualTo(URL);
        assertThat(storedProjectGitSync.getAccessToken()).isEqualTo("enc(old)");
    }

    @Test
    void testChangingUrlRequiresTokenReentry() {
        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.of(storedProjectGitSync()));

        assertThatThrownBy(() -> projectGitSyncFacade.saveProjectGitSync(
            PROJECT_ID,
            new ProjectGitSyncSettingsDTO("https://93.184.216.35/attacker/repo.git", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access token");

        verify(projectGitSyncService, never()).save(any());
    }

    @Test
    void testSameLocationAsAnotherProjectIsRejected() {
        ProjectGitSync otherProjectGitSync = new ProjectGitSync(99L);

        otherProjectGitSync.setSubDirectory("projects/demo");

        when(projectGitSyncService.getProjectGitSyncs(URL, "main")).thenReturn(List.of(otherProjectGitSync));

        assertThatThrownBy(() -> projectGitSyncFacade.saveProjectGitSync(
            PROJECT_ID, new ProjectGitSyncSettingsDTO(URL, "main", "projects/demo", null, null, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Another project");
    }

    @Test
    void testTraversalSubDirectoryIsRejected() {
        assertThatThrownBy(() -> projectGitSyncFacade.saveProjectGitSync(
            PROJECT_ID, new ProjectGitSyncSettingsDTO(URL, "main", "../outside", null, null, true)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(projectGitSyncService, never()).save(any());
    }

    private static ProjectGitSync storedProjectGitSync() {
        ProjectGitSync projectGitSync = new ProjectGitSync(PROJECT_ID);

        projectGitSync.setAccessToken("enc(old)");
        projectGitSync.setBranch("main");
        projectGitSync.setEnabled(true);
        projectGitSync.setRepositoryUrl(URL);

        return projectGitSync;
    }
}
