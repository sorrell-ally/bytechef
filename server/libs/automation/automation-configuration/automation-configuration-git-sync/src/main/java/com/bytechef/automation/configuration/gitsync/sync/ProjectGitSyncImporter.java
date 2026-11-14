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

package com.bytechef.automation.configuration.gitsync.sync;

import com.bytechef.atlas.configuration.domain.Workflow;
import com.bytechef.atlas.configuration.service.WorkflowService;
import com.bytechef.automation.configuration.domain.Project;
import com.bytechef.automation.configuration.domain.ProjectVersion;
import com.bytechef.automation.configuration.domain.ProjectWorkflow;
import com.bytechef.automation.configuration.facade.ProjectWorkflowFacade;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncPullResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncWorkflowDTO;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncException;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncSnapshot;
import com.bytechef.automation.configuration.gitsync.layout.ProjectGitSyncLayout;
import com.bytechef.automation.configuration.service.ProjectService;
import com.bytechef.automation.configuration.service.ProjectWorkflowService;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.workflow.validator.WorkflowValidatorFacade;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports workflow definitions read from Git into a project's DRAFT version, in one transaction: either every workflow
 * is imported or, if any definition fails validation, none is.
 *
 * <p>
 * Semantics:
 * <ul>
 * <li>a file whose uuid matches a draft workflow updates that workflow, through the same update path as the editor
 * ({@link ProjectWorkflowFacade#updateWorkflow}), unless the definitions are equal as JSON (reported unchanged);</li>
 * <li>a file with a new uuid is validated like {@link ProjectWorkflowFacade#addWorkflow} (no duplicate node names, an
 * enabled trigger) and added to the draft keeping the uuid from Git, so later pushes and pulls keep matching;</li>
 * <li>a file whose uuid already belongs to a workflow of another project is skipped (uuids identify workflows across
 * the whole instance);</li>
 * <li>draft workflows absent from Git are left untouched and reported as {@code notInGit}. Pull never deletes.</li>
 * </ul>
 * {@code project.json} is informational and is not read on pull.
 */
@Component
@ConditionalOnCEVersion
public class ProjectGitSyncImporter {

    private final ProjectService projectService;
    private final ProjectWorkflowFacade projectWorkflowFacade;
    private final ProjectWorkflowService projectWorkflowService;
    private final WorkflowService workflowService;
    private final WorkflowValidatorFacade workflowValidatorFacade;

    @SuppressFBWarnings("EI")
    public ProjectGitSyncImporter(
        ProjectService projectService, ProjectWorkflowFacade projectWorkflowFacade,
        ProjectWorkflowService projectWorkflowService, WorkflowService workflowService,
        WorkflowValidatorFacade workflowValidatorFacade) {

        this.projectService = projectService;
        this.projectWorkflowFacade = projectWorkflowFacade;
        this.projectWorkflowService = projectWorkflowService;
        this.workflowService = workflowService;
        this.workflowValidatorFacade = workflowValidatorFacade;
    }

    @Transactional
    public ProjectGitSyncPullResultDTO importSnapshot(long projectId, ProjectGitSyncSnapshot snapshot) {
        Project project = projectService.getProject(projectId);

        if (project.getLastStatus() != ProjectVersion.Status.DRAFT) {
            throw new ProjectGitSyncException("The project has no draft version to pull into");
        }

        int draftProjectVersion = project.getLastProjectVersion();

        Map<String, ProjectWorkflow> draftProjectWorkflows = new LinkedHashMap<>();

        for (ProjectWorkflow projectWorkflow : projectWorkflowService.getProjectWorkflows(
            projectId, draftProjectVersion)) {

            draftProjectWorkflows.put(projectWorkflow.getUuidAsString(), projectWorkflow);
        }

        List<ProjectGitSyncWorkflowDTO> added = new ArrayList<>();
        List<ProjectGitSyncWorkflowDTO> updated = new ArrayList<>();
        List<ProjectGitSyncWorkflowDTO> unchanged = new ArrayList<>();
        List<ProjectGitSyncWorkflowDTO> skipped = new ArrayList<>();
        Set<String> gitWorkflowUuids = new HashSet<>();

        for (Map.Entry<String, String> fileEntry : snapshot.files()
            .entrySet()) {

            Optional<String> uuidOptional = ProjectGitSyncLayout.parseWorkflowUuid(fileEntry.getKey());

            if (uuidOptional.isEmpty()) {
                continue;
            }

            String uuid = uuidOptional.get();
            String definition = fileEntry.getValue();

            gitWorkflowUuids.add(uuid);

            ProjectWorkflow draftProjectWorkflow = draftProjectWorkflows.get(uuid);

            if (draftProjectWorkflow != null) {
                Workflow workflow = workflowService.getWorkflow(draftProjectWorkflow.getWorkflowId());

                if (ProjectGitSyncLayout.definitionsEqual(workflow.getDefinition(), definition)) {
                    unchanged.add(new ProjectGitSyncWorkflowDTO(uuid, workflow.getLabel(), null));

                    continue;
                }

                validate(fileEntry.getKey(), definition);

                projectWorkflowFacade.updateWorkflow(workflow.getId(), definition, workflow.getVersion());

                Workflow updatedWorkflow = workflowService.getWorkflow(workflow.getId());

                updated.add(new ProjectGitSyncWorkflowDTO(uuid, updatedWorkflow.getLabel(), null));

                continue;
            }

            if (belongsToAnotherProject(projectId, uuid)) {
                skipped.add(
                    new ProjectGitSyncWorkflowDTO(uuid, null, "The workflow uuid belongs to another project"));

                continue;
            }

            validate(fileEntry.getKey(), definition);

            Workflow workflow = workflowService.create(definition, Workflow.Format.JSON, Workflow.SourceType.JDBC);

            projectWorkflowService.addWorkflow(
                projectId, draftProjectVersion, workflow.getId(), UUID.fromString(uuid));

            added.add(new ProjectGitSyncWorkflowDTO(uuid, workflow.getLabel(), null));
        }

        List<ProjectGitSyncWorkflowDTO> notInGit = new ArrayList<>();

        for (Map.Entry<String, ProjectWorkflow> draftEntry : draftProjectWorkflows.entrySet()) {
            if (gitWorkflowUuids.contains(draftEntry.getKey())) {
                continue;
            }

            ProjectWorkflow projectWorkflow = draftEntry.getValue();

            Workflow workflow = workflowService.getWorkflow(projectWorkflow.getWorkflowId());

            notInGit.add(
                new ProjectGitSyncWorkflowDTO(
                    draftEntry.getKey(), workflow.getLabel(), "Not present in Git; left unchanged"));
        }

        return new ProjectGitSyncPullResultDTO(snapshot.commitHash(), added, updated, unchanged, skipped, notInGit);
    }

    private boolean belongsToAnotherProject(long projectId, String uuid) {
        for (ProjectWorkflow projectWorkflow : projectWorkflowService.getWorkflowUuidProjectWorkflows(uuid)) {
            if (projectWorkflow.getProjectId() != projectId) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void validate(String path, String definition) {
        try {
            workflowValidatorFacade.validateNoDuplicateNodeNames(definition);
            workflowValidatorFacade.validateHasEnabledTrigger(definition);
        } catch (RuntimeException runtimeException) {
            throw new IllegalArgumentException(
                "Workflow file '" + path + "' is not valid: " + runtimeException.getMessage(), runtimeException);
        }
    }
}
