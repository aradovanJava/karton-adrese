package hr.kartonadrese.core.model;

import java.util.Locale;

/**
 * Četiri stanja pristupačnosti. "Nema podatka" se nikad ne prikazuje kao "pristupačno"
 * ni kao "nepristupačno" – izostanak podatka nije podatak.
 */
public enum StanjePristupacnosti {
    DA("pristupačno"),
    DJELOMICNO("djelomično pristupačno"),
    NE("nije pristupačno"),
    NEMA_PODATKA("nema podatka");

    private final String naziv;

    StanjePristupacnosti(String naziv) {
        this.naziv = naziv;
    }

    public String getNaziv() {
        return naziv;
    }

    /** Iz OSM oznake {@code wheelchair} (yes, limited, no, designated). */
    public static StanjePristupacnosti izOsm(String vrijednost) {
        if (vrijednost == null || vrijednost.isBlank()) {
            return NEMA_PODATKA;
        }
        return switch (vrijednost.trim().toLowerCase(Locale.ROOT)) {
            case "yes", "designated", "da" -> DA;
            case "limited", "djelomicno", "djelomično" -> DJELOMICNO;
            case "no", "ne" -> NE;
            default -> NEMA_PODATKA;
        };
    }
}
