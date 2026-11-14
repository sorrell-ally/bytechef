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

class SsoMicrosoftEntraUtilsTest {

    private static final String TENANT_ID = "72f988bf-86f1-41af-91ab-2d7cd011db47";

    @Test
    void testNormalizeIssuerAcceptsTenantSpecificIssuer() {
        assertThat(SsoMicrosoftEntraUtils.normalizeIssuer(
            " https://login.microsoftonline.com/72F988BF-86F1-41AF-91AB-2D7CD011DB47/v2.0/ "))
                .isEqualTo("https://login.microsoftonline.com/" + TENANT_ID + "/v2.0");
        assertThat(SsoMicrosoftEntraUtils.extractTenantId(
            "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0")).contains(TENANT_ID);
        assertThat(SsoMicrosoftEntraUtils.extractTenantId(
            "https://login.microsoftonline.us/" + TENANT_ID + "/v2.0")).contains(TENANT_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://login.microsoftonline.com/common/v2.0", "https://login.microsoftonline.com/organizations/v2.0",
        "https://login.microsoftonline.com/consumers/v2.0"
    })
    void testNormalizeIssuerRejectsMultiTenantAuthorities(String issuerUri) {
        assertThatThrownBy(() -> SsoMicrosoftEntraUtils.normalizeIssuer(issuerUri))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("single tenant");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://login.microsoftonline.com/contoso.onmicrosoft.com/v2.0",
        "https://login.microsoftonline.com/" + TENANT_ID,
        "https://login.microsoftonline.com/" + TENANT_ID + "/oauth2/v2.0",
        "https://sts.windows.net/" + TENANT_ID + "/",
        "https://evil.example.com/" + TENANT_ID + "/v2.0",
        "http://login.microsoftonline.com/" + TENANT_ID + "/v2.0",
        "https://login.microsoftonline.com/{tenantid}/v2.0",
        " "
    })
    void testNormalizeIssuerRejectsInvalidIssuers(String issuerUri) {
        assertThatThrownBy(() -> SsoMicrosoftEntraUtils.normalizeIssuer(issuerUri))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
