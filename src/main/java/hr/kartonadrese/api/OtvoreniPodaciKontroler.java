package hr.kartonadrese.api;

import hr.kartonadrese.baza.CelijaRepozitorij;
import hr.kartonadrese.core.geo.Laea3035;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.model.Celija;
import hr.kartonadrese.core.model.Kategorija;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Izlazni otvoreni skup podataka: indeks grada 15 minuta po ćeliji 1 km² (CC BY 4.0)
 * u GeoJSON-u i CSV-u, s DCAT-AP metapodacima za objavu na Portalu otvorenih podataka.
 */
@RestController
@RequestMapping("/api/otvoreni-podaci")
@Tag(name = "Otvoreni podaci", description = "Izlazni skup podataka Kartona adrese (CC BY 4.0)")
public class OtvoreniPodaciKontroler {

    private static final MediaType GEOJSON = new MediaType("application", "geo+json", StandardCharsets.UTF_8);
    private static final MediaType JSONLD = new MediaType("application", "ld+json", StandardCharsets.UTF_8);

    private final CelijaRepozitorij celije;

    public OtvoreniPodaciKontroler(CelijaRepozitorij celije) {
        this.celije = celije;
    }

    @GetMapping("/indeks15.geojson")
    @Operation(summary = "Indeks grada 15 minuta – GeoJSON (poligoni ćelija 1 km², WGS84)")
    public ResponseEntity<String> geojson() {
        List<Object> znacajke = new ArrayList<>();
        for (Celija c : celije.sve()) {
            if (c.indeks15() < 0) {
                continue;
            }
            Map<String, Object> svojstva = new LinkedHashMap<>();
            svojstva.put("celija", c.id());
            svojstva.put("indeks15", c.indeks15());
            svojstva.put("najvise", Kategorija.svakodnevne().size());
            svojstva.put("stanovnika", c.stanovnika());
            svojstva.put("nedostaje", c.nedostaje().stream().map(Kategorija::getNaziv).toList());
            List<Object> prsten = new ArrayList<>();
            for (double[] t : Laea3035.prsten(c.id())) {
                prsten.add(List.of(Math.round(t[0] * 1e6) / 1e6, Math.round(t[1] * 1e6) / 1e6));
            }
            Map<String, Object> geometrija = new LinkedHashMap<>();
            geometrija.put("type", "Polygon");
            geometrija.put("coordinates", List.of(prsten));
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("type", "Feature");
            f.put("id", c.id());
            f.put("properties", svojstva);
            f.put("geometry", geometrija);
            znacajke.add(f);
        }
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("name", "karton_adrese_indeks15");
        fc.put("licence", "CC BY 4.0 – navesti: Karton adrese, na temelju otvorenih podataka Grada Zagreba, ZET-a i DZS-a");
        fc.put("features", znacajke);
        return ResponseEntity.ok().contentType(GEOJSON).body(Json.zapisi(fc));
    }

    @GetMapping(value = "/indeks15.csv", produces = "text/csv")
    @Operation(summary = "Indeks grada 15 minuta – CSV (središte ćelije, indeks, stanovnici)")
    public ResponseEntity<String> csv() {
        StringBuilder sb = new StringBuilder("celija;lat;lon;indeks15;najvise;stanovnika;nedostaje\n");
        for (Celija c : celije.sve()) {
            if (c.indeks15() < 0) {
                continue;
            }
            sb.append(c.id()).append(';').append(String.format(java.util.Locale.ROOT, "%.6f;%.6f", c.lat(), c.lon()))
                    .append(';').append(c.indeks15()).append(';').append(Kategorija.svakodnevne().size()).append(';')
                    .append(c.stanovnika() == null ? "" : c.stanovnika()).append(';')
                    .append(String.join(", ", c.nedostaje().stream().map(Kategorija::getNaziv).toList())).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"karton-adrese-indeks15.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(sb.toString());
    }

    @GetMapping("/dcat.jsonld")
    @Operation(summary = "DCAT-AP metapodaci izlaznog skupa (za Portal otvorenih podataka)")
    public ResponseEntity<String> dcat(HttpServletRequest zahtjev) {
        String baza = zahtjev.getScheme() + "://" + zahtjev.getServerName()
                + (zahtjev.getServerPort() == 80 || zahtjev.getServerPort() == 443 ? "" : ":" + zahtjev.getServerPort());
        Map<String, Object> skup = new LinkedHashMap<>();
        skup.put("@context", Map.of("dcat", "http://www.w3.org/ns/dcat#", "dct", "http://purl.org/dc/terms/",
                "foaf", "http://xmlns.com/foaf/0.1/"));
        skup.put("@type", "dcat:Dataset");
        skup.put("dct:title", Map.of("@language", "hr", "@value", "Indeks grada 15 minuta – Grad Zagreb (mreža 1 km²)"));
        skup.put("dct:description", Map.of("@language", "hr", "@value",
                "Broj vrsta svakodnevnih usluga (osnovna škola, dječji vrtić, ljekarna, dom zdravlja ili ambulanta, "
                        + "stajalište javnog prijevoza, trgovina) dostupnih pješice unutar 15 minuta, po ćeliji "
                        + "statističke mreže 1 km² (EPSG:3035). Izračunato iz otvorenih podataka Grada Zagreba, ZET-a "
                        + "i DZS-a te OpenStreetMapa."));
        skup.put("dct:license", "https://creativecommons.org/licenses/by/4.0/");
        skup.put("dct:modified", LocalDate.now().toString());
        skup.put("dct:spatial", "Grad Zagreb");
        skup.put("dcat:keyword", List.of("grad 15 minuta", "dostupnost", "usluge", "Zagreb", "mreža 1 km²"));
        skup.put("dcat:theme", "http://publications.europa.eu/resource/authority/data-theme/REGI");
        skup.put("dct:publisher", Map.of("@type", "foaf:Agent", "foaf:name", "Tim Karton adrese"));
        skup.put("dcat:distribution", List.of(
                Map.of("@type", "dcat:Distribution", "dcat:accessURL", baza + "/api/otvoreni-podaci/indeks15.geojson",
                        "dcat:mediaType", "application/geo+json", "dct:format", "GeoJSON"),
                Map.of("@type", "dcat:Distribution", "dcat:accessURL", baza + "/api/otvoreni-podaci/indeks15.csv",
                        "dcat:mediaType", "text/csv", "dct:format", "CSV")));
        return ResponseEntity.ok().contentType(JSONLD).body(Json.zapisi(skup));
    }
}
