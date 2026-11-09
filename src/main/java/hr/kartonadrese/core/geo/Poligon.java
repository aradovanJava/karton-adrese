package hr.kartonadrese.core.geo;

import java.util.List;

/**
 * Poligon s jednim ili više vanjskih prstenova (npr. granica gradske četvrti).
 * Točke su {lon, lat} kao u GeoJSON-u.
 */
public record Poligon(String naziv, List<double[][]> prstenovi) {

    /** Provjera "točka u poligonu" metodom zrake (dovoljno precizno za granice četvrti). */
    public boolean sadrzi(double lat, double lon) {
        boolean unutra = false;
        for (double[][] prsten : prstenovi) {
            if (uPrstenu(prsten, lon, lat)) {
                unutra = !unutra;
            }
        }
        return unutra;
    }

    private static boolean uPrstenu(double[][] p, double x, double y) {
        boolean c = false;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
            double xi = p[i][0];
            double yi = p[i][1];
            double xj = p[j][0];
            double yj = p[j][1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                c = !c;
            }
        }
        return c;
    }

    /** Težište prvog prstena (prosjek vrhova), {lat, lon}. */
    public double[] srediste() {
        double[][] p = prstenovi.getFirst();
        double sx = 0;
        double sy = 0;
        int n = p.length > 1 && p[0][0] == p[p.length - 1][0] && p[0][1] == p[p.length - 1][1] ? p.length - 1 : p.length;
        for (int i = 0; i < n; i++) {
            sx += p[i][0];
            sy += p[i][1];
        }
        return new double[] {sy / n, sx / n};
    }
}
