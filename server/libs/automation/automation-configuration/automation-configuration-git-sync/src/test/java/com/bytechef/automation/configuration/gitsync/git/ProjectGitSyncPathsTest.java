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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectGitSyncPathsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "..", "../escape", "a/../b", "a/./b", ".git", "a/.GIT/b", "a\\b", "a//b", "a b", "a/~/b", "a:b", "a/%2e%2e"
    })
    void testTraversalAndUnsafeSubDirectoriesAreRejected(String subDirectory) {
        assertThatThrownBy(() -> ProjectGitSyncPaths.normalizeSubDirectory(subDirectory))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/projects/demo/", "projects/demo", "projects/demo/"
    })
    void testSubDirectoryIsNormalized(String subDirectory) {
        assertThat(ProjectGitSyncPaths.normalizeSubDirectory(subDirectory)).isEqualTo("projects/demo");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "/", "  "
    })
    void testEmptySubDirectoryMeansRepositoryRoot(String subDirectory) {
        assertThat(ProjectGitSyncPaths.normalizeSubDirectory(subDirectory)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "refs/heads/main", "-main", "a..b", "main.lock", "with space", "a~b", "a:b"
    })
    void testInvalidBranchesAreRejected(String branch) {
        assertThatThrownBy(() -> ProjectGitSyncPaths.validateBranch(branch))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "workflows/../11111111-1111-1111-1111-111111111111.json",
        "../workflows/11111111-1111-1111-1111-111111111111.json",
        "workflows/sub/11111111-1111-1111-1111-111111111111.json",
        "workflows/11111111-1111-1111-1111-111111111111.json.bak", "README.md"
    })
    void testOnlyExactWorkflowPathsAreManaged(String relativePath) {
        assertThat(ProjectGitSyncLayout.isWorkflowFile(relativePath)).isFalse();
        assertThat(ProjectGitSyncLayout.isManagedFile(relativePath)).isFalse();
    }
}
