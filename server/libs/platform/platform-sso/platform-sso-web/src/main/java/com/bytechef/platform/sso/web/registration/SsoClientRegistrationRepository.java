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

import com.bytechef.platform.sso.util.SsoProviderPathUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * The one {@link ClientRegistrationRepository} {@code oauth2Login} uses when SSO is on. Ids with the SSO prefix are
 * resolved from the database; every other id (the social-login {@code google}/{@code github} registrations) goes to the
 * social repository when one exists. The two namespaces cannot overlap: SSO ids are generated with the {@code sso-}
 * prefix and social ids are fixed literals.
 *
 * <p>
 * Deliberately not a Spring bean: a second {@code ClientRegistrationRepository} bean would make Boot's OAuth2 client
 * auto-configuration ambiguous whenever social login is also enabled.
 */
public class SsoClientRegistrationRepository implements ClientRegistrationRepository {

    public static final String REGISTRATION_ID_PREFIX = "sso-";

    private final @Nullable ClientRegistrationRepository fallbackClientRegistrationRepository;
    private final SsoRegistrationCache ssoRegistrationCache;

    public SsoClientRegistrationRepository(
        SsoRegistrationCache ssoRegistrationCache,
        @Nullable ClientRegistrationRepository fallbackClientRegistrationRepository) {

        this.fallbackClientRegistrationRepository = fallbackClientRegistrationRepository;
        this.ssoRegistrationCache = ssoRegistrationCache;
    }

    public static boolean isSsoRegistrationId(@Nullable String registrationId) {
        return registrationId != null && registrationId.startsWith(REGISTRATION_ID_PREFIX);
    }

    public static boolean isSsoCallbackPath(@Nullable String requestUri) {
        return requestUri != null &&
            (requestUri.startsWith(SsoProviderPathUtils.OIDC_CALLBACK_PATH + REGISTRATION_ID_PREFIX) ||
                requestUri.startsWith(SsoProviderPathUtils.SAML_ASSERTION_CONSUMER_PATH));
    }

    @Override
    public @Nullable ClientRegistration findByRegistrationId(String registrationId) {
        if (isSsoRegistrationId(registrationId)) {
            return ssoRegistrationCache.findClientRegistration(registrationId);
        }

        if (fallbackClientRegistrationRepository == null) {
            return null;
        }

        return fallbackClientRegistrationRepository.findByRegistrationId(registrationId);
    }
}
