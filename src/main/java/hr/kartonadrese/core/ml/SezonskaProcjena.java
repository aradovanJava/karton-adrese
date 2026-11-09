package hr.kartonadrese.core.ml;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sezonska procjena PM10: model se primjenjuje na "tipičnu godinu", dnevne vrijednosti
 * se uprosječe po mjesecu, a pojas nesigurnosti dobiva se množenjem s omjerima iz metapodataka.
 */
public final class SezonskaProcjena {

    private SezonskaProcjena() {
    }

    /** Model koji za matricu značajki vraća procjenu u prostoru cilja (log1p PM10). */
    @FunctionalInterface
    public interface Prediktor {
        float[] predvidi(float[][] znacajke);
    }

    public record Mjesec(int mjesec, double pm10, double donja, double gornja) {
    }

    public static List<Mjesec> izracunaj(MetapodaciModela meta, int godina, Prediktor prediktor) {
        float[][] x = Znacajke.matricaTipicneGodine(meta, godina);
        float[] p = prediktor.predvidi(x);
        double[] zbroj = new double[13];
        int[] broj = new int[13];
        LocalDate prvi = LocalDate.of(godina, 1, 1);
        boolean log = "log1p".equalsIgnoreCase(meta.transformacija());
        for (int i = 0; i < p.length; i++) {
            double v = log ? Math.expm1(p[i]) : p[i];
            int mj = prvi.plusDays(i).getMonthValue();
            zbroj[mj] += Math.max(0, v);
            broj[mj]++;
        }
        List<Mjesec> r = new ArrayList<>();
        for (int mj = 1; mj <= 12; mj++) {
            double prosjek = broj[mj] == 0 ? 0 : zbroj[mj] / broj[mj];
            r.add(new Mjesec(mj, zaokruzi(prosjek), zaokruzi(prosjek * meta.donjiOmjer()),
                    zaokruzi(prosjek * meta.gornjiOmjer())));
        }
        return r;
    }

    /** Rezerva kad model nije dostupan: izmjereni mjesečni prosjeci bez modela. */
    public static List<Mjesec> izMjerenja(MetapodaciModela meta) {
        List<Mjesec> r = new ArrayList<>();
        for (int mj = 1; mj <= 12; mj++) {
            Double v = meta.mjesecniProsjekMjerenja().get(mj);
            if (v != null) {
                r.add(new Mjesec(mj, zaokruzi(v), zaokruzi(v), zaokruzi(v)));
            }
        }
        return r;
    }

    private static double zaokruzi(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
