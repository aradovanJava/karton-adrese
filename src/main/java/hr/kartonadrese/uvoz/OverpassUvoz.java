package hr.kartonadrese.uvoz;

import hr.kartonadrese.config.IzvorKonfiguracija;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.http.Preuzimac;
import hr.kartonadrese.core.geo.Geometrija;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.model.Kategorija;
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
        return upit(filtar, p, false);
    }

    /** Overpass upit; s {@code geometrija} vraća obrise (out geom) umjesto samo središta. */
    static String upit(String filtar, KartonSvojstva.Podrucje p, boolean geometrija) {
        String okvir = String.format(Locale.ROOT, "(%.5f,%.5f,%.5f,%.5f)", p.minLat(), p.minLon(), p.maxLat(), p.maxLon());
        StringBuilder sb = new StringBuilder("[out:json][timeout:180];(");
        for (String dio : filtar.split(";")) {
            if (!dio.isBlank()) {
                sb.append(dio.trim()).append(okvir).append(';');
            }
        }
        return sb.append(geometrija ? ");out geom tags;" : ");out center tags;").toString();
    }

    public UvozniRezultat uvezi(IzvorKonfiguracija k) throws IOException {
        if (k.upit() == null || k.upit().isBlank()) {
            throw new IOException("Overpass izvor " + k.id() + " nema zadan 'upit'");
        }
        String url = k.url() == null || k.url().isBlank() ? ZADANI_URL : k.url();
        // Parkove trebamo s obrisom kako bi se udaljenost mjerila do ruba parka, a ne do središta.
        boolean sGeometrijom = k.kategorija() == Kategorija.PARK || k.kategorija() == Kategorija.GRADSKI_VRT;
        String odgovor = preuzimac.posaljiObrazac(url, Map.of("data", upit(k.upit(), svojstva.podrucje(), sGeometrijom)));
        List<UvozniZapis> zapisi = new ArrayList<>();
        int bezLokacije = 0;
        List<Object> elementi = Json.niz(Json.objekt(odgovor), "elements");
        for (Object e : elementi) {
            Geometrija geometrija = geometrija(e);
            Double lat = Json.broj(e, "lat").orElse(Json.broj(Json.mapa(e, "center"), "lat").orElse(null));
            Double lon = Json.broj(e, "lon").orElse(Json.broj(Json.mapa(e, "center"), "lon").orElse(null));
            if ((lat == null || lon == null) && geometrija != null) {
                double[] s = geometrija.srediste();
                lat = s[0];
                lon = s[1];
            }
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
            zapisi.add(new UvozniZapis(naziv, adresa, lat, lon, atributi, geometrija));
        }
        return new UvozniRezultat(zapisi, elementi.size(), 0, bezLokacije, LocalDate.now(), "Overpass: " + k.upit());
    }

    /** Obris iz odgovora "out geom": put (way) ili vanjski članovi relacije. */
    static Geometrija geometrija(Object element) {
        List<double[][]> dijelovi = new ArrayList<>();
        if (!Json.niz(element, "geometry").isEmpty()) {
            dijelovi.add(tocke(Json.niz(element, "geometry")));
        }
        for (Object clan : Json.niz(element, "members")) {
            String uloga = Json.tekst(clan, "role").orElse("outer");
            if (!"inner".equals(uloga) && !Json.niz(clan, "geometry").isEmpty()) {
                dijelovi.add(tocke(Json.niz(clan, "geometry")));
            }
        }
        dijelovi.removeIf(d -> d.length < 2);
        if (dijelovi.isEmpty()) {
            return null;
        }
        boolean zatvoreno = dijelovi.stream().allMatch(d -> d.length >= 4 && d[0][0] == d[d.length - 1][0]
                && d[0][1] == d[d.length - 1][1]);
        return new Geometrija(zatvoreno ? Geometrija.Tip.POVRSINA : Geometrija.Tip.LINIJA, dijelovi);
    }

    private static double[][] tocke(List<Object> geometrija) {
        double[][] r = new double[geometrija.size()][];
        for (int i = 0; i < geometrija.size(); i++) {
            r[i] = new double[] {Json.broj(geometrija.get(i), "lat").orElse(0.0), Json.broj(geometrija.get(i), "lon").orElse(0.0)};
        }
        return r;
    }
}
