package com.cap.Service_Registry;

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
		// Covers the main() method — Spring reuses the existing cached context
		Application.main(new String[]{
				"--server.port=0",
				"--eureka.client.register-with-eureka=false",
				"--eureka.client.fetch-registry=false",
				"--eureka.server.enable-self-preservation=false"
		});
	}

	@Test
	void applicationHasEurekaServerAnnotation() {
		assertThat(Application.class.isAnnotationPresent(
				org.springframework.cloud.netflix.eureka.server.EnableEurekaServer.class
		)).isTrue();
	}

	@Test
	void eurekaServerDashboardIsAccessible() {
		ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void actuatorHealthEndpointIsUp() {
		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("UP");
	}

	@Test
	void eurekaAppsEndpointIsAccessible() {
		ResponseEntity<String> response = restTemplate.getForEntity("/eureka/apps", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
