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

import org.jspecify.annotations.Nullable;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;

/**
 * Database-backed {@link RelyingPartyRegistrationRepository}. Not a Spring bean, for the same reason as
 * {@link SsoClientRegistrationRepository}; it is handed to {@code saml2Login} and {@code saml2Metadata} directly.
 */
public class SsoRelyingPartyRegistrationRepository implements RelyingPartyRegistrationRepository {

    private final SsoRegistrationCache ssoRegistrationCache;

    public SsoRelyingPartyRegistrationRepository(SsoRegistrationCache ssoRegistrationCache) {
        this.ssoRegistrationCache = ssoRegistrationCache;
    }

    @Override
    public @Nullable RelyingPartyRegistration findByRegistrationId(String registrationId) {
        if (!SsoClientRegistrationRepository.isSsoRegistrationId(registrationId)) {
            return null;
        }

        return ssoRegistrationCache.findRelyingPartyRegistration(registrationId);
    }
}
