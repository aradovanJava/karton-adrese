package hr.kartonadrese.config;

import hr.kartonadrese.core.model.Kategorija;

import java.util.List;

/**
 * Jedan izvor podataka iz {@code application.yml} (karton.izvori[…]).
 *
 * @param id          kratka oznaka (npr. {@code zg-vrtici})
 * @param naziv       naziv skupa kako ga navodi izdavatelj
 * @param izdavatelj  tijelo javne vlasti ili zajednica (OSM)
 * @param licenca     npr. "Otvorena dozvola", "CC BY 4.0", "ODbL"
 * @param vrsta       način spajanja
 * @param portal      osnovni URL CKAN portala (https://data.zagreb.hr ili https://data.gov.hr/ckan)
 * @param paket       identifikator skupa na portalu
 * @param resurs      (neobavezno) identifikator točno određenog resursa u skupu
 * @param url         izravna poveznica (GeoJSON/TABLICA/GTFS/WFS)
 * @param poveznica   stranica skupa za ljude (prikazuje se na stranici "O podacima")
 * @param kategorija  kategorija objekata u zajedničkom šifrarniku
 * @param formati     redoslijed željenih formata resursa (zadano: GeoJSON, CSV, XLSX)
 * @param upit        Overpass filtar, npr. {@code nwr["amenity"="pharmacy"]}; za WFS naziv sloja
 * @param stupacNaziv ručno mapiranje stupaca kad automatsko prepoznavanje ne uspije
 * @param omogucen    isključeni izvori se preskaču pri uvozu
 * @param mjesniOdbor za vrstu GRANICE: {@code true} ako su to mjesni odbori, inače gradske četvrti
 */
public record IzvorKonfiguracija(
        String id,
        String naziv,
        String izdavatelj,
        String licenca,
        VrstaIzvora vrsta,
        String portal,
        String paket,
        String resurs,
        String url,
        String poveznica,
        Kategorija kategorija,
        List<String> formati,
        String upit,
        String stupacNaziv,
        String stupacAdresa,
        String stupacLat,
        String stupacLon,
        Boolean omogucen,
        Boolean mjesniOdbor) {

    public boolean jeOmogucen() {
        return omogucen == null || omogucen;
    }

    public List<String> zeljeniFormati() {
        return formati == null || formati.isEmpty() ? List.of("GEOJSON", "CSV", "XLSX", "XLS") : formati;
    }

    public String stranica() {
        if (poveznica != null && !poveznica.isBlank()) {
            return poveznica;
        }
        if (vrsta == VrstaIzvora.CKAN && portal != null && paket != null) {
            return portal + "/dataset/" + paket;
        }
        return url;
    }
}
