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

package com.bytechef.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.config.ApplicationProperties;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.user.domain.AuthenticationPolicy;
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.dto.AuthenticationPolicyDTO;
import com.bytechef.platform.user.repository.AuthenticationPolicyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Ivica Cardic
 */
class AuthenticationPolicyServiceTest {

    private ApplicationProperties applicationProperties;
    private AuthenticationPolicyRepository authenticationPolicyRepository;
    private AuthenticationPolicyService authenticationPolicyService;
    private AuthorityService authorityService;

    @BeforeEach
    void beforeEach() {
        applicationProperties = new ApplicationProperties();
        applicationProperties.getSignUp()
            .setEnabled(true);

        authenticationPolicyRepository = mock(AuthenticationPolicyRepository.class);
        authorityService = mock(AuthorityService.class);
        authenticationPolicyService = new AuthenticationPolicyServiceImpl(
            applicationProperties, authenticationPolicyRepository, authorityService);

        when(authenticationPolicyRepository.findById(AuthenticationPolicy.SINGLETON_ID)).thenReturn(Optional.empty());
    }

    @Test
    void testSelfSignUpFollowsTheEnvironmentUntilAChoiceIsSaved() {
        assertThat(authenticationPolicyService.isSelfSignUpEnabled()).isTrue();

        applicationProperties.getSignUp()
            .setEnabled(false);

        assertThat(authenticationPolicyService.isSelfSignUpEnabled()).isFalse();
        assertThat(authenticationPolicyService.getAuthenticationPolicy()
            .signUpOverridden()).isFalse();
    }

    @Test
    void testASavedChoiceWinsOverTheEnvironment() {
        AuthenticationPolicy authenticationPolicy = storedPolicy(false, false);

        when(authenticationPolicyRepository.findById(AuthenticationPolicy.SINGLETON_ID))
            .thenReturn(Optional.of(authenticationPolicy));

        applicationProperties.getSignUp()
            .setEnabled(true);

        AuthenticationPolicyDTO authenticationPolicyDTO = authenticationPolicyService.getAuthenticationPolicy();

        assertThat(authenticationPolicyDTO.signUpEnabled()).isFalse();
        assertThat(authenticationPolicyDTO.signUpEnvironmentDefault()).isTrue();
        assertThat(authenticationPolicyDTO.signUpOverridden()).isTrue();
    }

    @Test
    void testANullStoredSignUpStillFollowsTheEnvironment() {
        AuthenticationPolicy authenticationPolicy = storedPolicy(null, true);

        when(authenticationPolicyRepository.findById(AuthenticationPolicy.SINGLETON_ID))
            .thenReturn(Optional.of(authenticationPolicy));

        applicationProperties.getSignUp()
            .setEnabled(false);

        assertThat(authenticationPolicyService.isSelfSignUpEnabled()).isFalse();
        assertThat(authenticationPolicyService.isSsoOnlyEnabled()).isTrue();
        assertThat(authenticationPolicyService.getAuthenticationPolicy()
            .signUpOverridden()).isFalse();
    }

    @Test
    void testUpdateSelfSignUpPersistsTheChoice() {
        AuthenticationPolicy authenticationPolicy = storedPolicy(null, false);

        when(authenticationPolicyRepository.findById(AuthenticationPolicy.SINGLETON_ID))
            .thenReturn(Optional.of(authenticationPolicy));
        when(authenticationPolicyRepository.save(authenticationPolicy)).thenReturn(authenticationPolicy);

        authenticationPolicyService.updateSelfSignUpEnabled(false);

        assertThat(authenticationPolicy.getSignUpEnabled()).isFalse();
        assertThat(authenticationPolicy.isNew()).isFalse();

        verify(authenticationPolicyRepository).save(authenticationPolicy);
    }

    @Test
    void testPasswordLoginBreakGlassReadsTheEnvironment() {
        assertThat(authenticationPolicyService.isPasswordLoginBreakGlass()).isFalse();

        ApplicationProperties.Security security = new ApplicationProperties.Security();

        security.setPasswordLoginBreakGlass(true);
        applicationProperties.setSecurity(security);

        assertThat(authenticationPolicyService.isPasswordLoginBreakGlass()).isTrue();
    }

    @Test
    void testActivatedAdminRequiresTheAdminAuthority() {
        when(authorityService.fetchAuthority(1L)).thenReturn(Optional.of(new Authority().id(1L)
            .name(AuthorityConstants.ADMIN)));
        when(authorityService.fetchAuthority(2L)).thenReturn(Optional.of(new Authority().id(2L)
            .name(AuthorityConstants.USER)));

        User administrator = new User();

        administrator.setActivated(true);
        administrator.setAuthorityIds(List.of(1L));

        User user = new User();

        user.setActivated(true);
        user.setAuthorityIds(List.of(2L));

        User deactivatedAdministrator = new User();

        deactivatedAdministrator.setActivated(false);
        deactivatedAdministrator.setAuthorityIds(List.of(1L));

        assertThat(authenticationPolicyService.isActivatedAdmin(administrator)).isTrue();
        assertThat(authenticationPolicyService.isActivatedAdmin(user)).isFalse();
        assertThat(authenticationPolicyService.isActivatedAdmin(deactivatedAdministrator)).isFalse();
        assertThat(authenticationPolicyService.isActivatedAdmin(null)).isFalse();
    }

    private static AuthenticationPolicy storedPolicy(Boolean signUpEnabled, boolean ssoOnlyEnabled) {
        AuthenticationPolicy authenticationPolicy = new AuthenticationPolicy();

        authenticationPolicy.setId(AuthenticationPolicy.SINGLETON_ID);
        authenticationPolicy.setSignUpEnabled(signUpEnabled);
        authenticationPolicy.setSsoOnlyEnabled(ssoOnlyEnabled);

        return authenticationPolicy;
    }
}
