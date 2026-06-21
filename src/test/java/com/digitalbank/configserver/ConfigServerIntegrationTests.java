package com.digitalbank.configserver;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerIntegrationTests {

	private static final GitTestRepository TEST_REPOSITORY = GitTestRepository.create();

	@LocalServerPort
	private int port;

	private RestTestClient client;

	@DynamicPropertySource
	static void configureGitRepository(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.config.server.git.uri", TEST_REPOSITORY::uri);
		registry.add("spring.cloud.config.server.git.default-label", () -> "main");
		registry.add("spring.cloud.config.server.git.try-master-branch", () -> false);
		registry.add("spring.cloud.config.server.git.search-paths", () -> "{application}");
		registry.add("spring.cloud.config.server.git.clone-on-start", () -> true);
		registry.add("spring.cloud.config.server.accept-empty", () -> false);
		registry.add(
				"spring.cloud.config.server.health.repositories.integration-test.name",
				() -> "test-service");
		registry.add(
				"spring.cloud.config.server.health.repositories.integration-test.profiles",
				() -> "default");
		registry.add(
				"spring.cloud.config.server.health.repositories.integration-test.label",
				() -> "main");
	}

	@BeforeEach
	void setUp() {
		client = RestTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@AfterAll
	static void deleteGitRepository() throws IOException {
		TEST_REPOSITORY.delete();
	}

	@Test
	void healthEndpointReportsUp() {
		client.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void configurationEndpointReturnsGitFixture() {
		client.get()
				.uri("/test-service/default")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("test-service")
				.jsonPath("$.profiles[0]").isEqualTo("default")
				.jsonPath("$.version").isEqualTo(TEST_REPOSITORY.revision())
				.jsonPath("$.propertySources[0].source['test.message']")
				.isEqualTo("config-server-integration-test");
	}

	@Test
	void missingConfigurationReturnsNotFound() {
		client.get()
				.uri("/missing-service/default")
				.exchange()
				.expectStatus().isNotFound();
	}
}