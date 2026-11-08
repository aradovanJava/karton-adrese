package hr.kartonadrese.core.geo;

import java.util.Locale;

/**
 * Osnovni geometrijski izračuni u WGS84 (EPSG:4326).
 *
 * <p>Korisniku prikazujemo zračnu udaljenost u metrima ({@link #formatiraj(double)}).
 * Indeks grada 15 minuta interno pretvara udaljenost u minute uz faktor zaobilaženja 1,3
 * (tipično za gradsku uličnu mrežu); izračun po pješačkoj mreži je u planu razvoja.
 */
public final class GeoUtil {

    public static final double POLUMJER_ZEMLJE_M = 6_371_008.8;
    public static final double FAKTOR_ZAOBILAZENJA = 1.3;
    public static final double BRZINA_HODA_M_MIN = 80.0;

    private GeoUtil() {
    }

    /** Zračna udaljenost između dvije točke u metrima (haversine). */
    public static double udaljenostM(double lat1, double lon1, double lat2, double lon2) {
        double f1 = Math.toRadians(lat1);
        double f2 = Math.toRadians(lat2);
        double df = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(df / 2) * Math.sin(df / 2)
                + Math.cos(f1) * Math.cos(f2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * POLUMJER_ZEMLJE_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Procijenjene minute hoda za zračnu udaljenost i brzinu hoda (m/min). */
    public static int minutaHoda(double zracnaUdaljenostM, double brzinaMMin) {
        double put = zracnaUdaljenostM * FAKTOR_ZAOBILAZENJA;
        return (int) Math.max(1, Math.round(put / brzinaMMin));
    }

    /** Najveća zračna udaljenost koju je moguće prehodati za zadane minute. */
    public static double dometM(int minuta, double brzinaMMin) {
        return minuta * brzinaMMin / FAKTOR_ZAOBILAZENJA;
    }

    /**
     * Prikaz udaljenosti: do 1 km u metrima zaokruženo na 10 m ("260 m"), dalje u
     * kilometrima s jednom decimalom ("1,2 km").
     */
    public static String formatiraj(double metara) {
        long m = Math.round(Math.max(0, metara) / 10.0) * 10;
        if (m < 1000) {
            return m + " m";
        }
        return String.format(Locale.ROOT, "%.1f", metara / 1000).replace('.', ',') + " km";
    }

    /** Pravokutnik koji sigurno obuhvaća krug zadanog polumjera (za brzi SQL predfiltar). */
    public static Okvir okvir(double lat, double lon, double polumjerM) {
        double dLat = Math.toDegrees(polumjerM / POLUMJER_ZEMLJE_M);
        double cos = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double dLon = Math.toDegrees(polumjerM / (POLUMJER_ZEMLJE_M * cos));
        return new Okvir(lat - dLat, lon - dLon, lat + dLat, lon + dLon);
    }

    /** Geografski pravokutnik (jug, zapad, sjever, istok). */
    public record Okvir(double minLat, double minLon, double maxLat, double maxLon) {

        public boolean sadrzi(double lat, double lon) {
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }
    }
}
