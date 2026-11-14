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

package com.bytechef.platform.sso.util;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Microsoft Entra ID issuer handling.
 *
 * <p>
 * Only a tenant-specific v2.0 issuer is accepted: {@code https://<authority host>/<tenant GUID>/v2.0}. The multi-tenant
 * authorities ({@code common}, {@code organizations}, {@code consumers}) are rejected because they let any Microsoft
 * tenant (or personal account) sign in, whose administrators control the email-like claims of their own users, and
 * because their discovery document's issuer is a {@code {tenantid}} template rather than a real issuer. Domain-name
 * tenants ({@code contoso.onmicrosoft.com}) are rejected too: the login check compares the token's {@code tid} GUID
 * with the configured tenant, so the GUID must be known.
 */
public final class SsoMicrosoftEntraUtils {

    /**
     * Authority hosts of the global, US Government and China (21Vianet) clouds.
     */
    static final Set<String> AUTHORITY_HOSTS = Set.of(
        "login.microsoftonline.com", "login.microsoftonline.us", "login.partner.microsoftonline.cn");

    private static final Pattern ISSUER_PATTERN = Pattern.compile(
        "^https://([a-z.]+)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/v2\\.0$");

    private SsoMicrosoftEntraUtils() {
    }

    /**
     * @return the tenant GUID of a valid tenant-specific Entra v2.0 issuer, or empty
     */
    public static Optional<String> extractTenantId(String issuerUri) {
        if (issuerUri == null) {
            return Optional.empty();
        }

        Matcher matcher = ISSUER_PATTERN.matcher(issuerUri);

        if (!matcher.matches() || !AUTHORITY_HOSTS.contains(matcher.group(1))) {
            return Optional.empty();
        }

        return Optional.of(matcher.group(2));
    }

    /**
     * Normalizes and validates an administrator-entered Entra issuer (trimmed, lower-cased, trailing slash removed).
     *
     * @throws IllegalArgumentException with an explanation when the issuer is not a tenant-specific v2.0 issuer
     */
    public static String normalizeIssuer(String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException(
                "issuerUri is required: https://login.microsoftonline.com/<tenant ID>/v2.0");
        }

        String normalizedIssuerUri = issuerUri.trim()
            .toLowerCase(Locale.ROOT);

        if (normalizedIssuerUri.endsWith("/")) {
            normalizedIssuerUri = normalizedIssuerUri.substring(0, normalizedIssuerUri.length() - 1);
        }

        if (extractTenantId(normalizedIssuerUri).isPresent()) {
            return normalizedIssuerUri;
        }

        String[] segments = normalizedIssuerUri.split("/");
        String tenantSegment = segments.length > 3 ? segments[3] : "";

        if (Set.of("common", "organizations", "consumers")
            .contains(tenantSegment)) {

            throw new IllegalArgumentException(
                "Microsoft Entra ID issuer must name a single tenant; the multi-tenant authority '" + tenantSegment +
                    "' would let any Microsoft tenant sign in. Use https://login.microsoftonline.com/<tenant ID>/v2.0");
        }

        throw new IllegalArgumentException(
            "Microsoft Entra ID issuer must be https://login.microsoftonline.com/<tenant ID>/v2.0, where the tenant ID "
                +
                "is the Directory (tenant) ID GUID (not a domain name) and the URL ends in /v2.0");
    }
}
