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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectGitSyncUrlValidatorTest {

    private final ProjectGitSyncUrlValidator projectGitSyncUrlValidator = new ProjectGitSyncUrlValidator(false);

    @ParameterizedTest
    @ValueSource(strings = {
        "http://github.com/org/repo.git", "ssh://git@github.com/org/repo.git", "git@github.com:org/repo.git",
        "file:///etc/repo", "https://user:token@github.com/org/repo.git", "https://token@github.com/org/repo.git",
        "https://github.com/org/repo.git?x=1", "https://github.com/org/repo.git#frag", "https://github.com",
        "https://localhost/repo.git", "https://127.0.0.1/repo.git", "https://10.0.0.5/repo.git",
        "https://169.254.169.254/latest", "https://[::1]/repo.git", "https://[fd00::1]/repo.git",
        "https://100.64.1.1/repo.git", "https://0.0.0.0/repo.git", "https://git hub.com/repo.git"
    })
    void testUnsafeUrlsAreRejected(String url) {
        assertThatThrownBy(() -> projectGitSyncUrlValidator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPublicIpHttpsUrlIsAcceptedAndNormalized() {
        assertThat(projectGitSyncUrlValidator.validate("  HTTPS://93.184.216.34/org/repo.git "))
            .isEqualTo("https://93.184.216.34/org/repo.git");
    }

    @Test
    void testPrivateHostsCanBeExplicitlyAllowed() {
        ProjectGitSyncUrlValidator permissiveProjectGitSyncUrlValidator = new ProjectGitSyncUrlValidator(true);

        assertThat(permissiveProjectGitSyncUrlValidator.validate("https://10.0.0.5:8443/org/repo.git"))
            .isEqualTo("https://10.0.0.5:8443/org/repo.git");
    }

    @Test
    void testNonPublicAddressClassification() throws Exception {
        InetAddress privateInetAddress = InetAddress.getByAddress(new byte[] {
            (byte) 192, (byte) 168, 1, 1
        });
        InetAddress publicInetAddress = InetAddress.getByAddress(new byte[] {
            8, 8, 8, 8
        });

        assertThat(ProjectGitSyncUrlValidator.isNonPublic(privateInetAddress)).isTrue();
        assertThat(ProjectGitSyncUrlValidator.isNonPublic(publicInetAddress)).isFalse();
    }
}
