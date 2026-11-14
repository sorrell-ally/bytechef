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

import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.service.SsoProviderService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;

/**
 * Resolves Spring Security registrations from the database on every lookup, caching only the expensive part (the remote
 * discovery document or metadata).
 *
 * <p>
 * Invalidation needs no events: each lookup reads the provider row (a primary-key-sized query) and a cached
 * registration is reused only while the row's {@code version} is unchanged and the entry is younger than the refresh
 * interval. Editing, disabling or deleting a provider therefore takes effect on the next request on every node, with no
 * restart. When a periodic refresh of an unchanged provider fails, the previous registration keeps serving so an IdP
 * outage of its metadata endpoint does not break logins that would otherwise verify.
 */
public class SsoRegistrationCache {

    static final Duration REFRESH_INTERVAL = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(SsoRegistrationCache.class);

    private final Map<String, CachedRegistration> cachedRegistrations = new ConcurrentHashMap<>();
    private final Clock clock;
    private final SsoProviderService ssoProviderService;
    private final SsoRegistrationFactory ssoRegistrationFactory;

    public SsoRegistrationCache(
        SsoProviderService ssoProviderService, SsoRegistrationFactory ssoRegistrationFactory) {

        this(ssoProviderService, ssoRegistrationFactory, Clock.systemUTC());
    }

    SsoRegistrationCache(
        SsoProviderService ssoProviderService, SsoRegistrationFactory ssoRegistrationFactory, Clock clock) {

        this.clock = clock;
        this.ssoProviderService = ssoProviderService;
        this.ssoRegistrationFactory = ssoRegistrationFactory;
    }

    public @Nullable ClientRegistration findClientRegistration(String registrationId) {
        return (ClientRegistration) find(
            registrationId, SsoProviderType.OIDC, ssoRegistrationFactory::createClientRegistration);
    }

    /**
     * @return the enabled provider behind a registration id, or empty
     */
    public Optional<SsoProvider> findEnabledSsoProvider(String registrationId) {
        return ssoProviderService.fetchSsoProviderByRegistrationId(registrationId)
            .filter(SsoProvider::isEnabled);
    }

    public @Nullable RelyingPartyRegistration findRelyingPartyRegistration(String registrationId) {
        return (RelyingPartyRegistration) find(
            registrationId, SsoProviderType.SAML, ssoRegistrationFactory::createRelyingPartyRegistration);
    }

    private @Nullable Object find(
        String registrationId, SsoProviderType type, Function<SsoProvider, Object> registrationFunction) {

        Optional<SsoProvider> optionalSsoProvider = findEnabledSsoProvider(registrationId);

        if (optionalSsoProvider.isEmpty() || optionalSsoProvider.get()
            .getType() != type) {

            cachedRegistrations.remove(registrationId);

            return null;
        }

        SsoProvider ssoProvider = optionalSsoProvider.get();

        Instant now = clock.instant();

        CachedRegistration cachedRegistration = cachedRegistrations.get(registrationId);

        boolean sameVersion = cachedRegistration != null && cachedRegistration.version() == ssoProvider.getVersion() &&
            Objects.equals(cachedRegistration.lastModifiedDate(), ssoProvider.getLastModifiedDate());

        if (sameVersion && now.isBefore(cachedRegistration.createdAt()
            .plus(REFRESH_INTERVAL))) {

            return cachedRegistration.registration();
        }

        try {
            Object registration = registrationFunction.apply(ssoProvider);

            cachedRegistrations.put(
                registrationId,
                new CachedRegistration(
                    registration, ssoProvider.getVersion(), ssoProvider.getLastModifiedDate(), now));

            return registration;
        } catch (RuntimeException runtimeException) {
            log.warn(
                "Could not build the {} registration for SSO provider {}: {}", type, registrationId,
                runtimeException.getMessage());

            if (sameVersion) {
                return cachedRegistration.registration();
            }

            cachedRegistrations.remove(registrationId);

            return null;
        }
    }

    private record CachedRegistration(Object registration, int version, Instant lastModifiedDate, Instant createdAt) {
    }
}
