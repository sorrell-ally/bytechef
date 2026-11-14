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

package com.bytechef.platform.sso.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.service.AuthorityService;
import com.bytechef.platform.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SsoPasswordLoginEnforcementFilterTest {

    private SsoPasswordLoginEnforcementFilter ssoPasswordLoginEnforcementFilter;
    private SsoProviderService ssoProviderService;
    private UserService userService;

    @BeforeEach
    void beforeEach() {
        AuthorityService authorityService = mock(AuthorityService.class);

        ssoProviderService = mock(SsoProviderService.class);
        userService = mock(UserService.class);

        when(authorityService.fetchAuthority(1L)).thenReturn(
            Optional.of(new Authority().id(1L)
                .name(AuthorityConstants.ADMIN)));
        when(authorityService.fetchAuthority(2L)).thenReturn(
            Optional.of(new Authority().id(2L)
                .name(AuthorityConstants.USER)));
        when(ssoProviderService.fetchEnabledSsoProviderByDomain(anyString())).thenReturn(Optional.empty());
        when(userService.fetchUserByEmail(anyString())).thenReturn(Optional.empty());
        when(userService.fetchUserByLogin(anyString())).thenReturn(Optional.empty());

        ssoPasswordLoginEnforcementFilter = new SsoPasswordLoginEnforcementFilter(
            authorityService, ssoProviderService, userService);
    }

    @Test
    void testRefusesPasswordLoginForEnforcedDomain() throws Exception {
        when(ssoProviderService.fetchEnabledSsoProviderByDomain("acme.com")).thenReturn(
            Optional.of(ssoProvider(true)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        ssoPasswordLoginEnforcementFilter.doFilter(loginRequest("Jane@Acme.com"), response, filterChain);

        assertThat(filterChain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"error\":\"sso_required\"")
            .contains("\"providerName\":\"Acme \\\"Corp\\\"\"")
            .contains("\"redirectUrl\":\"/oauth2/authorization/sso-0011223344556677\"");
    }

    @Test
    void testRefusesEvenWhenUserDoesNotExist() throws Exception {
        when(ssoProviderService.fetchEnabledSsoProviderByDomain("acme.com")).thenReturn(
            Optional.of(ssoProvider(true)));

        MockHttpServletResponse response = new MockHttpServletResponse();

        ssoPasswordLoginEnforcementFilter.doFilter(
            loginRequest("nobody@acme.com"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void testResolvesDomainFromLoginName() throws Exception {
        when(ssoProviderService.fetchEnabledSsoProviderByDomain("acme.com")).thenReturn(
            Optional.of(ssoProvider(true)));
        when(userService.fetchUserByLogin("jane")).thenReturn(Optional.of(user("LOCAL", 2L)));

        MockHttpServletResponse response = new MockHttpServletResponse();

        ssoPasswordLoginEnforcementFilter.doFilter(loginRequest("jane"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void testAllowsLocalAdminBreakGlass() throws Exception {
        when(ssoProviderService.fetchEnabledSsoProviderByDomain("acme.com")).thenReturn(
            Optional.of(ssoProvider(true)));
        when(userService.fetchUserByEmail("admin@acme.com")).thenReturn(Optional.of(user("LOCAL", 1L)));

        MockFilterChain filterChain = new MockFilterChain();

        ssoPasswordLoginEnforcementFilter.doFilter(
            loginRequest("admin@acme.com"), new MockHttpServletResponse(), filterChain);

        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    void testRefusesSsoLinkedAdmin() throws Exception {
        when(ssoProviderService.fetchEnabledSsoProviderByDomain("acme.com")).thenReturn(
            Optional.of(ssoProvider(true)));
        when(userService.fetchUserByEmail("admin@acme.com")).thenReturn(
            Optional.of(user("SSO-0011223344556677", 1L)));

        MockHttpServletResponse response = new MockHttpServletResponse();

        ssoPasswordLoginEnforcementFilter.doFilter(loginRequest("admin@acme.com"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void testAllowsWhenProviderDoesNotEnforce() throws Exception {
        when(ssoProviderService.fetchEnabledSsoProviderByDomain("acme.com")).thenReturn(
            Optional.of(ssoProvider(false)));

        MockFilterChain filterChain = new MockFilterChain();

        ssoPasswordLoginEnforcementFilter.doFilter(
            loginRequest("jane@acme.com"), new MockHttpServletResponse(), filterChain);

        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    void testIgnoresOtherRequests() throws Exception {
        when(ssoProviderService.fetchEnabledSsoProviderByDomain("acme.com")).thenReturn(
            Optional.of(ssoProvider(true)));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/account");

        request.setServletPath("/api/account");
        request.setParameter("username", "jane@acme.com");

        MockFilterChain filterChain = new MockFilterChain();

        ssoPasswordLoginEnforcementFilter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(filterChain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest loginRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST", SsoPasswordLoginEnforcementFilter.LOGIN_PROCESSING_PATH);

        request.setServletPath(SsoPasswordLoginEnforcementFilter.LOGIN_PROCESSING_PATH);
        request.setParameter("username", username);
        request.setParameter("password", "password");

        return request;
    }

    private static SsoProvider ssoProvider(boolean enforceSso) {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setDomains(List.of("acme.com"));
        ssoProvider.setEnabled(true);
        ssoProvider.setEnforceSso(enforceSso);
        ssoProvider.setName("Acme \"Corp\"");
        ssoProvider.setRegistrationId("sso-0011223344556677");
        ssoProvider.setType(SsoProviderType.OIDC);

        return ssoProvider;
    }

    private static User user(String authProvider, long authorityId) {
        User user = new User();

        user.setActivated(true);
        user.setAuthProvider(authProvider);
        user.setAuthorityIds(List.of(authorityId));
        user.setEmail("admin@acme.com");
        user.setLogin("jane");

        return user;
    }
}
