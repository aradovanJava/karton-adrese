package hr.kartonadrese;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Karton adrese – javni podaci spojeni po adresi.
 *
 * <p>Slojevi (vidi docs/arhitektura.png): izvori → prikupljanje ({@code uvoz}) → usklađivanje
 * ({@code core.uvoz}) → pohrana ({@code baza}) → model ({@code ml}) → analitika ({@code analitika})
 * → API ({@code api}) → prikaz ({@code web}, Thymeleaf).
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class KartonAdreseApplication {

    public static void main(String[] args) {
        SpringApplication.run(KartonAdreseApplication.class, args);
    }
}
