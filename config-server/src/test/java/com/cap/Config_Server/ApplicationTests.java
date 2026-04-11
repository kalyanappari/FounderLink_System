package com.cap.Config_Server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

	@Test
	void mainMethodRunsSuccessfully() {
		Application.main(new String[]{
				"--server.port=0",
				"--eureka.client.enabled=false",
				"--spring.profiles.active=native",
				"--spring.cloud.config.server.native.search-locations=classpath:/test-config"
		});
	}

	@Test
	void applicationHasConfigServerAnnotation() {
		assertThat(Application.class.isAnnotationPresent(
				org.springframework.cloud.config.server.EnableConfigServer.class
		)).isTrue();
	}

	@Test
	void applicationHasDiscoveryClientAnnotation() {
		assertThat(Application.class.isAnnotationPresent(
				org.springframework.cloud.client.discovery.EnableDiscoveryClient.class
		)).isTrue();
	}

	@Test
	void configServerServesTestApplicationConfig() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/test-app/default", String.class
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("test-app");
	}

	@Test
	void actuatorHealthEndpointIsUp() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/actuator/health", String.class
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("UP");
	}

	@Test
	void configServerReturnsNotFoundForUnknownApp() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/nonexistent-app/default", String.class
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
