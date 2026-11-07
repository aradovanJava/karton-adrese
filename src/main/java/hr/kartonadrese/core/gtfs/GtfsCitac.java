package hr.kartonadrese.core.gtfs;

import hr.kartonadrese.core.model.Stajaliste;
import hr.kartonadrese.core.tablica.CsvCitac;
import hr.kartonadrese.core.tablica.Tablica;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Čita statički GTFS (npr. ZET, https://zet.hr/gtfs-scheduled/latest) i za svako
 * stajalište vraća linije koje na njemu staju i vrstu prijevoza.
 *
 * <p>{@code stop_times.txt} može imati milijune redova pa se čita redak po redak.
 */
public final class GtfsCitac {

    private GtfsCitac() {
    }

    public static List<Stajaliste> procitaj(Path zip) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            Map<String, String[]> linije = new HashMap<>(); // route_id -> {short_name, route_type}
            Tablica routes = tablica(z, "routes.txt");
            int rId = stupac(routes, "route_id");
            int rIme = stupac(routes, "route_short_name");
            int rDugo = stupac(routes, "route_long_name");
            int rTip = stupac(routes, "route_type");
            for (List<String> red : routes.redovi()) {
                String ime = routes.vrijednost(red, rIme);
                if (ime.isEmpty()) {
                    ime = routes.vrijednost(red, rDugo);
                }
                linije.put(routes.vrijednost(red, rId), new String[] {ime, routes.vrijednost(red, rTip)});
            }

            Map<String, String> voznjaLinija = new HashMap<>(); // trip_id -> route_id
            Tablica trips = tablica(z, "trips.txt");
            int tRoute = stupac(trips, "route_id");
            int tTrip = stupac(trips, "trip_id");
            for (List<String> red : trips.redovi()) {
                voznjaLinija.put(trips.vrijednost(red, tTrip), trips.vrijednost(red, tRoute));
            }

            Map<String, Set<String>> linijeStajalista = new HashMap<>();
            Map<String, Integer> polazaka = new HashMap<>();
            ZipEntry st = z.getEntry("stop_times.txt");
            if (st == null) {
                throw new IOException("GTFS nema datoteku stop_times.txt");
            }
            try (InputStream in = z.getInputStream(st);
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String zaglavlje = r.readLine();
                if (zaglavlje == null) {
                    throw new IOException("Prazan stop_times.txt");
                }
                List<String> stupci = List.of(ukloniBom(zaglavlje).split(",", -1));
                int sTrip = stupci.indexOf("trip_id");
                int sStop = stupci.indexOf("stop_id");
                String linija;
                while ((linija = r.readLine()) != null) {
                    String[] p = linija.split(",", -1);
                    if (p.length <= Math.max(sTrip, sStop)) {
                        continue;
                    }
                    String stop = p[sStop].replace("\"", "");
                    String route = voznjaLinija.get(p[sTrip].replace("\"", ""));
                    if (route != null) {
                        linijeStajalista.computeIfAbsent(stop, k -> new HashSet<>()).add(route);
                    }
                    polazaka.merge(stop, 1, Integer::sum);
                }
            }

            Tablica stops = tablica(z, "stops.txt");
            int sId = stupac(stops, "stop_id");
            int sIme = stupac(stops, "stop_name");
            int sLat = stupac(stops, "stop_lat");
            int sLon = stupac(stops, "stop_lon");
            int sTip = stops.stupci().indexOf("location_type");
            List<Stajaliste> rezultat = new ArrayList<>();
            for (List<String> red : stops.redovi()) {
                String tip = sTip >= 0 ? stops.vrijednost(red, sTip) : "";
                if (!tip.isEmpty() && !"0".equals(tip)) {
                    continue; // kolodvori i ulazi nisu stajališta za ukrcaj
                }
                String id = stops.vrijednost(red, sId);
                Set<String> rute = linijeStajalista.getOrDefault(id, Set.of());
                TreeSet<String> imena = new TreeSet<>(Comparator.comparing(GtfsCitac::kljucLinije));
                boolean tramvaj = false;
                boolean autobus = false;
                for (String ruta : rute) {
                    String[] l = linije.get(ruta);
                    if (l == null) {
                        continue;
                    }
                    imena.add(l[0]);
                    tramvaj |= "0".equals(l[1]) || "900".equals(l[1]);
                    autobus |= "3".equals(l[1]) || "700".equals(l[1]);
                }
                try {
                    rezultat.add(new Stajaliste(id, stops.vrijednost(red, sIme),
                            Double.parseDouble(stops.vrijednost(red, sLat)), Double.parseDouble(stops.vrijednost(red, sLon)),
                            List.copyOf(imena), tramvaj, autobus || !tramvaj, polazaka.getOrDefault(id, 0)));
                } catch (NumberFormatException e) {
                    // stajalište bez koordinata se preskače
                }
            }
            return rezultat;
        }
    }

    private static Tablica tablica(ZipFile z, String ime) throws IOException {
        ZipEntry e = z.getEntry(ime);
        if (e == null) {
            throw new IOException("GTFS nema datoteku " + ime);
        }
        try (InputStream in = z.getInputStream(e)) {
            return CsvCitac.procitaj(in.readAllBytes());
        }
    }

    private static int stupac(Tablica t, String ime) throws IOException {
        int i = t.stupci().indexOf(ime);
        if (i < 0) {
            throw new IOException("GTFS: nedostaje stupac " + ime);
        }
        return i;
    }

    private static String ukloniBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** Linije se sortiraju brojčano (2, 11, 109), a ostale abecedno. */
    private static String kljucLinije(String s) {
        try {
            return String.format("%08d", Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return "~" + s;
        }
    }
}
