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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Project files read from a branch.
 *
 * @param commitHash     the commit the files were read from
 * @param directoryFound whether the project directory exists in that commit
 * @param files          UTF-8 file contents keyed by path relative to the project directory (only files accepted by the
 *                       caller's filter, regular files only, sorted by path)
 */
public record ProjectGitSyncSnapshot(String commitHash, boolean directoryFound, Map<String, String> files) {

    public ProjectGitSyncSnapshot {
        files = Collections.unmodifiableMap(new TreeMap<>(files));
    }
}
