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

import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.user.constant.UserConstants;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.service.SsoSignInProof;
import com.bytechef.platform.user.service.UserService;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A provider counts as tested when the acting administrator has completed a sign-in through it, which links
 * {@code user.auth_provider} to the provider's registration id.
 *
 * @author Ivica Cardic
 */
@Component
@ConditionalOnCEVersion
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.sso", name = "enabled", havingValue = "true")
public class SsoSignInProofImpl implements SsoSignInProof {

    private final SsoProviderService ssoProviderService;
    private final UserService userService;

    @SuppressFBWarnings("EI")
    public SsoSignInProofImpl(SsoProviderService ssoProviderService, UserService userService) {
        this.ssoProviderService = ssoProviderService;
        this.userService = userService;
    }

    @Override
    public boolean actingAdminSignedInThroughEnabledProvider() {
        User user = userService.getCurrentUser();

        String authProvider = user.getAuthProvider();

        if (authProvider == null || UserConstants.AUTH_PROVIDER_LOCAL.equals(authProvider)) {
            return false;
        }

        return ssoProviderService.getSsoProviders()
            .stream()
            .filter(SsoProvider::isEnabled)
            .anyMatch(ssoProvider -> authProvider.equals(ssoProvider.getAuthProvider()));
    }

    @Override
    public boolean enabledProviderExists() {
        return ssoProviderService.getSsoProviders()
            .stream()
            .anyMatch(SsoProvider::isEnabled);
    }
}
