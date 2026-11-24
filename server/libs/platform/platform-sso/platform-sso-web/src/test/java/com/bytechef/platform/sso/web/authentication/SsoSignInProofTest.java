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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.user.constant.UserConstants;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Ivica Cardic
 */
class SsoSignInProofTest {

    private SsoProviderService ssoProviderService;
    private SsoSignInProofImpl ssoSignInProof;
    private UserService userService;

    @BeforeEach
    void beforeEach() {
        ssoProviderService = mock(SsoProviderService.class);
        userService = mock(UserService.class);
        ssoSignInProof = new SsoSignInProofImpl(ssoProviderService, userService);
    }

    @Test
    void testEnabledProviderExists() {
        when(ssoProviderService.getSsoProviders()).thenReturn(List.of(ssoProvider(false), ssoProvider(true)));

        assertThat(ssoSignInProof.enabledProviderExists()).isTrue();
    }

    @Test
    void testADisabledProviderDoesNotCount() {
        when(ssoProviderService.getSsoProviders()).thenReturn(List.of(ssoProvider(false)));

        assertThat(ssoSignInProof.enabledProviderExists()).isFalse();
    }

    @Test
    void testActingAdminMustBeLinkedToAnEnabledProvider() {
        SsoProvider enabledProvider = ssoProvider(true);
        User user = new User();

        user.setAuthProvider(enabledProvider.getAuthProvider());

        when(ssoProviderService.getSsoProviders()).thenReturn(List.of(enabledProvider));
        when(userService.getCurrentUser()).thenReturn(user);

        assertThat(ssoSignInProof.actingAdminSignedInThroughEnabledProvider()).isTrue();

        user.setAuthProvider(UserConstants.AUTH_PROVIDER_LOCAL);

        assertThat(ssoSignInProof.actingAdminSignedInThroughEnabledProvider()).isFalse();
    }

    private static SsoProvider ssoProvider(boolean enabled) {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setEnabled(enabled);
        ssoProvider.setRegistrationId("sso-0011223344556677");

        return ssoProvider;
    }
}
