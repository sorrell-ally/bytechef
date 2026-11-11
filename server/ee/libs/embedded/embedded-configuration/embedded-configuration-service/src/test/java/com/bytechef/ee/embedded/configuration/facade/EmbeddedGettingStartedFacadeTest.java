/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the ByteChef Enterprise license (the "Enterprise License");
 * you may not use this file except in compliance with the Enterprise License.
 */

package com.bytechef.ee.embedded.configuration.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bytechef.ee.embedded.configuration.domain.EmbeddedGettingStarted;
import com.bytechef.ee.embedded.configuration.domain.EmbeddedGettingStarted.CompletionType;
import com.bytechef.ee.embedded.configuration.domain.Integration;
import com.bytechef.ee.embedded.configuration.domain.IntegrationInstanceConfiguration;
import com.bytechef.ee.embedded.configuration.dto.EmbeddedGettingStartedDTO;
import com.bytechef.ee.embedded.configuration.service.EmbeddedGettingStartedService;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceConfigurationService;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceService;
import com.bytechef.ee.embedded.configuration.service.IntegrationService;
import com.bytechef.ee.embedded.connected.user.domain.ConnectedUser;
import com.bytechef.ee.embedded.connected.user.service.ConnectedUserService;
import com.bytechef.ee.platform.external.invocation.log.domain.ExternalInvocationLog;
import com.bytechef.ee.platform.external.invocation.log.service.ExternalInvocationLogService;
import com.bytechef.platform.configuration.domain.Environment;
import com.bytechef.platform.constant.PlatformType;
import com.bytechef.platform.external.invocation.ExternalInvocationOutcome;
import com.bytechef.platform.security.domain.ApiKey;
import com.bytechef.platform.security.service.ApiKeyService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * @version ee
 *
 * @author Ivica Cardic
 */
class EmbeddedGettingStartedFacadeTest {

    private static final long PUBLISHED_INTEGRATION_ID = 10L;
    private static final long UNPUBLISHED_INTEGRATION_ID = 11L;

    private ApiKeyService apiKeyService;
    private ConnectedUserService connectedUserService;
    private EmbeddedGettingStartedService embeddedGettingStartedService;
    private ExternalInvocationLogService externalInvocationLogService;
    private IntegrationInstanceConfigurationService integrationInstanceConfigurationService;
    private IntegrationInstanceService integrationInstanceService;
    private IntegrationService integrationService;
    private EmbeddedGettingStartedFacadeImpl embeddedGettingStartedFacade;

    @BeforeEach
    void beforeEach() {
        apiKeyService = mock(ApiKeyService.class);
        connectedUserService = mock(ConnectedUserService.class);
        embeddedGettingStartedService = mock(EmbeddedGettingStartedService.class);
        externalInvocationLogService = mock(ExternalInvocationLogService.class);
        integrationInstanceConfigurationService = mock(IntegrationInstanceConfigurationService.class);
        integrationInstanceService = mock(IntegrationInstanceService.class);
        integrationService = mock(IntegrationService.class);

        when(embeddedGettingStartedService.fetchEmbeddedGettingStarted()).thenReturn(Optional.empty());

        embeddedGettingStartedFacade = new EmbeddedGettingStartedFacadeImpl(
            apiKeyService, connectedUserService, embeddedGettingStartedService, externalInvocationLogService,
            integrationInstanceConfigurationService, integrationInstanceService, integrationService);
    }

    @Test
    void testAllChecksFalseOnEmptyTenant() {
        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.apiKeyCreated()).isFalse();
        assertThat(embeddedGettingStartedDTO.integrationCreated()).isFalse();
        assertThat(embeddedGettingStartedDTO.integrationPublished()).isFalse();
        assertThat(embeddedGettingStartedDTO.integrationConfigurationCreated()).isFalse();
        assertThat(embeddedGettingStartedDTO.integrationConfigurationEnabled()).isFalse();
        assertThat(embeddedGettingStartedDTO.testUserAdded()).isFalse();
        assertThat(embeddedGettingStartedDTO.testUserConnected()).isFalse();
        assertThat(embeddedGettingStartedDTO.testUserExternalId()).isNull();
        assertThat(embeddedGettingStartedDTO.firstCallMade()).isFalse();
        assertThat(embeddedGettingStartedDTO.lastFailedCall()).isNull();
        assertThat(embeddedGettingStartedDTO.completed()).isFalse();
        assertThat(embeddedGettingStartedDTO.completionType()).isNull();
        assertThat(embeddedGettingStartedDTO.integrationId()).isNull();
        assertThat(embeddedGettingStartedDTO.integrationInstanceConfigurationId()).isNull();
        assertThat(embeddedGettingStartedDTO.testUserId()).isNull();
        assertThat(embeddedGettingStartedDTO.componentName()).isNull();
        assertThat(embeddedGettingStartedDTO.componentVersion()).isNull();

        verify(embeddedGettingStartedService, never()).complete(any());
    }

    @Test
    void testTargetIdsPointAtTheNewestPublishedIntegrationAndItsConfiguration() {
        long newerPublishedIntegrationId = 12L;

        Integration publishedIntegration = integration(PUBLISHED_INTEGRATION_ID, true);
        Integration newerPublishedIntegration = integration(newerPublishedIntegrationId, true);
        Integration unpublishedIntegration = integration(UNPUBLISHED_INTEGRATION_ID, false);

        when(integrationService.getIntegrations())
            .thenReturn(List.of(publishedIntegration, unpublishedIntegration, newerPublishedIntegration));

        IntegrationInstanceConfiguration publishedConfiguration =
            integrationInstanceConfiguration(PUBLISHED_INTEGRATION_ID);
        IntegrationInstanceConfiguration unpublishedConfiguration =
            integrationInstanceConfiguration(UNPUBLISHED_INTEGRATION_ID);

        when(integrationInstanceConfigurationService.getIntegrationInstanceConfigurations(
            Environment.DEVELOPMENT, null, null))
                .thenReturn(List.of(publishedConfiguration, unpublishedConfiguration));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.integrationId()).isEqualTo(newerPublishedIntegrationId);
        assertThat(embeddedGettingStartedDTO.integrationInstanceConfigurationId())
            .isEqualTo(configurationId(PUBLISHED_INTEGRATION_ID));
    }

    @Test
    void testComponentIsTheComponentOfTheNewestPublishedConfiguration() {
        long newerPublishedIntegrationId = 12L;

        Integration publishedIntegration = integration(PUBLISHED_INTEGRATION_ID, true);
        Integration newerPublishedIntegration = integration(newerPublishedIntegrationId, true);

        when(publishedIntegration.getComponentName()).thenReturn("httpClient");
        when(publishedIntegration.getComponentVersion()).thenReturn(2);
        when(newerPublishedIntegration.getComponentName()).thenReturn("github");

        when(integrationService.getIntegrations())
            .thenReturn(List.of(publishedIntegration, newerPublishedIntegration));

        IntegrationInstanceConfiguration publishedConfiguration =
            integrationInstanceConfiguration(PUBLISHED_INTEGRATION_ID);

        when(integrationInstanceConfigurationService.getIntegrationInstanceConfigurations(
            Environment.DEVELOPMENT, null, null))
                .thenReturn(List.of(publishedConfiguration));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.componentName()).isEqualTo("httpClient");
        assertThat(embeddedGettingStartedDTO.componentVersion()).isEqualTo(2);
    }

    @Test
    void testComponentIsTheComponentOfTheTargetIntegrationWithoutConfiguration() {
        Integration unpublishedIntegration = integration(UNPUBLISHED_INTEGRATION_ID, false);

        when(unpublishedIntegration.getComponentName()).thenReturn("github");
        when(unpublishedIntegration.getComponentVersion()).thenReturn(1);

        when(integrationService.getIntegrations()).thenReturn(List.of(unpublishedIntegration));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.componentName()).isEqualTo("github");
        assertThat(embeddedGettingStartedDTO.componentVersion()).isEqualTo(1);
    }

    @Test
    void testTargetIntegrationIsTheNewestIntegrationWhenNoneIsPublished() {
        Integration olderIntegration = integration(PUBLISHED_INTEGRATION_ID, false);
        Integration newerIntegration = integration(UNPUBLISHED_INTEGRATION_ID, false);

        when(integrationService.getIntegrations()).thenReturn(List.of(newerIntegration, olderIntegration));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.integrationId()).isEqualTo(UNPUBLISHED_INTEGRATION_ID);
        assertThat(embeddedGettingStartedDTO.integrationInstanceConfigurationId()).isNull();
    }

    @Test
    void testIntegrationConfigurationEnabledOnlyForPublishedIntegration() {
        Integration publishedIntegration = integration(PUBLISHED_INTEGRATION_ID, true);
        Integration unpublishedIntegration = integration(UNPUBLISHED_INTEGRATION_ID, false);

        when(integrationService.getIntegrations()).thenReturn(List.of(publishedIntegration, unpublishedIntegration));

        IntegrationInstanceConfiguration unpublishedConfiguration =
            integrationInstanceConfiguration(UNPUBLISHED_INTEGRATION_ID);

        when(integrationInstanceConfigurationService.getIntegrationInstanceConfigurations(
            Environment.DEVELOPMENT, true)).thenReturn(List.of(unpublishedConfiguration));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.integrationCreated()).isTrue();
        assertThat(embeddedGettingStartedDTO.integrationPublished()).isTrue();
        assertThat(embeddedGettingStartedDTO.integrationConfigurationEnabled()).isFalse();

        IntegrationInstanceConfiguration publishedConfiguration =
            integrationInstanceConfiguration(PUBLISHED_INTEGRATION_ID);

        when(integrationInstanceConfigurationService.getIntegrationInstanceConfigurations(
            Environment.DEVELOPMENT, true)).thenReturn(List.of(publishedConfiguration));

        embeddedGettingStartedDTO = embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.integrationConfigurationEnabled()).isTrue();
    }

    @Test
    void testIntegrationConfigurationCreatedCountsDisabledConfigurationsOfPublishedIntegrations() {
        Integration publishedIntegration = integration(PUBLISHED_INTEGRATION_ID, true);
        Integration unpublishedIntegration = integration(UNPUBLISHED_INTEGRATION_ID, false);

        when(integrationService.getIntegrations()).thenReturn(List.of(publishedIntegration, unpublishedIntegration));

        IntegrationInstanceConfiguration unpublishedConfiguration =
            integrationInstanceConfiguration(UNPUBLISHED_INTEGRATION_ID);

        when(integrationInstanceConfigurationService.getIntegrationInstanceConfigurations(
            Environment.DEVELOPMENT, null, null))
                .thenReturn(List.of(unpublishedConfiguration));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.integrationConfigurationCreated()).isFalse();

        IntegrationInstanceConfiguration publishedConfiguration =
            integrationInstanceConfiguration(PUBLISHED_INTEGRATION_ID);

        when(integrationInstanceConfigurationService.getIntegrationInstanceConfigurations(
            Environment.DEVELOPMENT, null, null))
                .thenReturn(List.of(publishedConfiguration));

        embeddedGettingStartedDTO = embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.integrationConfigurationCreated()).isTrue();
        assertThat(embeddedGettingStartedDTO.integrationConfigurationEnabled()).isFalse();
    }

    @Test
    void testTestUserAddedBeforeTheIntegrationIsProvisioned() {
        ConnectedUser connectedUser = mock(ConnectedUser.class);

        when(connectedUser.getId()).thenReturn(42L);
        when(connectedUserService.fetchLastEnabledConnectedUser(Environment.DEVELOPMENT))
            .thenReturn(Optional.of(connectedUser));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.testUserAdded()).isTrue();
        assertThat(embeddedGettingStartedDTO.testUserConnected()).isFalse();
        assertThat(embeddedGettingStartedDTO.testUserId()).isEqualTo(42L);
    }

    @Test
    void testCompletesWhenAllFourStepsHold() {
        stubAllFourStepsHold(Environment.DEVELOPMENT);

        EmbeddedGettingStarted embeddedGettingStarted = mock(EmbeddedGettingStarted.class);

        when(embeddedGettingStarted.getCompletionType()).thenReturn(CompletionType.COMPLETED);
        when(embeddedGettingStartedService.fetchEmbeddedGettingStarted())
            .thenReturn(Optional.empty(), Optional.of(embeddedGettingStarted));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        verify(embeddedGettingStartedService).complete(CompletionType.COMPLETED);

        assertThat(embeddedGettingStartedDTO.testUserConnected()).isTrue();
        assertThat(embeddedGettingStartedDTO.testUserExternalId()).isEqualTo("external-user");
        assertThat(embeddedGettingStartedDTO.completed()).isTrue();
        assertThat(embeddedGettingStartedDTO.completionType()).isEqualTo("COMPLETED");
    }

    @Test
    void testDoesNotCompleteAgainWhenRowExists() {
        stubAllFourStepsHold(Environment.DEVELOPMENT);

        EmbeddedGettingStarted embeddedGettingStarted = mock(EmbeddedGettingStarted.class);

        when(embeddedGettingStarted.getCompletionType()).thenReturn(CompletionType.DISMISSED);
        when(embeddedGettingStartedService.fetchEmbeddedGettingStarted())
            .thenReturn(Optional.of(embeddedGettingStarted));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        verify(embeddedGettingStartedService, never()).complete(any());

        assertThat(embeddedGettingStartedDTO.completed()).isTrue();
        assertThat(embeddedGettingStartedDTO.completionType()).isEqualTo("DISMISSED");
    }

    @Test
    void testLastFailedCallNullAfterSuccess() {
        when(externalInvocationLogService.hasExternalInvocationLog(
            eq(PlatformType.EMBEDDED), anyInt(), eq(ExternalInvocationOutcome.SUCCESS))).thenReturn(true);

        ExternalInvocationLog failedLog = failedLog();

        when(externalInvocationLogService.fetchLastFailedExternalInvocationLog(eq(PlatformType.EMBEDDED), anyInt()))
            .thenReturn(Optional.of(failedLog));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.firstCallMade()).isTrue();
        assertThat(embeddedGettingStartedDTO.lastFailedCall()).isNull();
    }

    @Test
    void testLastFailedCallReportedWhenNoSuccess() {
        ExternalInvocationLog failedLog = failedLog();

        when(externalInvocationLogService.fetchLastFailedExternalInvocationLog(
            PlatformType.EMBEDDED, Environment.DEVELOPMENT.ordinal())).thenReturn(Optional.of(failedLog));

        EmbeddedGettingStartedDTO embeddedGettingStartedDTO =
            embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT);

        assertThat(embeddedGettingStartedDTO.firstCallMade()).isFalse();
        assertThat(embeddedGettingStartedDTO.lastFailedCall()).isNotNull();
        assertThat(embeddedGettingStartedDTO.lastFailedCall()
            .outcome()).isEqualTo("CONNECTION_REQUIRED");
        assertThat(embeddedGettingStartedDTO.lastFailedCall()
            .errorMessage()).isEqualTo("Connection required");
    }

    @Test
    void testIsCompletedEvaluatesOtherEnvironments() {
        stubAllFourStepsHold(Environment.PRODUCTION);

        assertThat(embeddedGettingStartedFacade.isEmbeddedGettingStartedCompleted()).isTrue();

        verify(embeddedGettingStartedService).complete(CompletionType.COMPLETED);

        InOrder inOrder = inOrder(apiKeyService);

        inOrder.verify(apiKeyService)
            .getApiKeys(Environment.DEVELOPMENT.ordinal(), PlatformType.EMBEDDED);
        inOrder.verify(apiKeyService)
            .getApiKeys(Environment.STAGING.ordinal(), PlatformType.EMBEDDED);
        inOrder.verify(apiKeyService)
            .getApiKeys(Environment.PRODUCTION.ordinal(), PlatformType.EMBEDDED);
        inOrder.verifyNoMoreInteractions();

        verify(externalInvocationLogService, never()).fetchLastFailedExternalInvocationLog(any(), anyInt());
    }

    @Test
    void testIsCompletedStopsAtFirstPassingEnvironment() {
        stubAllFourStepsHold(Environment.STAGING);

        assertThat(embeddedGettingStartedFacade.isEmbeddedGettingStartedCompleted()).isTrue();

        verify(apiKeyService).getApiKeys(Environment.DEVELOPMENT.ordinal(), PlatformType.EMBEDDED);
        verify(apiKeyService).getApiKeys(Environment.STAGING.ordinal(), PlatformType.EMBEDDED);
        verify(apiKeyService, never()).getApiKeys(Environment.PRODUCTION.ordinal(), PlatformType.EMBEDDED);
    }

    @Test
    void testIsCompletedFalseWhenNoEnvironmentPasses() {
        assertThat(embeddedGettingStartedFacade.isEmbeddedGettingStartedCompleted()).isFalse();

        verify(embeddedGettingStartedService, never()).complete(any());
    }

    @Test
    void testIsCompletedDoesNotEvaluateWhenRowExists() {
        when(embeddedGettingStartedService.fetchEmbeddedGettingStarted())
            .thenReturn(Optional.of(mock(EmbeddedGettingStarted.class)));

        assertThat(embeddedGettingStartedFacade.isEmbeddedGettingStartedCompleted()).isTrue();

        verifyNoInteractions(
            apiKeyService, externalInvocationLogService, integrationInstanceConfigurationService,
            integrationInstanceService, integrationService);
    }

    @Test
    void testDismissStoresDismissed() {
        embeddedGettingStartedFacade.dismissEmbeddedGettingStarted();

        verify(embeddedGettingStartedService).complete(CompletionType.DISMISSED);
    }

    private void stubAllFourStepsHold(Environment environment) {
        ApiKey apiKey = mock(ApiKey.class);
        Integration integration = integration(PUBLISHED_INTEGRATION_ID, true);
        IntegrationInstanceConfiguration integrationInstanceConfiguration =
            integrationInstanceConfiguration(PUBLISHED_INTEGRATION_ID);

        when(apiKeyService.getApiKeys(environment.ordinal(), PlatformType.EMBEDDED)).thenReturn(List.of(apiKey));
        when(integrationService.getIntegrations()).thenReturn(List.of(integration));
        when(integrationInstanceConfigurationService.getIntegrationInstanceConfigurations(environment, true))
            .thenReturn(List.of(integrationInstanceConfiguration));
        when(integrationInstanceService.fetchFirstRunnableConnectedUserExternalId(environment))
            .thenReturn(Optional.of("external-user"));
        when(externalInvocationLogService.hasExternalInvocationLog(
            PlatformType.EMBEDDED, environment.ordinal(), ExternalInvocationOutcome.SUCCESS)).thenReturn(true);
    }

    private static ExternalInvocationLog failedLog() {
        ExternalInvocationLog externalInvocationLog = mock(ExternalInvocationLog.class);

        when(externalInvocationLog.getOutcome()).thenReturn(ExternalInvocationOutcome.CONNECTION_REQUIRED);
        when(externalInvocationLog.getErrorMessage()).thenReturn("Connection required");
        when(externalInvocationLog.getCreatedDate()).thenReturn(Instant.parse("2026-11-11T10:00:00Z"));

        return externalInvocationLog;
    }

    private static Integration integration(long id, boolean published) {
        Integration integration = mock(Integration.class);

        when(integration.getId()).thenReturn(id);
        when(integration.isPublished()).thenReturn(published);

        return integration;
    }

    private static IntegrationInstanceConfiguration integrationInstanceConfiguration(long integrationId) {
        IntegrationInstanceConfiguration integrationInstanceConfiguration =
            mock(IntegrationInstanceConfiguration.class);

        when(integrationInstanceConfiguration.getId()).thenReturn(configurationId(integrationId));
        when(integrationInstanceConfiguration.getIntegrationId()).thenReturn(integrationId);

        return integrationInstanceConfiguration;
    }

    private static long configurationId(long integrationId) {
        return integrationId + 1000;
    }
}
