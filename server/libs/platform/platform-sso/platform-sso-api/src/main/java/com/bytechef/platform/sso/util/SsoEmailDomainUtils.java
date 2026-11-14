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
import java.util.regex.Pattern;

/**
 * Email-domain parsing shared by provider validation, discovery, user resolution and password-login enforcement, so
 * every path compares domains the same way (trimmed, lower-cased, exact match - no subdomain wildcarding).
 */
public final class SsoEmailDomainUtils {

    private static final Pattern LABEL_PATTERN = Pattern.compile("[a-z0-9-]{1,63}");
    private static final int MAX_DOMAIN_LENGTH = 253;

    private SsoEmailDomainUtils() {
    }

    /**
     * Extracts the normalized domain of an email address.
     *
     * @return the domain, or empty when the value is not a plausible single-{@code @} email address
     */
    public static Optional<String> extractDomain(String email) {
        if (email == null) {
            return Optional.empty();
        }

        String trimmedEmail = email.trim();

        int atIndex = trimmedEmail.indexOf('@');

        if (atIndex < 1 || atIndex != trimmedEmail.lastIndexOf('@') || atIndex == trimmedEmail.length() - 1) {
            return Optional.empty();
        }

        String domain = trimmedEmail.substring(atIndex + 1)
            .toLowerCase(Locale.ROOT);

        if (!isValidDomain(domain)) {
            return Optional.empty();
        }

        return Optional.of(domain);
    }

    /**
     * Validates a lower-case DNS name label by label (no nested regex quantifiers, so no ReDoS): at least two labels,
     * each 1-63 characters of {@code [a-z0-9-]} not starting or ending with {@code -}, a top-level label of at least
     * two characters, and at most 253 characters overall.
     */
    public static boolean isValidDomain(String domain) {
        if (domain == null || domain.isEmpty() || domain.length() > MAX_DOMAIN_LENGTH) {
            return false;
        }

        String[] labels = domain.split("\\.", -1);

        if (labels.length < 2 || labels[labels.length - 1].length() < 2) {
            return false;
        }

        for (String label : labels) {
            if (!LABEL_PATTERN.matcher(label)
                .matches() || label.startsWith("-") || label.endsWith("-")) {

                return false;
            }
        }

        return true;
    }

    /**
     * Normalizes an administrator-entered domain ({@code " @Example.COM "} becomes {@code "example.com"}).
     *
     * @throws IllegalArgumentException when the value is not a valid domain name
     */
    public static String normalizeDomain(String domain) {
        if (domain == null) {
            throw new IllegalArgumentException("Domain must not be null");
        }

        String normalizedDomain = domain.trim()
            .toLowerCase(Locale.ROOT);

        if (normalizedDomain.startsWith("@")) {
            normalizedDomain = normalizedDomain.substring(1);
        }

        if (!isValidDomain(normalizedDomain)) {
            throw new IllegalArgumentException("Invalid email domain: " + domain);
        }

        return normalizedDomain;
    }
}
