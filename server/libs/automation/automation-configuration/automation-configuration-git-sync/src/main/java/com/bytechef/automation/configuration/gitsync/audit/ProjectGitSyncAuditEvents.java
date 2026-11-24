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

public final class ProjectGitSyncAuditEvents {

    public static final String PROJECT_GIT_SYNC_CONNECTION_TESTED = "PROJECT_GIT_SYNC_CONNECTION_TESTED";
    public static final String PROJECT_GIT_SYNC_DELETED = "PROJECT_GIT_SYNC_DELETED";
    public static final String PROJECT_GIT_SYNC_PULLED = "PROJECT_GIT_SYNC_PULLED";
    public static final String PROJECT_GIT_SYNC_PUSHED = "PROJECT_GIT_SYNC_PUSHED";
    public static final String PROJECT_GIT_SYNC_SAVED = "PROJECT_GIT_SYNC_SAVED";

    private ProjectGitSyncAuditEvents() {
    }
}
