package hr.kartonadrese.uvoz;

import hr.kartonadrese.config.IzvorKonfiguracija;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.geo.Laea3035;
import hr.kartonadrese.core.http.Preuzimac;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.uvoz.PrepoznavanjePolja;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Popis stanovništva 2021. na mreži 1 km² (DZS, INSPIRE WFS).
 *
 * <p>Ako sloj nije zadan ({@code upit}), čita se GetCapabilities i bira prvi sloj čiji naziv
 * upućuje na stanovništvo. Stupac s brojem stanovnika i oznaka ćelije prepoznaju se po imenu;
 * ako oznake nema, računa se iz središta ćelije u EPSG:3035.
 */
@Component
public class PopisWfsUvoz {

    private static final Logger LOG = LoggerFactory.getLogger(PopisWfsUvoz.class);
    private static final List<String> STANOVNISTVO = List.of("tot_p", "tot_p_2021", "ukupno", "stanovnika",
            "broj stanovnika", "population", "pop", "obs_value", "value", "t");
    private static final List<String> OZNAKA = List.of("grd_id", "cellcode", "grid_id", "oznaka", "id_celije");
    private static final Pattern SLOJ = Pattern.compile("<(?:wfs:)?Name>([^<]+)</(?:wfs:)?Name>");

    private final Preuzimac preuzimac;
    private final KartonSvojstva svojstva;

    public PopisWfsUvoz(Preuzimac preuzimac, KartonSvojstva svojstva) {
        this.preuzimac = preuzimac;
        this.svojstva = svojstva;
    }

    /** Rezultat: središta ćelija (lat, lon) i broj stanovnika po oznaci ćelije. */
    public record Mreza(Map<String, double[]> sredista, Map<String, Integer> stanovnika, String sloj) {
    }

    public Mreza uvezi(IzvorKonfiguracija k) throws IOException {
        String sloj = k.upit();
        if (sloj == null || sloj.isBlank()) {
            String cap = preuzimac.preuzmiTekst(k.url() + "?service=WFS&version=2.0.0&request=GetCapabilities");
            List<String> slojevi = new ArrayList<>();
            Matcher m = SLOJ.matcher(cap);
            while (m.find()) {
                slojevi.add(m.group(1).trim());
            }
            sloj = slojevi.stream().filter(s -> s.toLowerCase(Locale.ROOT).matches(".*(pop|stanov|pd).*")).findFirst()
                    .orElse(slojevi.isEmpty() ? null : slojevi.getFirst());
            if (sloj == null) {
                throw new IOException("WFS nema slojeva: " + k.url());
            }
            LOG.info("WFS sloj za stanovništvo: {} (dostupni: {})", sloj, slojevi);
        }
        KartonSvojstva.Podrucje p = svojstva.podrucje();
        Map<String, String> parametri = new LinkedHashMap<>();
        parametri.put("service", "WFS");
        parametri.put("version", "2.0.0");
        parametri.put("request", "GetFeature");
        parametri.put("typeNames", sloj);
        parametri.put("outputFormat", "application/json");
        parametri.put("srsName", "urn:ogc:def:crs:EPSG::4326");
        parametri.put("bbox", String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f,urn:ogc:def:crs:EPSG::4326",
                p.minLat(), p.minLon(), p.maxLat(), p.maxLon()));
        parametri.put("count", "50000");
        String geojson = preuzimac.preuzmiTekst(k.url() + "?" + Preuzimac.upit(parametri));

        Map<String, double[]> sredista = new HashMap<>();
        Map<String, Integer> stanovnika = new HashMap<>();
        List<Object> objekti = Json.niz(Json.objekt(geojson), "features");
        if (jeInspireRaspodjela(objekti)) {
            throw new IOException("WFS " + k.url() + " objavljuje INSPIRE StatisticalDistribution: jedan objekt za "
                    + "cijelu državu, broj stanovnika ugniježđen po ćelijama (poslužitelj vraća najviše "
                    + brojVrijednosti(objekti) + " vrijednosti) i bez geometrije ćelija (samo poveznica na "
                    + "su-grid-2021). Za mrežu 1 km² upotrijebite izvor vrste POPIS_MREZA (Eurostat Census grid 2021).");
        }
        for (Object f : objekti) {
            Map<String, String> svojstvaCelije = new LinkedHashMap<>();
            Json.mapa(f, "properties").forEach((kljuc, v) -> svojstvaCelije.put(kljuc, String.valueOf(v)));
            Optional<Integer> n = vrijednost(svojstvaCelije, STANOVNISTVO).flatMap(PrepoznavanjePolja::broj)
                    .map(d -> (int) Math.round(d));
            Optional<double[]> s = srediste(Json.mapa(f, "geometry"));
            if (n.isEmpty() || s.isEmpty()) {
                continue;
            }
            String id = vrijednost(svojstvaCelije, OZNAKA).filter(x -> x.startsWith("CRS3035"))
                    .orElse(Laea3035.oznakaCelije(s.get()[0], s.get()[1], 1000));
            double[] sr = id.startsWith("CRS3035") ? Laea3035.srediste(id) : s.get();
            sredista.put(id, sr);
            stanovnika.merge(id, n.get(), Integer::sum);
        }
        return new Mreza(sredista, stanovnika, sloj);
    }

    /** INSPIRE PD: svojstvo „value” je niz StatisticalValue s dimenzijama (prostorna = poveznica na ćeliju). */
    static boolean jeInspireRaspodjela(List<Object> objekti) {
        return objekti.size() <= 5 && objekti.stream().anyMatch(f -> {
            Object v = Json.mapa(f, "properties").get("value");
            return v instanceof List<?> l && !l.isEmpty() && l.getFirst() instanceof Map<?, ?> m
                    && m.containsKey("dimensions");
        });
    }

    private static int brojVrijednosti(List<Object> objekti) {
        return objekti.stream().mapToInt(f -> Json.mapa(f, "properties").get("value") instanceof List<?> l ? l.size() : 0)
                .sum();
    }

    private static Optional<String> vrijednost(Map<String, String> svojstva, List<String> kandidati) {
        for (String k : kandidati) {
            String trazeni = PrepoznavanjePolja.normaliziraj(k);
            for (Map.Entry<String, String> e : svojstva.entrySet()) {
                if (PrepoznavanjePolja.normaliziraj(e.getKey()).equals(trazeni) && e.getValue() != null
                        && !"null".equals(e.getValue())) {
                    return Optional.of(e.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /** Središte geometrije {lat, lon}; koordinate u EPSG:3035 (veliki brojevi) se pretvaraju. */
    private static Optional<double[]> srediste(Map<String, Object> geometrija) {
        List<double[]> tocke = new ArrayList<>();
        skupi(geometrija.get("coordinates"), tocke);
        if (tocke.isEmpty()) {
            return Optional.empty();
        }
        double sx = 0;
        double sy = 0;
        for (double[] t : tocke) {
            sx += t[0];
            sy += t[1];
        }
        double x = sx / tocke.size();
        double y = sy / tocke.size();
        if (Math.abs(x) > 1000 && Math.abs(y) > 1000) {
            return Optional.of(Laea3035.izProjekcije(x, y));
        }
        // GeoJSON je (lon, lat), a WFS uz EPSG::4326 ponekad vraća (lat, lon)
        return PrepoznavanjePolja.uWgs84(y, x);
    }

    private static void skupi(Object o, List<double[]> tocke) {
        if (o instanceof List<?> l && !l.isEmpty()) {
            if (l.get(0) instanceof Number a && l.size() >= 2 && l.get(1) instanceof Number b) {
                tocke.add(new double[] {a.doubleValue(), b.doubleValue()});
            } else {
                for (Object e : l) {
                    skupi(e, tocke);
                }
            }
        }
    }
}
