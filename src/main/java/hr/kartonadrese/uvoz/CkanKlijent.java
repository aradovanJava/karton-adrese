package hr.kartonadrese.uvoz;

import hr.kartonadrese.config.IzvorKonfiguracija;
import hr.kartonadrese.core.http.Preuzimac;
import hr.kartonadrese.core.json.Json;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Klijent za CKAN API (Portal otvorenih podataka RH i Portal Grada Zagreba).
 *
 * <p>{@code package_show} vraća popis resursa skupa; odabire se resurs po identifikatoru
 * ili po redoslijedu željenih formata, a među istim formatom najnoviji.
 */
@Component
public class CkanKlijent {

    private final Preuzimac preuzimac;

    public CkanKlijent(Preuzimac preuzimac) {
        this.preuzimac = preuzimac;
    }

    /** Odabrani resurs skupa. */
    public record Resurs(String id, String naziv, String format, String url, LocalDate izmijenjeno) {
    }

    /** Preuzeti sadržaj resursa. */
    public record Sadrzaj(Resurs resurs, byte[] podaci) {
    }

    public List<Resurs> resursi(String portal, String paket) throws IOException {
        String url = portal + "/api/3/action/package_show?" + Preuzimac.upit(Map.of("id", paket));
        Map<String, Object> odgovor = Json.objekt(preuzimac.preuzmiTekst(url));
        if (!Boolean.TRUE.equals(odgovor.get("success"))) {
            throw new IOException("CKAN package_show nije uspio za " + paket);
        }
        Map<String, Object> rezultat = Json.mapa(odgovor, "result");
        LocalDate izmjenaSkupa = datum(Json.tekst(rezultat, "metadata_modified").orElse(null));
        List<Resurs> r = new ArrayList<>();
        for (Object o : Json.niz(rezultat, "resources")) {
            String format = Json.tekst(o, "format").orElse("").toUpperCase(Locale.ROOT).replace(".", "");
            String u = Json.tekst(o, "url").orElse(null);
            if (u == null) {
                continue;
            }
            if (format.isEmpty()) {
                format = formatIzUrla(u);
            }
            LocalDate izmjena = datum(Json.tekst(o, "last_modified").or(() -> Json.tekst(o, "created")).orElse(null));
            r.add(new Resurs(Json.tekst(o, "id").orElse(""), Json.tekst(o, "name").orElse(""), format, u,
                    izmjena != null ? izmjena : izmjenaSkupa));
        }
        return r;
    }

    public Sadrzaj preuzmi(IzvorKonfiguracija k) throws IOException {
        List<Resurs> svi = resursi(k.portal(), k.paket());
        Resurs odabran = odaberi(svi, k.resurs(), k.zeljeniFormati())
                .orElseThrow(() -> new IOException("Skup " + k.paket() + " nema resurs u formatu "
                        + k.zeljeniFormati() + " (dostupno: " + svi.stream().map(Resurs::format).distinct().toList() + ")"));
        return new Sadrzaj(odabran, preuzimac.preuzmi(odabran.url()));
    }

    static Optional<Resurs> odaberi(List<Resurs> resursi, String resursId, List<String> formati) {
        if (resursId != null && !resursId.isBlank()) {
            return resursi.stream().filter(r -> r.id().equals(resursId)).findFirst();
        }
        for (String f : formati) {
            String trazeni = f.toUpperCase(Locale.ROOT);
            Optional<Resurs> r = resursi.stream()
                    .filter(x -> x.format().equals(trazeni) || ("GEOJSON".equals(trazeni) && x.format().contains("GEOJSON")))
                    .max(Comparator.comparing(x -> x.izmijenjeno() == null ? LocalDate.MIN : x.izmijenjeno()));
            if (r.isPresent()) {
                return r;
            }
        }
        return Optional.empty();
    }

    static String formatIzUrla(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.endsWith(".geojson") || u.contains("geojson")) {
            return "GEOJSON";
        }
        if (u.endsWith(".csv")) {
            return "CSV";
        }
        if (u.endsWith(".xlsx")) {
            return "XLSX";
        }
        if (u.endsWith(".xls")) {
            return "XLS";
        }
        if (u.endsWith(".json")) {
            return "JSON";
        }
        return "";
    }

    static LocalDate datum(String iso) {
        if (iso == null || iso.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
