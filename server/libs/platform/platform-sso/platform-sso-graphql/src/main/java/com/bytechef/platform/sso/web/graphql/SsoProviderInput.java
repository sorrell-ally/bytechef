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

package com.bytechef.platform.sso.web.graphql;

import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.domain.SsoProviderVendor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

/**
 * GraphQL input for creating or updating an SSO provider.
 */
@SuppressWarnings("PMD.ExcessiveParameterList")
@SuppressFBWarnings(value = {
    "EI_EXPOSE_REP", "EI_EXPOSE_REP2"
}, justification = "list components are copied into unmodifiable lists by the compact constructor")
public record SsoProviderInput(
    String name, SsoProviderType type, SsoProviderVendor vendor, Boolean enabled, List<String> domains,
    Boolean enforceSso,
    Boolean autoProvision, String defaultAuthority, String issuerUri, String clientId, String clientSecret,
    List<String> scopes, String metadataUrl, String metadataXml) {

    public SsoProviderInput {
        domains = domains == null ? null : List.copyOf(domains);
        scopes = scopes == null ? null : List.copyOf(scopes);
    }

    /**
     * Copies this input onto a new, unsaved {@link SsoProvider}. A blank client secret becomes {@code null}, which the
     * service treats as "keep the stored secret" on update.
     */
    SsoProvider toSsoProvider() {
        SsoProvider ssoProvider = new SsoProvider();

        ssoProvider.setAutoProvision(Boolean.TRUE.equals(autoProvision));
        ssoProvider.setClientId(clientId);
        ssoProvider.setClientSecret(clientSecret == null || clientSecret.isBlank() ? null : clientSecret);
        ssoProvider.setDefaultAuthority(defaultAuthority);
        ssoProvider.setDomains(domains == null ? List.of() : domains);
        ssoProvider.setEnabled(Boolean.TRUE.equals(enabled));
        ssoProvider.setEnforceSso(Boolean.TRUE.equals(enforceSso));
        ssoProvider.setIssuerUri(issuerUri);
        ssoProvider.setMetadataUrl(metadataUrl);
        ssoProvider.setMetadataXml(metadataXml);
        ssoProvider.setName(name);
        ssoProvider.setScopes(scopes == null ? List.of() : scopes);
        ssoProvider.setType(type);
        ssoProvider.setVendor(vendor);

        return ssoProvider;
    }

    @Override
    public String toString() {
        return "SsoProviderInput{name='" + name + "', type=" + type + ", vendor=" + vendor + ", enabled=" + enabled
            + "}";
    }
}
