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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.atlas.configuration.service.WorkflowService;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncDirection;
import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSyncStatus;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncClient;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncLimits;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncRemote;
import com.bytechef.automation.configuration.gitsync.layout.ProjectGitSyncWorkflowFile;
import com.bytechef.automation.configuration.gitsync.service.ProjectGitSyncService;
import com.bytechef.automation.configuration.service.ProjectWorkflowService;
import com.bytechef.test.extension.ObjectMapperSetupExtension;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Publish path: a captured version is pushed to a local bare repository and the outcome is recorded.
 */
@ExtendWith(ObjectMapperSetupExtension.class)
class ProjectGitSyncPublisherTest {

    private static final String ACCESS_TOKEN = "ghp_PublisherSecretToken987";
    private static final long PROJECT_ID = 7L;
    private static final String WORKFLOW_UUID = "33333333-3333-3333-3333-333333333333";

    @TempDir
    private Path temporaryDirectory;

    private File bareRepositoryDirectory;
    private ProjectGitSync projectGitSync;
    private ProjectGitSyncPublisher projectGitSyncPublisher;
    private final ProjectGitSyncRemoteFactory projectGitSyncRemoteFactory = mock(ProjectGitSyncRemoteFactory.class);
    private final ProjectGitSyncService projectGitSyncService = mock(ProjectGitSyncService.class);

    @BeforeEach
    void beforeEach() throws Exception {
        bareRepositoryDirectory = temporaryDirectory.resolve("remote.git")
            .toFile();

        try (Git git = Git.init()
            .setBare(true)
            .setInitialBranch("main")
            .setDirectory(bareRepositoryDirectory)
            .call()) {

            assertThat(git.getRepository()
                .getDirectory()).exists();
        }

        projectGitSync = new ProjectGitSync(PROJECT_ID);

        projectGitSync.setBranch("main");
        projectGitSync.setEnabled(true);
        projectGitSync.setRepositoryUrl("https://example.com/repository.git");
        projectGitSync.setSubDirectory("projects/demo");

        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.of(projectGitSync));
        when(projectGitSyncRemoteFactory.create(any())).thenReturn(
            new ProjectGitSyncRemote(bareRepositoryDirectory.toURI()
                .toString(), "main", null, null));

        projectGitSyncPublisher = new ProjectGitSyncPublisher(
            "noreply@bytechef.io", "ByteChef", new ProjectGitSyncClient(ProjectGitSyncLimits.DEFAULT),
            new ProjectGitSyncLocks(), projectGitSyncRemoteFactory, projectGitSyncService,
            mock(ProjectWorkflowService.class), mock(WorkflowService.class));
    }

    @Test
    void testPushWritesLayoutAndRecordsSuccess() throws Exception {
        projectGitSyncPublisher.push(publication(3, "{\"label\":\"First\",\"tasks\":[]}"));

        ArgumentCaptor<String> commitHashCaptor = ArgumentCaptor.forClass(String.class);

        verify(projectGitSyncService).updateSyncState(
            eq(PROJECT_ID), commitHashCaptor.capture(), eq(ProjectGitSyncDirection.PUSH),
            eq(ProjectGitSyncStatus.SUCCESS), isNull(), eq(3));

        try (Repository repository = Git.open(bareRepositoryDirectory)
            .getRepository(); RevWalk revWalk = new RevWalk(repository)) {

            ObjectId tipId = repository.resolve(Constants.R_HEADS + "main");

            assertThat(tipId.name()).isEqualTo(commitHashCaptor.getValue());

            RevCommit commit = revWalk.parseCommit(tipId);

            assertThat(commit.getFullMessage()).startsWith("Publish Demo version 3\n\nFirst release\n")
                .contains("Project-Version: 3")
                .contains("Published-By: admin@localhost.com");

            List<String> paths = new ArrayList<>();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    paths.add(treeWalk.getPathString());
                }
            }

            assertThat(paths).containsExactly(
                "projects/demo/project.json", "projects/demo/workflows/" + WORKFLOW_UUID + ".json");

            try (TreeWalk treeWalk = TreeWalk.forPath(
                repository, "projects/demo/workflows/" + WORKFLOW_UUID + ".json", commit.getTree())) {

                String content = new String(
                    repository.open(treeWalk.getObjectId(0))
                        .getBytes(),
                    StandardCharsets.UTF_8);

                assertThat(content).contains("\n    \"label\" : \"First\"")
                    .endsWith("\n");
            }
        }
    }

    @Test
    void testRepublishingUnchangedVersionRecordsNoChanges() {
        projectGitSyncPublisher.push(publication(3, "{\"label\":\"First\"}"));
        projectGitSyncPublisher.push(publication(4, "{\"label\":\"First\"}"));

        verify(projectGitSyncService).updateSyncState(
            eq(PROJECT_ID), any(), eq(ProjectGitSyncDirection.PUSH), eq(ProjectGitSyncStatus.NO_CHANGES), isNull(),
            eq(4));
    }

    @Test
    void testOlderVersionThanLastPushedIsSkipped() {
        ProjectGitSync newerProjectGitSync = mock(ProjectGitSync.class);

        when(newerProjectGitSync.isEnabled()).thenReturn(true);
        when(newerProjectGitSync.getLastPushedProjectVersion()).thenReturn(5);
        when(projectGitSyncService.fetchProjectGitSync(PROJECT_ID)).thenReturn(Optional.of(newerProjectGitSync));

        projectGitSyncPublisher.push(publication(4, "{}"));

        verify(projectGitSyncService).updateSyncState(
            eq(PROJECT_ID), isNull(), eq(ProjectGitSyncDirection.PUSH), eq(ProjectGitSyncStatus.SKIPPED), any(),
            isNull());
        verify(projectGitSyncRemoteFactory, never()).create(any());
    }

    @Test
    void testDisabledSettingsPushNothing() {
        projectGitSync.setEnabled(false);

        projectGitSyncPublisher.push(publication(3, "{}"));

        verify(projectGitSyncRemoteFactory, never()).create(any());
        verify(projectGitSyncService, never()).updateSyncState(
            anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void testFailedPushIsRecordedWithoutToken() {
        File missingDirectory = temporaryDirectory.resolve("missing.git")
            .toFile();

        when(projectGitSyncRemoteFactory.create(any())).thenReturn(
            new ProjectGitSyncRemote(
                missingDirectory.toURI() + "?access_token=" + ACCESS_TOKEN, "main", "user", ACCESS_TOKEN));

        projectGitSyncPublisher.push(publication(3, "{}"));

        ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);

        verify(projectGitSyncService).updateSyncState(
            eq(PROJECT_ID), isNull(), eq(ProjectGitSyncDirection.PUSH), eq(ProjectGitSyncStatus.FAILED),
            errorMessageCaptor.capture(), isNull());

        assertThat(errorMessageCaptor.getValue()).isNotBlank()
            .doesNotContain(ACCESS_TOKEN);
    }

    private static ProjectGitSyncPublication publication(int projectVersion, String definition) {
        return new ProjectGitSyncPublication(
            PROJECT_ID, "44444444-4444-4444-4444-444444444444", "Demo", "Demo project", projectVersion,
            "First release", "admin@localhost.com", "public",
            List.of(new ProjectGitSyncWorkflowFile(WORKFLOW_UUID, "First", definition)));
    }
}
