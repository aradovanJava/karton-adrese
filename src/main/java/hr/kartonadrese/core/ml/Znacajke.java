package hr.kartonadrese.core.ml;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Izračun značajki modela – mora biti jednak Python funkciji
 * {@code karton_ml.znacajke.kalendarske_znacajke} i {@code treniranje.matrica_tipicne_godine}.
 */
public final class Znacajke {

    private Znacajke() {
    }

    /** Vrijednost kalendarske značajke za datum ili {@code null} ako značajka nije kalendarska. */
    public static Double kalendarska(String naziv, LocalDate d) {
        int dan = d.getDayOfYear();
        int mjesec = d.getMonthValue();
        int danUTjednu = d.getDayOfWeek().getValue() - 1; // 0 = ponedjeljak
        return switch (naziv) {
            case "dan_sin" -> Math.sin(2 * Math.PI * dan / 365.25);
            case "dan_cos" -> Math.cos(2 * Math.PI * dan / 365.25);
            case "mjesec" -> (double) mjesec;
            case "dan_u_tjednu" -> (double) danUTjednu;
            case "vikend" -> d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY ? 1.0 : 0.0;
            case "sezona_grijanja" -> mjesec >= 10 || mjesec <= 4 ? 1.0 : 0.0;
            default -> null;
        };
    }

    /** Matrica značajki za svaki dan godine uz klimatološke meteorološke vrijednosti. */
    public static float[][] matricaTipicneGodine(MetapodaciModela meta, int godina) {
        LocalDate prvi = LocalDate.of(godina, 1, 1);
        int dana = prvi.lengthOfYear();
        List<String> znacajke = meta.znacajke();
        float[][] x = new float[dana][znacajke.size()];
        for (int i = 0; i < dana; i++) {
            LocalDate d = prvi.plusDays(i);
            Map<String, Double> klima = meta.klimatologija().get(d.getDayOfYear());
            for (int j = 0; j < znacajke.size(); j++) {
                String z = znacajke.get(j);
                Double v = kalendarska(z, d);
                if (v == null) {
                    v = klima == null ? null : klima.get(z);
                }
                if (v == null) {
                    throw new IllegalStateException("Nema vrijednosti značajke " + z + " za " + d);
                }
                x[i][j] = (float) v.doubleValue();
            }
        }
        return x;
    }
}
