package hr.kartonadrese.core.geo;

import hr.kartonadrese.core.json.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Linijska ili površinska geometrija objekta (biciklistička staza, park, gradski vrt).
 *
 * <p>Točke su {lat, lon} u WGS84. Udaljenost se mjeri do najbližeg ruba (za površine je 0
 * ako je točka unutra), a ne do središta – kod velikih parkova razlika može biti i kilometar.
 * Računa se u lokalnoj ekvidistantnoj projekciji, što je na udaljenostima do nekoliko
 * kilometara točno na metar.
 */
public record Geometrija(Tip tip, List<double[][]> dijelovi) {

    public enum Tip { LINIJA, POVRSINA }

    public Geometrija {
        dijelovi = List.copyOf(dijelovi);
    }

    /** Zračna udaljenost od točke do geometrije u metrima (0 ako je točka unutar površine). */
    public double udaljenostM(double lat, double lon) {
        if (tip == Tip.POVRSINA && sadrzi(lat, lon)) {
            return 0;
        }
        double kx = kx(lat);
        double ky = ky();
        double najmanje = Double.MAX_VALUE;
        for (double[][] dio : dijelovi) {
            if (dio.length == 1) {
                najmanje = Math.min(najmanje, GeoUtil.udaljenostM(lat, lon, dio[0][0], dio[0][1]));
            }
            for (int i = 1; i < dio.length; i++) {
                najmanje = Math.min(najmanje, doDuzine(lat, lon, dio[i - 1], dio[i], kx, ky));
            }
        }
        return najmanje;
    }

    /** Ukupna duljina dijelova linije čije je središte unutar zadanog polumjera od točke (m). */
    public double duljinaUKruguM(double lat, double lon, double polumjerM) {
        double ukupno = 0;
        for (double[][] dio : dijelovi) {
            for (int i = 1; i < dio.length; i++) {
                double sLat = (dio[i - 1][0] + dio[i][0]) / 2;
                double sLon = (dio[i - 1][1] + dio[i][1]) / 2;
                if (GeoUtil.udaljenostM(lat, lon, sLat, sLon) <= polumjerM) {
                    ukupno += GeoUtil.udaljenostM(dio[i - 1][0], dio[i - 1][1], dio[i][0], dio[i][1]);
                }
            }
        }
        return ukupno;
    }

    /** Točka unutar površine (pravilo parnosti preko svih prstenova, uključuje rupe). */
    public boolean sadrzi(double lat, double lon) {
        boolean unutra = false;
        for (double[][] p : dijelovi) {
            for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
                double yi = p[i][0];
                double xi = p[i][1];
                double yj = p[j][0];
                double xj = p[j][1];
                if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) {
                    unutra = !unutra;
                }
            }
        }
        return tip == Tip.POVRSINA && unutra;
    }

    /** Reprezentativna točka {lat, lon}: prosjek vrhova (za oznaku na karti i prostorni indeks). */
    public double[] srediste() {
        double sLat = 0;
        double sLon = 0;
        int n = 0;
        for (double[][] dio : dijelovi) {
            for (double[] t : dio) {
                sLat += t[0];
                sLon += t[1];
                n++;
            }
        }
        return n == 0 ? new double[] {0, 0} : new double[] {sLat / n, sLon / n};
    }

    public int brojTocaka() {
        return dijelovi.stream().mapToInt(d -> d.length).sum();
    }

    /** Kompaktan zapis za bazu: {"t":"L","d":[[[lat,lon],…],…]} s 6 decimala. */
    public String uJson() {
        StringBuilder sb = new StringBuilder("{\"t\":\"").append(tip == Tip.LINIJA ? 'L' : 'P').append("\",\"d\":[");
        for (int i = 0; i < dijelovi.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[');
            double[][] dio = dijelovi.get(i);
            for (int j = 0; j < dio.length; j++) {
                if (j > 0) {
                    sb.append(',');
                }
                sb.append(String.format(Locale.ROOT, "[%.6f,%.6f]", dio[j][0], dio[j][1]));
            }
            sb.append(']');
        }
        return sb.append("]}").toString();
    }

    /** Čita zapis iz {@link #uJson()}; vraća {@code null} za prazan ili neispravan zapis. */
    public static Geometrija izJsona(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> m = Json.objekt(json);
            Tip tip = "L".equals(m.get("t")) ? Tip.LINIJA : Tip.POVRSINA;
            List<double[][]> dijelovi = new ArrayList<>();
            for (Object dio : Json.niz(m, "d")) {
                List<?> tocke = (List<?>) dio;
                double[][] d = new double[tocke.size()][];
                for (int i = 0; i < tocke.size(); i++) {
                    List<?> t = (List<?>) tocke.get(i);
                    d[i] = new double[] {((Number) t.get(0)).doubleValue(), ((Number) t.get(1)).doubleValue()};
                }
                dijelovi.add(d);
            }
            return dijelovi.isEmpty() ? null : new Geometrija(tip, dijelovi);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double kx(double lat) {
        return Math.toRadians(1) * GeoUtil.POLUMJER_ZEMLJE_M * Math.cos(Math.toRadians(lat));
    }

    private static double ky() {
        return Math.toRadians(1) * GeoUtil.POLUMJER_ZEMLJE_M;
    }

    /** Udaljenost točke od dužine AB u lokalnoj ravnini (m). */
    private static double doDuzine(double lat, double lon, double[] a, double[] b, double kx, double ky) {
        double ax = (a[1] - lon) * kx;
        double ay = (a[0] - lat) * ky;
        double bx = (b[1] - lon) * kx;
        double by = (b[0] - lat) * ky;
        double dx = bx - ax;
        double dy = by - ay;
        double d2 = dx * dx + dy * dy;
        double t = d2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2));
        double px = ax + t * dx;
        double py = ay + t * dy;
        return Math.sqrt(px * px + py * py);
    }
}
