package hr.kartonadrese.core.geo;

/**
 * HTRS96 / Croatia TM (EPSG:3765) – službena projekcija RH (poprečna Mercatorova).
 *
 * <p>Mnogi skupovi Grada Zagreba i DGU-a daju koordinate kao x/y u metrima u ovoj projekciji.
 * Parametri: srednji meridijan 16,5° E, k0 = 0,9999, lažni istok 500 000 m, elipsoid GRS80.
 * Formule: EPSG Guidance Note 7-2, metoda 9807 (točnost reda milimetra unutar RH).
 */
public final class Htrs96Tm {

    private static final double A = 6_378_137.0;
    private static final double F = 1 / 298.257222101;
    private static final double E2 = 2 * F - F * F;
    private static final double EP2 = E2 / (1 - E2);
    private static final double K0 = 0.9999;
    private static final double LAMBDA0 = Math.toRadians(16.5);
    private static final double FE = 500_000.0;
    private static final double FN = 0.0;

    private Htrs96Tm() {
    }

    private static double meridijanskiLuk(double fi) {
        double e4 = E2 * E2;
        double e6 = e4 * E2;
        return A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * fi
                - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * fi)
                + (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * fi)
                - (35 * e6 / 3072) * Math.sin(6 * fi));
    }

    /** {lat, lon} u stupnjevima → {istočno (x), sjeverno (y)} u metrima. */
    public static double[] uProjekciju(double lat, double lon) {
        double fi = Math.toRadians(lat);
        double t = Math.tan(fi) * Math.tan(fi);
        double c = EP2 * Math.cos(fi) * Math.cos(fi);
        double a = (Math.toRadians(lon) - LAMBDA0) * Math.cos(fi);
        double nu = A / Math.sqrt(1 - E2 * Math.sin(fi) * Math.sin(fi));
        double m = meridijanskiLuk(fi);
        double istok = FE + K0 * nu * (a + (1 - t + c) * Math.pow(a, 3) / 6
                + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * Math.pow(a, 5) / 120);
        double sjever = FN + K0 * (m + nu * Math.tan(fi) * (a * a / 2
                + (5 - t + 9 * c + 4 * c * c) * Math.pow(a, 4) / 24
                + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * Math.pow(a, 6) / 720));
        return new double[] {istok, sjever};
    }

    /** {istočno (x), sjeverno (y)} u metrima → {lat, lon} u stupnjevima. */
    public static double[] izProjekcije(double istok, double sjever) {
        double e4 = E2 * E2;
        double e6 = e4 * E2;
        double m1 = (sjever - FN) / K0;
        double mu = m1 / (A * (1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256));
        double e1 = (1 - Math.sqrt(1 - E2)) / (1 + Math.sqrt(1 - E2));
        double fi1 = mu + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
                + (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu)
                + (1097 * Math.pow(e1, 4) / 512) * Math.sin(8 * mu);
        double sin1 = Math.sin(fi1);
        double nu1 = A / Math.sqrt(1 - E2 * sin1 * sin1);
        double rho1 = A * (1 - E2) / Math.pow(1 - E2 * sin1 * sin1, 1.5);
        double t1 = Math.tan(fi1) * Math.tan(fi1);
        double c1 = EP2 * Math.cos(fi1) * Math.cos(fi1);
        double d = (istok - FE) / (nu1 * K0);
        double fi = fi1 - (nu1 * Math.tan(fi1) / rho1) * (d * d / 2
                - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * Math.pow(d, 4) / 24
                + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * Math.pow(d, 6) / 720);
        double lambda = LAMBDA0 + (d - (1 + 2 * t1 + c1) * Math.pow(d, 3) / 6
                + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * Math.pow(d, 5) / 120)
                / Math.cos(fi1);
        return new double[] {Math.toDegrees(fi), Math.toDegrees(lambda)};
    }

    /** Je li par (x, y) vjerojatno u HTRS96/TM unutar Hrvatske. */
    public static boolean izgledaKaoHtrs(double x, double y) {
        return x > 200_000 && x < 800_000 && y > 4_650_000 && y < 5_200_000;
    }
}
