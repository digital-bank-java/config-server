package com.digitalbank.configserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.cloud.config.server.native.search-locations=classpath:/config-repo" })
class ConfigServerIntegrationTests {

	@LocalServerPort
	private int port;

	private RestTestClient client;

	@BeforeEach
	void setUp() {
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void healthEndpointReportsUp() {
		client.get().uri("/actuator/health").exchange().expectStatus().isOk().expectBody().jsonPath("$.status")
				.isEqualTo("UP");
	}

	@Test
	void configurationEndpointReturnsFixtureProperty() {
		client.get().uri("/test-service/default").exchange().expectStatus().isOk().expectBody().jsonPath("$.name")
				.isEqualTo("test-service").jsonPath("$.profiles[0]").isEqualTo("default")
				.jsonPath("$.propertySources[0].source['test.message']").isEqualTo("config-server-integration-test");
	}

}
