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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Removes credentials from text produced by JGit or the network stack before it is logged, stored or returned.
 */
public final class ProjectGitSyncRedactor {

    static final String REDACTED = "[REDACTED]";

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final Pattern URL_USER_INFO_PATTERN = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^/@\\s]+@");

    private ProjectGitSyncRedactor() {
    }

    public static String redact(@Nullable String text, @Nullable String secret) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String redacted = text;

        if (secret != null && !secret.isEmpty()) {
            redacted = redacted.replace(secret, REDACTED);

            String encodedSecret = URLEncoder.encode(secret, StandardCharsets.UTF_8);

            redacted = redacted.replace(encodedSecret, REDACTED);
        }

        redacted = URL_USER_INFO_PATTERN.matcher(redacted)
            .replaceAll("$1" + REDACTED + "@");

        if (redacted.length() > MAX_MESSAGE_LENGTH) {
            redacted = redacted.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        return redacted;
    }

    /**
     * Builds a single-line description of an exception chain (type and message of each cause), redacted.
     */
    public static String describe(Throwable throwable, @Nullable String secret) {
        StringBuilder description = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;

        while (current != null && depth < 5) {
            if (!description.isEmpty()) {
                description.append(": ");
            }

            Class<? extends Throwable> currentClass = current.getClass();
            String message = current.getMessage();

            description.append(message == null ? currentClass.getSimpleName() : message);

            current = current.getCause() == current ? null : current.getCause();
            depth++;
        }

        return redact(description.toString(), secret);
    }
}
