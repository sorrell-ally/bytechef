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

package com.bytechef.platform.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bytechef.encryption.EncryptionKey;
import com.bytechef.jackson.config.JacksonConfiguration;
import com.bytechef.liquibase.config.LiquibaseConfiguration;
import com.bytechef.platform.configuration.config.PlatformConfigurationIntTestConfiguration.PlatformConfigurationIntTestJdbcConfiguration;
import com.bytechef.platform.configuration.domain.GettingStarted;
import com.bytechef.platform.configuration.domain.GettingStarted.CompletionType;
import com.bytechef.platform.configuration.repository.GettingStartedRepository;
import com.bytechef.platform.constant.PlatformType;
import com.bytechef.test.config.testcontainers.PostgreSQLContainerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Ivica Cardic
 */
@SpringBootTest(classes = GettingStartedServiceIntTest.GettingStartedServiceIntTestConfiguration.class)
class GettingStartedServiceIntTest {

    @Autowired
    private GettingStartedRepository gettingStartedRepository;

    @Autowired
    private GettingStartedService gettingStartedService;

    @AfterEach
    void afterEach() {
        gettingStartedRepository.deleteAll();
    }

    @Test
    void testFetchIsEmptyBeforeCompletion() {
        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.EMBEDDED)).isEmpty();
    }

    @Test
    void testCompleteIsIdempotentAndKeepsFirstType() {
        gettingStartedService.complete(PlatformType.EMBEDDED, CompletionType.COMPLETED);
        gettingStartedService.complete(PlatformType.EMBEDDED, CompletionType.DISMISSED);
        gettingStartedService.complete(PlatformType.EMBEDDED, CompletionType.COMPLETED);

        assertThat(gettingStartedRepository.findAll()).hasSize(1);
        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.EMBEDDED))
            .get()
            .extracting(GettingStarted::getCompletionType)
            .isEqualTo(CompletionType.COMPLETED);
    }

    @Test
    void testCompletionIsKeptPerPlatformType() {
        gettingStartedService.complete(PlatformType.EMBEDDED, CompletionType.DISMISSED);

        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.AUTOMATION)).isEmpty();

        gettingStartedService.complete(PlatformType.AUTOMATION, CompletionType.COMPLETED);

        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.AUTOMATION))
            .hasValueSatisfying(gettingStarted -> {
                assertThat(gettingStarted.getType()).isEqualTo(PlatformType.AUTOMATION);
                assertThat(gettingStarted.getCompletionType()).isEqualTo(CompletionType.COMPLETED);
            });
        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.EMBEDDED))
            .get()
            .extracting(GettingStarted::getCompletionType)
            .isEqualTo(CompletionType.DISMISSED);
    }

    @ComponentScan(basePackages = "com.bytechef.encryption")
    @Configuration
    @EnableAutoConfiguration
    @Import({
        GettingStartedServiceImpl.class, JacksonConfiguration.class, LiquibaseConfiguration.class,
        PlatformConfigurationIntTestJdbcConfiguration.class, PostgreSQLContainerConfiguration.class
    })
    static class GettingStartedServiceIntTestConfiguration {

        @Bean
        EncryptionKey encryptionKey() {
            return () -> "tTB1/UBIbYLuCXVi4PPfzA==";
        }
    }
}
