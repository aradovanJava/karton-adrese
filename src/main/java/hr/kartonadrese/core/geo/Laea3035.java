package hr.kartonadrese.core.geo;

/**
 * ETRS89 / LAEA Europe (EPSG:3035) – Lambertova azimutna ekvivalentna projekcija.
 *
 * <p>U njoj su definirane europske statističke mreže (GEOSTAT, Popis 2021. na mreži 1 km²),
 * pa se iz nje računa oznaka ćelije, npr. {@code CRS3035RES1000mN2502000E4810000}
 * (koordinate donjeg lijevog kuta). Formule: EPSG Guidance Note 7-2, metoda 9820.
 */
public final class Laea3035 {

    private static final double A = 6_378_137.0;
    private static final double F = 1 / 298.257222101;
    private static final double E2 = 2 * F - F * F;
    private static final double E = Math.sqrt(E2);
    private static final double FI0 = Math.toRadians(52);
    private static final double LAMBDA0 = Math.toRadians(10);
    private static final double FE = 4_321_000.0;
    private static final double FN = 3_210_000.0;

    private static final double QP = q(Math.PI / 2);
    private static final double Q0 = q(FI0);
    private static final double RQ = A * Math.sqrt(QP / 2);
    private static final double BETA0 = Math.asin(Q0 / QP);
    private static final double D = A * (Math.cos(FI0) / Math.sqrt(1 - E2 * Math.sin(FI0) * Math.sin(FI0)))
            / (RQ * Math.cos(BETA0));

    private Laea3035() {
    }

    private static double q(double fi) {
        double s = Math.sin(fi);
        return (1 - E2) * (s / (1 - E2 * s * s) - (1 / (2 * E)) * Math.log((1 - E * s) / (1 + E * s)));
    }

    /** WGS84/ETRS89 (lat, lon u stupnjevima) → {istočno, sjeverno} u metrima. */
    public static double[] uProjekciju(double lat, double lon) {
        double fi = Math.toRadians(lat);
        double dl = Math.toRadians(lon) - LAMBDA0;
        double beta = Math.asin(q(fi) / QP);
        double b = RQ * Math.sqrt(2 / (1 + Math.sin(BETA0) * Math.sin(beta)
                + Math.cos(BETA0) * Math.cos(beta) * Math.cos(dl)));
        double istok = FE + b * D * Math.cos(beta) * Math.sin(dl);
        double sjever = FN + (b / D) * (Math.cos(BETA0) * Math.sin(beta)
                - Math.sin(BETA0) * Math.cos(beta) * Math.cos(dl));
        return new double[] {istok, sjever};
    }

    /** {istočno, sjeverno} u metrima → {lat, lon} u stupnjevima. */
    public static double[] izProjekcije(double istok, double sjever) {
        double x = istok - FE;
        double y = sjever - FN;
        double rho = Math.hypot(x / D, D * y);
        if (rho < 1e-9) {
            return new double[] {Math.toDegrees(FI0), Math.toDegrees(LAMBDA0)};
        }
        double c = 2 * Math.asin(rho / (2 * RQ));
        double betaC = Math.asin(Math.cos(c) * Math.sin(BETA0) + (D * y * Math.sin(c) * Math.cos(BETA0)) / rho);
        double lambda = LAMBDA0 + Math.atan2(x * Math.sin(c),
                D * rho * Math.cos(BETA0) * Math.cos(c) - D * D * y * Math.sin(BETA0) * Math.sin(c));
        double e4 = E2 * E2;
        double e6 = e4 * E2;
        double fi = betaC
                + (E2 / 3 + 31 * e4 / 180 + 517 * e6 / 5040) * Math.sin(2 * betaC)
                + (23 * e4 / 360 + 251 * e6 / 3780) * Math.sin(4 * betaC)
                + (761 * e6 / 45360) * Math.sin(6 * betaC);
        return new double[] {Math.toDegrees(fi), Math.toDegrees(lambda)};
    }

    /** Oznaka ćelije mreže zadane rezolucije (u metrima) u kojoj se nalazi točka. */
    public static String oznakaCelije(double lat, double lon, int rezolucijaM) {
        double[] p = uProjekciju(lat, lon);
        long e = (long) Math.floor(p[0] / rezolucijaM) * rezolucijaM;
        long n = (long) Math.floor(p[1] / rezolucijaM) * rezolucijaM;
        return "CRS3035RES" + rezolucijaM + "mN" + n + "E" + e;
    }

    /** Donji lijevi kut ćelije {istočno, sjeverno} iz oznake ćelije. */
    public static long[] kutCelije(String oznaka) {
        int iN = oznaka.indexOf("mN");
        int iE = oznaka.lastIndexOf('E');
        if (iN < 0 || iE < iN) {
            throw new IllegalArgumentException("Neispravna oznaka ćelije: " + oznaka);
        }
        long n = Long.parseLong(oznaka.substring(iN + 2, iE));
        long e = Long.parseLong(oznaka.substring(iE + 1));
        return new long[] {e, n};
    }

    public static int rezolucija(String oznaka) {
        int iR = oznaka.indexOf("RES");
        int iM = oznaka.indexOf("mN");
        return Integer.parseInt(oznaka.substring(iR + 3, iM));
    }

    /** Središte ćelije {lat, lon}. */
    public static double[] srediste(String oznaka) {
        long[] k = kutCelije(oznaka);
        int r = rezolucija(oznaka);
        return izProjekcije(k[0] + r / 2.0, k[1] + r / 2.0);
    }

    /** Uglovi ćelije kao zatvoreni prsten [[lon, lat], …] (GeoJSON redoslijed). */
    public static double[][] prsten(String oznaka) {
        long[] k = kutCelije(oznaka);
        int r = rezolucija(oznaka);
        double[][] uglovi = {
            {k[0], k[1]}, {k[0] + r, k[1]}, {k[0] + r, k[1] + r}, {k[0], k[1] + r}, {k[0], k[1]}
        };
        double[][] prsten = new double[5][];
        for (int i = 0; i < 5; i++) {
            double[] ll = izProjekcije(uglovi[i][0], uglovi[i][1]);
            prsten[i] = new double[] {ll[1], ll[0]};
        }
        return prsten;
    }
}
