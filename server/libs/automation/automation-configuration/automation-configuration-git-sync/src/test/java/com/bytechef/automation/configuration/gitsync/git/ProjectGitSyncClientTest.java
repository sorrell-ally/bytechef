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

package com.bytechef.automation.configuration.gitsync.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bytechef.automation.configuration.gitsync.layout.ProjectGitSyncLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link ProjectGitSyncClient} against a local bare repository (no network).
 */
class ProjectGitSyncClientTest {

    private static final String BRANCH = "main";
    private static final String WORKFLOW_UUID_1 = "11111111-1111-1111-1111-111111111111";
    private static final String WORKFLOW_UUID_2 = "22222222-2222-2222-2222-222222222222";
    private static final PersonIdent AUTHOR = new PersonIdent("ByteChef", "noreply@bytechef.io");

    @TempDir
    private Path temporaryDirectory;

    private File bareRepositoryDirectory;
    private ProjectGitSyncClient projectGitSyncClient;
    private ProjectGitSyncRemote remote;

    @BeforeEach
    void beforeEach() throws Exception {
        bareRepositoryDirectory = temporaryDirectory.resolve("remote.git")
            .toFile();

        try (Git git = Git.init()
            .setBare(true)
            .setInitialBranch(BRANCH)
            .setDirectory(bareRepositoryDirectory)
            .call()) {

            // the repository is created empty
            assertThat(git.getRepository()
                .getDirectory()).exists();
        }

        projectGitSyncClient = new ProjectGitSyncClient(
            new ProjectGitSyncLimits(Duration.ofSeconds(60), 50L * 1024 * 1024, 100, 1024 * 1024, 10L * 1024 * 1024,
                5));

        String url = bareRepositoryDirectory.toURI()
            .toString();

        remote = new ProjectGitSyncRemote(url, BRANCH, null, null);
    }

    @Test
    void testPushCreatesBranchInEmptyRepository() throws Exception {
        ProjectGitSyncPushResult pushResult = projectGitSyncClient.push(
            remote, "projects/demo", files(Map.of(WORKFLOW_UUID_1, "{\"label\": \"First\"}")),
            ProjectGitSyncLayout::isManagedFile, "Publish demo", AUTHOR);

        assertThat(pushResult.changed()).isTrue();

        Map<String, String> remoteFiles = readRemoteFiles();

        assertThat(remoteFiles).containsOnlyKeys(
            "projects/demo/project.json", "projects/demo/workflows/" + WORKFLOW_UUID_1 + ".json");
        assertThat(remoteFiles.get("projects/demo/project.json")).contains("\"formatVersion\"");
        assertThat(getRemoteTip()).isEqualTo(pushResult.commitHash());

        try (Repository repository = openRemote(); RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(ObjectId.fromString(pushResult.commitHash()));

            PersonIdent authorIdent = commit.getAuthorIdent();

            assertThat(authorIdent.getName()).isEqualTo("ByteChef");
            assertThat(commit.getFullMessage()).isEqualTo("Publish demo");
        }
    }

    @Test
    void testPushKeepsUnrelatedFilesAndDeletesOnlyManagedFiles() throws Exception {
        seedRemote(
            Map.of(
                "README.md", "root readme\n",
                "projects/other/workflows/" + WORKFLOW_UUID_2 + ".json", "{}\n",
                "projects/demo/notes.txt", "kept\n",
                "projects/demo/workflows/" + WORKFLOW_UUID_2 + ".json", "{\"stale\": true}\n"));

        projectGitSyncClient.push(
            remote, "projects/demo", files(Map.of(WORKFLOW_UUID_1, "{\"label\": \"First\"}")),
            ProjectGitSyncLayout::isManagedFile, "Publish demo", AUTHOR);

        Map<String, String> remoteFiles = readRemoteFiles();

        assertThat(remoteFiles).containsOnlyKeys(
            "README.md", "projects/other/workflows/" + WORKFLOW_UUID_2 + ".json", "projects/demo/notes.txt",
            "projects/demo/project.json", "projects/demo/workflows/" + WORKFLOW_UUID_1 + ".json");
        assertThat(remoteFiles.get("README.md")).isEqualTo("root readme\n");
        assertThat(remoteFiles.get("projects/demo/notes.txt")).isEqualTo("kept\n");
    }

    @Test
    void testPushToRepositoryRootKeepsUnrelatedFiles() throws Exception {
        seedRemote(Map.of("README.md", "root readme\n", "docs/guide.md", "guide\n"));

        projectGitSyncClient.push(
            remote, "", files(Map.of(WORKFLOW_UUID_1, "{}")), ProjectGitSyncLayout::isManagedFile, "Publish",
            AUTHOR);

        assertThat(readRemoteFiles()).containsOnlyKeys(
            "README.md", "docs/guide.md", "project.json", "workflows/" + WORKFLOW_UUID_1 + ".json");
    }

    @Test
    void testPushWithoutChangesCreatesNoCommit() throws Exception {
        Map<String, String> files = files(Map.of(WORKFLOW_UUID_1, "{\"label\": \"First\"}"));

        ProjectGitSyncPushResult firstPushResult = projectGitSyncClient.push(
            remote, "demo", files, ProjectGitSyncLayout::isManagedFile, "Publish 1", AUTHOR);
        ProjectGitSyncPushResult secondPushResult = projectGitSyncClient.push(
            remote, "demo", files, ProjectGitSyncLayout::isManagedFile, "Publish 2", AUTHOR);

        assertThat(secondPushResult.changed()).isFalse();
        assertThat(secondPushResult.commitHash()).isEqualTo(firstPushResult.commitHash());
        assertThat(getRemoteTip()).isEqualTo(firstPushResult.commitHash());
    }

    @Test
    void testPushRejectsFileOnProjectDirectoryPath() throws Exception {
        seedRemote(Map.of("projects", "a file, not a directory\n"));

        assertThatThrownBy(() -> projectGitSyncClient.push(
            remote, "projects/demo", files(Map.of(WORKFLOW_UUID_1, "{}")), ProjectGitSyncLayout::isManagedFile,
            "Publish", AUTHOR))
                .isInstanceOf(ProjectGitSyncException.class)
                .hasMessageContaining("is a file in the repository");

        assertThat(readRemoteFiles()).containsOnlyKeys("projects");
    }

    @Test
    void testConcurrentPushesToDifferentDirectoriesBothSucceed() throws Exception {
        seedRemote(Map.of("README.md", "readme\n"));

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        try {
            List<Callable<ProjectGitSyncPushResult>> callables = new ArrayList<>();

            for (int index = 0; index < 4; index++) {
                String directory = "project-" + index;

                callables.add(() -> projectGitSyncClient.push(
                    remote, directory, files(Map.of(WORKFLOW_UUID_1, "{\"index\": \"" + directory + "\"}")),
                    ProjectGitSyncLayout::isManagedFile, "Publish " + directory, AUTHOR));
            }

            for (Future<ProjectGitSyncPushResult> future : executorService.invokeAll(callables)) {
                assertThat(future.get()
                    .changed()).isTrue();
            }
        } finally {
            executorService.shutdownNow();
        }

        Map<String, String> remoteFiles = readRemoteFiles();

        assertThat(remoteFiles).containsKey("README.md");

        for (int index = 0; index < 4; index++) {
            assertThat(remoteFiles).containsKey("project-" + index + "/workflows/" + WORKFLOW_UUID_1 + ".json");
        }
    }

    @Test
    void testReadReturnsOnlyFilteredRegularFiles() throws Exception {
        seedRemote(
            Map.of(
                "demo/project.json", "{}\n",
                "demo/workflows/" + WORKFLOW_UUID_1 + ".json", "{\"a\": 1}\n",
                "demo/workflows/not-a-uuid.json", "{}\n",
                "demo/README.md", "readme\n",
                "other/workflows/" + WORKFLOW_UUID_2 + ".json", "{}\n"));

        ProjectGitSyncSnapshot snapshot = projectGitSyncClient.read(
            remote, "demo", ProjectGitSyncLayout::isWorkflowFile);

        assertThat(snapshot.directoryFound()).isTrue();
        assertThat(snapshot.commitHash()).isEqualTo(getRemoteTip());
        assertThat(snapshot.files()).containsOnlyKeys("workflows/" + WORKFLOW_UUID_1 + ".json");
        assertThat(snapshot.files()
            .get("workflows/" + WORKFLOW_UUID_1 + ".json")).isEqualTo("{\"a\": 1}\n");
    }

    @Test
    void testReadMissingDirectoryAndMissingBranch() throws Exception {
        seedRemote(Map.of("README.md", "readme\n"));

        ProjectGitSyncSnapshot snapshot = projectGitSyncClient.read(
            remote, "missing", ProjectGitSyncLayout::isWorkflowFile);

        assertThat(snapshot.directoryFound()).isFalse();
        assertThat(snapshot.files()).isEmpty();

        ProjectGitSyncRemote missingBranchRemote = new ProjectGitSyncRemote(remote.url(), "nope", null, null);

        assertThatThrownBy(() -> projectGitSyncClient.read(
            missingBranchRemote, "demo", ProjectGitSyncLayout::isWorkflowFile))
                .isInstanceOf(ProjectGitSyncException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void testReadRejectsOversizedFile() throws Exception {
        ProjectGitSyncClient smallLimitProjectGitSyncClient = new ProjectGitSyncClient(
            new ProjectGitSyncLimits(Duration.ofSeconds(60), 50L * 1024 * 1024, 100, 10, 1024, 1));

        seedRemote(Map.of("demo/workflows/" + WORKFLOW_UUID_1 + ".json", "{\"definition\": \"too large\"}\n"));

        assertThatThrownBy(() -> smallLimitProjectGitSyncClient.read(
            remote, "demo", ProjectGitSyncLayout::isWorkflowFile))
                .isInstanceOf(ProjectGitSyncException.class)
                .hasMessageContaining("exceeds the maximum size");
    }

    @Test
    void testListBranches() throws Exception {
        assertThat(projectGitSyncClient.listBranches(remote)).isEmpty();

        seedRemote(Map.of("README.md", "readme\n"));

        assertThat(projectGitSyncClient.listBranches(remote)).containsExactly(BRANCH);
    }

    @Test
    void testFailureMessageDoesNotContainToken() {
        String accessToken = "ghp_SuperSecretToken123";

        File missingDirectory = temporaryDirectory.resolve("missing.git")
            .toFile();

        ProjectGitSyncRemote missingRemote = new ProjectGitSyncRemote(
            missingDirectory.toURI() + "?token=" + accessToken, BRANCH, "user", accessToken);

        assertThatThrownBy(() -> projectGitSyncClient.listBranches(missingRemote))
            .isInstanceOf(ProjectGitSyncException.class)
            .satisfies(throwable -> assertThat(throwable.getMessage()).doesNotContain(accessToken));

        ProjectGitSyncRemote tokenRemote = new ProjectGitSyncRemote(remote.url(), BRANCH, "user", accessToken);

        assertThat(tokenRemote.toString()).doesNotContain(accessToken);
    }

    private static Map<String, String> files(Map<String, String> definitions) {
        Map<String, String> files = new TreeMap<>();

        files.put(ProjectGitSyncLayout.PROJECT_FILE, "{\n    \"formatVersion\": 1\n}\n");

        for (Map.Entry<String, String> entry : definitions.entrySet()) {
            files.put(ProjectGitSyncLayout.getWorkflowPath(entry.getKey()), entry.getValue() + "\n");
        }

        return files;
    }

    private void seedRemote(Map<String, String> files) throws Exception {
        Path workDirectory = Files.createTempDirectory(temporaryDirectory, "seed");

        try (Git git = Git.init()
            .setInitialBranch(BRANCH)
            .setDirectory(workDirectory.toFile())
            .call()) {

            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path filePath = workDirectory.resolve(entry.getKey());

                Files.createDirectories(Objects.requireNonNull(filePath.getParent()));
                Files.writeString(filePath, entry.getValue(), StandardCharsets.UTF_8);
            }

            git.add()
                .addFilepattern(".")
                .call();

            git.commit()
                .setMessage("seed")
                .setAuthor(AUTHOR)
                .setCommitter(AUTHOR)
                .setSign(false)
                .call();

            git.push()
                .setRemote(bareRepositoryDirectory.toURI()
                    .toString())
                .add(BRANCH)
                .setForce(true)
                .call();
        }
    }

    private Repository openRemote() throws IOException {
        return Git.open(bareRepositoryDirectory)
            .getRepository();
    }

    private String getRemoteTip() throws IOException {
        try (Repository repository = openRemote()) {
            ObjectId tipId = repository.resolve(Constants.R_HEADS + BRANCH);

            return tipId.name();
        }
    }

    private Map<String, String> readRemoteFiles() throws IOException {
        Map<String, String> files = new TreeMap<>();

        try (Repository repository = openRemote(); RevWalk revWalk = new RevWalk(repository);
            TreeWalk treeWalk = new TreeWalk(repository)) {

            RevCommit commit = revWalk.parseCommit(repository.resolve(Constants.R_HEADS + BRANCH));

            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                assertThat(treeWalk.getFileMode(0)).isEqualTo(FileMode.REGULAR_FILE);

                ObjectLoader objectLoader = repository.open(treeWalk.getObjectId(0));

                files.put(treeWalk.getPathString(), new String(objectLoader.getBytes(), StandardCharsets.UTF_8));
            }
        }

        return files;
    }
}
