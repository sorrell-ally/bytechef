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

package com.bytechef.automation.configuration.gitsync.config;

import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncClient;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncLimits;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncUrlValidator;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure beans for project Git sync (CE edition only).
 *
 * <p>
 * Properties (all optional):
 * <ul>
 * <li>{@code bytechef.project-git-sync.allow-private-network-hosts} (default {@code false}) - allow repository hosts
 * that resolve to private, loopback or link-local addresses, e.g. a self-hosted Git server on an internal network</li>
 * <li>{@code bytechef.project-git-sync.timeout} (default {@code 60s}) - wall-clock budget of one remote operation</li>
 * <li>{@code bytechef.project-git-sync.max-repository-size} (default 200 MB, in bytes) - maximum fetched size</li>
 * </ul>
 */
@Configuration
@ConditionalOnCEVersion
class ProjectGitSyncConfiguration {

    @Bean
    ProjectGitSyncClient projectGitSyncClient(
        @Value("${bytechef.project-git-sync.timeout:60s}") Duration timeout,
        @Value("${bytechef.project-git-sync.max-repository-size:209715200}") long maxRepositorySize) {

        ProjectGitSyncLimits defaultLimits = ProjectGitSyncLimits.DEFAULT;

        return new ProjectGitSyncClient(
            new ProjectGitSyncLimits(
                timeout, maxRepositorySize, defaultLimits.maxFiles(), defaultLimits.maxFileBytes(),
                defaultLimits.maxTotalFileBytes(), defaultLimits.maxPushAttempts()));
    }

    @Bean
    ProjectGitSyncUrlValidator projectGitSyncUrlValidator(
        @Value("${bytechef.project-git-sync.allow-private-network-hosts:false}") boolean allowPrivateNetworkHosts) {

        return new ProjectGitSyncUrlValidator(allowPrivateNetworkHosts);
    }
}
