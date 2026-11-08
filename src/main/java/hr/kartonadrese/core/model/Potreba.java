package hr.kartonadrese.core.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Potrebe osobe s invaliditetom koje mijenjaju prikaz pristupačnosti (profil "Osoba s invaliditetom"). */
public enum Potreba {
    KOLICA("Krećem se u kolicima"),
    OTEZANO_HODANJE("Otežano hodam"),
    SLABOVIDNOST("Slabovidna ili slijepa osoba");

    private final String naziv;

    Potreba(String naziv) {
        this.naziv = naziv;
    }

    public String getNaziv() {
        return naziv;
    }

    public String getOznaka() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Set<Potreba> izTeksta(List<String> vrijednosti) {
        Set<Potreba> r = EnumSet.noneOf(Potreba.class);
        if (vrijednosti == null) {
            return r;
        }
        for (String v : vrijednosti) {
            for (String dio : v == null ? new String[0] : v.split(",")) {
                for (Potreba p : values()) {
                    if (p.name().equalsIgnoreCase(dio.trim().replace('-', '_'))) {
                        r.add(p);
                    }
                }
            }
        }
        return r;
    }
}
