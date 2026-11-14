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

package com.bytechef.platform.sso.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.domain.SsoProviderVendor;
import com.bytechef.platform.sso.repository.SsoProviderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SsoProviderServiceTest {

    private SsoProviderRepository ssoProviderRepository;
    private SsoProviderService ssoProviderService;

    @BeforeEach
    void beforeEach() {
        ssoProviderRepository = mock(SsoProviderRepository.class);

        when(ssoProviderRepository.save(any(SsoProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ssoProviderRepository.findAll()).thenReturn(List.of());

        ssoProviderService = new SsoProviderServiceImpl(ssoProviderRepository);
    }

    @Test
    void testCreateOidcProviderNormalizesAndGeneratesRegistrationId() {
        SsoProvider ssoProvider = oidcProvider();

        ssoProvider.setRegistrationId("sso-chosen-by-caller");

        SsoProvider createdSsoProvider = ssoProviderService.create(ssoProvider);

        assertThat(createdSsoProvider.getRegistrationId()).startsWith("sso-")
            .hasSize(20)
            .isNotEqualTo("sso-chosen-by-caller");
        assertThat(createdSsoProvider.getAuthProvider()).hasSizeLessThanOrEqualTo(20);
        assertThat(createdSsoProvider.getDomains()).containsExactly("acme.com", "acme.io");
        assertThat(createdSsoProvider.getScopes()).containsExactly("openid", "profile", "email");
        assertThat(createdSsoProvider.getDefaultAuthority()).isEqualTo(AuthorityConstants.USER);
        assertThat(createdSsoProvider.getName()).isEqualTo("Acme");
    }

    @Test
    void testCreateGeneratesDistinctRegistrationIds() {
        String firstRegistrationId = ssoProviderService.create(oidcProvider())
            .getRegistrationId();
        String secondRegistrationId = ssoProviderService.create(oidcProvider())
            .getRegistrationId();

        assertThat(firstRegistrationId).isNotEqualTo(secondRegistrationId);
    }

    @Test
    void testCreateAddsOpenidScope() {
        SsoProvider ssoProvider = oidcProvider();

        ssoProvider.setScopes(List.of("email"));

        assertThat(ssoProviderService.create(ssoProvider)
            .getScopes()).containsExactly("openid", "email");
    }

    @Test
    void testCreateRejectsInvalidInput() {
        SsoProvider missingSecret = oidcProvider();

        missingSecret.setClientSecret(null);

        assertThatThrownBy(() -> ssoProviderService.create(missingSecret))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("clientSecret");

        SsoProvider insecureIssuer = oidcProvider();

        insecureIssuer.setIssuerUri("http://idp.acme.com");

        assertThatThrownBy(() -> ssoProviderService.create(insecureIssuer))
            .isInstanceOf(IllegalArgumentException.class);

        SsoProvider unknownAuthority = oidcProvider();

        unknownAuthority.setDefaultAuthority("ROLE_SYSTEM_ADMIN");

        assertThatThrownBy(() -> ssoProviderService.create(unknownAuthority))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("defaultAuthority");

        SsoProvider enforcedWithoutDomains = oidcProvider();

        enforcedWithoutDomains.setDomains(List.of());
        enforcedWithoutDomains.setEnforceSso(true);

        assertThatThrownBy(() -> ssoProviderService.create(enforcedWithoutDomains))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("domain");

        verify(ssoProviderRepository, never()).save(any(SsoProvider.class));
    }

    @Test
    void testCreateRejectsDomainClaimedByAnotherProvider() {
        SsoProvider existingSsoProvider = oidcProvider();

        existingSsoProvider.setId(1L);
        existingSsoProvider.setName("Other");
        existingSsoProvider.setDomains(List.of("acme.io"));

        when(ssoProviderRepository.findAll()).thenReturn(List.of(existingSsoProvider));

        assertThatThrownBy(() -> ssoProviderService.create(oidcProvider()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("acme.io");
    }

    @Test
    void testCreateSamlProviderRequiresExactlyOneMetadataSource() {
        SsoProvider ssoProvider = samlProvider();

        ssoProvider.setMetadataXml("<EntityDescriptor/>");

        assertThatThrownBy(() -> ssoProviderService.create(ssoProvider))
            .isInstanceOf(IllegalArgumentException.class);

        SsoProvider doctypeProvider = samlProvider();

        doctypeProvider.setMetadataUrl(null);
        doctypeProvider.setMetadataXml("<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><a/>");

        assertThatThrownBy(() -> ssoProviderService.create(doctypeProvider))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DOCTYPE");

        SsoProvider createdSsoProvider = ssoProviderService.create(samlProvider());

        assertThat(createdSsoProvider.getClientSecret()).isNull();
        assertThat(createdSsoProvider.getIssuerUri()).isNull();
    }

    @Test
    void testUpdateKeepsSecretWhenNotProvided() {
        SsoProvider storedSsoProvider = oidcProvider();

        storedSsoProvider.setId(7L);
        storedSsoProvider.setRegistrationId("sso-0011223344556677");

        when(ssoProviderRepository.findById(7L)).thenReturn(Optional.of(storedSsoProvider));

        SsoProvider ssoProvider = oidcProvider();

        ssoProvider.setId(7L);
        ssoProvider.setClientSecret(null);
        ssoProvider.setName("Acme Renamed");
        ssoProvider.setRegistrationId("sso-attacker");
        ssoProvider.setType(SsoProviderType.SAML);

        SsoProvider updatedSsoProvider = ssoProviderService.update(ssoProvider);

        assertThat(updatedSsoProvider.getClientSecret()).isEqualTo("secret");
        assertThat(updatedSsoProvider.getName()).isEqualTo("Acme Renamed");
        assertThat(updatedSsoProvider.getRegistrationId()).isEqualTo("sso-0011223344556677");
        assertThat(updatedSsoProvider.getType()).isEqualTo(SsoProviderType.OIDC);
    }

    @Test
    void testUpdateReplacesSecretWhenProvided() {
        SsoProvider storedSsoProvider = oidcProvider();

        storedSsoProvider.setId(7L);
        storedSsoProvider.setRegistrationId("sso-0011223344556677");

        when(ssoProviderRepository.findById(7L)).thenReturn(Optional.of(storedSsoProvider));

        SsoProvider ssoProvider = oidcProvider();

        ssoProvider.setId(7L);
        ssoProvider.setClientSecret("rotated");

        assertThat(ssoProviderService.update(ssoProvider)
            .getClientSecret()).isEqualTo("rotated");
    }

    @Test
    void testFetchEnabledSsoProviderByDomain() {
        SsoProvider ssoProvider = oidcProvider();

        ssoProvider.setDomains(List.of("acme.com"));

        when(ssoProviderRepository.findAllByEnabled(true)).thenReturn(List.of(ssoProvider));

        assertThat(ssoProviderService.fetchEnabledSsoProviderByDomain(" ACME.com")).contains(ssoProvider);
        assertThat(ssoProviderService.fetchEnabledSsoProviderByDomain("sub.acme.com")).isEmpty();
        assertThat(ssoProviderService.fetchEnabledSsoProviderByDomain(null)).isEmpty();
    }

    @Test
    void testFetchSsoProviderByRegistrationIdIgnoresNonSsoIds() {
        assertThat(ssoProviderService.fetchSsoProviderByRegistrationId("google")).isEmpty();

        verify(ssoProviderRepository, never()).findByRegistrationId("google");
    }

    @Test
    void testCreateMicrosoftEntraProviderNormalizesTenantIssuer() {
        SsoProvider ssoProvider = microsoftEntraProvider(
            "https://login.microsoftonline.com/72F988BF-86F1-41AF-91AB-2D7CD011DB47/v2.0/");

        SsoProvider createdSsoProvider = ssoProviderService.create(ssoProvider);

        assertThat(createdSsoProvider.getIssuerUri())
            .isEqualTo("https://login.microsoftonline.com/72f988bf-86f1-41af-91ab-2d7cd011db47/v2.0");
        assertThat(createdSsoProvider.getScopes()).containsExactly("openid", "profile", "email");
        assertThat(createdSsoProvider.isMicrosoftEntraId()).isTrue();
    }

    @Test
    void testCreateMicrosoftEntraProviderAlwaysRequestsProfileScope() {
        SsoProvider ssoProvider = microsoftEntraProvider(
            "https://login.microsoftonline.com/72f988bf-86f1-41af-91ab-2d7cd011db47/v2.0");

        ssoProvider.setScopes(List.of("email"));

        assertThat(ssoProviderService.create(ssoProvider)
            .getScopes()).containsExactly("openid", "email", "profile");
    }

    @Test
    void testCreateMicrosoftEntraProviderRejectsMultiTenantAndNonGuidIssuers() {
        assertThatThrownBy(() -> ssoProviderService.create(
            microsoftEntraProvider("https://login.microsoftonline.com/common/v2.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single tenant");

        assertThatThrownBy(() -> ssoProviderService.create(
            microsoftEntraProvider("https://login.microsoftonline.com/organizations/v2.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single tenant");

        assertThatThrownBy(() -> ssoProviderService.create(
            microsoftEntraProvider("https://login.microsoftonline.com/contoso.onmicrosoft.com/v2.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GUID");

        verify(ssoProviderRepository, never()).save(any(SsoProvider.class));
    }

    @Test
    void testGenericProviderAcceptsMultiTenantLookingIssuer() {
        SsoProvider ssoProvider = oidcProvider();

        ssoProvider.setIssuerUri("https://login.microsoftonline.com/common/v2.0");

        assertThat(ssoProviderService.create(ssoProvider)
            .getVendor()).isEqualTo(SsoProviderVendor.GENERIC);
    }

    @Test
    void testSamlProviderIsAlwaysGenericVendor() {
        SsoProvider ssoProvider = samlProvider();

        ssoProvider.setVendor(SsoProviderVendor.MICROSOFT_ENTRA_ID);

        SsoProvider createdSsoProvider = ssoProviderService.create(ssoProvider);

        assertThat(createdSsoProvider.getVendor()).isEqualTo(SsoProviderVendor.GENERIC);
        assertThat(createdSsoProvider.isMicrosoftEntraId()).isFalse();
    }

    @Test
    void testToStringOmitsSecret() {
        assertThat(oidcProvider().toString()).doesNotContain("secret");
    }

    private static SsoProvider oidcProvider() {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setClientId("client");
        ssoProvider.setClientSecret("secret");
        ssoProvider.setDomains(List.of("Acme.com", "@acme.io", "acme.com"));
        ssoProvider.setEnabled(true);
        ssoProvider.setIssuerUri("https://idp.acme.com/realms/acme");
        ssoProvider.setName(" Acme ");
        ssoProvider.setType(SsoProviderType.OIDC);

        return ssoProvider;
    }

    private static SsoProvider microsoftEntraProvider(String issuerUri) {
        SsoProvider ssoProvider = oidcProvider();

        ssoProvider.setIssuerUri(issuerUri);
        ssoProvider.setVendor(SsoProviderVendor.MICROSOFT_ENTRA_ID);

        return ssoProvider;
    }

    private static SsoProvider samlProvider() {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setClientSecret("ignored");
        ssoProvider.setDomains(List.of("acme.com"));
        ssoProvider.setEnabled(true);
        ssoProvider.setIssuerUri("https://ignored.example.com");
        ssoProvider.setMetadataUrl("https://idp.acme.com/metadata");
        ssoProvider.setName("Acme SAML");
        ssoProvider.setType(SsoProviderType.SAML);

        return ssoProvider;
    }
}
