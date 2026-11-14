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

package com.bytechef.automation.configuration.gitsync.domain;

/**
 * Outcome of the last Git sync operation. Stored by ordinal: only append new values.
 */
public enum ProjectGitSyncStatus {

    /** The operation completed and changed the remote branch (push) or the draft (pull). */
    SUCCESS,

    /** The operation failed; see the recorded error message. */
    FAILED,

    /** The operation completed without changes (nothing to commit or nothing to import). */
    NO_CHANGES,

    /** A push was skipped because a newer project version had already been pushed. */
    SKIPPED
}
