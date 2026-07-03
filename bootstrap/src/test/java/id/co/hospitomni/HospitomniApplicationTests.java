/*
 * Context-load smoke test against a Testcontainers-managed Postgres.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HospitomniApplicationTests {

	@Test
	void contextLoads() {
	}

}
