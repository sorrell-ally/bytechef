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

package com.bytechef.platform.sso.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SsoRemoteUrlValidatorTest {

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    void testValidateAcceptsHttpsUrl() {
        assertThat(SsoRemoteUrlValidator.validate("https://login.example.com/realms/acme")
            .getHost()).isEqualTo("login.example.com");
        assertThat(SsoRemoteUrlValidator.validate("https://10.0.0.5/metadata.xml")
            .getHost()).isEqualTo("10.0.0.5");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://login.example.com", "ftp://login.example.com", "https://localhost/realm",
        "https://idp.localhost/realm", "https://127.0.0.1/realm", "https://169.254.169.254/latest/meta-data",
        "https://0.0.0.0/realm", "https://[::1]/realm", "https://user:password@login.example.com",
        "https://login.example.com/#fragment", "not a url", "", "/relative/path"
    })
    void testValidateRejectsDisallowedUrl(String url) {
        assertThatThrownBy(() -> SsoRemoteUrlValidator.validate(url))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testValidateRejectsNullUrl() {
        assertThatThrownBy(() -> SsoRemoteUrlValidator.validate(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
