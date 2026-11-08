package hr.kartonadrese.core.model;

import java.util.List;
import java.util.Set;

/**
 * Profil korisnika mijenja redoslijed sekcija, istaknute kategorije i brzinu hoda
 * kojom se računa vrijeme do objekata (umirovljenici i osobe s invaliditetom hodaju sporije).
 */
public enum Profil {
    OPCENITO("Općenito", 80,
            List.of(Sekcija.OBRAZOVANJE, Sekcija.PRIJEVOZ, Sekcija.ZDRAVLJE, Sekcija.ZRAK, Sekcija.PRISTUPACNOST,
                    Sekcija.JAVNE_POVRSINE, Sekcija.POTRES, Sekcija.OKOLICA, Sekcija.STANOVNISTVO),
            Set.of()),
    OBITELJ("Obitelj s djecom", 75,
            List.of(Sekcija.OBRAZOVANJE, Sekcija.JAVNE_POVRSINE, Sekcija.OKOLICA, Sekcija.ZRAK, Sekcija.ZDRAVLJE,
                    Sekcija.PRIJEVOZ, Sekcija.POTRES, Sekcija.PRISTUPACNOST, Sekcija.STANOVNISTVO),
            Set.of(Kategorija.SKOLA, Kategorija.VRTIC, Kategorija.PARK, Kategorija.IGRALISTE, Kategorija.SPORT,
                    Kategorija.ODMORKO)),
    STUDENT("Student", 85,
            List.of(Sekcija.STUDENT, Sekcija.PRIJEVOZ, Sekcija.OKOLICA, Sekcija.ZDRAVLJE, Sekcija.JAVNE_POVRSINE,
                    Sekcija.ZRAK, Sekcija.STANOVNISTVO, Sekcija.POTRES, Sekcija.OBRAZOVANJE, Sekcija.PRISTUPACNOST),
            Set.of(Kategorija.STAJALISTE, Kategorija.KNJIZNICA, Kategorija.TRGOVINA, Kategorija.MENZA,
                    Kategorija.VISOKO_UCILISTE)),
    UMIROVLJENIK("Umirovljenik", 65,
            List.of(Sekcija.ZDRAVLJE, Sekcija.PRIJEVOZ, Sekcija.OKOLICA, Sekcija.JAVNE_POVRSINE, Sekcija.ZRAK,
                    Sekcija.PRISTUPACNOST, Sekcija.POTRES, Sekcija.STANOVNISTVO, Sekcija.OBRAZOVANJE),
            Set.of(Kategorija.ZDRAVSTVO, Kategorija.LJEKARNA, Kategorija.STAJALISTE, Kategorija.PARK,
                    Kategorija.GRADSKI_VRT)),
    OSI("Osoba s invaliditetom", 55,
            List.of(Sekcija.PRISTUPACNOST, Sekcija.PRIJEVOZ, Sekcija.ZDRAVLJE, Sekcija.POTRES, Sekcija.OKOLICA,
                    Sekcija.JAVNE_POVRSINE, Sekcija.ZRAK, Sekcija.OBRAZOVANJE, Sekcija.STANOVNISTVO),
            Set.of(Kategorija.PRISTUPACNOST, Kategorija.STAJALISTE, Kategorija.ZDRAVSTVO, Kategorija.LJEKARNA));

    private final String naziv;
    private final double brzinaHodaMMin;
    private final List<Sekcija> redoslijed;
    private final Set<Kategorija> istaknute;

    Profil(String naziv, double brzinaHodaMMin, List<Sekcija> redoslijed, Set<Kategorija> istaknute) {
        this.naziv = naziv;
        this.brzinaHodaMMin = brzinaHodaMMin;
        this.redoslijed = redoslijed;
        this.istaknute = istaknute;
    }

    public String getNaziv() {
        return naziv;
    }

    public double getBrzinaHodaMMin() {
        return brzinaHodaMMin;
    }

    public List<Sekcija> getRedoslijed() {
        return redoslijed;
    }

    public Set<Kategorija> getIstaknute() {
        return istaknute;
    }

    /** Oznaka za URL (npr. {@code obitelj}). */
    public String getOznaka() {
        return name().toLowerCase();
    }

    public static Profil izTeksta(String s) {
        if (s == null || s.isBlank()) {
            return OPCENITO;
        }
        for (Profil p : values()) {
            if (p.name().equalsIgnoreCase(s.trim())) {
                return p;
            }
        }
        return OPCENITO;
    }
}
