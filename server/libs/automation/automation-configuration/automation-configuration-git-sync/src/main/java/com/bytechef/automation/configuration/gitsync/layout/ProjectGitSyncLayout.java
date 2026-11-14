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

package com.bytechef.automation.configuration.gitsync.layout;

import com.bytechef.commons.util.JsonUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The repository layout of a synced project, relative to the configured project directory:
 *
 * <pre>
 * project.json              manifest: format version, project uuid/name/description, workflow index
 * workflows/&lt;uuid&gt;.json     one pretty-printed workflow definition per project_workflow.uuid
 * </pre>
 *
 * The manifest deliberately does not contain the project version or any timestamp, so publishing an unchanged project
 * produces an identical tree and therefore no commit. Only {@code project.json} and files matching
 * {@code workflows/<uuid>.json} are owned by the sync; anything else in the directory (a README, CI files) is never
 * modified or deleted.
 */
public final class ProjectGitSyncLayout {

    public static final int FORMAT_VERSION = 1;
    public static final String PROJECT_FILE = "project.json";
    public static final String WORKFLOWS_DIRECTORY = "workflows";

    private static final Pattern WORKFLOW_FILE_PATTERN = Pattern.compile(
        "workflows/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.json");

    private ProjectGitSyncLayout() {
    }

    /**
     * Builds the files of a project version, keyed by path relative to the project directory.
     */
    public static Map<String, String> toFiles(
        String projectUuid, String projectName, @Nullable String projectDescription,
        List<ProjectGitSyncWorkflowFile> workflowFiles) {

        List<ProjectGitSyncWorkflowFile> sortedWorkflowFiles = new ArrayList<>(workflowFiles);

        sortedWorkflowFiles.sort(Comparator.comparing(ProjectGitSyncWorkflowFile::uuid));

        Map<String, String> files = new TreeMap<>();
        List<Map<String, Object>> workflowEntries = new ArrayList<>();

        for (ProjectGitSyncWorkflowFile workflowFile : sortedWorkflowFiles) {
            String uuid = normalizeUuid(workflowFile.uuid());
            String path = getWorkflowPath(uuid);

            Map<String, Object> workflowEntry = new LinkedHashMap<>();

            workflowEntry.put("uuid", uuid);
            workflowEntry.put("label", workflowFile.label());
            workflowEntry.put("file", path);

            workflowEntries.add(workflowEntry);

            files.put(path, formatDefinition(workflowFile.definition()));
        }

        Map<String, Object> manifest = new LinkedHashMap<>();

        manifest.put("formatVersion", FORMAT_VERSION);
        manifest.put("projectUuid", projectUuid);
        manifest.put("name", projectName);
        manifest.put("description", projectDescription);
        manifest.put("workflows", workflowEntries);

        files.put(PROJECT_FILE, withTrailingNewline(JsonUtils.writeWithDefaultPrettyPrinter(manifest)));

        return files;
    }

    public static String getWorkflowPath(String uuid) {
        return WORKFLOWS_DIRECTORY + "/" + normalizeUuid(uuid) + ".json";
    }

    /**
     * Whether the sync owns the file at this path (relative to the project directory) and may replace or delete it.
     */
    public static boolean isManagedFile(String relativePath) {
        return PROJECT_FILE.equals(relativePath) || isWorkflowFile(relativePath);
    }

    public static boolean isWorkflowFile(String relativePath) {
        Matcher matcher = WORKFLOW_FILE_PATTERN.matcher(relativePath);

        return matcher.matches();
    }

    /**
     * @return the workflow uuid encoded in a workflow file path, if the path is a workflow file
     */
    public static Optional<String> parseWorkflowUuid(String relativePath) {
        Matcher matcher = WORKFLOW_FILE_PATTERN.matcher(relativePath);

        if (!matcher.matches()) {
            return Optional.empty();
        }

        return Optional.of(normalizeUuid(matcher.group(1)));
    }

    /**
     * Pretty-prints a JSON definition so diffs are readable; a definition that is not valid JSON is written as is.
     */
    public static String formatDefinition(String definition) {
        try {
            Object tree = JsonUtils.readTree(definition);

            return withTrailingNewline(JsonUtils.writeWithDefaultPrettyPrinter(tree));
        } catch (RuntimeException runtimeException) {
            return withTrailingNewline(definition);
        }
    }

    /**
     * Compares two definitions as JSON trees, so formatting differences are not treated as changes.
     */
    public static boolean definitionsEqual(@Nullable String firstDefinition, @Nullable String secondDefinition) {
        if (firstDefinition == null || secondDefinition == null) {
            return Objects.equals(firstDefinition, secondDefinition);
        }

        try {
            return Objects.equals(JsonUtils.readTree(firstDefinition), JsonUtils.readTree(secondDefinition));
        } catch (RuntimeException runtimeException) {
            return Objects.equals(firstDefinition.strip(), secondDefinition.strip());
        }
    }

    private static String normalizeUuid(String uuid) {
        UUID parsedUuid = UUID.fromString(uuid);

        String uuidString = parsedUuid.toString();

        return uuidString.toLowerCase(Locale.ROOT);
    }

    private static String withTrailingNewline(String content) {
        return content.endsWith("\n") ? content : content + "\n";
    }
}
