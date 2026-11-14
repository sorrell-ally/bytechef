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

import java.time.Duration;

/**
 * Resource limits applied to every remote Git operation.
 *
 * @param operationTimeout   wall-clock budget for one fetch/push attempt; also used as JGit's I/O timeout
 * @param maxRepositoryBytes maximum size of the fetched (shallow, single-branch) object data
 * @param maxFiles           maximum number of project files read on pull
 * @param maxFileBytes       maximum size of one project file read on pull
 * @param maxTotalFileBytes  maximum total size of the project files read on pull
 * @param maxPushAttempts    attempts when a push is rejected because the branch moved (non-fast-forward)
 */
public record ProjectGitSyncLimits(
    Duration operationTimeout, long maxRepositoryBytes, int maxFiles, long maxFileBytes, long maxTotalFileBytes,
    int maxPushAttempts) {

    public static final ProjectGitSyncLimits DEFAULT = new ProjectGitSyncLimits(
        Duration.ofSeconds(60), 200L * 1024 * 1024, 500, 5L * 1024 * 1024, 50L * 1024 * 1024, 3);
}
