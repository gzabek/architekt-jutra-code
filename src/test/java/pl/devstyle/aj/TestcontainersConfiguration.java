package pl.devstyle.aj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final PostgreSQLContainer POSTGRES_CONTAINER;

	static {
		POSTGRES_CONTAINER = new PostgreSQLContainer(DockerImageName.parse("postgres:18").asCompatibleSubstituteFor("postgres"));
		POSTGRES_CONTAINER.start();
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES_CONTAINER;
	}

	public static void main(String[] args) {
		SpringApplication.from(AjApplication::main)
				.with(TestcontainersConfiguration.class)
				.run(args);
	}
}
