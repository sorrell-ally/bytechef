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

package com.bytechef.platform.user.facade;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.exception.ConfigurationException;
import com.bytechef.platform.user.dto.AuthenticationPolicyDTO;
import com.bytechef.platform.user.service.AuthenticationPolicyService;
import com.bytechef.platform.user.service.SsoSignInProof;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * @author Ivica Cardic
 */
class AuthenticationPolicyFacadeTest {

    private AuthenticationPolicyFacade authenticationPolicyFacade;
    private AuthenticationPolicyService authenticationPolicyService;
    private SsoSignInProof ssoSignInProof;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
        authenticationPolicyService = mock(AuthenticationPolicyService.class);
        ssoSignInProof = mock(SsoSignInProof.class);

        ObjectProvider<SsoSignInProof> ssoSignInProofProvider = mock(ObjectProvider.class);

        when(ssoSignInProofProvider.getIfAvailable()).thenReturn(ssoSignInProof);
        when(authenticationPolicyService.getAuthenticationPolicy()).thenReturn(
            new AuthenticationPolicyDTO(false, true, true, false, false));

        authenticationPolicyFacade = new AuthenticationPolicyFacadeImpl(
            authenticationPolicyService, ssoSignInProofProvider);
    }

    @Test
    void testTurningSsoOnlyOffDoesNotRequireAProvider() {
        authenticationPolicyFacade.updateSsoOnlyEnabled(false);

        verify(authenticationPolicyService).updateSsoOnlyEnabled(false);
        verify(ssoSignInProof, never()).enabledProviderExists();
    }

    @Test
    void testTurningSsoOnlyOnRequiresAnEnabledProviderTheAdminHasUsed() {
        when(ssoSignInProof.enabledProviderExists()).thenReturn(true);
        when(ssoSignInProof.actingAdminSignedInThroughEnabledProvider()).thenReturn(true);

        authenticationPolicyFacade.updateSsoOnlyEnabled(true);

        verify(authenticationPolicyService).updateSsoOnlyEnabled(true);
    }

    @Test
    void testTurningSsoOnlyOnFailsWhenNoProviderIsEnabled() {
        when(ssoSignInProof.enabledProviderExists()).thenReturn(false);

        assertThatThrownBy(() -> authenticationPolicyFacade.updateSsoOnlyEnabled(true))
            .isInstanceOf(ConfigurationException.class);

        verify(authenticationPolicyService, never()).updateSsoOnlyEnabled(true);
    }

    @Test
    void testTurningSsoOnlyOnFailsWhenTheAdminHasNotSignedInWithSso() {
        when(ssoSignInProof.enabledProviderExists()).thenReturn(true);
        when(ssoSignInProof.actingAdminSignedInThroughEnabledProvider()).thenReturn(false);

        assertThatThrownBy(() -> authenticationPolicyFacade.updateSsoOnlyEnabled(true))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("signed in");

        verify(authenticationPolicyService, never()).updateSsoOnlyEnabled(true);
    }

    @Test
    void testTurningSsoOnlyOnFailsWhenSsoIsNotAvailable() {
        authenticationPolicyFacade = new AuthenticationPolicyFacadeImpl(
            authenticationPolicyService, emptyProofProvider());

        assertThatThrownBy(() -> authenticationPolicyFacade.updateSsoOnlyEnabled(true))
            .isInstanceOf(ConfigurationException.class);

        verify(authenticationPolicyService, never()).updateSsoOnlyEnabled(true);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SsoSignInProof> emptyProofProvider() {
        ObjectProvider<SsoSignInProof> ssoSignInProofProvider = mock(ObjectProvider.class);

        when(ssoSignInProofProvider.getIfAvailable()).thenReturn(null);

        return ssoSignInProofProvider;
    }
}
