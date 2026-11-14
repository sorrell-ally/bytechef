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

import java.util.Locale;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Repository;
import org.jspecify.annotations.Nullable;

/**
 * Validation of the branch name and repository sub-directory. Paths are Git tree paths (forward slashes, relative to
 * the repository root); they are never resolved against the local file system.
 */
public final class ProjectGitSyncPaths {

    private static final int MAX_BRANCH_LENGTH = 255;
    private static final int MAX_DIRECTORY_DEPTH = 10;
    private static final int MAX_DIRECTORY_LENGTH = 255;
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private ProjectGitSyncPaths() {
    }

    public static String validateBranch(@Nullable String branch) {
        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException("Branch is required");
        }

        String trimmedBranch = branch.trim();

        if (trimmedBranch.startsWith("refs/")) {
            throw new IllegalArgumentException("Branch must be a short branch name, without refs/");
        }

        if (trimmedBranch.length() > MAX_BRANCH_LENGTH || trimmedBranch.startsWith("-") ||
            !Repository.isValidRefName("refs/heads/" + trimmedBranch)) {

            throw new IllegalArgumentException("Branch name is not valid");
        }

        return trimmedBranch;
    }

    /**
     * Normalizes a sub-directory to the form {@code a/b/c} (no leading or trailing slash), or the empty string for the
     * repository root. Rejects {@code .}, {@code ..}, {@code .git}, backslashes and any character outside
     * {@code [A-Za-z0-9._-]}.
     */
    public static String normalizeSubDirectory(@Nullable String subDirectory) {
        if (subDirectory == null) {
            return "";
        }

        String trimmedSubDirectory = subDirectory.trim();

        if (trimmedSubDirectory.length() > MAX_DIRECTORY_LENGTH) {
            throw new IllegalArgumentException("Sub-directory is too long");
        }

        while (trimmedSubDirectory.startsWith("/")) {
            trimmedSubDirectory = trimmedSubDirectory.substring(1);
        }

        while (trimmedSubDirectory.endsWith("/")) {
            trimmedSubDirectory = trimmedSubDirectory.substring(0, trimmedSubDirectory.length() - 1);
        }

        if (trimmedSubDirectory.isEmpty()) {
            return "";
        }

        String[] segments = trimmedSubDirectory.split("/", -1);

        if (segments.length > MAX_DIRECTORY_DEPTH) {
            throw new IllegalArgumentException("Sub-directory is nested too deeply");
        }

        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment) ||
                ".git".equals(segment.toLowerCase(Locale.ROOT)) || !SEGMENT_PATTERN.matcher(segment)
                    .matches()) {

                throw new IllegalArgumentException(
                    "Sub-directory may only contain path segments made of letters, digits, '.', '_' and '-', and " +
                        "must not contain '.', '..' or '.git' segments");
            }
        }

        return trimmedSubDirectory;
    }

    /**
     * Joins the normalized project directory and a relative path into a repository tree path.
     */
    public static String join(String projectDirectory, String relativePath) {
        return projectDirectory.isEmpty() ? relativePath : projectDirectory + "/" + relativePath;
    }
}
