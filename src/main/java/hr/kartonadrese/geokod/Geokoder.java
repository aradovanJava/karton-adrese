package hr.kartonadrese.geokod;

import hr.kartonadrese.baza.AdresaRepozitorij;
import hr.kartonadrese.baza.GranicaRepozitorij;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.http.Preuzimac;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.model.Lokacija;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Geokodiranje adrese: prvo lokalna predmemorija (adrese koje su već tražene ili uvezene),
 * zatim Nominatim (OpenStreetMap) ako je uključen. Rezultat se pamti u bazi, a adresi se
 * pridružuju gradska četvrt i mjesni odbor iz uvezenih granica.
 */
@Service
public class Geokoder {

    private static final Logger LOG = LoggerFactory.getLogger(Geokoder.class);

    private final AdresaRepozitorij adrese;
    private final GranicaRepozitorij granice;
    private final Preuzimac preuzimac;
    private final KartonSvojstva svojstva;

    public Geokoder(AdresaRepozitorij adrese, GranicaRepozitorij granice, Preuzimac preuzimac, KartonSvojstva svojstva) {
        this.adrese = adrese;
        this.granice = granice;
        this.preuzimac = preuzimac;
        this.svojstva = svojstva;
    }

    public Optional<Lokacija> geokodiraj(String adresa) {
        if (adresa == null || adresa.isBlank()) {
            return Optional.empty();
        }
        Optional<Lokacija> lokalno = adrese.nadji(adresa);
        if (lokalno.isPresent()) {
            return lokalno;
        }
        KartonSvojstva.Geokodiranje g = svojstva.geokodiranje();
        if (g == null || !g.nominatim()) {
            return Optional.empty();
        }
        try {
            Optional<double[]> t = nominatim(adresa);
            if (t.isEmpty()) {
                return Optional.empty();
            }
            double lat = t.get()[0];
            double lon = t.get()[1];
            if (!svojstva.podrucje().okvir().sadrzi(lat, lon)) {
                return Optional.empty();
            }
            Lokacija l = new Lokacija(urediNaziv(adresa), lat, lon,
                    granice.nadji(GranicaRepozitorij.GRADSKA_CETVRT, lat, lon).orElse(null),
                    granice.nadji(GranicaRepozitorij.MJESNI_ODBOR, lat, lon).orElse(null),
                    "nominatim");
            adrese.spremi(l);
            return Optional.of(l);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Geokodiranje nije uspjelo za '{}': {}", adresa, e.getMessage());
            return Optional.empty();
        }
    }

    /** Samo koordinate (za uvoz objekata koji imaju adresu, a nemaju koordinate). */
    public Optional<double[]> koordinate(String adresa) {
        return geokodiraj(adresa).map(l -> new double[] {l.lat(), l.lon()});
    }

    public List<String> prijedlozi(String upit) {
        return adrese.prijedlozi(upit, 8);
    }

    private Optional<double[]> nominatim(String adresa) throws IOException {
        KartonSvojstva.Geokodiranje g = svojstva.geokodiranje();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("q", adresa.toLowerCase().contains(g.grad().toLowerCase()) ? adresa : adresa + ", " + g.grad());
        p.put("format", "jsonv2");
        p.put("limit", "1");
        p.put("countrycodes", "hr");
        p.put("accept-language", "hr");
        String odgovor = preuzimac.preuzmiTekst(g.nominatimUrl() + "/search?" + Preuzimac.upit(p));
        Object o = Json.parsiraj(odgovor);
        if (o instanceof List<?> l && !l.isEmpty()) {
            Optional<Double> lat = Json.broj(l.getFirst(), "lat");
            Optional<Double> lon = Json.broj(l.getFirst(), "lon");
            if (lat.isPresent() && lon.isPresent()) {
                return Optional.of(new double[] {lat.get(), lon.get()});
            }
        }
        return Optional.empty();
    }

    /** "gorjanovićeva ulica 15" → "Gorjanovićeva ulica 15" (prvo slovo veliko, ostatak kako je upisano). */
    static String urediNaziv(String adresa) {
        String a = adresa.trim().replaceAll("\\s+", " ");
        return a.isEmpty() ? a : Character.toUpperCase(a.charAt(0)) + a.substring(1);
    }
}
