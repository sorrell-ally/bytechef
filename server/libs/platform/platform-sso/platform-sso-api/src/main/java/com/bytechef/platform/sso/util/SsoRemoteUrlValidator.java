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

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Guards the administrator-supplied URLs the server fetches on its own (OIDC discovery document, token and JWK set
 * endpoints, SAML metadata) against server-side request forgery.
 *
 * <p>
 * Rules: absolute {@code https} URL, no user-info, no fragment, and a host that is not {@code localhost} and does not
 * resolve to a loopback, link-local (this covers cloud instance-metadata addresses such as {@code 169.254.169.254}),
 * wildcard or multicast address. Private (site-local) ranges are allowed on purpose: self-hosted identity providers
 * commonly live on the corporate network. {@link #validate(String)} checks syntax only and is what the admin API runs
 * on save; {@link #validateResolved(String)} additionally resolves the host and is what runs immediately before each
 * outbound fetch. A DNS answer that changes between that check and the connection (rebinding) is not covered.
 */
public final class SsoRemoteUrlValidator {

    private SsoRemoteUrlValidator() {
    }

    /**
     * Syntax-level validation.
     *
     * @return the parsed URI
     * @throws IllegalArgumentException when the URL is not acceptable
     */
    public static URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }

        URI uri;

        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException uriSyntaxException) {
            throw new IllegalArgumentException("Invalid URL: " + url, uriSyntaxException);
        }

        String scheme = uri.getScheme();

        if (scheme == null || !"https".equals(scheme.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("URL must use https: " + url);
        }

        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("URL must not contain user info or a fragment: " + url);
        }

        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must have a host: " + url);
        }

        String lowerCaseHost = host.toLowerCase(Locale.ROOT);

        if ("localhost".equals(lowerCaseHost) || lowerCaseHost.endsWith(".localhost")) {
            throw new IllegalArgumentException("URL must not point to localhost: " + url);
        }

        if (isIpLiteral(lowerCaseHost)) {
            checkAddress(toLiteralAddress(lowerCaseHost), url);
        }

        return uri;
    }

    /**
     * Syntax validation plus a DNS resolution check of every address of the host.
     *
     * @throws IllegalArgumentException when the URL is not acceptable or the host cannot be resolved
     */
    public static URI validateResolved(String url) {
        URI uri = validate(url);

        InetAddress[] inetAddresses;

        try {
            inetAddresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException unknownHostException) {
            throw new IllegalArgumentException("Unknown host in URL: " + url, unknownHostException);
        }

        for (InetAddress inetAddress : inetAddresses) {
            checkAddress(inetAddress, url);
        }

        return uri;
    }

    static void checkAddress(InetAddress inetAddress, String url) {
        if (inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress() || inetAddress.isAnyLocalAddress() ||
            inetAddress.isMulticastAddress()) {

            throw new IllegalArgumentException("URL resolves to a disallowed address: " + url);
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.startsWith("[") || host.matches("^[0-9.]+$");
    }

    private static InetAddress toLiteralAddress(String host) {
        String literal = host;

        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }

        try {
            // an IP literal is parsed locally, no DNS lookup happens
            return InetAddress.getByName(literal);
        } catch (UnknownHostException unknownHostException) {
            throw new IllegalArgumentException("Invalid IP address: " + host, unknownHostException);
        }
    }
}
