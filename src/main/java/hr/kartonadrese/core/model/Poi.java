package hr.kartonadrese.core.model;

import java.time.LocalDate;
import java.util.Map;

/**
 * Objekt od interesa (ustanova, stajalište, trgovina …) nakon usklađivanja.
 * Koordinate su uvijek WGS84; {@code azurirano} je datum podatka kod izdavatelja ili datum preuzimanja.
 */
public record Poi(
        long id,
        String izvorId,
        Kategorija kategorija,
        String naziv,
        String adresa,
        double lat,
        double lon,
        Map<String, String> atributi,
        LocalDate azurirano) {

    public String atribut(String... moguciKljucevi) {
        if (atributi == null) {
            return null;
        }
        for (String kljuc : moguciKljucevi) {
            for (Map.Entry<String, String> e : atributi.entrySet()) {
                if (e.getKey().equalsIgnoreCase(kljuc) && e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return null;
    }
}
