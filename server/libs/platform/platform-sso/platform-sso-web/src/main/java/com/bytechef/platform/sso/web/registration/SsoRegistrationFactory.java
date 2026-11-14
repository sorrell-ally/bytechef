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
import com.bytechef.platform.sso.util.SsoProviderPathUtils;
import com.bytechef.platform.sso.util.SsoRemoteUrlValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

/**
 * Builds Spring Security registrations from {@link SsoProvider} records.
 *
 * <p>
 * OIDC: the discovery document is fetched from {@code <issuer>/.well-known/openid-configuration} through
 * {@link SsoRemoteDocumentFetcher}; its {@code issuer} must equal the configured issuer exactly (OIDC Discovery 1.0
 * section 4.3), and the endpoints the server later calls itself (token, JWK set, userinfo) must pass the same URL
 * rules. PKCE is always required. SAML: metadata is either the stored XML or fetched from the metadata URL, never
 * through Spring's own resource loader, so the fetch is guarded too. The SP entity id is derived:
 * {@code {baseUrl}/saml2/metadata/{registrationId}}.
 */
public class SsoRegistrationFactory {

    private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";

    private final SsoRemoteDocumentFetcher ssoRemoteDocumentFetcher;

    public SsoRegistrationFactory(SsoRemoteDocumentFetcher ssoRemoteDocumentFetcher) {
        this.ssoRemoteDocumentFetcher = ssoRemoteDocumentFetcher;
    }

    public ClientRegistration createClientRegistration(SsoProvider ssoProvider) {
        String issuerUri = ssoProvider.getIssuerUri();

        String discoveryUrl = (issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri) +
            DISCOVERY_PATH;

        byte[] document = ssoRemoteDocumentFetcher.fetch(discoveryUrl, "application/json");

        JsonParser jsonParser = JsonParserFactory.getJsonParser();

        Map<String, Object> configuration = jsonParser.parseMap(new String(document, StandardCharsets.UTF_8));

        return createClientRegistration(ssoProvider, configuration);
    }

    ClientRegistration createClientRegistration(SsoProvider ssoProvider, Map<String, Object> configuration) {
        String issuer = getString(configuration, "issuer");

        if (!ssoProvider.getIssuerUri()
            .equals(issuer)) {

            throw new IllegalArgumentException(
                "Discovery issuer " + issuer + " does not match the configured issuer " + ssoProvider.getIssuerUri());
        }

        String authorizationEndpoint = getString(configuration, "authorization_endpoint");
        String jwksUri = getString(configuration, "jwks_uri");
        String tokenEndpoint = getString(configuration, "token_endpoint");

        if (authorizationEndpoint == null || jwksUri == null || tokenEndpoint == null) {
            throw new IllegalArgumentException("Discovery document is missing a required endpoint");
        }

        SsoRemoteUrlValidator.validate(authorizationEndpoint);
        SsoRemoteUrlValidator.validateResolved(jwksUri);
        SsoRemoteUrlValidator.validateResolved(tokenEndpoint);

        // Entra: identity comes from the ID token alone (tid, oid, preferred_username); its userinfo endpoint is
        // Microsoft Graph and returns none of those claims, so it is not called
        String userInfoEndpoint = ssoProvider.isMicrosoftEntraId() ? null
            : getString(configuration, "userinfo_endpoint");

        if (userInfoEndpoint != null) {
            SsoRemoteUrlValidator.validateResolved(userInfoEndpoint);
        }

        ClientAuthenticationMethod clientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;

        if (configuration.get("token_endpoint_auth_methods_supported") instanceof List<?> supportedMethods &&
            !supportedMethods.contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue()) &&
            supportedMethods.contains(ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue())) {

            clientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST;
        }

        return ClientRegistration.withRegistrationId(ssoProvider.getRegistrationId())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationUri(authorizationEndpoint)
            .clientAuthenticationMethod(clientAuthenticationMethod)
            .clientId(ssoProvider.getClientId())
            .clientName(ssoProvider.getName())
            .clientSecret(ssoProvider.getClientSecret())
            .clientSettings(
                ClientRegistration.ClientSettings.builder()
                    .requireProofKey(true)
                    .build())
            .issuerUri(issuer)
            .jwkSetUri(jwksUri)
            .redirectUri("{baseUrl}" + SsoProviderPathUtils.OIDC_CALLBACK_PATH + "{registrationId}")
            .scope(ssoProvider.getScopes())
            .tokenUri(tokenEndpoint)
            .userInfoUri(userInfoEndpoint)
            .userNameAttributeName("sub")
            .build();
    }

    public RelyingPartyRegistration createRelyingPartyRegistration(SsoProvider ssoProvider) {
        byte[] metadata;

        String metadataXml = ssoProvider.getMetadataXml();

        if (metadataXml != null && !metadataXml.isBlank()) {
            metadata = metadataXml.getBytes(StandardCharsets.UTF_8);
        } else {
            metadata = ssoRemoteDocumentFetcher.fetch(
                ssoProvider.getMetadataUrl(), "application/samlmetadata+xml, application/xml, text/xml");
        }

        return createRelyingPartyRegistration(ssoProvider, metadata);
    }

    RelyingPartyRegistration createRelyingPartyRegistration(SsoProvider ssoProvider, byte[] metadata) {
        try (InputStream inputStream = new ByteArrayInputStream(metadata)) {
            return RelyingPartyRegistrations.fromMetadata(inputStream)
                .registrationId(ssoProvider.getRegistrationId())
                .entityId("{baseUrl}" + SsoProviderPathUtils.SAML_METADATA_PATH + "{registrationId}")
                .assertionConsumerServiceLocation(
                    "{baseUrl}" + SsoProviderPathUtils.SAML_ASSERTION_CONSUMER_PATH + "{registrationId}")
                .build();
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private static String getString(Map<String, Object> configuration, String key) {
        Object value = configuration.get(key);

        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }
}
