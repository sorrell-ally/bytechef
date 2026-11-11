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

package com.bytechef.automation.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bytechef.automation.configuration.config.ProjectIntTestConfiguration;
import com.bytechef.automation.configuration.config.ProjectIntTestConfigurationSharedMocks;
import com.bytechef.automation.configuration.domain.Project;
import com.bytechef.automation.configuration.domain.Workspace;
import com.bytechef.automation.configuration.repository.ProjectRepository;
import com.bytechef.automation.configuration.repository.WorkspaceRepository;
import com.bytechef.platform.configuration.domain.GettingStarted;
import com.bytechef.platform.configuration.domain.GettingStarted.CompletionType;
import com.bytechef.platform.configuration.repository.GettingStartedRepository;
import com.bytechef.platform.configuration.service.GettingStartedService;
import com.bytechef.platform.constant.PlatformType;
import com.bytechef.test.config.testcontainers.PostgreSQLContainerConfiguration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Ivica Cardic
 */
@SpringBootTest(
    classes = ProjectIntTestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgreSQLContainerConfiguration.class)
@ProjectIntTestConfigurationSharedMocks
class AutomationGettingStartedBackfillIntTest {

    private static final String BACKFILL_CHANGE_SET_ID = "202611111500010-1";
    private static final String CHANGELOG_PATH =
        "config/liquibase/changelog/automation/configuration/202611111500010_automation_configuration_backfilled_getting_started.xml";

    @Autowired
    private GettingStartedRepository gettingStartedRepository;

    @Autowired
    private GettingStartedService gettingStartedService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private long otherWorkspaceId;
    private long workspaceId;

    @BeforeEach
    void beforeEach() {
        gettingStartedRepository.deleteAll();

        Workspace workspace = workspaceRepository.save(new Workspace("Getting Started Workspace"));

        workspaceId = workspace.getId();

        Workspace otherWorkspace = workspaceRepository.save(new Workspace("Other Getting Started Workspace"));

        otherWorkspaceId = otherWorkspace.getId();
    }

    @AfterEach
    void afterEach() {
        gettingStartedRepository.deleteAll();
        projectRepository.deleteAll();

        workspaceRepository.deleteById(workspaceId);
        workspaceRepository.deleteById(otherWorkspaceId);
    }

    @Test
    void testBackfillMarksTenantWithProjectsAsBackfilled() throws Exception {
        createProject(workspaceId);
        createProject(otherWorkspaceId);

        executeBackfill();

        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.AUTOMATION))
            .get()
            .extracting(GettingStarted::getCompletionType)
            .isEqualTo(CompletionType.BACKFILLED);
        assertThat(gettingStartedRepository.count()).isEqualTo(1);
    }

    @Test
    void testBackfillSkipsTenantWithoutProjects() throws Exception {
        executeBackfill();

        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.AUTOMATION)).isEmpty();
    }

    @Test
    void testBackfillKeepsAnExistingCompletion() throws Exception {
        gettingStartedService.complete(PlatformType.AUTOMATION, CompletionType.COMPLETED);

        createProject(workspaceId);

        executeBackfill();

        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.AUTOMATION))
            .get()
            .extracting(GettingStarted::getCompletionType)
            .isEqualTo(CompletionType.COMPLETED);
    }

    @Test
    void testBackfillLeavesTheEmbeddedGuideAlone() throws Exception {
        gettingStartedService.complete(PlatformType.EMBEDDED, CompletionType.DISMISSED);

        createProject(workspaceId);

        executeBackfill();

        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.AUTOMATION))
            .get()
            .extracting(GettingStarted::getCompletionType)
            .isEqualTo(CompletionType.BACKFILLED);
        assertThat(gettingStartedService.fetchGettingStarted(PlatformType.EMBEDDED))
            .get()
            .extracting(GettingStarted::getCompletionType)
            .isEqualTo(CompletionType.DISMISSED);
    }

    private void createProject(long projectWorkspaceId) {
        projectRepository.save(
            Project.builder()
                .name("project")
                .workspaceId(projectWorkspaceId)
                .build());
    }

    @SuppressFBWarnings("SQL_INJECTION_SPRING_JDBC")
    private void executeBackfill() throws Exception {
        jdbcTemplate.execute(loadBackfillSql());
    }

    private static String loadBackfillSql() throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

        ClassPathResource changelogResource = new ClassPathResource(CHANGELOG_PATH);

        try (InputStream inputStream = changelogResource.getInputStream()) {
            Document document = documentBuilder.parse(inputStream);

            NodeList changeSetNodes = document.getElementsByTagName("changeSet");

            for (int index = 0; index < changeSetNodes.getLength(); index++) {
                Element changeSetElement = (Element) changeSetNodes.item(index);

                if (BACKFILL_CHANGE_SET_ID.equals(changeSetElement.getAttribute("id"))) {
                    NodeList sqlNodes = changeSetElement.getElementsByTagName("sql");

                    Node sqlNode = sqlNodes.item(0);

                    return sqlNode.getTextContent();
                }
            }
        }

        throw new IllegalStateException("Change set " + BACKFILL_CHANGE_SET_ID + " not found in " + CHANGELOG_PATH);
    }
}
