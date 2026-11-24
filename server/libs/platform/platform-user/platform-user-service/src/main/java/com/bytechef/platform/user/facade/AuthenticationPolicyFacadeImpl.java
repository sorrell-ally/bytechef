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

import com.bytechef.atlas.coordinator.annotation.ConditionalOnCoordinator;
import com.bytechef.exception.ConfigurationException;
import com.bytechef.platform.audit.AuditData;
import com.bytechef.platform.audit.Audited;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.user.audit.AuthenticationPolicyAuditEvents;
import com.bytechef.platform.user.dto.AuthenticationPolicyDTO;
import com.bytechef.platform.user.exception.UserErrorType;
import com.bytechef.platform.user.service.AuthenticationPolicyService;
import com.bytechef.platform.user.service.SsoSignInProof;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Ivica Cardic
 */
@Service
@Transactional
@ConditionalOnCoordinator
public class AuthenticationPolicyFacadeImpl implements AuthenticationPolicyFacade {

    private final AuthenticationPolicyService authenticationPolicyService;
    private final ObjectProvider<SsoSignInProof> ssoSignInProofProvider;

    @SuppressFBWarnings("EI")
    public AuthenticationPolicyFacadeImpl(
        AuthenticationPolicyService authenticationPolicyService,
        ObjectProvider<SsoSignInProof> ssoSignInProofProvider) {

        this.authenticationPolicyService = authenticationPolicyService;
        this.ssoSignInProofProvider = ssoSignInProofProvider;
    }

    @Override
    @PreAuthorize("hasAuthority(\"" + AuthorityConstants.ADMIN + "\")")
    public AuthenticationPolicyDTO getAuthenticationPolicy() {
        return authenticationPolicyService.getAuthenticationPolicy();
    }

    @Override
    @Audited(event = AuthenticationPolicyAuditEvents.SIGN_UP_ENABLED_UPDATED, data = {
        @AuditData(key = "enabled", value = "#enabled")
    })
    @PreAuthorize("hasAuthority(\"" + AuthorityConstants.ADMIN + "\")")
    public AuthenticationPolicyDTO updateSelfSignUpEnabled(boolean enabled) {
        authenticationPolicyService.updateSelfSignUpEnabled(enabled);

        return authenticationPolicyService.getAuthenticationPolicy();
    }

    @Override
    @Audited(event = AuthenticationPolicyAuditEvents.SSO_ONLY_ENABLED_UPDATED, data = {
        @AuditData(key = "enabled", value = "#enabled")
    })
    @PreAuthorize("hasAuthority(\"" + AuthorityConstants.ADMIN + "\")")
    public AuthenticationPolicyDTO updateSsoOnlyEnabled(boolean enabled) {
        if (enabled) {
            requireTestedSsoSignIn();
        }

        authenticationPolicyService.updateSsoOnlyEnabled(enabled);

        return authenticationPolicyService.getAuthenticationPolicy();
    }

    private void requireTestedSsoSignIn() {
        SsoSignInProof ssoSignInProof = ssoSignInProofProvider.getIfAvailable();

        if (ssoSignInProof == null || !ssoSignInProof.enabledProviderExists()) {
            throw new ConfigurationException(
                "Turn on SSO-only mode only when at least one single sign-on provider is enabled.",
                UserErrorType.SSO_ONLY_NOT_ALLOWED);
        }

        if (!ssoSignInProof.actingAdminSignedInThroughEnabledProvider()) {
            throw new ConfigurationException(
                "Turn on SSO-only mode only after you have signed in through an enabled single sign-on provider. " +
                    "Sign out, sign in with that provider, and try again.",
                UserErrorType.SSO_ONLY_NOT_ALLOWED);
        }
    }
}
