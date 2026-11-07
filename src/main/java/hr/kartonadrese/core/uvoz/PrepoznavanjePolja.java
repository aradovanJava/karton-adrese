package hr.kartonadrese.core.uvoz;

import hr.kartonadrese.core.geo.Htrs96Tm;
import hr.kartonadrese.core.tablica.Tablica;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepoznaje stupce s nazivom, adresom i koordinatama u tablicama s otvorenih portala.
 *
 * <p>Skupovi istog izdavatelja često imaju različite nazive stupaca ("Geografska širina",
 * "LAT", "y_wgs84", "Koordinata X" u HTRS96/TM …). Umjesto ručnog mapiranja za svaki skup,
 * stupci se prepoznaju po imenu i po rasponu vrijednosti, a ručno se mapiraju samo iznimke
 * (svojstva {@code stupac-*} u konfiguraciji izvora).
 */
public final class PrepoznavanjePolja {

    private static final List<String> LAT = List.of("lat", "latitude", "geo_sirina", "geografska sirina", "sirina",
            "y_wgs", "wgs84_lat", "wgs_lat", "lat_wgs84", "geo_lat");
    private static final List<String> LON = List.of("lon", "lng", "long", "longitude", "geo_duzina", "geografska duzina",
            "duzina", "x_wgs", "wgs84_lon", "wgs_lon", "lon_wgs84", "geo_lon");
    private static final List<String> X_Y = List.of("x", "y", "e", "n", "koordinata x", "koordinata y", "x_koord",
            "y_koord", "htrs_x", "htrs_y", "easting", "northing", "x_htrs96", "y_htrs96", "koord_x", "koord_y");
    private static final List<String> KOORDINATE = List.of("koordinate", "geometrija", "geometry", "wkt", "geom",
            "lokacija (koordinate)", "the_geom");
    private static final List<String> NAZIV = List.of("naziv", "naziv ustanove", "naziv objekta", "naziv vrtica",
            "naziv skole", "naziv ljekarne", "ime", "name", "ustanova", "objekt", "naziv_ustanove", "naziv_objekta",
            "title", "naziv postaje", "naziv stajalista");
    private static final List<String> ADRESA = List.of("adresa", "address", "ulica i kucni broj", "ulica i broj",
            "adresa ustanove", "adresa objekta", "sjediste", "ulica", "lokacija");

    private static final Pattern WKT_TOCKA = Pattern.compile("POINT\\s*\\(\\s*(-?[\\d.]+)\\s+(-?[\\d.]+)\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAR_BROJEVA = Pattern.compile("(-?\\d+[.,]\\d+)\\s*[,; ]\\s*(-?\\d+[.,]\\d+)");

    private PrepoznavanjePolja() {
    }

    /** Ručno zadani nazivi stupaca (null = prepoznaj automatski). */
    public record Mapiranje(String naziv, String adresa, String lat, String lon) {
        public static final Mapiranje AUTOMATSKI = new Mapiranje(null, null, null, null);
    }

    /** Rezultat uvoza tablice: zapisi i broj preskočenih redova po razlogu. */
    public record Rezultat(List<UvozniZapis> zapisi, int bezNaziva, int bezLokacije, String opisMapiranja) {
    }

    public static String normaliziraj(String s) {
        String bez = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return bez.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).replaceAll("[_\\-]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    static int nadjiStupac(List<String> stupci, List<String> kandidati, String rucno) {
        if (rucno != null && !rucno.isBlank()) {
            String r = normaliziraj(rucno);
            for (int i = 0; i < stupci.size(); i++) {
                if (normaliziraj(stupci.get(i)).equals(r)) {
                    return i;
                }
            }
            return -1;
        }
        List<String> norm = stupci.stream().map(PrepoznavanjePolja::normaliziraj).toList();
        for (String k : kandidati) {
            String kn = normaliziraj(k);
            int i = norm.indexOf(kn);
            if (i >= 0) {
                return i;
            }
        }
        for (String k : kandidati) {
            String kn = normaliziraj(k);
            if (kn.length() < 4) {
                continue; // kratke oznake (x, y, lat) samo kao točno podudaranje
            }
            for (int i = 0; i < norm.size(); i++) {
                if (norm.get(i).contains(kn)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static Optional<Double> broj(String s) {
        if (s == null) {
            return Optional.empty();
        }
        String t = s.trim().replace(" ", "").replace(" ", "");
        if (t.isEmpty()) {
            return Optional.empty();
        }
        if (t.indexOf(',') >= 0 && t.indexOf('.') >= 0) {
            t = t.lastIndexOf(',') > t.lastIndexOf('.') ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        } else {
            t = t.replace(',', '.');
        }
        try {
            return Optional.of(Double.parseDouble(t));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Pretvara par brojeva u WGS84 {lat, lon} ako je moguće: prepoznaje WGS84 (i zamijenjeni
     * redoslijed) te HTRS96/TM u bilo kojem redoslijedu.
     */
    public static Optional<double[]> uWgs84(double a, double b) {
        if (uHrvatskoj(a, b)) {
            return Optional.of(new double[] {a, b});
        }
        if (uHrvatskoj(b, a)) {
            return Optional.of(new double[] {b, a});
        }
        if (Htrs96Tm.izgledaKaoHtrs(a, b)) {
            return Optional.of(Htrs96Tm.izProjekcije(a, b));
        }
        if (Htrs96Tm.izgledaKaoHtrs(b, a)) {
            return Optional.of(Htrs96Tm.izProjekcije(b, a));
        }
        return Optional.empty();
    }

    static boolean uHrvatskoj(double lat, double lon) {
        return lat > 42.0 && lat < 46.7 && lon > 13.0 && lon < 19.6;
    }

    /** Koordinate zapisane u jednom polju: "45.81, 15.97" ili WKT "POINT (15.97 45.81)". */
    public static Optional<double[]> izTeksta(String s) {
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        Matcher w = WKT_TOCKA.matcher(s);
        if (w.find()) {
            double x = Double.parseDouble(w.group(1));
            double y = Double.parseDouble(w.group(2));
            return uWgs84(y, x);
        }
        Matcher p = PAR_BROJEVA.matcher(s);
        if (p.find()) {
            Optional<Double> a = broj(p.group(1));
            Optional<Double> b = broj(p.group(2));
            if (a.isPresent() && b.isPresent()) {
                return uWgs84(a.get(), b.get());
            }
        }
        return Optional.empty();
    }

    public static Rezultat izTablice(Tablica t, Mapiranje m) {
        List<String> stupci = t.stupci();
        int iNaziv = nadjiStupac(stupci, NAZIV, m.naziv());
        int iAdresa = nadjiStupac(stupci, ADRESA, m.adresa());
        int iLat = nadjiStupac(stupci, LAT, m.lat());
        int iLon = nadjiStupac(stupci, LON, m.lon());
        int iKoord = nadjiStupac(stupci, KOORDINATE, null);
        int[] xy = stupciXy(stupci);

        List<UvozniZapis> zapisi = new ArrayList<>();
        int bezNaziva = 0;
        int bezLokacije = 0;
        for (List<String> red : t.redovi()) {
            String naziv = t.vrijednost(red, iNaziv);
            String adresa = t.vrijednost(red, iAdresa);
            if (naziv.isEmpty()) {
                naziv = adresa;
            }
            if (naziv.isEmpty()) {
                bezNaziva++;
                continue;
            }
            Optional<double[]> ll = Optional.empty();
            if (iLat >= 0 && iLon >= 0) {
                Optional<Double> a = broj(t.vrijednost(red, iLat));
                Optional<Double> b = broj(t.vrijednost(red, iLon));
                if (a.isPresent() && b.isPresent()) {
                    ll = uWgs84(a.get(), b.get());
                }
            }
            if (ll.isEmpty() && xy[0] >= 0 && xy[1] >= 0) {
                Optional<Double> a = broj(t.vrijednost(red, xy[0]));
                Optional<Double> b = broj(t.vrijednost(red, xy[1]));
                if (a.isPresent() && b.isPresent()) {
                    ll = uWgs84(a.get(), b.get());
                }
            }
            if (ll.isEmpty() && iKoord >= 0) {
                ll = izTeksta(t.vrijednost(red, iKoord));
            }
            if (ll.isEmpty() && adresa.isEmpty()) {
                bezLokacije++;
                continue;
            }
            Map<String, String> atributi = new LinkedHashMap<>();
            for (int i = 0; i < stupci.size() && atributi.size() < 40; i++) {
                if (i == iLat || i == iLon || i == xy[0] || i == xy[1] || i == iKoord) {
                    continue;
                }
                String v = t.vrijednost(red, i);
                if (!v.isEmpty() && !stupci.get(i).isBlank()) {
                    atributi.put(stupci.get(i).trim(), v.length() > 300 ? v.substring(0, 300) : v);
                }
            }
            zapisi.add(new UvozniZapis(naziv, adresa.isEmpty() ? null : adresa,
                    ll.map(x -> x[0]).orElse(null), ll.map(x -> x[1]).orElse(null), atributi));
        }
        String opis = "naziv=" + ime(stupci, iNaziv) + ", adresa=" + ime(stupci, iAdresa)
                + ", lat=" + ime(stupci, iLat) + ", lon=" + ime(stupci, iLon)
                + ", x/y=" + ime(stupci, xy[0]) + "/" + ime(stupci, xy[1]) + ", koordinate=" + ime(stupci, iKoord);
        return new Rezultat(zapisi, bezNaziva, bezLokacije, opis);
    }

    private static int[] stupciXy(List<String> stupci) {
        int x = -1;
        int y = -1;
        List<String> dopusteni = X_Y.stream().map(PrepoznavanjePolja::normaliziraj).toList();
        for (int i = 0; i < stupci.size(); i++) {
            String n = normaliziraj(stupci.get(i));
            if (!dopusteni.contains(n)) {
                continue;
            }
            if (n.contains("x") || n.equals("e") || n.contains("east")) {
                x = x < 0 ? i : x;
            } else {
                y = y < 0 ? i : y;
            }
        }
        return new int[] {x, y};
    }

    private static String ime(List<String> stupci, int i) {
        return i >= 0 ? "\"" + stupci.get(i) + "\"" : "—";
    }

    /** Odabire najbolji naziv iz svojstava GeoJSON objekta. */
    public static String nazivIzSvojstava(Map<String, String> svojstva) {
        return vrijednostPoKandidatima(svojstva, NAZIV);
    }

    public static String adresaIzSvojstava(Map<String, String> svojstva) {
        return vrijednostPoKandidatima(svojstva, ADRESA);
    }

    private static String vrijednostPoKandidatima(Map<String, String> svojstva, List<String> kandidati) {
        List<String> kljucevi = new ArrayList<>(svojstva.keySet());
        int i = nadjiStupac(kljucevi, kandidati, null);
        if (i < 0) {
            return null;
        }
        String v = svojstva.get(kljucevi.get(i));
        return v == null || v.isBlank() ? null : v.trim();
    }
}
