package hr.kartonadrese.core.model;

/** Geokodirana adresa s prostornim jedinicama kojima pripada. */
public record Lokacija(
        String adresa,
        double lat,
        double lon,
        String gradskaCetvrt,
        String mjesniOdbor,
        String izvor) {
}
