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

package com.bytechef.platform.sso.web.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.domain.SsoProviderVendor;
import com.bytechef.platform.sso.service.SsoProviderService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Covers the registration factory, the version/TTL cache and the two database-backed repositories.
 */
class SsoRegistrationTest {

    private static final String ISSUER = "https://93.184.216.34/realms/acme";
    private static final String REGISTRATION_ID = "sso-0011223344556677";

    private final AtomicInteger buildCount = new AtomicInteger();
    private MutableClock clock;
    private SsoRegistrationCache ssoRegistrationCache;
    private SsoProviderService ssoProviderService;

    @BeforeEach
    void beforeEach() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ssoProviderService = mock(SsoProviderService.class);

        when(ssoProviderService.fetchSsoProviderByRegistrationId(anyString())).thenReturn(Optional.empty());

        SsoRegistrationFactory ssoRegistrationFactory = new SsoRegistrationFactory(new SsoRemoteDocumentFetcher()) {

            @Override
            public ClientRegistration createClientRegistration(SsoProvider ssoProvider) {
                buildCount.incrementAndGet();

                return createClientRegistration(ssoProvider, discoveryDocument());
            }
        };

        ssoRegistrationCache = new SsoRegistrationCache(ssoProviderService, ssoRegistrationFactory, clock);
    }

    @Test
    void testCreateClientRegistrationFromDiscoveryDocument() {
        SsoRegistrationFactory ssoRegistrationFactory = new SsoRegistrationFactory(new SsoRemoteDocumentFetcher());

        ClientRegistration clientRegistration = ssoRegistrationFactory.createClientRegistration(
            ssoProvider(1), discoveryDocument());

        assertThat(clientRegistration.getRegistrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(clientRegistration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(clientRegistration.getClientAuthenticationMethod())
            .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(clientRegistration.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
        assertThat(clientRegistration.getScopes()).containsExactly("openid", "email");
        assertThat(clientRegistration.getClientSettings()
            .isRequireProofKey()).isTrue();
        assertThat(clientRegistration.getProviderDetails()
            .getIssuerUri()).isEqualTo(ISSUER);
    }

    @Test
    void testCreateClientRegistrationSkipsUserInfoForMicrosoftEntra() {
        SsoRegistrationFactory ssoRegistrationFactory = new SsoRegistrationFactory(new SsoRemoteDocumentFetcher());

        SsoProvider ssoProvider = ssoProvider(1);

        ssoProvider.setVendor(SsoProviderVendor.MICROSOFT_ENTRA_ID);

        ClientRegistration clientRegistration = ssoRegistrationFactory.createClientRegistration(
            ssoProvider, discoveryDocument());

        assertThat(clientRegistration.getProviderDetails()
            .getUserInfoEndpoint()
            .getUri()).isNull();
    }

    @Test
    void testCreateClientRegistrationRejectsIssuerMismatch() {
        SsoRegistrationFactory ssoRegistrationFactory = new SsoRegistrationFactory(new SsoRemoteDocumentFetcher());

        Map<String, Object> discoveryDocument = new HashMap<>(discoveryDocument());

        discoveryDocument.put("issuer", "https://93.184.216.34/realms/other");

        assertThatThrownBy(() -> ssoRegistrationFactory.createClientRegistration(ssoProvider(1), discoveryDocument))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match");
    }

    @Test
    void testCreateClientRegistrationRejectsInternalTokenEndpoint() {
        SsoRegistrationFactory ssoRegistrationFactory = new SsoRegistrationFactory(new SsoRemoteDocumentFetcher());

        Map<String, Object> discoveryDocument = new HashMap<>(discoveryDocument());

        discoveryDocument.put("token_endpoint", "https://169.254.169.254/latest/meta-data");

        assertThatThrownBy(() -> ssoRegistrationFactory.createClientRegistration(ssoProvider(1), discoveryDocument))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCacheReusesRegistrationWhileVersionUnchanged() {
        when(ssoProviderService.fetchSsoProviderByRegistrationId(REGISTRATION_ID)).thenReturn(
            Optional.of(ssoProvider(1)));

        ClientRegistration firstClientRegistration = ssoRegistrationCache.findClientRegistration(REGISTRATION_ID);
        ClientRegistration secondClientRegistration = ssoRegistrationCache.findClientRegistration(REGISTRATION_ID);

        assertThat(firstClientRegistration).isSameAs(secondClientRegistration);
        assertThat(buildCount.get()).isEqualTo(1);
    }

    @Test
    void testCacheRebuildsWhenProviderEdited() {
        when(ssoProviderService.fetchSsoProviderByRegistrationId(REGISTRATION_ID)).thenReturn(
            Optional.of(ssoProvider(1)));

        ssoRegistrationCache.findClientRegistration(REGISTRATION_ID);

        when(ssoProviderService.fetchSsoProviderByRegistrationId(REGISTRATION_ID)).thenReturn(
            Optional.of(ssoProvider(2)));

        ssoRegistrationCache.findClientRegistration(REGISTRATION_ID);

        assertThat(buildCount.get()).isEqualTo(2);
    }

    @Test
    void testCacheRefreshesAfterInterval() {
        when(ssoProviderService.fetchSsoProviderByRegistrationId(REGISTRATION_ID)).thenReturn(
            Optional.of(ssoProvider(1)));

        ssoRegistrationCache.findClientRegistration(REGISTRATION_ID);

        clock.advance(SsoRegistrationCache.REFRESH_INTERVAL.plusSeconds(1));

        ssoRegistrationCache.findClientRegistration(REGISTRATION_ID);

        assertThat(buildCount.get()).isEqualTo(2);
    }

    @Test
    void testCacheReturnsNullForDisabledOrWrongTypeProvider() {
        SsoProvider disabledSsoProvider = ssoProvider(1);

        disabledSsoProvider.setEnabled(false);

        when(ssoProviderService.fetchSsoProviderByRegistrationId(REGISTRATION_ID)).thenReturn(
            Optional.of(disabledSsoProvider));

        assertThat(ssoRegistrationCache.findClientRegistration(REGISTRATION_ID)).isNull();

        SsoProvider samlSsoProvider = ssoProvider(1);

        samlSsoProvider.setType(SsoProviderType.SAML);

        when(ssoProviderService.fetchSsoProviderByRegistrationId(REGISTRATION_ID)).thenReturn(
            Optional.of(samlSsoProvider));

        assertThat(ssoRegistrationCache.findClientRegistration(REGISTRATION_ID)).isNull();
        assertThat(buildCount.get()).isZero();
    }

    @Test
    void testClientRegistrationRepositoryRoutesByPrefix() {
        when(ssoProviderService.fetchSsoProviderByRegistrationId(REGISTRATION_ID)).thenReturn(
            Optional.of(ssoProvider(1)));

        ClientRegistrationRepository socialClientRegistrationRepository = mock(ClientRegistrationRepository.class);
        ClientRegistration googleClientRegistration = ClientRegistration.withRegistrationId("google")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .clientId("google-client")
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .tokenUri("https://oauth2.googleapis.com/token")
            .build();

        when(socialClientRegistrationRepository.findByRegistrationId("google")).thenReturn(googleClientRegistration);

        SsoClientRegistrationRepository ssoClientRegistrationRepository = new SsoClientRegistrationRepository(
            ssoRegistrationCache, socialClientRegistrationRepository);

        assertThat(ssoClientRegistrationRepository.findByRegistrationId("google")).isSameAs(googleClientRegistration);
        assertThat(ssoClientRegistrationRepository.findByRegistrationId(REGISTRATION_ID)
            .getRegistrationId()).isEqualTo(REGISTRATION_ID);

        verify(socialClientRegistrationRepository, never()).findByRegistrationId(REGISTRATION_ID);

        SsoClientRegistrationRepository ssoOnlyClientRegistrationRepository = new SsoClientRegistrationRepository(
            ssoRegistrationCache, null);

        assertThat(ssoOnlyClientRegistrationRepository.findByRegistrationId("google")).isNull();
    }

    @Test
    void testRelyingPartyRegistrationRepositoryIgnoresNonSsoIds() {
        SsoRelyingPartyRegistrationRepository ssoRelyingPartyRegistrationRepository =
            new SsoRelyingPartyRegistrationRepository(ssoRegistrationCache);

        assertThat(ssoRelyingPartyRegistrationRepository.findByRegistrationId("google")).isNull();
        assertThat(ssoRelyingPartyRegistrationRepository.findByRegistrationId(REGISTRATION_ID)).isNull();

        verify(ssoProviderService, never()).fetchSsoProviderByRegistrationId("google");
    }

    @Test
    void testCallbackPathDetection() {
        assertThat(SsoClientRegistrationRepository.isSsoCallbackPath("/login/oauth2/code/sso-0011223344556677"))
            .isTrue();
        assertThat(SsoClientRegistrationRepository.isSsoCallbackPath("/login/saml2/sso/sso-0011223344556677"))
            .isTrue();
        assertThat(SsoClientRegistrationRepository.isSsoCallbackPath("/login/oauth2/code/google")).isFalse();
        assertThat(SsoClientRegistrationRepository.isSsoCallbackPath(null)).isFalse();
    }

    private static Map<String, Object> discoveryDocument() {
        return Map.of(
            "issuer", ISSUER,
            "authorization_endpoint", ISSUER + "/protocol/openid-connect/auth",
            "token_endpoint", ISSUER + "/protocol/openid-connect/token",
            "jwks_uri", ISSUER + "/protocol/openid-connect/certs",
            "userinfo_endpoint", ISSUER + "/protocol/openid-connect/userinfo",
            "token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
    }

    private static SsoProvider ssoProvider(int version) {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setClientId("client");
        ssoProvider.setClientSecret("secret");
        ssoProvider.setDomains(List.of("acme.com"));
        ssoProvider.setEnabled(true);
        ssoProvider.setIssuerUri(ISSUER);
        ssoProvider.setName("Acme");
        ssoProvider.setRegistrationId(REGISTRATION_ID);
        ssoProvider.setScopes(List.of("openid", "email"));
        ssoProvider.setType(SsoProviderType.OIDC);
        ssoProvider.setVersion(version);

        return ssoProvider;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public Clock withZone(ZoneId zoneId) {
            return this;
        }
    }
}
