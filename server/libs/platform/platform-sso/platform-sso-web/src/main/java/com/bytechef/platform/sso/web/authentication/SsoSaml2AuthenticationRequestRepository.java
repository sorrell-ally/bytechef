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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.web.Saml2AuthenticationRequestRepository;

/**
 * Stores outstanding SAML AuthnRequests keyed by their {@code RelayState} instead of in the HTTP session.
 *
 * <p>
 * The IdP returns the assertion with a cross-site top-level POST. ByteChef's session cookie carries no
 * {@code SameSite=None}, so browsers default it to {@code Lax} and do not send it on that POST; a session-backed
 * repository would then find no request and {@code InResponseTo} validation would reject every SP-initiated login.
 * Keying by the unguessable relay state avoids the cookie entirely. Entries are single-use, expire after five minutes,
 * and the map is bounded so unauthenticated calls to {@code /saml2/authenticate/...} cannot grow it without limit.
 *
 * <p>
 * The store is per JVM: in a multi-node deployment the SAML callback must reach the node that started the login.
 */
public class SsoSaml2AuthenticationRequestRepository
    implements Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest> {

    static final int MAX_ENTRIES = 10_000;
    static final Duration TIME_TO_LIVE = Duration.ofMinutes(5);

    private static final String RELAY_STATE_PARAMETER = "RelayState";

    private final Map<String, StoredRequest> storedRequests = new ConcurrentHashMap<>();
    private final Clock clock;

    public SsoSaml2AuthenticationRequestRepository() {
        this(Clock.systemUTC());
    }

    SsoSaml2AuthenticationRequestRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public @Nullable AbstractSaml2AuthenticationRequest loadAuthenticationRequest(HttpServletRequest request) {
        String relayState = request.getParameter(RELAY_STATE_PARAMETER);

        if (relayState == null) {
            return null;
        }

        StoredRequest storedRequest = storedRequests.get(relayState);

        if (storedRequest == null) {
            return null;
        }

        if (isExpired(storedRequest)) {
            storedRequests.remove(relayState);

            return null;
        }

        return storedRequest.authenticationRequest();
    }

    @Override
    public @Nullable AbstractSaml2AuthenticationRequest removeAuthenticationRequest(
        HttpServletRequest request, HttpServletResponse response) {

        AbstractSaml2AuthenticationRequest authenticationRequest = loadAuthenticationRequest(request);

        String relayState = request.getParameter(RELAY_STATE_PARAMETER);

        if (relayState != null) {
            storedRequests.remove(relayState);
        }

        return authenticationRequest;
    }

    @Override
    public void saveAuthenticationRequest(
        @Nullable AbstractSaml2AuthenticationRequest authenticationRequest, HttpServletRequest request,
        HttpServletResponse response) {

        if (authenticationRequest == null) {
            return;
        }

        String relayState = authenticationRequest.getRelayState();

        if (relayState == null) {
            return;
        }

        if (storedRequests.size() >= MAX_ENTRIES) {
            storedRequests.entrySet()
                .removeIf(entry -> isExpired(entry.getValue()));
        }

        if (storedRequests.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("Too many SAML logins in progress");
        }

        storedRequests.put(
            relayState,
            new StoredRequest(authenticationRequest, clock.instant()
                .plus(TIME_TO_LIVE)));
    }

    int size() {
        return storedRequests.size();
    }

    private boolean isExpired(StoredRequest storedRequest) {
        return !clock.instant()
            .isBefore(storedRequest.expiresAt());
    }

    private record StoredRequest(AbstractSaml2AuthenticationRequest authenticationRequest, Instant expiresAt) {
    }
}
