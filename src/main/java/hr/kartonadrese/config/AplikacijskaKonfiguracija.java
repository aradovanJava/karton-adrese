package hr.kartonadrese.config;

import hr.kartonadrese.core.http.Preuzimac;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AplikacijskaKonfiguracija {

    @Bean
    public Preuzimac preuzimac(KartonSvojstva svojstva) {
        Preuzimac p = new Preuzimac(svojstva.userAgent(), Duration.ofMinutes(3));
        // Pravila korištenja: Nominatim najviše 1 zahtjev/s, Overpass umjereno
        p.ograniciBrzinu("nominatim.openstreetmap.org", 1100);
        p.ograniciBrzinu("overpass-api.de", 2000);
        return p;
    }

    @Bean
    public OpenAPI kartonOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Karton adrese API")
                .version("1.0.0")
                .description("Javni podaci spojeni po adresi: obrazovanje, prijevoz, zdravlje, kvaliteta zraka, "
                        + "pristupačnost, sigurnost i indeks grada 15 minuta. Izvori i datumi navedeni su uz svaki podatak.")
                .license(new License().name("Kod: EUPL-1.2 · Izlazni podaci: CC BY 4.0")
                        .url("https://creativecommons.org/licenses/by/4.0/deed.hr")));
    }
}
