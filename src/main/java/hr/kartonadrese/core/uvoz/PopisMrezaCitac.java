package hr.kartonadrese.core.uvoz;

import hr.kartonadrese.core.geo.Laea3035;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Popis stanovništva 2021. na europskoj mreži 1 km² (Eurostat GISCO, „Census grid 2021”), CSV.
 *
 * <p>Zaglavlje: {@code GRD_ID,T,M,F,Y_LT15,Y_1564,Y_GE65,…,CNTR_ID}. Oznaka ćelije je INSPIRE kod
 * {@code CRS3035RES1000mN<sjever>E<istok>} (donji lijevi kut u EPSG:3035), a {@code T} ukupan broj
 * stanovnika. Vrijednosti su za zaštitu povjerljivosti malo izmijenjene (pa M + F ne mora biti T).
 * Datoteka za cijeli EU ima ≈ 4,6 milijuna redaka; čitaju se samo ćelije unutar zadanog okvira.
 */
public final class PopisMrezaCitac {

    private static final List<String> OZNAKA = List.of("grd_id", "cellcode", "grid_id", "id_celije", "oznaka");
    private static final List<String> UKUPNO = List.of("t", "tot_p_2021", "tot_p", "obs_value", "stanovnika", "ukupno");

    private PopisMrezaCitac() {
    }

    public record Celija(String id, double lat, double lon, int stanovnika) {
    }

    /** Čita ćelije s barem jednim stanovnikom čije je središte unutar okvira (WGS84). */
    public static List<Celija> procitaj(BufferedReader citac, double minLat, double minLon, double maxLat,
                                        double maxLon) throws IOException {
        String zaglavlje = citac.readLine();
        if (zaglavlje == null) {
            throw new IOException("Prazna datoteka popisa");
        }
        zaglavlje = zaglavlje.replace("﻿", "");
        char razdjelnik = zaglavlje.chars().filter(c -> c == ';').count() > zaglavlje.chars().filter(c -> c == ',').count()
                ? ';' : ',';
        String[] stupci = podijeli(zaglavlje, razdjelnik);
        int iOznaka = indeks(stupci, OZNAKA);
        int iUkupno = indeks(stupci, UKUPNO);
        if (iOznaka < 0 || iUkupno < 0) {
            throw new IOException("Ne prepoznajem stupce oznake ćelije i broja stanovnika: " + String.join(",", stupci));
        }
        // Okvir u EPSG:3035 (s rubom jedne ćelije) za brzo odbacivanje redaka bez preračunavanja koordinata
        double[][] kutovi = {Laea3035.uProjekciju(minLat, minLon), Laea3035.uProjekciju(minLat, maxLon),
                Laea3035.uProjekciju(maxLat, minLon), Laea3035.uProjekciju(maxLat, maxLon)};
        double minE = Double.MAX_VALUE;
        double maxE = -Double.MAX_VALUE;
        double minN = Double.MAX_VALUE;
        double maxN = -Double.MAX_VALUE;
        for (double[] k : kutovi) {
            minE = Math.min(minE, k[0]);
            maxE = Math.max(maxE, k[0]);
            minN = Math.min(minN, k[1]);
            maxN = Math.max(maxN, k[1]);
        }
        List<Celija> rezultat = new ArrayList<>();
        String red;
        while ((red = citac.readLine()) != null) {
            String[] v = podijeli(red, razdjelnik);
            if (v.length <= Math.max(iOznaka, iUkupno)) {
                continue;
            }
            String id = v[iOznaka].trim();
            long[] kut = kut(id);
            if (kut == null || kut[1] < minE - 1000 || kut[1] > maxE || kut[0] < minN - 1000 || kut[0] > maxN) {
                continue;
            }
            int n;
            try {
                n = (int) Math.round(Double.parseDouble(v[iUkupno].trim().replace(',', '.')));
            } catch (NumberFormatException e) {
                continue;
            }
            if (n <= 0) {
                continue;
            }
            double[] s = Laea3035.srediste(id);
            if (s[0] >= minLat && s[0] <= maxLat && s[1] >= minLon && s[1] <= maxLon) {
                rezultat.add(new Celija(id, s[0], s[1], n));
            }
        }
        return rezultat;
    }

    /** {sjever, istok} donjeg lijevog kuta iz oznake CRS3035RES1000mN2490000E4800000, ili null. */
    static long[] kut(String id) {
        int n = id.lastIndexOf('N');
        int e = id.lastIndexOf('E');
        if (!id.startsWith("CRS3035") || n < 0 || e < n) {
            return null;
        }
        try {
            return new long[] {Long.parseLong(id.substring(n + 1, e)), Long.parseLong(id.substring(e + 1))};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int indeks(String[] stupci, List<String> kandidati) {
        for (String k : kandidati) {
            for (int i = 0; i < stupci.length; i++) {
                if (stupci[i].trim().replace("\"", "").toLowerCase(Locale.ROOT).equals(k)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String[] podijeli(String red, char razdjelnik) {
        return red.replace("\"", "").split(String.valueOf(razdjelnik), -1);
    }
}
