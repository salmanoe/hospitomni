/*
 * Local dev launcher running the app against Testcontainers instead of Compose.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni;

import org.springframework.boot.SpringApplication;

public class TestHospitomniApplication {

	public static void main(String[] args) {
		SpringApplication.from(HospitomniApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
