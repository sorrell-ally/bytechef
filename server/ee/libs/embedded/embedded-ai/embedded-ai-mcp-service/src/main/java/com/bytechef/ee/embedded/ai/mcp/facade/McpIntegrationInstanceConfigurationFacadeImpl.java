/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the ByteChef Enterprise license (the "Enterprise License");
 * you may not use this file except in compliance with the Enterprise License.
 */

package com.bytechef.ee.embedded.ai.mcp.facade;

import static com.bytechef.ee.embedded.ai.mcp.facade.McpIntegrationInstanceConfigurationWorkflowFacadeImpl.getToolCallableTrigger;

import com.bytechef.atlas.configuration.domain.Workflow;
import com.bytechef.atlas.configuration.domain.WorkflowTask;
import com.bytechef.atlas.configuration.service.WorkflowService;
import com.bytechef.commons.util.MapUtils;
import com.bytechef.component.definition.Property;
import com.bytechef.component.definition.Property.ArrayProperty;
import com.bytechef.component.definition.Property.ObjectProperty;
import com.bytechef.definition.BaseProperty.BaseValueProperty;
import com.bytechef.ee.embedded.ai.mcp.audit.McpIntegrationInstanceConfigurationAuditEvents;
import com.bytechef.ee.embedded.ai.mcp.domain.McpIntegrationInstanceConfiguration;
import com.bytechef.ee.embedded.ai.mcp.domain.McpIntegrationInstanceConfigurationWorkflow;
import com.bytechef.ee.embedded.ai.mcp.service.McpIntegrationInstanceConfigurationService;
import com.bytechef.ee.embedded.ai.mcp.service.McpIntegrationInstanceConfigurationWorkflowService;
import com.bytechef.ee.embedded.configuration.domain.Integration;
import com.bytechef.ee.embedded.configuration.domain.IntegrationInstanceConfiguration;
import com.bytechef.ee.embedded.configuration.domain.IntegrationInstanceConfigurationWorkflow;
import com.bytechef.ee.embedded.configuration.domain.IntegrationInstanceConfigurationWorkflowConnection;
import com.bytechef.ee.embedded.configuration.domain.IntegrationWorkflow;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceConfigurationService;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceConfigurationWorkflowService;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceWorkflowService;
import com.bytechef.ee.embedded.configuration.service.IntegrationService;
import com.bytechef.ee.embedded.configuration.service.IntegrationWorkflowService;
import com.bytechef.platform.ai.tool.constant.ToolConstants;
import com.bytechef.platform.annotation.ConditionalOnEEVersion;
import com.bytechef.platform.audit.AuditData;
import com.bytechef.platform.audit.Audited;
import com.bytechef.platform.component.constant.WorkflowConstants;
import com.bytechef.platform.component.definition.PropertyFactory;
import com.bytechef.platform.configuration.domain.ComponentConnection;
import com.bytechef.platform.configuration.domain.Environment;
import com.bytechef.platform.configuration.domain.WorkflowTrigger;
import com.bytechef.platform.configuration.dto.WorkflowConfigurationDTO;
import com.bytechef.platform.configuration.dto.WorkflowConfigurationDTO.WorkflowConnectionDTO;
import com.bytechef.platform.configuration.dto.WorkflowToolParametersDTO;
import com.bytechef.platform.configuration.facade.ComponentConnectionFacade;
import com.bytechef.platform.connection.domain.Connection;
import com.bytechef.platform.connection.service.ConnectionService;
import com.bytechef.platform.util.SchemaUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @version ee
 *
 * @author Ivica Cardic
 */
@Service
@Transactional
@ConditionalOnEEVersion
public class McpIntegrationInstanceConfigurationFacadeImpl implements McpIntegrationInstanceConfigurationFacade {

    private static final Logger log = LoggerFactory.getLogger(McpIntegrationInstanceConfigurationFacadeImpl.class);

    private final ComponentConnectionFacade componentConnectionFacade;
    private final ConnectionService connectionService;
    private final IntegrationInstanceConfigurationService integrationInstanceConfigurationService;
    private final IntegrationInstanceConfigurationWorkflowService integrationInstanceConfigurationWorkflowService;
    private final IntegrationInstanceWorkflowService integrationInstanceWorkflowService;
    private final IntegrationService integrationService;
    private final IntegrationWorkflowService integrationWorkflowService;
    private final McpIntegrationInstanceConfigurationService mcpIntegrationInstanceConfigurationService;
    private final McpIntegrationInstanceConfigurationWorkflowService mcpIntegrationInstanceConfigurationWorkflowService;
    private final WorkflowService workflowService;

    @SuppressFBWarnings("EI")
    public McpIntegrationInstanceConfigurationFacadeImpl(
        ComponentConnectionFacade componentConnectionFacade, ConnectionService connectionService,
        IntegrationInstanceConfigurationService integrationInstanceConfigurationService,
        IntegrationInstanceConfigurationWorkflowService integrationInstanceConfigurationWorkflowService,
        IntegrationInstanceWorkflowService integrationInstanceWorkflowService, IntegrationService integrationService,
        IntegrationWorkflowService integrationWorkflowService,
        McpIntegrationInstanceConfigurationService mcpIntegrationInstanceConfigurationService,
        McpIntegrationInstanceConfigurationWorkflowService mcpIntegrationInstanceConfigurationWorkflowService,
        WorkflowService workflowService) {

        this.componentConnectionFacade = componentConnectionFacade;
        this.connectionService = connectionService;
        this.integrationInstanceConfigurationService = integrationInstanceConfigurationService;
        this.integrationInstanceConfigurationWorkflowService = integrationInstanceConfigurationWorkflowService;
        this.integrationInstanceWorkflowService = integrationInstanceWorkflowService;
        this.integrationService = integrationService;
        this.integrationWorkflowService = integrationWorkflowService;
        this.mcpIntegrationInstanceConfigurationService = mcpIntegrationInstanceConfigurationService;
        this.mcpIntegrationInstanceConfigurationWorkflowService = mcpIntegrationInstanceConfigurationWorkflowService;
        this.workflowService = workflowService;
    }

    @Override
    @Audited(
        event = McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_CREATED, data = {
            @AuditData(key = "mcpIntegrationInstanceConfigurationId", value = "#result?.id"),
            @AuditData(key = "mcpServerId", value = "#mcpServerId"),
            @AuditData(key = "integrationInstanceConfigurationId", value = "#integrationInstanceConfigurationId"),
            @AuditData(key = "workflowIds", value = "#selectedWorkflowIds")
        })
    @PreAuthorize("isTenantAdmin()")
    public McpIntegrationInstanceConfiguration createMcpIntegrationInstanceConfiguration(
        long mcpServerId, long integrationInstanceConfigurationId, List<String> selectedWorkflowIds) {

        return createMcpIntegrationInstanceConfiguration(
            mcpServerId, integrationInstanceConfigurationId, selectedWorkflowIds, List.of(), List.of());
    }

    @Override
    @Audited(
        event = McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_CREATED, data = {
            @AuditData(key = "mcpIntegrationInstanceConfigurationId", value = "#result?.id"),
            @AuditData(key = "mcpServerId", value = "#mcpServerId"),
            @AuditData(key = "integrationInstanceConfigurationId", value = "#integrationInstanceConfigurationId"),
            @AuditData(key = "workflowIds", value = "#selectedWorkflowIds")
        })
    @PreAuthorize("isTenantAdmin()")
    public McpIntegrationInstanceConfiguration createMcpIntegrationInstanceConfiguration(
        long mcpServerId, long integrationInstanceConfigurationId, List<String> selectedWorkflowIds,
        List<WorkflowConfigurationDTO> workflowConfigurations, List<WorkflowToolParametersDTO> toolParameters) {

        IntegrationInstanceConfiguration integrationInstanceConfiguration =
            integrationInstanceConfigurationService.getIntegrationInstanceConfiguration(
                integrationInstanceConfigurationId);

        Map<String, WorkflowConfigurationDTO> workflowConfigurationMap = toWorkflowConfigurationMap(
            workflowConfigurations);

        Map<String, Map<String, ?>> toolParametersMap = toToolParametersMap(toolParameters);

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            new McpIntegrationInstanceConfiguration(integrationInstanceConfigurationId, mcpServerId);

        mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationService.create(mcpIntegrationInstanceConfiguration);

        List<IntegrationInstanceConfigurationWorkflow> existingWorkflows =
            integrationInstanceConfigurationWorkflowService.getIntegrationInstanceConfigurationWorkflows(
                integrationInstanceConfigurationId);

        Map<String, IntegrationInstanceConfigurationWorkflow> existingWorkflowsByWorkflowId = new HashMap<>();

        for (IntegrationInstanceConfigurationWorkflow existingWorkflow : existingWorkflows) {
            existingWorkflowsByWorkflowId.put(existingWorkflow.getWorkflowId(), existingWorkflow);
        }

        for (String workflowId : selectedWorkflowIds) {
            IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow =
                saveIntegrationInstanceConfigurationWorkflow(
                    integrationInstanceConfiguration, existingWorkflowsByWorkflowId.get(workflowId), workflowId,
                    workflowConfigurationMap.get(workflowId));

            McpIntegrationInstanceConfigurationWorkflow mcpIntegrationInstanceConfigurationWorkflow =
                mcpIntegrationInstanceConfigurationWorkflowService.create(
                    mcpIntegrationInstanceConfiguration.getId(), integrationInstanceConfigurationWorkflow.getId());

            storeToolParameters(mcpIntegrationInstanceConfigurationWorkflow, toolParametersMap.get(workflowId));
        }

        return mcpIntegrationInstanceConfiguration;
    }

    @Override
    @Audited(
        event = McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_DELETED, data = {
            @AuditData(key = "mcpIntegrationInstanceConfigurationId", value = "#mcpIntegrationInstanceConfigurationId")
        })
    @PreAuthorize("isTenantAdmin()")
    public void deleteMcpIntegrationInstanceConfiguration(long mcpIntegrationInstanceConfigurationId) {
        mcpIntegrationInstanceConfigurationService
            .fetchMcpIntegrationInstanceConfiguration(mcpIntegrationInstanceConfigurationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "McpIntegrationInstanceConfiguration not found: " + mcpIntegrationInstanceConfigurationId));

        List<McpIntegrationInstanceConfigurationWorkflow> mcpIntegrationInstanceConfigurationWorkflows =
            mcpIntegrationInstanceConfigurationWorkflowService
                .getMcpIntegrationInstanceConfigurationMcpIntegrationInstanceConfigurationWorkflows(
                    mcpIntegrationInstanceConfigurationId);

        for (McpIntegrationInstanceConfigurationWorkflow mcpIntegrationInstanceConfigurationWorkflow : mcpIntegrationInstanceConfigurationWorkflows) {
            long integrationInstanceConfigurationWorkflowId =
                mcpIntegrationInstanceConfigurationWorkflow.getIntegrationInstanceConfigurationWorkflowId();

            integrationInstanceWorkflowService.deleteByIntegrationInstanceConfigurationWorkflowId(
                integrationInstanceConfigurationWorkflowId);

            mcpIntegrationInstanceConfigurationWorkflowService
                .delete(mcpIntegrationInstanceConfigurationWorkflow.getId());

            integrationInstanceConfigurationWorkflowService.updateEnabled(
                integrationInstanceConfigurationWorkflowId, false);
        }

        mcpIntegrationInstanceConfigurationService.delete(mcpIntegrationInstanceConfigurationId);
    }

    @Override
    @Audited(
        event = McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_UPDATED, data = {
            @AuditData(key = "mcpIntegrationInstanceConfigurationId", value = "#mcpIntegrationInstanceConfigurationId"),
            @AuditData(key = "mcpServerId", value = "#result?.mcpServerId"),
            @AuditData(key = "workflowIds", value = "#selectedWorkflowIds")
        })
    @PreAuthorize("isTenantAdmin()")
    public McpIntegrationInstanceConfiguration updateMcpIntegrationInstanceConfiguration(
        long mcpIntegrationInstanceConfigurationId, List<String> selectedWorkflowIds) {

        return updateMcpIntegrationInstanceConfiguration(
            mcpIntegrationInstanceConfigurationId, selectedWorkflowIds, List.of(), List.of());
    }

    @Override
    @Audited(
        event = McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_UPDATED, data = {
            @AuditData(key = "mcpIntegrationInstanceConfigurationId", value = "#mcpIntegrationInstanceConfigurationId"),
            @AuditData(key = "mcpServerId", value = "#result?.mcpServerId"),
            @AuditData(key = "workflowIds", value = "#selectedWorkflowIds")
        })
    @PreAuthorize("isTenantAdmin()")
    public McpIntegrationInstanceConfiguration updateMcpIntegrationInstanceConfiguration(
        long mcpIntegrationInstanceConfigurationId, List<String> selectedWorkflowIds,
        List<WorkflowConfigurationDTO> workflowConfigurations, List<WorkflowToolParametersDTO> toolParameters) {

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationService
                .fetchMcpIntegrationInstanceConfiguration(mcpIntegrationInstanceConfigurationId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "McpIntegrationInstanceConfiguration not found: " + mcpIntegrationInstanceConfigurationId));

        List<McpIntegrationInstanceConfigurationWorkflow> existingMcpIntegrationInstanceConfigurationWorkflows =
            mcpIntegrationInstanceConfigurationWorkflowService
                .getMcpIntegrationInstanceConfigurationMcpIntegrationInstanceConfigurationWorkflows(
                    mcpIntegrationInstanceConfigurationId);

        List<IntegrationInstanceConfigurationWorkflow> integrationInstanceConfigurationWorkflows =
            integrationInstanceConfigurationWorkflowService.getIntegrationInstanceConfigurationWorkflows(
                mcpIntegrationInstanceConfiguration.getIntegrationInstanceConfigurationId());

        Map<Long, IntegrationInstanceConfigurationWorkflow> integrationInstanceConfigurationWorkflowMap =
            new HashMap<>();
        Map<String, IntegrationInstanceConfigurationWorkflow> integrationInstanceConfigurationWorkflowByWorkflowIdMap =
            new HashMap<>();

        for (IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow : integrationInstanceConfigurationWorkflows) {

            integrationInstanceConfigurationWorkflowMap.put(
                integrationInstanceConfigurationWorkflow.getId(), integrationInstanceConfigurationWorkflow);
            integrationInstanceConfigurationWorkflowByWorkflowIdMap.put(
                integrationInstanceConfigurationWorkflow.getWorkflowId(), integrationInstanceConfigurationWorkflow);
        }

        Map<String, McpIntegrationInstanceConfigurationWorkflow> existingWorkflowIdMap = new HashMap<>();

        for (McpIntegrationInstanceConfigurationWorkflow mcpIntegrationInstanceConfigurationWorkflow : existingMcpIntegrationInstanceConfigurationWorkflows) {
            IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow =
                integrationInstanceConfigurationWorkflowMap.get(
                    mcpIntegrationInstanceConfigurationWorkflow.getIntegrationInstanceConfigurationWorkflowId());

            if (integrationInstanceConfigurationWorkflow != null) {
                existingWorkflowIdMap.put(
                    integrationInstanceConfigurationWorkflow.getWorkflowId(),
                    mcpIntegrationInstanceConfigurationWorkflow);
            }
        }

        Set<String> selectedWorkflowIdSet = new HashSet<>(selectedWorkflowIds);

        IntegrationInstanceConfiguration integrationInstanceConfiguration =
            integrationInstanceConfigurationService.getIntegrationInstanceConfiguration(
                mcpIntegrationInstanceConfiguration.getIntegrationInstanceConfigurationId());

        Map<String, WorkflowConfigurationDTO> workflowConfigurationMap = toWorkflowConfigurationMap(
            workflowConfigurations);

        Map<String, Map<String, ?>> toolParametersMap = toToolParametersMap(toolParameters);

        for (String workflowId : selectedWorkflowIds) {
            IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow =
                integrationInstanceConfigurationWorkflowByWorkflowIdMap.get(workflowId);

            if (existingWorkflowIdMap.containsKey(workflowId)) {
                WorkflowConfigurationDTO workflowConfiguration = workflowConfigurationMap.get(workflowId);

                if (workflowConfiguration != null) {
                    saveIntegrationInstanceConfigurationWorkflow(
                        integrationInstanceConfiguration, integrationInstanceConfigurationWorkflow, workflowId,
                        workflowConfiguration);
                }
            } else {
                integrationInstanceConfigurationWorkflow = saveIntegrationInstanceConfigurationWorkflow(
                    integrationInstanceConfiguration, integrationInstanceConfigurationWorkflow, workflowId,
                    workflowConfigurationMap.get(workflowId));

                McpIntegrationInstanceConfigurationWorkflow mcpIntegrationInstanceConfigurationWorkflow =
                    mcpIntegrationInstanceConfigurationWorkflowService.create(
                        mcpIntegrationInstanceConfigurationId, integrationInstanceConfigurationWorkflow.getId());

                storeToolParameters(mcpIntegrationInstanceConfigurationWorkflow, toolParametersMap.get(workflowId));
            }
        }

        for (Map.Entry<String, McpIntegrationInstanceConfigurationWorkflow> entry : existingWorkflowIdMap.entrySet()) {
            if (selectedWorkflowIdSet.contains(entry.getKey())) {
                storeToolParameters(entry.getValue(), toolParametersMap.get(entry.getKey()));
            }
        }

        for (Map.Entry<String, McpIntegrationInstanceConfigurationWorkflow> entry : existingWorkflowIdMap.entrySet()) {
            if (!selectedWorkflowIdSet.contains(entry.getKey())) {
                McpIntegrationInstanceConfigurationWorkflow mcpIntegrationInstanceConfigurationWorkflow =
                    entry.getValue();

                long integrationInstanceConfigurationWorkflowId =
                    mcpIntegrationInstanceConfigurationWorkflow.getIntegrationInstanceConfigurationWorkflowId();

                integrationInstanceWorkflowService.deleteByIntegrationInstanceConfigurationWorkflowId(
                    integrationInstanceConfigurationWorkflowId);

                mcpIntegrationInstanceConfigurationWorkflowService
                    .delete(mcpIntegrationInstanceConfigurationWorkflow.getId());

                integrationInstanceConfigurationWorkflowService.updateEnabled(
                    integrationInstanceConfigurationWorkflowId, false);
            }
        }

        return mcpIntegrationInstanceConfiguration;
    }

    @Override
    @Audited(
        event = McpIntegrationInstanceConfigurationAuditEvents.MCP_INTEGRATION_INSTANCE_CONFIGURATION_VERSION_UPDATED,
        data = {
            @AuditData(key = "mcpIntegrationInstanceConfigurationId", value = "#mcpIntegrationInstanceConfigurationId"),
            @AuditData(key = "integrationVersion", value = "#integrationVersion"),
            @AuditData(key = "workflowUuids", value = "#workflowUuids")
        })
    @PreAuthorize("isTenantAdmin()")
    public void updateMcpIntegrationInstanceConfigurationVersion(
        long mcpIntegrationInstanceConfigurationId, int integrationVersion, List<String> workflowUuids) {

        McpIntegrationInstanceConfiguration mcpIntegrationInstanceConfiguration =
            mcpIntegrationInstanceConfigurationService
                .fetchMcpIntegrationInstanceConfiguration(mcpIntegrationInstanceConfigurationId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "McpIntegrationInstanceConfiguration not found: " + mcpIntegrationInstanceConfigurationId));

        long integrationInstanceConfigurationId =
            mcpIntegrationInstanceConfiguration.getIntegrationInstanceConfigurationId();

        List<McpIntegrationInstanceConfigurationWorkflow> mcpIntegrationInstanceConfigurationWorkflows =
            mcpIntegrationInstanceConfigurationWorkflowService
                .getMcpIntegrationInstanceConfigurationMcpIntegrationInstanceConfigurationWorkflows(
                    mcpIntegrationInstanceConfigurationId);

        Map<String, McpIntegrationInstanceConfigurationWorkflow> previousMcpWorkflowsByUuid = new HashMap<>();

        for (McpIntegrationInstanceConfigurationWorkflow mcpIntegrationInstanceConfigurationWorkflow : mcpIntegrationInstanceConfigurationWorkflows) {
            IntegrationInstanceConfigurationWorkflow previousIntegrationInstanceConfigurationWorkflow =
                integrationInstanceConfigurationWorkflowService.getIntegrationInstanceConfigurationWorkflow(
                    mcpIntegrationInstanceConfigurationWorkflow.getIntegrationInstanceConfigurationWorkflowId());

            IntegrationWorkflow previousIntegrationWorkflow = integrationWorkflowService.getWorkflowIntegrationWorkflow(
                previousIntegrationInstanceConfigurationWorkflow.getWorkflowId());

            previousMcpWorkflowsByUuid.put(
                previousIntegrationWorkflow.getUuidAsString(), mcpIntegrationInstanceConfigurationWorkflow);

            integrationInstanceWorkflowService.deleteByIntegrationInstanceConfigurationWorkflowId(
                mcpIntegrationInstanceConfigurationWorkflow.getIntegrationInstanceConfigurationWorkflowId());

            mcpIntegrationInstanceConfigurationWorkflowService
                .delete(mcpIntegrationInstanceConfigurationWorkflow.getId());
        }

        List<IntegrationInstanceConfigurationWorkflow> oldIntegrationInstanceConfigurationWorkflows =
            integrationInstanceConfigurationWorkflowService.getIntegrationInstanceConfigurationWorkflows(
                integrationInstanceConfigurationId);

        for (IntegrationInstanceConfigurationWorkflow oldWorkflow : oldIntegrationInstanceConfigurationWorkflows) {
            integrationInstanceWorkflowService.deleteByIntegrationInstanceConfigurationWorkflowId(
                oldWorkflow.getId());

            integrationInstanceConfigurationWorkflowService.delete(oldWorkflow.getId());
        }

        IntegrationInstanceConfiguration integrationInstanceConfiguration =
            integrationInstanceConfigurationService.getIntegrationInstanceConfiguration(
                integrationInstanceConfigurationId);

        integrationInstanceConfiguration.setIntegrationVersion(integrationVersion);

        integrationInstanceConfigurationService.update(integrationInstanceConfiguration);

        IntegrationInstanceConfiguration updatedIntegrationInstanceConfiguration =
            integrationInstanceConfigurationService.getIntegrationInstanceConfiguration(
                integrationInstanceConfigurationId);

        long integrationId = updatedIntegrationInstanceConfiguration.getIntegrationId();

        List<IntegrationWorkflow> integrationWorkflows =
            integrationWorkflowService.getIntegrationWorkflows(integrationId, integrationVersion);

        Set<String> workflowUuidSet = new HashSet<>(workflowUuids);

        for (IntegrationWorkflow integrationWorkflow : integrationWorkflows) {
            if (workflowUuidSet.contains(integrationWorkflow.getUuidAsString())) {
                IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow =
                    new IntegrationInstanceConfigurationWorkflow();

                integrationInstanceConfigurationWorkflow.setIntegrationInstanceConfigurationId(
                    integrationInstanceConfigurationId);
                integrationInstanceConfigurationWorkflow.setWorkflowId(integrationWorkflow.getWorkflowId());
                integrationInstanceConfigurationWorkflow.setEnabled(true);
                integrationInstanceConfigurationWorkflow.setInputs(Map.of());

                integrationInstanceConfigurationWorkflow =
                    integrationInstanceConfigurationWorkflowService.create(integrationInstanceConfigurationWorkflow);

                McpIntegrationInstanceConfigurationWorkflow recreatedMcpWorkflow =
                    mcpIntegrationInstanceConfigurationWorkflowService.create(
                        mcpIntegrationInstanceConfigurationId, integrationInstanceConfigurationWorkflow.getId());

                McpIntegrationInstanceConfigurationWorkflow previousMcpWorkflow = previousMcpWorkflowsByUuid.get(
                    integrationWorkflow.getUuidAsString());

                if (previousMcpWorkflow != null) {
                    carryOverToolSettings(
                        previousMcpWorkflow, recreatedMcpWorkflow.getId(), integrationWorkflow.getWorkflowId());
                }
            }
        }
    }

    private void carryOverToolSettings(
        McpIntegrationInstanceConfigurationWorkflow previousMcpWorkflow, long recreatedMcpWorkflowId,
        String workflowId) {

        Map<String, ?> previousParameters = previousMcpWorkflow.getParameters();

        Map<String, Object> carriedParameters = filterToolParameters(
            previousParameters, getToolInputProperties(workflowService.getWorkflow(workflowId)));

        if (!carriedParameters.isEmpty()) {
            mcpIntegrationInstanceConfigurationWorkflowService.updateParameters(
                recreatedMcpWorkflowId, carriedParameters);
        }

        if (!previousMcpWorkflow.isEnabled()) {
            mcpIntegrationInstanceConfigurationWorkflowService.updateEnabled(recreatedMcpWorkflowId, false);
        }
    }

    private static Map<String, Object> filterToolParameters(
        Map<String, ?> parameters, List<? extends Property> inputProperties) {

        Map<String, Object> filteredParameters = new HashMap<>();

        for (String toolParameterName : List.of(ToolConstants.TOOL_NAME, ToolConstants.TOOL_DESCRIPTION)) {
            if (parameters.containsKey(toolParameterName)) {
                filteredParameters.put(toolParameterName, parameters.get(toolParameterName));
            }
        }

        filteredParameters.putAll(filterValues(parameters, inputProperties, false));

        return filteredParameters;
    }

    private static Map<String, Object> filterValues(
        Map<?, ?> values, List<? extends Property> properties, boolean additionalPropertiesAllowed) {

        Map<String, Property> propertiesByName = new HashMap<>();

        for (Property property : properties) {
            propertiesByName.put(property.getName(), property);
        }

        Map<String, Object> filteredValues = new HashMap<>();

        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Property property = propertiesByName.get(name);

            if (property != null) {
                filteredValues.put(name, filterValue(entry.getValue(), property));
            } else if (additionalPropertiesAllowed) {
                filteredValues.put(name, entry.getValue());
            }
        }

        return filteredValues;
    }

    private static @Nullable Object filterValue(@Nullable Object value, Property property) {
        if (value instanceof Map<?, ?> map && property instanceof ObjectProperty objectProperty) {
            List<? extends Property> childProperties = objectProperty.getProperties();

            if (childProperties.isEmpty()) {
                return value;
            }

            List<? extends Property> additionalProperties = objectProperty.getAdditionalProperties();

            return filterValues(map, childProperties, !additionalProperties.isEmpty());
        }

        if (value instanceof List<?> list && property instanceof ArrayProperty arrayProperty) {
            List<? extends Property> itemProperties = arrayProperty.getItems();

            if (itemProperties.size() != 1) {
                return value;
            }

            Property itemProperty = itemProperties.getFirst();

            List<Object> filteredItems = new ArrayList<>();

            for (Object item : list) {
                filteredItems.add(filterValue(item, itemProperty));
            }

            return filteredItems;
        }

        return value;
    }

    private static List<? extends Property> getToolInputProperties(Workflow workflow) {
        WorkflowTrigger callableTrigger = getToolCallableTrigger(workflow);

        if (callableTrigger == null) {
            return List.of();
        }

        String inputSchema = MapUtils.getString(callableTrigger.getParameters(), WorkflowConstants.INPUT_SCHEMA);

        if (inputSchema == null || inputSchema.isEmpty()) {
            return List.of();
        }

        BaseValueProperty<?> inputProperty;

        try {
            inputProperty = SchemaUtils.getJsonSchemaProperty(
                inputSchema, PropertyFactory.JSON_SCHEMA_PROPERTY_FACTORY);
        } catch (RuntimeException exception) {
            log.warn(
                "Unable to parse the input schema of workflow {}, its tool inputs are not carried over",
                workflow.getId(), exception);

            return List.of();
        }

        if (inputProperty instanceof ObjectProperty objectProperty) {
            return objectProperty.getProperties();
        }

        if (inputProperty instanceof Property property) {
            return List.of(property);
        }

        return List.of();
    }

    @Override
    @PreAuthorize("isTenantAdmin()")
    public McpIntegrationInstanceConfiguration getMcpIntegrationInstanceConfiguration(long id) {
        return mcpIntegrationInstanceConfigurationService.fetchMcpIntegrationInstanceConfiguration(id)
            .orElse(null);
    }

    @Override
    @PreAuthorize("isTenantAdmin()")
    public List<McpIntegrationInstanceConfiguration> getMcpIntegrationInstanceConfigurations() {
        return mcpIntegrationInstanceConfigurationService.getMcpIntegrationInstanceConfigurations();
    }

    @Override
    @PreAuthorize("isTenantAdmin()")
    public List<McpIntegrationInstanceConfiguration> getMcpServerMcpIntegrationInstanceConfigurations(
        long mcpServerId) {

        return mcpIntegrationInstanceConfigurationService.getMcpServerMcpIntegrationInstanceConfigurations(mcpServerId);
    }

    private static boolean isSameConnectionSlot(
        WorkflowConnectionDTO workflowConnection, ComponentConnection componentConnection) {

        return Objects.equals(workflowConnection.workflowNodeName(), componentConnection.workflowNodeName()) &&
            Objects.equals(workflowConnection.workflowConnectionKey(), componentConnection.key());
    }

    private void storeToolParameters(
        McpIntegrationInstanceConfigurationWorkflow mcpIntegrationInstanceConfigurationWorkflow,
        @Nullable Map<String, ?> parameters) {

        if (parameters != null) {
            mcpIntegrationInstanceConfigurationWorkflowService.updateParameters(
                Objects.requireNonNull(mcpIntegrationInstanceConfigurationWorkflow.getId()), parameters);
        }
    }

    private static Map<String, Map<String, ?>> toToolParametersMap(List<WorkflowToolParametersDTO> toolParameters) {
        Map<String, Map<String, ?>> toolParametersMap = new HashMap<>();

        for (WorkflowToolParametersDTO workflowToolParameters : toolParameters) {
            toolParametersMap.put(workflowToolParameters.workflowId(), workflowToolParameters.parameters());
        }

        return toolParametersMap;
    }

    private static Map<String, WorkflowConfigurationDTO> toWorkflowConfigurationMap(
        List<WorkflowConfigurationDTO> workflowConfigurations) {

        Map<String, WorkflowConfigurationDTO> workflowConfigurationMap = new HashMap<>();

        for (WorkflowConfigurationDTO workflowConfiguration : workflowConfigurations) {
            workflowConfigurationMap.put(workflowConfiguration.workflowId(), workflowConfiguration);
        }

        return workflowConfigurationMap;
    }

    private List<ComponentConnection> getComponentConnections(Workflow workflow) {
        List<ComponentConnection> componentConnections = new ArrayList<>();

        for (WorkflowTrigger workflowTrigger : WorkflowTrigger.of(workflow)) {
            componentConnections.addAll(componentConnectionFacade.getComponentConnections(workflowTrigger));
        }

        for (WorkflowTask workflowTask : workflow.getTasks(true)) {
            componentConnections.addAll(componentConnectionFacade.getComponentConnections(workflowTask));
        }

        return componentConnections;
    }

    private boolean isConnectionUsable(
        long connectionId, ComponentConnection componentConnection, Environment environment) {

        Connection connection = connectionService.getConnection(connectionId);

        return connection.getEnvironmentId() == environment.ordinal() &&
            Objects.equals(connection.getComponentName(), componentConnection.componentName());
    }

    private IntegrationInstanceConfigurationWorkflow saveIntegrationInstanceConfigurationWorkflow(
        IntegrationInstanceConfiguration integrationInstanceConfiguration,
        @Nullable IntegrationInstanceConfigurationWorkflow integrationInstanceConfigurationWorkflow, String workflowId,
        @Nullable WorkflowConfigurationDTO workflowConfiguration) {

        if (integrationInstanceConfigurationWorkflow == null) {
            integrationInstanceConfigurationWorkflow = new IntegrationInstanceConfigurationWorkflow();

            integrationInstanceConfigurationWorkflow.setIntegrationInstanceConfigurationId(
                integrationInstanceConfiguration.getId());
            integrationInstanceConfigurationWorkflow.setWorkflowId(workflowId);
            integrationInstanceConfigurationWorkflow.setEnabled(true);
            integrationInstanceConfigurationWorkflow.setInputs(Map.of());

            if (workflowConfiguration != null) {
                integrationInstanceConfigurationWorkflow.setConnections(
                    toIntegrationInstanceConfigurationWorkflowConnections(
                        integrationInstanceConfiguration, workflowId, workflowConfiguration.connections()));
                integrationInstanceConfigurationWorkflow.setInputs(workflowConfiguration.inputs());
            }

            return integrationInstanceConfigurationWorkflowService.create(integrationInstanceConfigurationWorkflow);
        }

        if (workflowConfiguration != null) {
            integrationInstanceConfigurationWorkflow.setConnections(
                toIntegrationInstanceConfigurationWorkflowConnections(
                    integrationInstanceConfiguration, workflowId, workflowConfiguration.connections()));
            integrationInstanceConfigurationWorkflow.setEnabled(true);
            integrationInstanceConfigurationWorkflow.setInputs(workflowConfiguration.inputs());

            return integrationInstanceConfigurationWorkflowService.update(integrationInstanceConfigurationWorkflow);
        }

        if (!integrationInstanceConfigurationWorkflow.isEnabled()) {
            integrationInstanceConfigurationWorkflowService.updateEnabled(
                integrationInstanceConfigurationWorkflow.getId(), true);
        }

        return integrationInstanceConfigurationWorkflow;
    }

    private List<IntegrationInstanceConfigurationWorkflowConnection>
        toIntegrationInstanceConfigurationWorkflowConnections(
            IntegrationInstanceConfiguration integrationInstanceConfiguration, String workflowId,
            List<WorkflowConnectionDTO> workflowConnections) {

        Integration integration = integrationService.getIntegration(
            integrationInstanceConfiguration.getIntegrationId());

        Environment environment = integrationInstanceConfiguration.getEnvironment();

        List<ComponentConnection> componentConnections = getComponentConnections(
            workflowService.getWorkflow(workflowId));

        for (WorkflowConnectionDTO workflowConnection : workflowConnections) {
            boolean configurable = componentConnections.stream()
                .anyMatch(componentConnection -> isSameConnectionSlot(workflowConnection, componentConnection) &&
                    !Objects.equals(componentConnection.componentName(), integration.getComponentName()));

            if (!configurable) {
                throw new IllegalArgumentException(
                    "Connection for " + workflowConnection.workflowNodeName() + " of workflow " + workflowId +
                        " cannot be configured: it is not a connection of a component other than " +
                        integration.getComponentName());
            }
        }

        List<IntegrationInstanceConfigurationWorkflowConnection> integrationInstanceConfigurationWorkflowConnections =
            new ArrayList<>();

        for (ComponentConnection componentConnection : componentConnections) {
            if (Objects.equals(componentConnection.componentName(), integration.getComponentName())) {
                continue;
            }

            Optional<WorkflowConnectionDTO> workflowConnection = workflowConnections.stream()
                .filter(curWorkflowConnection -> isSameConnectionSlot(curWorkflowConnection, componentConnection))
                .findFirst();

            if (workflowConnection.isPresent()) {
                long connectionId = workflowConnection.get()
                    .connectionId();

                if (!isConnectionUsable(connectionId, componentConnection, environment)) {
                    throw new IllegalArgumentException(
                        "Connection " + connectionId + " cannot be used for " + componentConnection.workflowNodeName() +
                            " of workflow " + workflowId + ": it must be a " + componentConnection.componentName() +
                            " connection in the " + environment + " environment");
                }

                integrationInstanceConfigurationWorkflowConnections.add(
                    new IntegrationInstanceConfigurationWorkflowConnection(
                        connectionId, componentConnection.key(), componentConnection.workflowNodeName()));
            } else if (componentConnection.required()) {
                throw new IllegalArgumentException(
                    "Workflow " + workflowId + " requires a " + componentConnection.componentName() +
                        " connection for " + componentConnection.workflowNodeName());
            }
        }

        return integrationInstanceConfigurationWorkflowConnections;
    }
}
