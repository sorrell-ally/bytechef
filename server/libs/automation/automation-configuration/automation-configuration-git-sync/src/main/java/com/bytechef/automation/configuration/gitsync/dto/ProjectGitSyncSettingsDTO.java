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

package com.bytechef.automation.configuration.gitsync.dto;

import org.jspecify.annotations.Nullable;

/**
 * Settings to save. Every field is optional on update: {@code null} keeps the stored value, so a client may send only
 * {@code branch} and {@code enabled}. For {@code accessToken}, {@code null} keeps the stored token and an empty string
 * clears it; for {@code subDirectory} and {@code username}, an empty string clears the value.
 */
public record ProjectGitSyncSettingsDTO(
    @Nullable String repositoryUrl, @Nullable String branch, @Nullable String subDirectory, @Nullable String username,
    @Nullable String accessToken, @Nullable Boolean enabled) {

    @Override
    public String toString() {
        return "ProjectGitSyncSettingsDTO{" +
            "repositoryUrl='" + repositoryUrl + '\'' +
            ", branch='" + branch + '\'' +
            ", subDirectory='" + subDirectory + '\'' +
            ", username='" + username + '\'' +
            ", accessToken=" + (accessToken == null ? "null" : "[REDACTED]") +
            ", enabled=" + enabled +
            '}';
    }
}
