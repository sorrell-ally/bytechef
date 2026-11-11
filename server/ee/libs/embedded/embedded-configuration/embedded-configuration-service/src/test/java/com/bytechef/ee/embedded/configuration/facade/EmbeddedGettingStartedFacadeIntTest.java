/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the ByteChef Enterprise license (the "Enterprise License");
 * you may not use this file except in compliance with the Enterprise License.
 */

package com.bytechef.ee.embedded.configuration.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bytechef.automation.configuration.security.AutomationMethodSecurityConfiguration;
import com.bytechef.automation.configuration.service.PermissionService;
import com.bytechef.ee.embedded.configuration.config.IntegrationIntTestConfiguration;
import com.bytechef.ee.embedded.configuration.config.IntegrationIntTestConfigurationSharedMocks;
import com.bytechef.ee.embedded.configuration.service.IntegrationInstanceService;
import com.bytechef.platform.configuration.domain.Environment;
import com.bytechef.platform.configuration.repository.GettingStartedRepository;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.security.util.SecurityUtils;
import com.bytechef.test.config.testcontainers.PostgreSQLContainerConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * @version ee
 *
 * @author Ivica Cardic
 */
@SpringBootTest(
    classes = {
        IntegrationIntTestConfiguration.class, EmbeddedGettingStartedFacadeIntTest.MethodSecurityConfiguration.class
    },
    properties = {
        "bytechef.workflow.repository.jdbc.enabled=true",
        "bytechef.webhook-url=/webhooks/{id}"
    })
@Import(PostgreSQLContainerConfiguration.class)
@IntegrationIntTestConfigurationSharedMocks
class EmbeddedGettingStartedFacadeIntTest {

    @Autowired
    private EmbeddedGettingStartedFacade embeddedGettingStartedFacade;

    @Autowired
    private GettingStartedRepository gettingStartedRepository;

    @MockitoBean
    private IntegrationInstanceService integrationInstanceService;

    @BeforeEach
    void beforeEach() {
        gettingStartedRepository.deleteAll();
    }

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();

        gettingStartedRepository.deleteAll();
    }

    @Test
    void testGetEmbeddedGettingStartedAllowedOnlyForAdmin() {
        authenticate(nonAdmin());

        assertThatThrownBy(() -> embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT))
            .isInstanceOf(AccessDeniedException.class);

        authenticate(admin());

        assertThat(embeddedGettingStartedFacade.getEmbeddedGettingStarted(Environment.DEVELOPMENT)).isNotNull();
    }

    @Test
    void testIsEmbeddedGettingStartedCompletedAllowedOnlyForAdmin() {
        authenticate(nonAdmin());

        assertThatThrownBy(() -> embeddedGettingStartedFacade.isEmbeddedGettingStartedCompleted())
            .isInstanceOf(AccessDeniedException.class);

        authenticate(admin());

        assertThat(embeddedGettingStartedFacade.isEmbeddedGettingStartedCompleted()).isFalse();
    }

    @Test
    void testDismissEmbeddedGettingStartedAllowedOnlyForAdmin() {
        authenticate(nonAdmin());

        assertThatThrownBy(() -> embeddedGettingStartedFacade.dismissEmbeddedGettingStarted())
            .isInstanceOf(AccessDeniedException.class);

        assertThat(gettingStartedRepository.findAll()).isEmpty();

        authenticate(admin());

        assertThatCode(() -> embeddedGettingStartedFacade.dismissEmbeddedGettingStarted()).doesNotThrowAnyException();

        assertThat(embeddedGettingStartedFacade.isEmbeddedGettingStartedCompleted()).isTrue();
    }

    private static Authentication admin() {
        return new UsernamePasswordAuthenticationToken(
            "admin", "n/a", List.of(new SimpleGrantedAuthority(AuthorityConstants.ADMIN)));
    }

    private static void authenticate(Authentication authentication) {
        SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
    }

    private static Authentication nonAdmin() {
        return new UsernamePasswordAuthenticationToken(
            "user", "n/a", List.of(new SimpleGrantedAuthority(AuthorityConstants.USER)));
    }

    @Configuration
    @EnableMethodSecurity
    @ImportAutoConfiguration(AutomationMethodSecurityConfiguration.class)
    static class MethodSecurityConfiguration {

        @Bean("permissionService")
        PermissionService permissionService() {
            PermissionService permissionService = mock(PermissionService.class);

            when(permissionService.isTenantAdmin())
                .thenAnswer(invocation -> SecurityUtils.hasCurrentUserThisAuthority(AuthorityConstants.ADMIN));

            return permissionService;
        }
    }
}
