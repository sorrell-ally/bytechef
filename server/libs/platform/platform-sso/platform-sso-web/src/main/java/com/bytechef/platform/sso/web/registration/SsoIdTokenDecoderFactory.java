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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * ID-token decoder factory that invalidates when a registration changes.
 *
 * <p>
 * Spring's {@link OidcIdTokenDecoderFactory} caches one decoder per registration id forever. Registration ids of SSO
 * providers are stable while the provider is edited, so an admin changing the issuer or client id would keep validating
 * ID tokens against the old JWK set, issuer and audience until restart. This factory keys the decoder by registration
 * id plus every value the decoder captures (issuer, JWK set URI, client id, client secret for HMAC algorithms), and
 * replaces the entry when any of them changes. Decoders are otherwise built exactly as Spring builds them, so
 * social-login registrations are unaffected.
 */
public class SsoIdTokenDecoderFactory implements JwtDecoderFactory<ClientRegistration> {

    private final Map<String, CachedDecoder> cachedDecoders = new ConcurrentHashMap<>();
    private final Function<ClientRegistration, JwtDecoder> decoderFunction;

    public SsoIdTokenDecoderFactory() {
        this(clientRegistration -> {
            OidcIdTokenDecoderFactory oidcIdTokenDecoderFactory = new OidcIdTokenDecoderFactory();

            return oidcIdTokenDecoderFactory.createDecoder(clientRegistration);
        });
    }

    SsoIdTokenDecoderFactory(Function<ClientRegistration, JwtDecoder> decoderFunction) {
        this.decoderFunction = decoderFunction;
    }

    @Override
    public JwtDecoder createDecoder(ClientRegistration clientRegistration) {
        String fingerprint = fingerprint(clientRegistration);

        CachedDecoder cachedDecoder = cachedDecoders.compute(
            clientRegistration.getRegistrationId(),
            (registrationId, currentCachedDecoder) -> {
                if (currentCachedDecoder != null && currentCachedDecoder.fingerprint()
                    .equals(fingerprint)) {

                    return currentCachedDecoder;
                }

                return new CachedDecoder(fingerprint, decoderFunction.apply(clientRegistration));
            });

        return cachedDecoder.jwtDecoder();
    }

    private static String fingerprint(ClientRegistration clientRegistration) {
        ClientRegistration.ProviderDetails providerDetails = clientRegistration.getProviderDetails();

        return String.join(
            "\n", String.valueOf(providerDetails.getIssuerUri()), String.valueOf(providerDetails.getJwkSetUri()),
            String.valueOf(clientRegistration.getClientId()),
            String.valueOf(clientRegistration.getClientSecret()));
    }

    private record CachedDecoder(String fingerprint, JwtDecoder jwtDecoder) {
    }
}
