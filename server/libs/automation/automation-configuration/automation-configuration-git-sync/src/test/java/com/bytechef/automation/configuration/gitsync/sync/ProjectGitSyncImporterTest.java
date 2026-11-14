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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.atlas.configuration.domain.Workflow;
import com.bytechef.atlas.configuration.service.WorkflowService;
import com.bytechef.automation.configuration.domain.Project;
import com.bytechef.automation.configuration.domain.ProjectVersion;
import com.bytechef.automation.configuration.domain.ProjectWorkflow;
import com.bytechef.automation.configuration.facade.ProjectWorkflowFacade;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncPullResultDTO;
import com.bytechef.automation.configuration.gitsync.dto.ProjectGitSyncWorkflowDTO;
import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncSnapshot;
import com.bytechef.automation.configuration.service.ProjectService;
import com.bytechef.automation.configuration.service.ProjectWorkflowService;
import com.bytechef.platform.workflow.validator.WorkflowValidatorFacade;
import com.bytechef.test.extension.ObjectMapperSetupExtension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ObjectMapperSetupExtension.class)
class ProjectGitSyncImporterTest {

    private static final long PROJECT_ID = 21L;
    private static final int DRAFT_VERSION = 3;
    private static final String EXISTING_UUID = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String UNCHANGED_UUID = "aaaaaaaa-0000-0000-0000-000000000002";
    private static final String DRAFT_ONLY_UUID = "aaaaaaaa-0000-0000-0000-000000000003";
    private static final String NEW_UUID = "bbbbbbbb-0000-0000-0000-000000000001";
    private static final String FOREIGN_UUID = "cccccccc-0000-0000-0000-000000000001";

    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectWorkflowFacade projectWorkflowFacade = mock(ProjectWorkflowFacade.class);
    private final ProjectWorkflowService projectWorkflowService = mock(ProjectWorkflowService.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final WorkflowValidatorFacade workflowValidatorFacade = mock(WorkflowValidatorFacade.class);
    private ProjectGitSyncImporter projectGitSyncImporter;

    @BeforeEach
    void beforeEach() {
        Project project = new Project();

        project.setId(PROJECT_ID);
        project.setProjectVersions(
            new ArrayList<>(
                List.of(
                    new ProjectVersion(2, ProjectVersion.Status.PUBLISHED.ordinal(), null, "v2"),
                    new ProjectVersion(DRAFT_VERSION))));

        when(projectService.getProject(PROJECT_ID)).thenReturn(project);
        when(projectWorkflowService.getProjectWorkflows(PROJECT_ID, DRAFT_VERSION)).thenReturn(
            List.of(
                new ProjectWorkflow(PROJECT_ID, DRAFT_VERSION, "workflow-existing", UUID.fromString(EXISTING_UUID)),
                new ProjectWorkflow(PROJECT_ID, DRAFT_VERSION, "workflow-unchanged", UUID.fromString(UNCHANGED_UUID)),
                new ProjectWorkflow(
                    PROJECT_ID, DRAFT_VERSION, "workflow-draft-only", UUID.fromString(DRAFT_ONLY_UUID))));

        when(workflowService.getWorkflow("workflow-existing")).thenReturn(
            new Workflow("workflow-existing", "{\"label\":\"Old\"}", Workflow.Format.JSON));
        when(workflowService.getWorkflow("workflow-unchanged")).thenReturn(
            new Workflow("workflow-unchanged", "{\"label\":\"Same\",\"tasks\":[]}", Workflow.Format.JSON));
        when(workflowService.getWorkflow("workflow-draft-only")).thenReturn(
            new Workflow("workflow-draft-only", "{\"label\":\"Draft only\"}", Workflow.Format.JSON));
        when(workflowService.create(anyString(), eq(Workflow.Format.JSON), eq(Workflow.SourceType.JDBC)))
            .thenReturn(new Workflow("workflow-new", "{\"label\":\"New\"}", Workflow.Format.JSON));
        when(projectWorkflowService.getWorkflowUuidProjectWorkflows(NEW_UUID)).thenReturn(List.of());
        when(projectWorkflowService.getWorkflowUuidProjectWorkflows(FOREIGN_UUID)).thenReturn(
            List.of(new ProjectWorkflow(999L, 1, "workflow-foreign", UUID.fromString(FOREIGN_UUID))));

        projectGitSyncImporter = new ProjectGitSyncImporter(
            projectService, projectWorkflowFacade, projectWorkflowService, workflowService, workflowValidatorFacade);
    }

    @Test
    void testImportAddsUpdatesSkipsAndReports() {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("workflows/" + EXISTING_UUID + ".json", "{\n    \"label\" : \"New label\"\n}\n");
        files.put("workflows/" + UNCHANGED_UUID + ".json", "{\n    \"label\" : \"Same\",\n    \"tasks\" : [ ]\n}\n");
        files.put("workflows/" + NEW_UUID + ".json", "{\"label\":\"New\"}\n");
        files.put("workflows/" + FOREIGN_UUID + ".json", "{\"label\":\"Foreign\"}\n");

        ProjectGitSyncPullResultDTO pullResultDTO = projectGitSyncImporter.importSnapshot(
            PROJECT_ID, new ProjectGitSyncSnapshot("abc123", true, files));

        assertThat(pullResultDTO.commitHash()).isEqualTo("abc123");
        assertThat(uuids(pullResultDTO.updated())).containsExactly(EXISTING_UUID);
        assertThat(uuids(pullResultDTO.unchanged())).containsExactly(UNCHANGED_UUID);
        assertThat(uuids(pullResultDTO.added())).containsExactly(NEW_UUID);
        assertThat(uuids(pullResultDTO.skipped())).containsExactly(FOREIGN_UUID);
        assertThat(uuids(pullResultDTO.notInGit())).containsExactly(DRAFT_ONLY_UUID);

        verify(projectWorkflowFacade).updateWorkflow(eq("workflow-existing"), anyString(), anyInt());
        verify(projectWorkflowFacade, never()).updateWorkflow(eq("workflow-unchanged"), anyString(), anyInt());
        verify(projectWorkflowService).addWorkflow(
            PROJECT_ID, DRAFT_VERSION, "workflow-new", UUID.fromString(NEW_UUID));
        verify(projectWorkflowService, never()).delete(any());
    }

    @Test
    void testPathsOutsideTheWorkflowPatternAreIgnored() {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("workflows/../" + NEW_UUID + ".json", "{}");
        files.put("../workflows/" + NEW_UUID + ".json", "{}");
        files.put("workflows/" + NEW_UUID.toUpperCase() + ".json", "{}");
        files.put("project.json", "{}");

        ProjectGitSyncPullResultDTO pullResultDTO = projectGitSyncImporter.importSnapshot(
            PROJECT_ID, new ProjectGitSyncSnapshot("abc123", true, files));

        assertThat(pullResultDTO.hasChanges()).isFalse();
        verify(workflowService, never()).create(anyString(), any(), any());
    }

    @Test
    void testInvalidDefinitionAbortsImport() {
        doThrow(new IllegalArgumentException("no enabled trigger")).when(workflowValidatorFacade)
            .validateHasEnabledTrigger(anyString());

        Map<String, String> files = Map.of("workflows/" + NEW_UUID + ".json", "{\"label\":\"New\"}");

        assertThatThrownBy(() -> projectGitSyncImporter.importSnapshot(
            PROJECT_ID, new ProjectGitSyncSnapshot("abc123", true, files)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workflows/" + NEW_UUID + ".json")
                .hasMessageContaining("no enabled trigger");

        verify(workflowService, never()).create(anyString(), any(), any());
    }

    private static List<String> uuids(List<ProjectGitSyncWorkflowDTO> workflowDTOs) {
        return workflowDTOs.stream()
            .map(ProjectGitSyncWorkflowDTO::uuid)
            .toList();
    }
}
