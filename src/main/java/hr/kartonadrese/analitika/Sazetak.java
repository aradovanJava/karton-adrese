package hr.kartonadrese.analitika;

import hr.kartonadrese.core.model.Sekcija;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Sažetak kartona ("Vrijedi znati"): najviše tri rečenice izvedene izravno iz podataka,
 * redoslijedom sekcija za odabrani profil. Svaka rečenica nosi izvor i datum, a ništa se
 * ne izmišlja – ako podatka nema, rečenica se izostavlja.
 */
final class Sazetak {

    private Sazetak() {
    }

    static List<Karton.Stavka> izradi(Karton k) {
        List<Karton.Stavka> r = new ArrayList<>();
        for (Sekcija s : k.redoslijed()) {
            if (r.size() >= 3) {
                break;
            }
            stavka(k, s).ifPresent(r::add);
        }
        return r;
    }

    private static Optional<Karton.Stavka> stavka(Karton k, Sekcija s) {
        return switch (s) {
            case STUDENT -> Optional.ofNullable(k.student()).flatMap(st -> st.fakultet() != null
                    ? Optional.of(new Karton.Stavka(st.fakultet().naziv() + ": " + st.fakultet().udaljenost()
                            + " zračne udaljenosti" + (st.fakultet().linijeBezPresjedanja().isEmpty()
                            ? "; izravne linije ZET-a nema." : "; bez presjedanja voze linije "
                            + String.join(", ", st.fakultet().linijeBezPresjedanja()) + "."),
                            "Geoportal visokoškolske ustanove · ZET GTFS", null))
                    : st.stavke().stream().findFirst().map(o -> new Karton.Stavka(
                            o.kategorijaNaziv() + ": " + o.naziv() + ", " + o.udaljenost() + ".",
                            o.izvor(), o.datum()))));
            case OBRAZOVANJE -> k.obrazovanje().skole().stream().findFirst().map(o -> new Karton.Stavka(
                    "Najbliža osnovna škola, " + o.naziv() + ", udaljena je " + o.udaljenost()
                            + detalj(o, "smjen").map(d -> " (" + d + ")").orElse("") + ".",
                    o.izvor(), o.datum()));
            case PRIJEVOZ -> k.prijevoz().stajalista().stream().findFirst().map(st -> new Karton.Stavka(
                    "Stajalište " + st.naziv() + " udaljeno je " + st.udaljenost()
                            + (st.linije().isEmpty() ? "" : " (linije " + String.join(", ", st.linije()) + ")") + ".",
                    k.prijevoz().izvor(), k.prijevoz().datum()));
            case ZDRAVLJE -> k.zdravlje().zdravstvo().stream().findFirst().map(o -> new Karton.Stavka(
                    "Najbliža zdravstvena ustanova je " + o.naziv() + ", " + o.udaljenost()
                            + k.zdravlje().ljekarne().stream().findFirst()
                                    .map(lj -> ", a ljekarna " + lj.udaljenost()).orElse("") + ".",
                    o.izvor(), o.datum()));
            case ZRAK -> Optional.ofNullable(k.zrak())
                    .filter(z -> z.prosjekPm10() != null && !z.prosjekPm10().isNaN() && z.godina() != null)
                    .map(z -> new Karton.Stavka(String.format(Locale.forLanguageTag("hr"),
                            "Na najbližoj mjernoj postaji (%s) prosjek PM10 u %d. bio je %.1f µg/m³.",
                            z.postaja(), z.godina(), z.prosjekPm10()), z.izvorMjerenja(), null));
            case PRISTUPACNOST -> k.pristupacnost().ustanove().stream().findFirst().map(o -> new Karton.Stavka(
                    "Najbliža ustanova za osobe s invaliditetom je " + o.naziv() + ", " + o.udaljenost() + ".",
                    o.izvor(), o.datum()));
            case POTRES -> Optional.ofNullable(k.potres().zbornoMjesto()).map(o -> new Karton.Stavka(
                    "Najbliže zborno mjesto civilne zaštite: " + o.naziv() + ", " + o.udaljenost() + ".",
                    o.izvor(), o.datum()));
            case JAVNE_POVRSINE -> k.javnePovrsine().igralista().stream().findFirst().map(o -> new Karton.Stavka(
                    "Najbliže igralište (" + o.naziv() + ") udaljeno je " + o.udaljenost()
                            + Optional.ofNullable(k.javnePovrsine().park())
                                    .map(pk -> pk.udaljenostM() == 0 ? ", a adresa je u parku " + pk.naziv()
                                            : ", a park " + pk.naziv() + " " + pk.udaljenost() + " (do ruba)")
                                    .orElse("") + ".",
                    o.izvor(), o.datum()));
            case OKOLICA -> Optional.of(new Karton.Stavka("U krugu od " + k.okolica().prag() + " (oko 15 minuta hoda) "
                    + "dostupno je " + k.okolica().indeks() + " od " + k.okolica().najvise()
                    + " vrsta svakodnevnih usluga.", "Izračun Kartona adrese", null));
            case STANOVNISTVO -> Optional.ofNullable(k.stanovnistvo().stanovnika()).map(n -> new Karton.Stavka(
                    "U kvadratu 1 km² oko adrese živi oko " + n + " stanovnika.", k.stanovnistvo().izvor(), "2021."));
        };
    }

    private static Optional<String> detalj(Karton.Objekt o, String kljuc) {
        return o.detalji().stream().filter(d -> d.toLowerCase(Locale.ROOT).contains(kljuc)).findFirst();
    }
}
