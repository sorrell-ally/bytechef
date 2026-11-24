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

package com.bytechef.platform.sso.web.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.domain.SsoProviderVendor;
import com.bytechef.platform.sso.web.registration.SsoRegistrationCache;
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.service.AuthorityService;
import com.bytechef.platform.user.service.PendingUserInvitation;
import com.bytechef.platform.user.service.UserService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Login through {@link SsoOidcUserService} with real {@link SsoUserResolver} rules, for Microsoft Entra ID and for
 * generic OIDC providers.
 */
class SsoOidcUserServiceTest {

    private static final String ENTRA_ISSUER =
        "https://login.microsoftonline.com/72f988bf-86f1-41af-91ab-2d7cd011db47/v2.0";
    private static final String ENTRA_TENANT_ID = "72f988bf-86f1-41af-91ab-2d7cd011db47";
    private static final String OBJECT_ID = "00000000-0000-0000-66f3-3332eca7ea81";
    private static final String REGISTRATION_ID = "sso-0011223344556677";

    private SsoProvider ssoProvider;
    private SsoOidcUserService ssoOidcUserService;
    private UserService userService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
        AuthorityService authorityService = mock(AuthorityService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SsoRegistrationCache ssoRegistrationCache = mock(SsoRegistrationCache.class);

        userService = mock(UserService.class);

        when(authorityService.getAuthorities()).thenReturn(
            List.of(new Authority().id(2L)
                .name(AuthorityConstants.USER)));
        when(authorityService.fetchAuthority(2L)).thenReturn(
            Optional.of(new Authority().id(2L)
                .name(AuthorityConstants.USER)));
        when(passwordEncoder.encode(anyString())).thenReturn("{hashed}");
        when(userService.fetchUserByAuthProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.empty());
        when(userService.fetchUserByEmail(anyString())).thenReturn(Optional.empty());
        when(ssoRegistrationCache.findEnabledSsoProvider(REGISTRATION_ID)).thenAnswer(
            invocation -> Optional.of(ssoProvider));

        ssoProvider = entraProvider();

        ObjectProvider<PendingUserInvitation> pendingUserInvitations = mock(ObjectProvider.class);

        SsoUserResolver ssoUserResolver = new SsoUserResolver(
            authorityService, passwordEncoder, pendingUserInvitations, userService);

        ssoOidcUserService = new SsoOidcUserService(
            oidcUserRequest -> new DefaultOidcUser(List.of(), oidcUserRequest.getIdToken()),
            oidcUserRequest -> {
                throw new IllegalStateException("fallback must not be used for SSO registrations");
            },
            ssoRegistrationCache, ssoUserResolver);
    }

    @Test
    void testEntraLoginWithOnlyPreferredUsername() {
        Map<String, Object> claims = entraClaims();

        claims.put("preferred_username", "Jane.Doe@Acme.com");

        OidcUser oidcUser = ssoOidcUserService.loadUser(request(claims));

        User provisionedUser = savedUser();

        assertThat(oidcUser.getName()).isEqualTo("jane.doe@acme.com");
        assertThat(provisionedUser.getEmail()).isEqualTo("jane.doe@acme.com");
        assertThat(provisionedUser.getProviderId()).isEqualTo(OBJECT_ID);
        assertThat(provisionedUser.getAuthProvider()).isEqualTo("SSO-0011223344556677");
    }

    @Test
    void testEntraLoginWithDomainVerifiedEmailClaim() {
        Map<String, Object> claims = entraClaims();

        claims.put("email", "jane@acme.com");
        claims.put("preferred_username", "jdoe@acme.onmicrosoft.com");
        claims.put("xms_edov", true);

        ssoOidcUserService.loadUser(request(claims));

        assertThat(savedUser().getEmail()).isEqualTo("jane@acme.com");
    }

    @Test
    void testEntraIgnoresEmailClaimWithoutDomainVerification() {
        Map<String, Object> claims = entraClaims();

        claims.put("email", "ceo@acme.com");
        claims.put("preferred_username", "intern@acme.com");

        ssoOidcUserService.loadUser(request(claims));

        assertThat(savedUser().getEmail()).isEqualTo("intern@acme.com");
    }

    @Test
    void testEntraRefusesTenantMismatch() {
        Map<String, Object> claims = entraClaims();

        claims.put("preferred_username", "jane@acme.com");
        claims.put("tid", "11111111-2222-3333-4444-555555555555");

        assertLoginRefused(claims, SsoLoginError.TENANT_MISMATCH);

        claims.remove("tid");

        assertLoginRefused(claims, SsoLoginError.TENANT_MISMATCH);
    }

    @Test
    void testEntraRefusesDomainOutsideProviderDomains() {
        Map<String, Object> claims = entraClaims();

        claims.put("preferred_username", "victim@other.com");

        assertLoginRefused(claims, SsoLoginError.EMAIL_DOMAIN_NOT_ALLOWED);
    }

    @Test
    void testEntraRefusesGuestAccounts() {
        Map<String, Object> claims = entraClaims();

        claims.put("idp", "https://sts.windows.net/99999999-9999-9999-9999-999999999999/");
        claims.put("preferred_username", "jane@acme.com");

        assertLoginRefused(claims, SsoLoginError.GUEST_ACCOUNT_NOT_ALLOWED);

        claims.remove("idp");
        claims.put("acct", 1);

        assertLoginRefused(claims, SsoLoginError.GUEST_ACCOUNT_NOT_ALLOWED);
    }

    @Test
    void testEntraRefusesNonEmailUsernameWithoutVerifiedEmail() {
        Map<String, Object> claims = entraClaims();

        claims.put("email", "jane@acme.com");
        claims.put("preferred_username", "+15551234567");

        assertLoginRefused(claims, SsoLoginError.EMAIL_MISSING);
    }

    @Test
    void testGenericOidcStillRequiresEmailVerified() {
        ssoProvider = genericProvider();

        Map<String, Object> claims = new HashMap<>();

        claims.put("sub", "generic-subject");
        claims.put("email", "jane@acme.com");
        claims.put("preferred_username", "jane@acme.com");
        claims.put("tid", ENTRA_TENANT_ID);

        assertLoginRefused(claims, SsoLoginError.EMAIL_NOT_VERIFIED);

        claims.put("email_verified", true);

        ssoOidcUserService.loadUser(request(claims));

        User provisionedUser = savedUser();

        assertThat(provisionedUser.getEmail()).isEqualTo("jane@acme.com");
        assertThat(provisionedUser.getProviderId()).isEqualTo("generic-subject");
    }

    private void assertLoginRefused(Map<String, Object> claims, SsoLoginError ssoLoginError) {
        assertThatThrownBy(() -> ssoOidcUserService.loadUser(request(claims)))
            .isInstanceOfSatisfying(
                OAuth2AuthenticationException.class,
                oAuth2AuthenticationException -> assertThat(oAuth2AuthenticationException.getError()
                    .getErrorCode()).isEqualTo(ssoLoginError.getCode()));

        verify(userService, never()).save(any(User.class));
    }

    private User savedUser() {
        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);

        verify(userService).save(userArgumentCaptor.capture());

        return userArgumentCaptor.getValue();
    }

    private static Map<String, Object> entraClaims() {
        Map<String, Object> claims = new HashMap<>();

        claims.put("iss", ENTRA_ISSUER);
        claims.put("oid", OBJECT_ID);
        claims.put("sub", "pairwise-subject");
        claims.put("tid", ENTRA_TENANT_ID);

        return claims;
    }

    private static SsoProvider entraProvider() {
        SsoProvider ssoProvider = genericProvider();

        ssoProvider.setIssuerUri(ENTRA_ISSUER);
        ssoProvider.setVendor(SsoProviderVendor.MICROSOFT_ENTRA_ID);

        return ssoProvider;
    }

    private static SsoProvider genericProvider() {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setAutoProvision(true);
        ssoProvider.setDefaultAuthority(AuthorityConstants.USER);
        ssoProvider.setDomains(List.of("acme.com"));
        ssoProvider.setEnabled(true);
        ssoProvider.setIssuerUri("https://idp.acme.com");
        ssoProvider.setName("Acme");
        ssoProvider.setRegistrationId(REGISTRATION_ID);
        ssoProvider.setType(SsoProviderType.OIDC);

        return ssoProvider;
    }

    private static OidcUserRequest request(Map<String, Object> claims) {
        Instant issuedAt = Instant.now();

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationUri("https://idp.example.com/authorize")
            .clientId("client")
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .tokenUri("https://idp.example.com/token")
            .build();

        return new OidcUserRequest(
            clientRegistration,
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-token", issuedAt, issuedAt.plusSeconds(60)),
            new OidcIdToken("id-token", issuedAt, issuedAt.plusSeconds(60), new HashMap<>(claims)));
    }
}
