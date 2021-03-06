/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.server.runtime.connector;

import java.io.IOException;
import java.io.InputStream;

import io.syndesis.server.connector.generator.ConnectorGenerator;
import io.syndesis.server.connector.generator.swagger.SwaggerUnifiedShapeConnectorGenerator;
import io.syndesis.common.model.Violation;
import io.syndesis.common.model.action.ActionsSummary;
import io.syndesis.common.model.connection.Connector;
import io.syndesis.common.model.connection.ConnectorSettings;
import io.syndesis.common.model.connection.ConnectorSummary;
import io.syndesis.common.model.connection.ConnectorTemplate;
import io.syndesis.server.runtime.BaseITCase;

import okio.Okio;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration
public class CustomSwaggerConnectorITCase extends BaseITCase {

    private static final String TEMPLATE_ID = "connector-template";

    private final ConnectorTemplate template = createConnectorTemplate(TEMPLATE_ID, "connector template");

    @Configuration
    public static class TestConfiguration {
        private static final ActionsSummary ACTIONS_SUMMARY = new ActionsSummary.Builder().totalActions(5)
            .putActionCountByTag("updating", 1).putActionCountByTag("creating", 1).putActionCountByTag("fetching", 2)
            .putActionCountByTag("destruction", 1).putActionCountByTag("tasks", 5).build();

        @Bean(TEMPLATE_ID)
        public ConnectorGenerator swaggerConnectorGenerator() {
            return new SwaggerUnifiedShapeConnectorGenerator();
        }
    }

    @Before
    public void createConnectorTemplates() {
        dataManager.create(template);
    }

    @Test
    public void shouldCreateCustomConnectorInfoForUploadedSwagger() throws IOException {
        final ConnectorSettings connectorSettings = new ConnectorSettings.Builder().connectorTemplateId(TEMPLATE_ID).build();

        final ResponseEntity<Connector> response = post("/api/v1/connectors/custom",
            multipartBodyForInfo(connectorSettings, getClass().getResourceAsStream("/io/syndesis/server/runtime/test-swagger.json")),
            Connector.class, tokenRule.validToken(), HttpStatus.OK, multipartHeaders());

        final Connector got = response.getBody();

        assertThat(got).isNotNull();
    }

    @Test
    public void shouldOfferCustomConnectorInfoForUploadedSwagger() throws IOException {
        final ConnectorSettings connectorSettings = new ConnectorSettings.Builder().connectorTemplateId(TEMPLATE_ID).build();

        final ResponseEntity<ConnectorSummary> response = post("/api/v1/connectors/custom/info",
            multipartBodyForInfo(connectorSettings, getClass().getResourceAsStream("/io/syndesis/server/runtime/test-swagger.json")),
            ConnectorSummary.class, tokenRule.validToken(), HttpStatus.OK, multipartHeaders());

        final ConnectorSummary expected = new ConnectorSummary.Builder()// \
            .name("Todo App API")//
            .description("unspecified")//
            .actionsSummary(TestConfiguration.ACTIONS_SUMMARY)//
            .addWarning(new Violation.Builder().error("missing-response-schema")
                .message("Operation DELETE /api/{id} does not provide a response schema for code 204").build())
            .build();

        final ConnectorSummary got = response.getBody();

        assertThat(got).isEqualToIgnoringGivenFields(expected, "icon");
        assertThat(got.getIcon()).startsWith("data:image");
    }

    private MultiValueMap<String, Object> multipartBodyForInfo(final ConnectorSettings connectorSettings, final InputStream is)
        throws IOException {
        final LinkedMultiValueMap<String, Object> multipartData = new LinkedMultiValueMap<>();
        multipartData.add("connectorSettings", connectorSettings);
        multipartData.add("specification", Okio.buffer(Okio.source(is)).readUtf8());
        return multipartData;
    }

    private HttpHeaders multipartHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private static ConnectorTemplate createConnectorTemplate(final String id, final String name) {
        return new ConnectorTemplate.Builder()//
            .id(id)//
            .name(name)//
            .build();
    }
}
