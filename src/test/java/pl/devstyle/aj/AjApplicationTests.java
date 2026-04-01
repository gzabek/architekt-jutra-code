package pl.devstyle.aj;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AjApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
	}

	@Test
	void dataSourceIsConfigured() {
		assertThat(dataSource).isNotNull();
	}

	@Test
	void dataSourceConnectsToPostgres() throws Exception {
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.isValid(1)).isTrue();
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
		}
	}

}
