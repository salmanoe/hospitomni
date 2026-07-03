/*
 * HospitOmni entry point — channel-manager backend serving the Channex-shaped native north API for HospitOps and fanning ARI out to OTA adapters.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HospitomniApplication {

	public static void main(String[] args) {
		SpringApplication.run(HospitomniApplication.class, args);
	}

}
