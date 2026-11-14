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

package com.bytechef.automation.configuration.gitsync.git;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Validates admin-supplied repository URLs to limit SSRF and credential leakage: only {@code https://}, no embedded
 * credentials, no query or fragment and, unless explicitly allowed, no host that resolves to a loopback, link-local,
 * site-local, unique-local, carrier-grade NAT, multicast or wildcard address.
 *
 * <p>
 * The host is resolved here and again by JGit when connecting, so a DNS-rebinding attacker who controls the name server
 * of an allowed host could still steer the connection; redirects are disabled separately on every JGit repository
 * ({@code http.followRedirects=false}).
 */
public class ProjectGitSyncUrlValidator {

    private static final int MAX_URL_LENGTH = 1024;

    private final boolean allowPrivateNetworkHosts;

    public ProjectGitSyncUrlValidator(boolean allowPrivateNetworkHosts) {
        this.allowPrivateNetworkHosts = allowPrivateNetworkHosts;
    }

    /**
     * @return the normalized URL (trimmed, scheme and host lower-cased)
     */
    public String validate(@Nullable String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Repository URL is required");
        }

        String trimmedUrl = url.trim();

        if (trimmedUrl.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Repository URL is too long");
        }

        for (int index = 0; index < trimmedUrl.length(); index++) {
            char character = trimmedUrl.charAt(index);

            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                throw new IllegalArgumentException("Repository URL must not contain whitespace or control characters");
            }
        }

        URI uri;

        try {
            uri = new URI(trimmedUrl);
        } catch (URISyntaxException uriSyntaxException) {
            throw new IllegalArgumentException("Repository URL is not a valid URI");
        }

        String scheme = uri.getScheme();

        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Repository URL must use https://");
        }

        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                "Repository URL must not contain credentials; use the username and access token fields");
        }

        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Repository URL must not contain a query or fragment");
        }

        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Repository URL must contain a host");
        }

        String path = uri.getRawPath();

        if (path == null || path.isEmpty() || "/".equals(path)) {
            throw new IllegalArgumentException("Repository URL must contain a repository path");
        }

        if (!allowPrivateNetworkHosts) {
            checkPublicHost(host);
        }

        int port = uri.getPort();

        return "https://" + host.toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port) + path;
    }

    private static void checkPublicHost(String host) {
        InetAddress[] inetAddresses;

        try {
            inetAddresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException unknownHostException) {
            throw new IllegalArgumentException("Repository host cannot be resolved");
        }

        for (InetAddress inetAddress : inetAddresses) {
            if (isNonPublic(inetAddress)) {
                throw new IllegalArgumentException(
                    "Repository host resolves to a private or local network address, which is not allowed");
            }
        }
    }

    static boolean isNonPublic(InetAddress inetAddress) {
        if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress() ||
            inetAddress.isSiteLocalAddress() || inetAddress.isMulticastAddress()) {

            return true;
        }

        byte[] address = inetAddress.getAddress();

        if (address.length == 4) {
            int firstOctet = address[0] & 0xff;
            int secondOctet = address[1] & 0xff;

            // 100.64.0.0/10 carrier-grade NAT, 0.0.0.0/8 "this network"
            return (firstOctet == 100 && secondOctet >= 64 && secondOctet <= 127) || firstOctet == 0;
        }

        // fc00::/7 unique local addresses
        return (address[0] & 0xfe) == 0xfc;
    }
}
