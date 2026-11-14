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

package com.bytechef.platform.sso.service;

import com.bytechef.platform.sso.domain.SsoProvider;
import java.util.List;
import java.util.Optional;

/**
 * Manages administrator-configured SSO providers.
 *
 * <p>
 * The mutating and listing methods are admin-only. The {@code fetch*} lookups are used by the unauthenticated login
 * path (discovery, registration resolution, password-login enforcement) and are therefore not access-controlled; they
 * never return disabled providers unless stated otherwise.
 */
public interface SsoProviderService {

    /**
     * Creates a provider. A random {@code registrationId} is generated; any value on the argument is ignored.
     */
    SsoProvider create(SsoProvider ssoProvider);

    void delete(long id);

    /**
     * Looks up an enabled provider serving the given email domain.
     */
    Optional<SsoProvider> fetchEnabledSsoProviderByDomain(String domain);

    /**
     * Looks up an enabled provider by its display name, ignoring case.
     */
    Optional<SsoProvider> fetchEnabledSsoProviderByName(String name);

    /**
     * Looks up a provider by its registration id, enabled or not.
     */
    Optional<SsoProvider> fetchSsoProviderByRegistrationId(String registrationId);

    SsoProvider getSsoProvider(long id);

    List<SsoProvider> getSsoProviders();

    /**
     * Updates the editable fields of a provider. The type and registration id cannot change. A {@code null} or blank
     * client secret on the argument keeps the stored secret (secrets are write-only).
     */
    SsoProvider update(SsoProvider ssoProvider);
}
