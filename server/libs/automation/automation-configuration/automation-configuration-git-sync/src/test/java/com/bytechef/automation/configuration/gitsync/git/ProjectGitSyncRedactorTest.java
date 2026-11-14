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

import java.io.IOException;
import org.junit.jupiter.api.Test;

class ProjectGitSyncRedactorTest {

    private static final String TOKEN = "ghp_a+b/c=Secret";

    @Test
    void testTokenPlainAndUrlEncodedIsRedacted() {
        String redacted = ProjectGitSyncRedactor.redact(
            "auth failed for " + TOKEN + " and ghp_a%2Bb%2Fc%3DSecret", TOKEN);

        assertThat(redacted).doesNotContain(TOKEN)
            .doesNotContain("ghp_a%2Bb%2Fc%3DSecret")
            .contains(ProjectGitSyncRedactor.REDACTED);
    }

    @Test
    void testUrlUserInfoIsRedactedWithoutKnownSecret() {
        String redacted = ProjectGitSyncRedactor.redact(
            "https://user:otherSecret@github.com/org/repo.git: not authorized", null);

        assertThat(redacted).isEqualTo("https://[REDACTED]@github.com/org/repo.git: not authorized");
    }

    @Test
    void testDescribeRedactsWholeCauseChain() {
        Exception exception = new IllegalStateException(
            "outer", new IOException("inner with " + TOKEN, new RuntimeException()));

        String description = ProjectGitSyncRedactor.describe(exception, TOKEN);

        assertThat(description).startsWith("outer: inner with ")
            .doesNotContain(TOKEN)
            .contains("RuntimeException");
    }
}
