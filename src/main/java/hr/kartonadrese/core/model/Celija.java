package hr.kartonadrese.core.model;

import java.util.List;

/**
 * Ćelija statističke mreže 1 km² (EPSG:3035) s brojem stanovnika (DZS, Popis 2021.)
 * i izračunatim indeksom grada 15 minuta.
 */
public record Celija(
        String id,
        double lat,
        double lon,
        Integer stanovnika,
        int indeks15,
        List<Kategorija> nedostaje) {
}
