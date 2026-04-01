package pl.devstyle.aj;

import org.springframework.boot.SpringApplication;

public class TestAjApplication {

	public static void main(String[] args) {
		SpringApplication.from(AjApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
