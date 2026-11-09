package hr.kartonadrese.core.model;

import java.util.List;

/** Stajalište javnog prijevoza iz ZET GTFS-a s linijama koje na njemu staju. */
public record Stajaliste(
        String id,
        String naziv,
        double lat,
        double lon,
        List<String> linije,
        boolean tramvaj,
        boolean autobus,
        int polazakaDnevno) {

    public String vrsta() {
        if (tramvaj && autobus) {
            return "tramvaj i autobus";
        }
        return tramvaj ? "tramvaj" : "autobus";
    }
}
