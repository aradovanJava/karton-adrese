package hr.kartonadrese.config;

import hr.kartonadrese.core.geo.GeoUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Konfiguracija aplikacije (prefiks {@code karton} u application.yml). */
@ConfigurationProperties(prefix = "karton")
public record KartonSvojstva(
        boolean ogledniPodaci,
        String userAgent,
        Podrucje podrucje,
        Geokodiranje geokodiranje,
        Uvoz uvoz,
        Model model,
        Admin admin,
        List<String> primjeriAdresa,
        List<IzvorKonfiguracija> izvori) {

    /** Obuhvat analize (Grad Zagreb) i središte karte. */
    public record Podrucje(String naziv, double minLat, double minLon, double maxLat, double maxLon,
                           double centarLat, double centarLon) {

        public GeoUtil.Okvir okvir() {
            return new GeoUtil.Okvir(minLat, minLon, maxLat, maxLon);
        }
    }

    /**
     * Geokodiranje adresa. Nominatim se koristi samo za pojedinačne upite (nikad za
     * automatsko dovršavanje, prema pravilima korištenja OSMF-a) uz najviše 1 zahtjev u sekundi.
     */
    public record Geokodiranje(boolean nominatim, String nominatimUrl, String grad, int najvisePoUvozu) {
    }

    /** Raspored osvježavanja (cron; "-" isključuje) i pokretanje uvoza pri startu. */
    public record Uvoz(boolean priPokretanju, String raspored, String privremenaMapa) {
    }

    /** Mapa s ONNX modelima i metapodacima (model/&lt;postaja&gt;/…). */
    public record Model(String mapa) {
    }

    /** Token za administrativne pozive (prazno = administrativni API isključen). */
    public record Admin(String token) {
    }
}
