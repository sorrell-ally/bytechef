/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the ByteChef Enterprise license (the "Enterprise License");
 * you may not use this file except in compliance with the Enterprise License.
 */

package com.bytechef.ee.embedded.ai.mcp.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bytechef.atlas.configuration.domain.WorkflowTask;
import com.bytechef.atlas.configuration.repository.WorkflowCrudRepository;
import com.bytechef.atlas.configuration.repository.WorkflowRepository;
import com.bytechef.atlas.configuration.service.WorkflowService;
import com.bytechef.atlas.configuration.service.WorkflowServiceImpl;
import com.bytechef.automation.configuration.service.PermissionService;
import com.bytechef.ee.embedded.ai.mcp.audit.McpIntegrationInstanceConfigurationAuditEvents;
import com.bytechef.ee.embedded.ai.mcp.config.EmbeddedMcpAuditIntTestConfiguration;
import com.bytechef.ee.embedded.ai.mcp.config.EmbeddedMcpIntTestConfiguration;
import com.bytechef.ee.embedded.ai.mcp.domain.McpIntegrationInstanceConfiguration;
import com.bytechef.ee.embedded.ai.mcp.domain.McpIntegrationInstanceConfigurationWorkflow;
import com.bytechef.ee.embedded.ai.mcp.service.McpIntegrationInstanceConfigurationService;
import com.bytechef.ee.embedded.ai.mcp.service.McpIntegrationInstanceConfigurationServiceImpl;
import com.bytechef.ee.embedded.ai.mcp.service.McpIntegrationInstanceConfigurationWorkflowService;
import com.bytechef.ee.embedded.ai.mcp.service.McpIntegrationInstanceConfigurationWorkflowServiceImpl;
import com.bytechef.ee.embedded.configuration.domain.Integration;
import com.bytechef.ee.embedded.configuration.domain.IntegrationInstanceConfiguration;
import com.bytechef.ee.embedded.configuration.domain.IntegrationInstanceConfigurationWorkflow;
import com.bytechef.ee.embedded.configuration.domain.IntegrationInstanceConfigurationWorkflowConnection;
import com.bytechef.ee.embedded.configuration.domain.IntegrationWorkflow;
import com.bytechef.ee.embedded.configuration.repository.IntegrationWorkflowRepository;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceConfigurationService;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceConfigurationServiceImpl;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceConfigurationWorkflowService;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceConfigurationWorkflowServiceImpl;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceWorkflowServiceImpl;
import com.bytechef.ee.embedded.configuration.service.IntegrationService;
import com.bytechef.ee.embedded.configuration.service.IntegrationServiceImpl;
import com.bytechef.ee.embedded.configuration.service.IntegrationWorkflowServiceImpl;
import com.bytechef.ee.platform.audit.repository.PersistenceAuditEventRepository;
import com.bytechef.platform.configuration.domain.ComponentConnection;
import com.bytechef.platform.configuration.domain.Environment;
import com.bytechef.platform.configuration.dto.WorkflowConfigurationDTO;
import com.bytechef.platform.configuration.dto.WorkflowConfigurationDTO.WorkflowConnectionDTO;
import com.bytechef.platform.configuration.dto.WorkflowToolParametersDTO;
import com.bytechef.platform.configuration.facade.ComponentConnectionFacade;
import com.bytechef.platform.connection.domain.Connection;
import com.bytechef.platform.connection.service.ConnectionService;
import com.bytechef.platform.connection.service.ConnectionServiceImpl;
import com.bytechef.platform.constant.PlatformType;
import com.bytechef.platform.mcp.domain.McpServer;
import com.bytechef.platform.mcp.repository.McpServerRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * @version ee
 *
 * @author Ivica Cardic
 */
@SpringBootTest(
    classes = {
        EmbeddedMcpIntTestConfiguration.class,
        McpIntegrationInstanceConfigurationFacadeIntTest.McpIntegrationInstanceConfigurationFacadeIntTestConfiguration.class
    },
    properties = {
        "bytechef.edition=ee", "bytechef.audit.enabled=true", "bytechef.workflow.repository.jdbc.enabled=true"
    })
class McpIntegrationInstanceConfigurationFacadeIntTest {

    private static final String WORKFLOW_ID = "5eed0000-0000-0000-0000-0000000000b1";

    @MockitoBean
    private ComponentConnectionFacade componentConnectionFacade;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private IntegrationInstanceConfigurationService integrationInstanceConfigurationService;

    @Autowired
    private IntegrationInstanceConfigurationWorkflowService integrationInstanceConfigurationWorkflowService;

    @Autowired
    private IntegrationService integrationService;

    @Autowired
    private IntegrationWorkflowRepository integrationWorkflowRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private McpIntegrationInstanceConfigurationFacade mcpIntegrationInstanceConfigurationFacade;

    @Autowired
    private McpIntegrationInstanceConfigurationService mcpIntegrationInstanceConfigurationService;

    @Autowired
    private McpIntegrationInstanceConfigurationWorkflowService mcpIntegrationInstanceConfigurationWorkflowService;

    @Autowired
    private McpServerRepository mcpServerRepository;

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    @MockitoBean
    private PermissionService permissionService;

    private long integrationId;
    private long integrationInstanceConfigurationId;
    private long mcpServerId;

    @BeforeEach
    void beforeEach() {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        when(permissionService.isTenantAdmin()).thenReturn(true);

        persistenceAuditEventRepository.deleteAll();

        McpServer mcpServer = mcpServerRepository.save(
            new McpServer("Google", PlatformType.EMBEDDED, Environment.DEVELOPMENT));

        mcpServerId = mcpServer.getId();

        Integration integration = new Integration();

        integration.setComponentName("gmail");
        integration.setName("Gmail");

        integration = integrationService.create(integration);

        integrationId = integration.getId();

        IntegrationInstanceConfiguration integrationInstanceConfiguration = new IntegrationInstanceConfiguration();

        integrationInstanceConfiguration.setConnectionParameters(Map.of());
        integrationInstanceConfiguration.setEnvironment(Environment.DEVELOPMENT);
        integrationInstanceConfiguration.setIntegrationId(integration.getId());
        integrationInstanceConfiguration.setIntegrationVersion(1);
        integrationInstanceConfiguration.setName("Gmail configuration");

        integrationInstanceConfiguration = integrationInstanceConfigurationService.create(
            integrationInstanceConfiguration);

        integrationInstanceConfigurationId = integrationInstanceConfiguration.getId();

        jdbcTemplate.update(
            """
                INSERT INTO workflow (id, definition, format, created_date, created_by, last_modified_date,
                    last_modified_by, version)
                VALUES (?, ?, 0, now(), 'system', now(), 'system', 0)
                """,
            WORKFLOW_ID,
            """
                {"tasks":[{"name":"gmail_1","type":"gmail/v1/sendEmail"}]}""");

        when(componentConnectionFacade.getComponentConnections(any(WorkflowTask.class)))
            .thenReturn(
                List.of(
                    new ComponentConnection("gmail", 1, "gmail_1", "gmail", true),
                    new ComponentConnection("slack", 1, "slack_1", "slack", true)));
    }

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();

        jdbcTemplate.update("DELETE FROM mcp_integration_instance_configuration_workflow");
        jdbcTemplate.update("DELETE FROM mcp_integration_instance_configuration");
        jdbcTemplate.update("DELETE FROM integration_instance_configuration_workflow_connection");
        jdbcTemplate.update("DELETE FROM integration_instance_configuration_workflow");
        jdbcTemplate.update("DELETE FROM integration_instance_configuration");
        jdbcTemplate.update("DELETE FROM integration_workflow");
        jdbcTemplate.update("DELETE FROM integration_version");
        jdbcTemplate.update("DELETE FROM integration");
        jdbcTemplate.update("DELETE FROM connection");
        jdbcTemplate.update("DELETE FROM workflow");

        mcpServerRepository.deleteAll();
        persistenceAuditEventRepository.deleteAll();
    }

    @Test
    void testCreateStoresTheConfiguredInputsAndNonIntegrationConnections() {
        long connectionId = createConnection("slack", Environment.DEVELOPMENT);

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID),
                List.of(slackWorkflowConfiguration(connectionId, Map.of("subject", "Hello"))), List.of());

        IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow =
            getIntegrationInstanceConfigurationWorkflow();

        assertThat(integrationInstanceConfigurationWorkflow.getConnections())
            .containsExactly(new IntegrationInstanceConfigurationWorkflowConnection(connectionId, "slack", "slack_1"));
        assertThat(integrationInstanceConfigurationWorkflow.getInputs()).isEqualTo(Map.of("subject", "Hello"));

        assertThat(getMcpIntegrationInstanceConfigurationWorkflows(mcpIntegrationInstanceConfiguration.getId()))
            .extracting(McpIntegrationInstanceConfigurationWorkflow::getIntegrationInstanceConfigurationWorkflowId)
            .containsExactly(integrationInstanceConfigurationWorkflow.getId());
    }

    @Test
    void testCreateRejectsAConnectionForTheIntegrationComponent() {
        long connectionId = createConnection("gmail", Environment.DEVELOPMENT);

        assertThatThrownBy(() -> mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
            mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID),
            List.of(
                new WorkflowConfigurationDTO(
                    WORKFLOW_ID, List.of(new WorkflowConnectionDTO(connectionId, "gmail", "gmail_1")), Map.of())),
            List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be configured");

        assertNothingWasStored();
    }

    @Test
    void testCreateRejectsAConnectionFromAnotherEnvironment() {
        long connectionId = createConnection("slack", Environment.PRODUCTION);

        assertThatThrownBy(() -> mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
            mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID),
            List.of(slackWorkflowConfiguration(connectionId, Map.of())), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Connection " + connectionId + " cannot be used");

        assertNothingWasStored();
    }

    @Test
    void testCreateRejectsAConfigurationWithoutARequiredNonIntegrationConnection() {
        assertThatThrownBy(() -> mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
            mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID),
            List.of(new WorkflowConfigurationDTO(WORKFLOW_ID, List.of(), Map.of())), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a slack connection");

        assertNothingWasStored();
    }

    @Test
    void testCreateWithoutAConfigurationKeepsEmptyInputs() {
        mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
            mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID));

        IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow =
            getIntegrationInstanceConfigurationWorkflow();

        assertThat(integrationInstanceConfigurationWorkflow.getConnections()).isEmpty();
        assertThat(integrationInstanceConfigurationWorkflow.getInputs()).isEmpty();
        assertThat(integrationInstanceConfigurationWorkflow.isEnabled()).isTrue();
    }

    @Test
    void testUpdateAppliesTheConfigurationOfAKeptWorkflow() {
        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID));

        long integrationInstanceConfigurationWorkflowId = getIntegrationInstanceConfigurationWorkflow().getId();

        long connectionId = createConnection("slack", Environment.DEVELOPMENT);

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfiguration(
            mcpIntegrationInstanceConfiguration.getId(), List.of(WORKFLOW_ID),
            List.of(slackWorkflowConfiguration(connectionId, Map.of("subject", "Hi"))), List.of());

        IntegrationInstanceConfigurationWorkflow updatedIntegrationInstanceConfigurationWorkflow =
            getIntegrationInstanceConfigurationWorkflow();

        assertThat(updatedIntegrationInstanceConfigurationWorkflow.getId())
            .isEqualTo(integrationInstanceConfigurationWorkflowId);
        assertThat(updatedIntegrationInstanceConfigurationWorkflow.getConnections())
            .containsExactly(new IntegrationInstanceConfigurationWorkflowConnection(connectionId, "slack", "slack_1"));
        assertThat(updatedIntegrationInstanceConfigurationWorkflow.getInputs()).isEqualTo(Map.of("subject", "Hi"));

        assertThat(getMcpIntegrationInstanceConfigurationWorkflows(mcpIntegrationInstanceConfiguration.getId()))
            .extracting(McpIntegrationInstanceConfigurationWorkflow::getIntegrationInstanceConfigurationWorkflowId)
            .containsExactly(integrationInstanceConfigurationWorkflowId);
    }

    @Test
    void testCreateRecordsTheMcpIntegrationInstanceConfiguration() {
        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID));

        assertThat(
            findSingleEventData(
                McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_CREATED))
                    .containsEntry("result", "SUCCESS")
                    .containsEntry(
                        "mcpIntegrationInstanceConfigurationId",
                        String.valueOf(mcpIntegrationInstanceConfiguration.getId()))
                    .containsEntry("mcpServerId", String.valueOf(mcpServerId))
                    .containsEntry("integrationInstanceConfigurationId",
                        String.valueOf(integrationInstanceConfigurationId))
                    .containsEntry("workflowIds", "[" + WORKFLOW_ID + "]");
    }

    @Test
    void testUpdateRecordsTheSelectedWorkflows() {
        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID));

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfiguration(
            mcpIntegrationInstanceConfiguration.getId(), List.of());

        assertThat(
            findSingleEventData(
                McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_UPDATED))
                    .containsEntry("result", "SUCCESS")
                    .containsEntry(
                        "mcpIntegrationInstanceConfigurationId",
                        String.valueOf(mcpIntegrationInstanceConfiguration.getId()))
                    .containsEntry("mcpServerId", String.valueOf(mcpServerId))
                    .containsEntry("workflowIds", "[]");
    }

    @Test
    void testDeleteRecordsTheMcpIntegrationInstanceConfiguration() {
        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(WORKFLOW_ID));

        mcpIntegrationInstanceConfigurationFacade.deleteMcpIntegrationInstanceConfiguration(
            mcpIntegrationInstanceConfiguration.getId());

        assertThat(
            findSingleEventData(
                McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_DELETED))
                    .containsEntry("result", "SUCCESS")
                    .containsEntry(
                        "mcpIntegrationInstanceConfigurationId",
                        String.valueOf(mcpIntegrationInstanceConfiguration.getId()));
    }

    @Test
    void testDeleteOfAnUnknownMcpIntegrationInstanceConfigurationRecordsAFailure() {
        assertThatThrownBy(
            () -> mcpIntegrationInstanceConfigurationFacade.deleteMcpIntegrationInstanceConfiguration(Long.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(
            findSingleEventData(
                McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_DELETED))
                    .containsEntry("result", "ERROR")
                    .containsEntry("errorClass", IllegalArgumentException.class.getName())
                    .containsEntry("mcpIntegrationInstanceConfigurationId", String.valueOf(Long.MAX_VALUE));
    }

    @Test
    void testUpdateVersionOfAnUnknownMcpIntegrationInstanceConfigurationRecordsAFailure() {
        assertThatThrownBy(
            () -> mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfigurationVersion(
                Long.MAX_VALUE, 2, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);

        assertThat(
            findSingleEventData(
                McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_VERSION_UPDATED))
                    .containsEntry("result", "ERROR")
                    .containsEntry("mcpIntegrationInstanceConfigurationId", String.valueOf(Long.MAX_VALUE))
                    .containsEntry("integrationVersion", "2");
    }

    @Test
    void testUpdateVersionKeepsWorkflowToolParametersAndEnabled() {
        UUID workflowUuid = UUID.randomUUID();
        String versionOneWorkflowId = "5eed0000-0000-0000-0000-0000000000c1";
        String versionTwoWorkflowId = "5eed0000-0000-0000-0000-0000000000c2";

        insertCallableWorkflow(versionOneWorkflowId, 1, workflowUuid, "message", "obsolete");
        insertCallableWorkflow(versionTwoWorkflowId, 2, workflowUuid, "message");

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(versionOneWorkflowId));

        McpIntegrationInstanceConfigurationWorkflow previousWorkflow = getMcpIntegrationInstanceConfigurationWorkflows(
            mcpIntegrationInstanceConfiguration.getId()).getFirst();

        mcpIntegrationInstanceConfigurationWorkflowService.updateParameters(
            previousWorkflow.getId(),
            Map.of("message", "=fromAi('message', 'STRING', {'required': true})", "obsolete", "x"));
        mcpIntegrationInstanceConfigurationWorkflowService.updateEnabled(previousWorkflow.getId(), false);

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfigurationVersion(
            mcpIntegrationInstanceConfiguration.getId(), 2, List.of(workflowUuid.toString()));

        List<McpIntegrationInstanceConfigurationWorkflow> recreatedWorkflows =
            getMcpIntegrationInstanceConfigurationWorkflows(mcpIntegrationInstanceConfiguration.getId());

        assertThat(recreatedWorkflows).hasSize(1);

        McpIntegrationInstanceConfigurationWorkflow recreatedWorkflow = recreatedWorkflows.getFirst();

        assertThat(recreatedWorkflow.getParameters())
            .isEqualTo(Map.of("message", "=fromAi('message', 'STRING', {'required': true})"));
        assertThat(recreatedWorkflow.isEnabled()).isFalse();
    }

    @Test
    void testUpdateVersionDropsNestedFieldsMissingInNewVersion() {
        UUID workflowUuid = UUID.randomUUID();
        String versionOneWorkflowId = "5eed0000-0000-0000-0000-0000000000e1";
        String versionTwoWorkflowId = "5eed0000-0000-0000-0000-0000000000e2";

        insertCallableWorkflowWithProperties(
            versionOneWorkflowId, 1, workflowUuid,
            "\"customer\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}," +
                "\"legacyId\":{\"type\":\"string\"}}}");
        insertCallableWorkflowWithProperties(
            versionTwoWorkflowId, 2, workflowUuid,
            "\"customer\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(versionOneWorkflowId));

        McpIntegrationInstanceConfigurationWorkflow previousWorkflow = getMcpIntegrationInstanceConfigurationWorkflows(
            mcpIntegrationInstanceConfiguration.getId()).getFirst();

        mcpIntegrationInstanceConfigurationWorkflowService.updateParameters(
            previousWorkflow.getId(),
            Map.of(
                "toolName", "findCustomer",
                "customer",
                Map.of("name", "=fromAi('name', 'STRING')", "legacyId", "=fromAi('legacyId', 'STRING')")));

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfigurationVersion(
            mcpIntegrationInstanceConfiguration.getId(), 2, List.of(workflowUuid.toString()));

        McpIntegrationInstanceConfigurationWorkflow recreatedWorkflow = getMcpIntegrationInstanceConfigurationWorkflows(
            mcpIntegrationInstanceConfiguration.getId()).getFirst();

        assertThat(recreatedWorkflow.getParameters())
            .isEqualTo(Map.of("toolName", "findCustomer", "customer", Map.of("name", "=fromAi('name', 'STRING')")));
    }

    @Test
    void testUpdateVersionFiltersArrayItemFieldsMissingInNewVersion() {
        UUID workflowUuid = UUID.randomUUID();
        String versionOneWorkflowId = "5eed0000-0000-0000-0000-0000000000e3";
        String versionTwoWorkflowId = "5eed0000-0000-0000-0000-0000000000e4";

        insertCallableWorkflowWithProperties(
            versionOneWorkflowId, 1, workflowUuid,
            "\"lineItems\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{" +
                "\"sku\":{\"type\":\"string\"},\"legacy\":{\"type\":\"string\"}}}}");
        insertCallableWorkflowWithProperties(
            versionTwoWorkflowId, 2, workflowUuid,
            "\"lineItems\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{" +
                "\"sku\":{\"type\":\"string\"}}}}");

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(versionOneWorkflowId));

        McpIntegrationInstanceConfigurationWorkflow previousWorkflow = getMcpIntegrationInstanceConfigurationWorkflows(
            mcpIntegrationInstanceConfiguration.getId()).getFirst();

        mcpIntegrationInstanceConfigurationWorkflowService.updateParameters(
            previousWorkflow.getId(),
            Map.of(
                "lineItems",
                List.of(Map.of("sku", "A-1", "legacy", "x"),
                    Map.of("sku", "=fromAi('sku', 'STRING')", "legacy", "y"))));

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfigurationVersion(
            mcpIntegrationInstanceConfiguration.getId(), 2, List.of(workflowUuid.toString()));

        McpIntegrationInstanceConfigurationWorkflow recreatedWorkflow = getMcpIntegrationInstanceConfigurationWorkflows(
            mcpIntegrationInstanceConfiguration.getId()).getFirst();

        assertThat(recreatedWorkflow.getParameters())
            .isEqualTo(
                Map.of("lineItems", List.of(Map.of("sku", "A-1"), Map.of("sku", "=fromAi('sku', 'STRING')"))));
    }

    @Test
    void testUpdateVersionWithUnparseableInputSchemaKeepsToolNameAndEnabled() {
        UUID workflowUuid = UUID.randomUUID();
        String versionOneWorkflowId = "5eed0000-0000-0000-0000-0000000000e5";
        String versionTwoWorkflowId = "5eed0000-0000-0000-0000-0000000000e6";

        insertCallableWorkflow(versionOneWorkflowId, 1, workflowUuid, "message");
        insertCallableWorkflowWithProperties(versionTwoWorkflowId, 2, workflowUuid,
            "\"message\":{\"type\":\"unknown\"}");

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(versionOneWorkflowId));

        McpIntegrationInstanceConfigurationWorkflow previousWorkflow = getMcpIntegrationInstanceConfigurationWorkflows(
            mcpIntegrationInstanceConfiguration.getId()).getFirst();

        mcpIntegrationInstanceConfigurationWorkflowService.updateParameters(
            previousWorkflow.getId(),
            Map.of(
                "toolName", "sendMessage", "toolDescription", "Sends a message", "message",
                "=fromAi('message', 'STRING')"));
        mcpIntegrationInstanceConfigurationWorkflowService.updateEnabled(previousWorkflow.getId(), false);

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfigurationVersion(
            mcpIntegrationInstanceConfiguration.getId(), 2, List.of(workflowUuid.toString()));

        McpIntegrationInstanceConfigurationWorkflow recreatedWorkflow = getMcpIntegrationInstanceConfigurationWorkflows(
            mcpIntegrationInstanceConfiguration.getId()).getFirst();

        assertThat(recreatedWorkflow.getParameters())
            .isEqualTo(Map.of("toolName", "sendMessage", "toolDescription", "Sends a message"));
        assertThat(recreatedWorkflow.isEnabled()).isFalse();
    }

    @Test
    void testUpdateVersionDropsWorkflowsMissingInNewVersion() {
        UUID workflowUuid = UUID.randomUUID();
        String versionOneWorkflowId = "5eed0000-0000-0000-0000-0000000000c1";

        insertCallableWorkflow(versionOneWorkflowId, 1, workflowUuid, "message");

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(versionOneWorkflowId));

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfigurationVersion(
            mcpIntegrationInstanceConfiguration.getId(), 2, List.of(workflowUuid.toString()));

        assertThat(getMcpIntegrationInstanceConfigurationWorkflows(mcpIntegrationInstanceConfiguration.getId()))
            .isEmpty();
    }

    @Test
    void testCreateStoresToolParameters() {
        String firstWorkflowId = "5eed0000-0000-0000-0000-0000000000d1";
        String secondWorkflowId = "5eed0000-0000-0000-0000-0000000000d2";

        insertCallableWorkflow(firstWorkflowId, 1, UUID.randomUUID(), "message");
        insertCallableWorkflow(secondWorkflowId, 1, UUID.randomUUID(), "message");

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(firstWorkflowId, secondWorkflowId), List.of(),
                List.of(new WorkflowToolParametersDTO(firstWorkflowId, Map.of("toolName", "first_tool"))));

        Map<String, McpIntegrationInstanceConfigurationWorkflow> mcpWorkflowMap =
            getMcpWorkflowsByWorkflowId(mcpIntegrationInstanceConfiguration.getId());

        assertThat(mcpWorkflowMap.get(firstWorkflowId)
            .getParameters()).isEqualTo(Map.of("toolName", "first_tool"));
        assertThat(mcpWorkflowMap.get(secondWorkflowId)
            .getParameters()).isNullOrEmpty();
    }

    @Test
    void testUpdateStoresToolParametersForNewAndKeptWorkflows() {
        String firstWorkflowId = "5eed0000-0000-0000-0000-0000000000d1";
        String secondWorkflowId = "5eed0000-0000-0000-0000-0000000000d2";

        insertCallableWorkflow(firstWorkflowId, 1, UUID.randomUUID(), "message");
        insertCallableWorkflow(secondWorkflowId, 1, UUID.randomUUID(), "message");

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(firstWorkflowId));

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfiguration(
            mcpIntegrationInstanceConfiguration.getId(), List.of(firstWorkflowId, secondWorkflowId), List.of(),
            List.of(
                new WorkflowToolParametersDTO(firstWorkflowId, Map.of("toolName", "kept_tool")),
                new WorkflowToolParametersDTO(secondWorkflowId, Map.of("toolName", "added_tool"))));

        Map<String, McpIntegrationInstanceConfigurationWorkflow> mcpWorkflowMap =
            getMcpWorkflowsByWorkflowId(mcpIntegrationInstanceConfiguration.getId());

        assertThat(mcpWorkflowMap.get(firstWorkflowId)
            .getParameters()).isEqualTo(Map.of("toolName", "kept_tool"));
        assertThat(mcpWorkflowMap.get(secondWorkflowId)
            .getParameters()).isEqualTo(Map.of("toolName", "added_tool"));
    }

    @Test
    void testUpdateLeavesParametersOfWorkflowsWithoutToolParameters() {
        String firstWorkflowId = "5eed0000-0000-0000-0000-0000000000d1";
        String secondWorkflowId = "5eed0000-0000-0000-0000-0000000000d2";

        insertCallableWorkflow(firstWorkflowId, 1, UUID.randomUUID(), "message");
        insertCallableWorkflow(secondWorkflowId, 1, UUID.randomUUID(), "message");

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationFacade.createMcpIntegrationInstanceConfiguration(
                mcpServerId, integrationInstanceConfigurationId, List.of(firstWorkflowId, secondWorkflowId), List.of(),
                List.of(
                    new WorkflowToolParametersDTO(firstWorkflowId, Map.of("toolName", "first_tool")),
                    new WorkflowToolParametersDTO(secondWorkflowId, Map.of("toolName", "second_tool"))));

        mcpIntegrationInstanceConfigurationFacade.updateMcpIntegrationInstanceConfiguration(
            mcpIntegrationInstanceConfiguration.getId(), List.of(firstWorkflowId, secondWorkflowId), List.of(),
            List.of(new WorkflowToolParametersDTO(secondWorkflowId, Map.of("toolName", "renamed_tool"))));

        Map<String, McpIntegrationInstanceConfigurationWorkflow> mcpWorkflowMap =
            getMcpWorkflowsByWorkflowId(mcpIntegrationInstanceConfiguration.getId());

        assertThat(mcpWorkflowMap.get(firstWorkflowId)
            .getParameters()).isEqualTo(Map.of("toolName", "first_tool"));
        assertThat(mcpWorkflowMap.get(secondWorkflowId)
            .getParameters()).isEqualTo(Map.of("toolName", "renamed_tool"));
    }

    private Map<String, McpIntegrationInstanceConfigurationWorkflow> getMcpWorkflowsByWorkflowId(
        long mcpIntegrationInstanceConfigurationId) {

        Map<Long, String> workflowIdMap = new HashMap<>();

        for (IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow : integrationInstanceConfigurationWorkflowService
            .getIntegrationInstanceConfigurationWorkflows(integrationInstanceConfigurationId)) {

            workflowIdMap.put(
                integrationInstanceConfigurationWorkflow.getId(),
                integrationInstanceConfigurationWorkflow.getWorkflowId());
        }

        Map<String, McpIntegrationInstanceConfigurationWorkflow> mcpWorkflowMap = new HashMap<>();

        for (McpIntegrationInstanceConfigurationWorkflow mcpWorkflow : getMcpIntegrationInstanceConfigurationWorkflows(
            mcpIntegrationInstanceConfigurationId)) {

            mcpWorkflowMap.put(
                workflowIdMap.get(mcpWorkflow.getIntegrationInstanceConfigurationWorkflowId()), mcpWorkflow);
        }

        return mcpWorkflowMap;
    }

    private void insertCallableWorkflow(
        String workflowId, int integrationVersion, UUID workflowUuid, String... inputNames) {

        StringBuilder properties = new StringBuilder();

        for (String inputName : inputNames) {
            if (properties.length() > 0) {
                properties.append(',');
            }

            properties.append("\"")
                .append(inputName)
                .append("\":{\"type\":\"string\"}");
        }

        insertCallableWorkflowWithProperties(workflowId, integrationVersion, workflowUuid, properties.toString());
    }

    private void insertCallableWorkflowWithProperties(
        String workflowId, int integrationVersion, UUID workflowUuid, String propertiesJson) {

        String inputSchema = "{\"type\":\"object\",\"properties\":{" + propertiesJson + "}}";

        String definition =
            "{\"triggers\":[{\"name\":\"trigger_1\",\"type\":\"workflow/v1/newWorkflowCall\",\"parameters\":" +
                "{\"inputSchema\":\"" + inputSchema.replace("\"", "\\\"") + "\"}}],\"tasks\":[]}";

        jdbcTemplate.update(
            """
                INSERT INTO workflow (id, definition, format, created_date, created_by, last_modified_date,
                    last_modified_by, version)
                VALUES (?, ?, 0, now(), 'system', now(), 'system', 0)
                """,
            workflowId, definition);

        integrationWorkflowRepository.save(
            new IntegrationWorkflow(integrationId, integrationVersion, workflowId, workflowUuid));
    }

    private void assertNothingWasStored() {
        assertThat(
            integrationInstanceConfigurationWorkflowService.getIntegrationInstanceConfigurationWorkflows(
                integrationInstanceConfigurationId)).isEmpty();
        assertThat(mcpIntegrationInstanceConfigurationService.getMcpIntegrationInstanceConfigurations()).isEmpty();
    }

    private Map<String, String> findSingleEventData(String eventType) {
        return EmbeddedMcpAuditIntTestConfiguration.findSingleEventData(persistenceAuditEventRepository, eventType);
    }

    private long createConnection(String componentName, Environment environment) {
        Connection connection = connectionService.create(
            null, componentName, 1, environment.ordinal(), componentName + " connection", Map.of(),
            PlatformType.EMBEDDED);

        return connection.getId();
    }

    private IntegrationInstanceConfigurationWorkflow getIntegrationInstanceConfigurationWorkflow() {
        List<IntegrationInstanceConfigurationWorkflow> integrationInstanceConfigurationWorkflows =
            integrationInstanceConfigurationWorkflowService.getIntegrationInstanceConfigurationWorkflows(
                integrationInstanceConfigurationId);

        assertThat(integrationInstanceConfigurationWorkflows).hasSize(1);

        return integrationInstanceConfigurationWorkflows.getFirst();
    }

    private List<McpIntegrationInstanceConfigurationWorkflow> getMcpIntegrationInstanceConfigurationWorkflows(
        long mcpIntegrationInstanceConfigurationId) {

        return mcpIntegrationInstanceConfigurationWorkflowService
            .getMcpIntegrationInstanceConfigurationMcpIntegrationInstanceConfigurationWorkflows(
                mcpIntegrationInstanceConfigurationId);
    }

    private static WorkflowConfigurationDTO slackWorkflowConfiguration(long connectionId, Map<String, Object> inputs) {
        return new WorkflowConfigurationDTO(
            WORKFLOW_ID, List.of(new WorkflowConnectionDTO(connectionId, "slack", "slack_1")), inputs);
    }

    @Configuration
    @EnableCaching
    @Import({
        ConnectionServiceImpl.class, IntegrationInstanceConfigurationServiceImpl.class,
        IntegrationInstanceConfigurationWorkflowServiceImpl.class, IntegrationInstanceWorkflowServiceImpl.class,
        IntegrationServiceImpl.class, IntegrationWorkflowServiceImpl.class,
        McpIntegrationInstanceConfigurationFacadeImpl.class, McpIntegrationInstanceConfigurationServiceImpl.class,
        McpIntegrationInstanceConfigurationWorkflowServiceImpl.class, EmbeddedMcpAuditIntTestConfiguration.class
    })
    static class McpIntegrationInstanceConfigurationFacadeIntTestConfiguration {

        @Bean
        WorkflowService workflowService(
            CacheManager cacheManager, List<WorkflowCrudRepository> workflowCrudRepositories,
            List<WorkflowRepository> workflowRepositories) {

            return new WorkflowServiceImpl(cacheManager, workflowCrudRepositories, workflowRepositories);
        }
    }
}
