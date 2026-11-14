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

package com.bytechef.automation.configuration.gitsync.sync;

import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncPaths;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncRemote;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncUrlValidator;
import com.bytechef.encryption.Encryption;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Turns stored settings into a {@link ProjectGitSyncRemote}: re-validates the stored URL on every use (its host may now
 * resolve elsewhere) and decrypts the token only at that moment.
 */
@Component
@ConditionalOnCEVersion
public class ProjectGitSyncRemoteFactory {

    private final Encryption encryption;
    private final ProjectGitSyncUrlValidator projectGitSyncUrlValidator;

    @SuppressFBWarnings("EI")
    public ProjectGitSyncRemoteFactory(Encryption encryption, ProjectGitSyncUrlValidator projectGitSyncUrlValidator) {
        this.encryption = encryption;
        this.projectGitSyncUrlValidator = projectGitSyncUrlValidator;
    }

    public ProjectGitSyncRemote create(ProjectGitSync projectGitSync) {
        String url = projectGitSyncUrlValidator.validate(projectGitSync.getRepositoryUrl());
        String branch = ProjectGitSyncPaths.validateBranch(projectGitSync.getBranch());

        return new ProjectGitSyncRemote(
            url, branch, projectGitSync.getUsername(), decrypt(projectGitSync.getAccessToken()));
    }

    public @Nullable String decrypt(@Nullable String encryptedAccessToken) {
        if (encryptedAccessToken == null || encryptedAccessToken.isEmpty()) {
            return null;
        }

        return encryption.decrypt(encryptedAccessToken);
    }

    public String encrypt(String accessToken) {
        return encryption.encrypt(accessToken);
    }
}
