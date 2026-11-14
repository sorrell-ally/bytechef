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
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.service.AuthorityService;
import com.bytechef.platform.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class SsoUserResolverTest {

    private static final String AUTH_PROVIDER = "SSO-0011223344556677";
    private static final String SUBJECT = "subject-1";

    private AuthorityService authorityService;
    private SsoUserResolver ssoUserResolver;
    private UserService userService;

    @BeforeEach
    void beforeEach() {
        authorityService = mock(AuthorityService.class);
        userService = mock(UserService.class);

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        when(passwordEncoder.encode(anyString())).thenReturn("{hashed}");
        when(authorityService.getAuthorities()).thenReturn(
            List.of(
                new Authority().id(1L)
                    .name(AuthorityConstants.ADMIN),
                new Authority().id(2L)
                    .name(AuthorityConstants.USER)));
        when(userService.fetchUserByAuthProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.empty());
        when(userService.fetchUserByEmail(anyString())).thenReturn(Optional.empty());

        ssoUserResolver = new SsoUserResolver(authorityService, passwordEncoder, userService);
    }

    @Test
    void testResolveLinkedUser() {
        User user = user("LOCAL", null, true);

        user.setAuthProvider(AUTH_PROVIDER);
        user.setProviderId(SUBJECT);

        when(userService.fetchUserByAuthProviderAndProviderId(AUTH_PROVIDER, SUBJECT)).thenReturn(Optional.of(user));

        User resolvedUser = ssoUserResolver.resolveUser(
            ssoProvider(false), new SsoIdentity(SUBJECT, "jane@other.com", false, "Jane", null));

        assertThat(resolvedUser).isSameAs(user);
        assertThat(resolvedUser.getFirstName()).isEqualTo("Jane");
    }

    @Test
    void testResolveLinkedUserRejectsDeactivatedAccount() {
        User user = user(AUTH_PROVIDER, SUBJECT, false);

        when(userService.fetchUserByAuthProviderAndProviderId(AUTH_PROVIDER, SUBJECT)).thenReturn(Optional.of(user));

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(false), identity("jane@acme.com", true)),
            SsoLoginError.ACCOUNT_DISABLED);
    }

    @Test
    void testLinkLocalUserByVerifiedEmailInServedDomain() {
        User user = user("LOCAL", null, true);

        when(userService.fetchUserByEmail("jane@acme.com")).thenReturn(Optional.of(user));

        User resolvedUser = ssoUserResolver.resolveUser(ssoProvider(false), identity("Jane@ACME.com", true));

        assertThat(resolvedUser.getAuthProvider()).isEqualTo(AUTH_PROVIDER);
        assertThat(resolvedUser.getProviderId()).isEqualTo(SUBJECT);

        verify(userService).save(user);
    }

    @Test
    void testLinkActivatesInvitedUser() {
        User user = user("LOCAL", null, false);

        user.setInvited(true);
        user.setActivationKey("key");

        when(userService.fetchUserByEmail("jane@acme.com")).thenReturn(Optional.of(user));

        User resolvedUser = ssoUserResolver.resolveUser(ssoProvider(false), identity("jane@acme.com", true));

        assertThat(resolvedUser.isActivated()).isTrue();
        assertThat(resolvedUser.getActivationKey()).isNull();
    }

    @Test
    void testLinkRefusesUnverifiedEmail() {
        when(userService.fetchUserByEmail("jane@acme.com")).thenReturn(Optional.of(user("LOCAL", null, true)));

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(true), identity("jane@acme.com", false)),
            SsoLoginError.EMAIL_NOT_VERIFIED);

        verify(userService, never()).save(any(User.class));
    }

    @Test
    void testLinkRefusesEmailOutsideServedDomains() {
        when(userService.fetchUserByEmail("admin@victim.com")).thenReturn(Optional.of(user("LOCAL", null, true)));

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(true), identity("admin@victim.com", true)),
            SsoLoginError.EMAIL_DOMAIN_NOT_ALLOWED);

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(true), identity("admin@sub.acme.com", true)),
            SsoLoginError.EMAIL_DOMAIN_NOT_ALLOWED);

        verify(userService, never()).save(any(User.class));
    }

    @Test
    void testLinkRefusesAccountLinkedToAnotherIdentity() {
        when(userService.fetchUserByEmail("jane@acme.com")).thenReturn(
            Optional.of(user("GOOGLE", "google-subject", true)));

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(false), identity("jane@acme.com", true)),
            SsoLoginError.ACCOUNT_LINKED_ELSEWHERE);

        when(userService.fetchUserByEmail("jane@acme.com")).thenReturn(
            Optional.of(user(AUTH_PROVIDER, "other-subject", true)));

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(false), identity("jane@acme.com", true)),
            SsoLoginError.ACCOUNT_LINKED_ELSEWHERE);
    }

    @Test
    void testLinkRefusesDeactivatedNonInvitedAccount() {
        when(userService.fetchUserByEmail("jane@acme.com")).thenReturn(Optional.of(user("LOCAL", null, false)));

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(false), identity("jane@acme.com", true)),
            SsoLoginError.ACCOUNT_DISABLED);
    }

    @Test
    void testUnknownUserRejectedWhenAutoProvisionOff() {
        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(false), identity("jane@acme.com", true)),
            SsoLoginError.ACCOUNT_NOT_PROVISIONED);

        verify(userService, never()).save(any(User.class));
    }

    @Test
    void testProvisionUserWithDefaultAuthority() {
        User user = ssoUserResolver.resolveUser(ssoProvider(true), identity("New.User@Acme.com", true));

        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);

        verify(userService).save(userArgumentCaptor.capture());

        User savedUser = userArgumentCaptor.getValue();

        assertThat(savedUser).isSameAs(user);
        assertThat(savedUser.getLogin()).isEqualTo("new.user@acme.com");
        assertThat(savedUser.getEmail()).isEqualTo("new.user@acme.com");
        assertThat(savedUser.getAuthProvider()).isEqualTo(AUTH_PROVIDER);
        assertThat(savedUser.getProviderId()).isEqualTo(SUBJECT);
        assertThat(savedUser.isActivated()).isTrue();
        assertThat(savedUser.getAuthorityIds()).containsExactly(2L);
    }

    @Test
    void testProvisionRefusesUnverifiedEmail() {
        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(true), identity("jane@acme.com", false)),
            SsoLoginError.EMAIL_NOT_VERIFIED);
    }

    @Test
    void testProvisionFailsWhenAuthorityMissing() {
        when(authorityService.getAuthorities()).thenReturn(List.of());

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(true), identity("jane@acme.com", true)),
            SsoLoginError.PROVISIONING_FAILED);
    }

    @Test
    void testRejectsMissingSubjectAndEmail() {
        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(true), new SsoIdentity(" ", "jane@acme.com", true, null,
                null)),
            SsoLoginError.INVALID_IDENTITY);

        assertSsoLoginError(
            () -> ssoUserResolver.resolveUser(ssoProvider(true), new SsoIdentity(SUBJECT, null, true, null, null)),
            SsoLoginError.EMAIL_MISSING);
    }

    @Test
    void testGetGrantedAuthorities() {
        User user = user("LOCAL", null, true);

        user.setAuthorityIds(List.of(1L));

        when(authorityService.fetchAuthority(1L)).thenReturn(
            Optional.of(new Authority().id(1L)
                .name(AuthorityConstants.ADMIN)));

        assertThat(ssoUserResolver.getGrantedAuthorities(user))
            .extracting(grantedAuthority -> grantedAuthority.getAuthority())
            .containsExactly(AuthorityConstants.ADMIN);
    }

    private static void assertSsoLoginError(Runnable runnable, SsoLoginError ssoLoginError) {
        assertThatThrownBy(runnable::run)
            .isInstanceOfSatisfying(
                SsoLoginException.class,
                ssoLoginException -> assertThat(ssoLoginException.getSsoLoginError()).isEqualTo(ssoLoginError));
    }

    private static SsoIdentity identity(String email, boolean emailVerified) {
        return new SsoIdentity(SUBJECT, email, emailVerified, null, null);
    }

    private static SsoProvider ssoProvider(boolean autoProvision) {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setAutoProvision(autoProvision);
        ssoProvider.setDefaultAuthority(AuthorityConstants.USER);
        ssoProvider.setDomains(List.of("acme.com"));
        ssoProvider.setEnabled(true);
        ssoProvider.setName("Acme");
        ssoProvider.setRegistrationId("sso-0011223344556677");
        ssoProvider.setType(SsoProviderType.OIDC);

        return ssoProvider;
    }

    private static User user(String authProvider, String providerId, boolean activated) {
        User user = new User();

        user.setActivated(activated);
        user.setAuthProvider(authProvider);
        user.setEmail("jane@acme.com");
        user.setId(42L);
        user.setLogin("jane@acme.com");
        user.setProviderId(providerId);

        return user;
    }
}
