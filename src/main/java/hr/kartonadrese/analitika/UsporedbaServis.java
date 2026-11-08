package hr.kartonadrese.analitika;

import hr.kartonadrese.core.model.Profil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Usporedba dviju adresa po istim kriterijima. Oznaka "Bliže / dostupnije" znači samo kraću
 * (zračnu) udaljenost ili veću dostupnost – ne ocjenjujemo naselje ni vrijednost nekretnine.
 */
@Service
public class UsporedbaServis {

    private final KartonServis kartoni;

    public UsporedbaServis(KartonServis kartoni) {
        this.kartoni = kartoni;
    }

    public record Redak(String kriterij, String a, String b, String bolje) {
    }

    public record Usporedba(Karton a, Karton b, List<Redak> redovi) {
    }

    public Optional<Usporedba> usporedi(String adresaA, String adresaB, Profil profil) {
        Optional<Karton> a = kartoni.karton(adresaA, profil);
        Optional<Karton> b = kartoni.karton(adresaB, profil);
        if (a.isEmpty() || b.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Usporedba(a.get(), b.get(), redovi(a.get(), b.get())));
    }

    static List<Redak> redovi(Karton a, Karton b) {
        List<Redak> r = new ArrayList<>();
        r.add(minute("Osnovna škola", a, b, k -> k.obrazovanje().skole().stream().findFirst()));
        r.add(minute("Igralište", a, b, k -> k.javnePovrsine().igralista().stream().findFirst()));
        r.add(minute("Park (do ruba)", a, b, k -> Optional.ofNullable(k.javnePovrsine().park())));
        r.add(minute("Dječji vrtić", a, b, k -> k.obrazovanje().vrtici().stream().findFirst()));
        r.add(veci("Linije javnog prijevoza u 600 m", a.prijevoz().brojLinija(), b.prijevoz().brojLinija(), ""));
        r.add(minute("Dom zdravlja ili ambulanta", a, b, k -> k.zdravlje().zdravstvo().stream().findFirst()));
        r.add(minute("Ljekarna", a, b, k -> k.zdravlje().ljekarne().stream().findFirst()));
        r.add(veci("Indeks grada 15 minuta", a.okolica().indeks(), b.okolica().indeks(), "/" + a.okolica().najvise()));
        r.add(manji("PM10 na najbližoj postaji (godišnji prosjek)", pm10(a), pm10(b), " µg/m³"));
        r.add(neutralno("Stanovnika na 1 km²", a.stanovnistvo().stanovnika(), b.stanovnistvo().stanovnika()));
        r.add(minute("Zborno mjesto civilne zaštite", a, b, k -> Optional.ofNullable(k.potres().zbornoMjesto())));
        return r;
    }

    private static Redak minute(String kriterij, Karton a, Karton b, Function<Karton, Optional<Karton.Objekt>> f) {
        Optional<Karton.Objekt> oa = f.apply(a);
        Optional<Karton.Objekt> ob = f.apply(b);
        String bolje = null;
        // Razlika manja od 50 m nije značajna (zračna udaljenost, preciznost lokacije objekata).
        if (oa.isPresent() && ob.isPresent() && Math.abs(oa.get().udaljenostM() - ob.get().udaljenostM()) >= 50) {
            bolje = oa.get().udaljenostM() < ob.get().udaljenostM() ? "A" : "B";
        }
        return new Redak(kriterij, oa.map(UsporedbaServis::opis).orElse("nema podatka"),
                ob.map(UsporedbaServis::opis).orElse("nema podatka"), bolje);
    }

    private static String opis(Karton.Objekt o) {
        return o.udaljenost();
    }

    private static Redak veci(String kriterij, int a, int b, String jedinica) {
        return new Redak(kriterij, a + jedinica, b + jedinica, a == b ? null : (a > b ? "A" : "B"));
    }

    private static Redak manji(String kriterij, Double a, Double b, String jedinica) {
        String bolje = a != null && b != null && Math.abs(a - b) >= 0.5 ? (a < b ? "A" : "B") : null;
        return new Redak(kriterij, a == null ? "nema podatka" : String.format(Locale.forLanguageTag("hr"), "%.1f%s", a, jedinica),
                b == null ? "nema podatka" : String.format(Locale.forLanguageTag("hr"), "%.1f%s", b, jedinica), bolje);
    }

    private static Redak neutralno(String kriterij, Integer a, Integer b) {
        return new Redak(kriterij, a == null ? "nema podatka" : String.valueOf(a),
                b == null ? "nema podatka" : String.valueOf(b), null);
    }

    private static Double pm10(Karton k) {
        return k.zrak() == null || k.zrak().prosjekPm10() == null || k.zrak().prosjekPm10().isNaN()
                ? null : k.zrak().prosjekPm10();
    }
}
