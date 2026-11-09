package hr.kartonadrese.core.model;

import hr.kartonadrese.core.geo.Geometrija;
import hr.kartonadrese.core.geo.GeoUtil;

import java.time.LocalDate;
import java.util.Map;

/**
 * Objekt od interesa (ustanova, stajalište, trgovina …) nakon usklađivanja.
 * Koordinate su uvijek WGS84; {@code azurirano} je datum podatka kod izdavatelja ili datum preuzimanja.
 * Linijski i površinski objekti (biciklističke staze, parkovi, gradski vrtovi) imaju i
 * {@code geometrija}, pa se udaljenost mjeri do ruba, a ne do središta.
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
        LocalDate azurirano,
        Geometrija geometrija) {

    public Poi(long id, String izvorId, Kategorija kategorija, String naziv, String adresa, double lat, double lon,
               Map<String, String> atributi, LocalDate azurirano) {
        this(id, izvorId, kategorija, naziv, adresa, lat, lon, atributi, azurirano, null);
    }

    /** Zračna udaljenost od točke: do ruba geometrije ako postoji, inače do točke objekta. */
    public double udaljenostOdM(double odLat, double odLon) {
        return geometrija != null ? geometrija.udaljenostM(odLat, odLon) : GeoUtil.udaljenostM(odLat, odLon, lat, lon);
    }

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
