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

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @param success      whether the remote could be reached with the given credentials
 * @param branches     the remote branch names (empty for an empty repository)
 * @param branchExists whether the configured branch exists; a push creates it when missing
 * @param errorMessage the redacted failure reason when {@code success} is false
 */
public record ProjectGitSyncConnectionTestResultDTO(
    boolean success, List<String> branches, boolean branchExists, @Nullable String errorMessage) {

    public ProjectGitSyncConnectionTestResultDTO {
        branches = List.copyOf(branches);
    }
}
