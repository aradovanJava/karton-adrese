package hr.kartonadrese.core.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Interesi studenta koji mijenjaju redoslijed i isticanje u kartonu (profil "Student"). */
public enum Interes {
    MENZA("Menza", List.of(Kategorija.MENZA)),
    KNJIZNICA("Knjižnica", List.of(Kategorija.KNJIZNICA)),
    BICIKL("Bicikl", List.of(Kategorija.BICIKLISTICKA_STAZA, Kategorija.JAVNI_BICIKL)),
    SPORT("Sport", List.of(Kategorija.SPORT, Kategorija.IGRALISTE));

    private final String naziv;
    private final List<Kategorija> kategorije;

    Interes(String naziv, List<Kategorija> kategorije) {
        this.naziv = naziv;
        this.kategorije = kategorije;
    }

    public String getNaziv() {
        return naziv;
    }

    public List<Kategorija> getKategorije() {
        return kategorije;
    }

    public String getOznaka() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Čita popis iz parametra ("menza,bicikl") ili više parametara; nepoznate vrijednosti preskače. */
    public static Set<Interes> izTeksta(List<String> vrijednosti) {
        Set<Interes> r = EnumSet.noneOf(Interes.class);
        if (vrijednosti == null) {
            return r;
        }
        for (String v : vrijednosti) {
            for (String dio : v == null ? new String[0] : v.split(",")) {
                for (Interes i : values()) {
                    if (i.name().equalsIgnoreCase(dio.trim()) || i.naziv.equalsIgnoreCase(dio.trim())) {
                        r.add(i);
                    }
                }
            }
        }
        return r;
    }
}
