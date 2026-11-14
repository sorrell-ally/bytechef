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

package com.bytechef.platform.sso.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.sso.web.rest.SsoProviderDiscoveryController.SsoDiscoveryRequest;
import com.bytechef.platform.sso.web.rest.SsoProviderDiscoveryController.SsoDiscoveryResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SsoProviderDiscoveryControllerTest {

    private SsoProviderDiscoveryController ssoProviderDiscoveryController;
    private SsoProviderService ssoProviderService;

    @BeforeEach
    void beforeEach() {
        ssoProviderService = mock(SsoProviderService.class);

        when(ssoProviderService.fetchEnabledSsoProviderByDomain(anyString())).thenReturn(Optional.empty());
        when(ssoProviderService.fetchEnabledSsoProviderByName(anyString())).thenReturn(Optional.empty());

        ssoProviderDiscoveryController = new SsoProviderDiscoveryController(ssoProviderService);
    }

    @Test
    void testDiscoverOidcProviderByEmail() {
        when(ssoProviderService.fetchEnabledSsoProviderByDomain("acme.com")).thenReturn(
            Optional.of(ssoProvider(SsoProviderType.OIDC)));

        SsoDiscoveryResponse ssoDiscoveryResponse = ssoProviderDiscoveryController.discover(
            new SsoDiscoveryRequest("Jane@Acme.com"));

        assertThat(ssoDiscoveryResponse.providerName()).isEqualTo("Acme");
        assertThat(ssoDiscoveryResponse.redirectUrl()).isEqualTo("/oauth2/authorization/sso-0011223344556677");
        assertThat(ssoDiscoveryResponse.enforced()).isTrue();
    }

    @Test
    void testDiscoverSamlProviderByName() {
        when(ssoProviderService.fetchEnabledSsoProviderByName("acme")).thenReturn(
            Optional.of(ssoProvider(SsoProviderType.SAML)));

        SsoDiscoveryResponse ssoDiscoveryResponse = ssoProviderDiscoveryController.discoverByName("acme");

        assertThat(ssoDiscoveryResponse.redirectUrl()).isEqualTo("/saml2/authenticate/sso-0011223344556677");
    }

    @Test
    void testDiscoverReturnsEmptyResponseWhenNothingMatches() {
        assertThat(ssoProviderDiscoveryController.discover(new SsoDiscoveryRequest("jane@other.com"))
            .redirectUrl()).isNull();
        assertThat(ssoProviderDiscoveryController.discover(null)
            .redirectUrl()).isNull();
        assertThat(ssoProviderDiscoveryController.discover(new SsoDiscoveryRequest("not-an-email"))
            .redirectUrl()).isNull();
        assertThat(ssoProviderDiscoveryController.discoverByName(null)
            .redirectUrl()).isNull();
        assertThat(ssoProviderDiscoveryController.discoverByName("unknown")
            .redirectUrl()).isNull();
    }

    @Test
    void testDiscoverIgnoresOverlongInput() {
        String overlongEmail = "a".repeat(400) + "@acme.com";

        assertThat(ssoProviderDiscoveryController.discover(new SsoDiscoveryRequest(overlongEmail))
            .redirectUrl()).isNull();

        verify(ssoProviderService, never()).fetchEnabledSsoProviderByDomain(anyString());
    }

    private static SsoProvider ssoProvider(SsoProviderType type) {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setDomains(List.of("acme.com"));
        ssoProvider.setEnabled(true);
        ssoProvider.setEnforceSso(true);
        ssoProvider.setName("Acme");
        ssoProvider.setRegistrationId("sso-0011223344556677");
        ssoProvider.setType(type);

        return ssoProvider;
    }
}
