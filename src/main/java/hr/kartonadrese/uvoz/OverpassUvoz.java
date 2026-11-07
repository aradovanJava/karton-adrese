package hr.kartonadrese.uvoz;

import hr.kartonadrese.config.IzvorKonfiguracija;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.http.Preuzimac;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.uvoz.UvozniZapis;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenStreetMap preko Overpass API-ja – pomoćni izvor (ODbL) za kategorije koje javni
 * sektor ne objavljuje (npr. trgovine prehranom). Upit se ograničava na područje grada.
 */
@Component
public class OverpassUvoz {

    private static final String ZADANI_URL = "https://overpass-api.de/api/interpreter";

    private final Preuzimac preuzimac;
    private final KartonSvojstva svojstva;

    public OverpassUvoz(Preuzimac preuzimac, KartonSvojstva svojstva) {
        this.preuzimac = preuzimac;
        this.svojstva = svojstva;
    }

    static String upit(String filtar, KartonSvojstva.Podrucje p) {
        String okvir = String.format(Locale.ROOT, "(%.5f,%.5f,%.5f,%.5f)", p.minLat(), p.minLon(), p.maxLat(), p.maxLon());
        StringBuilder sb = new StringBuilder("[out:json][timeout:180];(");
        for (String dio : filtar.split(";")) {
            if (!dio.isBlank()) {
                sb.append(dio.trim()).append(okvir).append(';');
            }
        }
        return sb.append(");out center tags;").toString();
    }

    public UvozniRezultat uvezi(IzvorKonfiguracija k) throws IOException {
        if (k.upit() == null || k.upit().isBlank()) {
            throw new IOException("Overpass izvor " + k.id() + " nema zadan 'upit'");
        }
        String url = k.url() == null || k.url().isBlank() ? ZADANI_URL : k.url();
        String odgovor = preuzimac.posaljiObrazac(url, Map.of("data", upit(k.upit(), svojstva.podrucje())));
        List<UvozniZapis> zapisi = new ArrayList<>();
        int bezLokacije = 0;
        List<Object> elementi = Json.niz(Json.objekt(odgovor), "elements");
        for (Object e : elementi) {
            Double lat = Json.broj(e, "lat").orElse(Json.broj(Json.mapa(e, "center"), "lat").orElse(null));
            Double lon = Json.broj(e, "lon").orElse(Json.broj(Json.mapa(e, "center"), "lon").orElse(null));
            if (lat == null || lon == null) {
                bezLokacije++;
                continue;
            }
            Map<String, String> oznake = new LinkedHashMap<>();
            Json.mapa(e, "tags").forEach((kljuc, v) -> oznake.put(kljuc, String.valueOf(v)));
            String naziv = oznake.getOrDefault("name", k.kategorija() == null ? "Objekt" : k.kategorija().getNaziv());
            String ulica = oznake.get("addr:street");
            String broj = oznake.get("addr:housenumber");
            String adresa = ulica == null ? null : (broj == null ? ulica : ulica + " " + broj);
            Map<String, String> atributi = new LinkedHashMap<>();
            for (String kljuc : List.of("opening_hours", "wheelchair", "operator", "brand", "shop", "amenity", "website")) {
                if (oznake.containsKey(kljuc)) {
                    atributi.put(kljuc, oznake.get(kljuc));
                }
            }
            atributi.put("osm_id", Json.tekst(e, "type").orElse("") + "/" + Json.tekst(e, "id").orElse(""));
            zapisi.add(new UvozniZapis(naziv, adresa, lat, lon, atributi));
        }
        return new UvozniRezultat(zapisi, elementi.size(), 0, bezLokacije, LocalDate.now(), "Overpass: " + k.upit());
    }
}
