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

import org.jspecify.annotations.Nullable;

/**
 * A remote branch plus the credentials used to reach it. The access token is plain text here (decrypted just before
 * use) and must never be logged; {@link #toString()} redacts it.
 *
 * @param url         the remote URL, already validated by {@link ProjectGitSyncUrlValidator}
 * @param branch      the short branch name (without {@code refs/heads/})
 * @param username    the HTTPS username, or {@code null} to use {@link #DEFAULT_USERNAME}
 * @param accessToken the access token used as password, or {@code null} for anonymous access
 */
public record ProjectGitSyncRemote(
    String url, String branch, @Nullable String username, @Nullable String accessToken) {

    /**
     * Username sent with a token when none is configured. GitHub accepts any non-empty username with a personal access
     * token; GitLab ({@code oauth2}) and Bitbucket ({@code x-token-auth}) need theirs configured explicitly.
     */
    public static final String DEFAULT_USERNAME = "x-access-token";

    @Override
    public String toString() {
        return "ProjectGitSyncRemote{" +
            "url='" + url + '\'' +
            ", branch='" + branch + '\'' +
            ", username='" + username + '\'' +
            ", accessToken=" + (accessToken == null ? "null" : "[REDACTED]") +
            '}';
    }
}
