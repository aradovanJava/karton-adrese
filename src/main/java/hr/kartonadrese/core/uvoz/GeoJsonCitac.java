package hr.kartonadrese.core.uvoz;

import hr.kartonadrese.core.geo.Htrs96Tm;
import hr.kartonadrese.core.geo.Poligon;
import hr.kartonadrese.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Čita GeoJSON (FeatureCollection) u uvozne zapise.
 *
 * <p>Točke daju koordinate izravno; poligoni i linije zamjenjuju se središtem. Ako su
 * koordinate u HTRS96/TM (npr. izvoz iz Geoportala Grada Zagreba), pretvaraju se u WGS84.
 */
public final class GeoJsonCitac {

    private GeoJsonCitac() {
    }

    public static PrepoznavanjePolja.Rezultat procitaj(String geojson) {
        Map<String, Object> korijen = Json.objekt(geojson);
        List<Object> objekti = "Feature".equals(korijen.get("type")) ? List.of(korijen) : Json.niz(korijen, "features");
        List<UvozniZapis> zapisi = new ArrayList<>();
        int bezNaziva = 0;
        int bezLokacije = 0;
        for (Object o : objekti) {
            Map<String, String> svojstva = svojstva(Json.mapa(o, "properties"));
            Optional<double[]> tocka = tocka(Json.mapa(o, "geometry"));
            String naziv = PrepoznavanjePolja.nazivIzSvojstava(svojstva);
            String adresa = PrepoznavanjePolja.adresaIzSvojstava(svojstva);
            if (naziv == null) {
                naziv = adresa;
            }
            if (naziv == null) {
                bezNaziva++;
                continue;
            }
            if (tocka.isEmpty() && adresa == null) {
                bezLokacije++;
                continue;
            }
            zapisi.add(new UvozniZapis(naziv, adresa, tocka.map(t -> t[0]).orElse(null),
                    tocka.map(t -> t[1]).orElse(null), svojstva));
        }
        return new PrepoznavanjePolja.Rezultat(zapisi, bezNaziva, bezLokacije, "GeoJSON (svojstva objekata)");
    }

    /** Poligoni iz GeoJSON-a (npr. granice gradskih četvrti), s nazivom iz svojstava. */
    public static List<Poligon> poligoni(String geojson) {
        Map<String, Object> korijen = Json.objekt(geojson);
        List<Poligon> rezultat = new ArrayList<>();
        for (Object o : Json.niz(korijen, "features")) {
            Map<String, Object> geometrija = Json.mapa(o, "geometry");
            Map<String, String> svojstva = svojstva(Json.mapa(o, "properties"));
            String naziv = PrepoznavanjePolja.nazivIzSvojstava(svojstva);
            List<double[][]> prstenovi = new ArrayList<>();
            String tip = String.valueOf(geometrija.get("type"));
            Object koord = geometrija.get("coordinates");
            if ("Polygon".equals(tip) && koord instanceof List<?> poligon && !poligon.isEmpty()) {
                prstenovi.add(prsten(poligon.getFirst()));
            } else if ("MultiPolygon".equals(tip) && koord instanceof List<?> vise) {
                for (Object p : vise) {
                    if (p instanceof List<?> poligon && !poligon.isEmpty()) {
                        prstenovi.add(prsten(poligon.getFirst()));
                    }
                }
            }
            if (!prstenovi.isEmpty() && naziv != null) {
                rezultat.add(new Poligon(naziv, prstenovi));
            }
        }
        return rezultat;
    }

    private static Map<String, String> svojstva(Map<String, Object> izvorna) {
        Map<String, String> s = new LinkedHashMap<>();
        izvorna.forEach((k, v) -> {
            if (v != null && !(v instanceof Map) && !(v instanceof List) && s.size() < 40) {
                String t = String.valueOf(v).trim();
                if (!t.isEmpty()) {
                    s.put(k, t.length() > 300 ? t.substring(0, 300) : t);
                }
            }
        });
        return s;
    }

    /** {lat, lon} u WGS84 za bilo koju vrstu geometrije. */
    static Optional<double[]> tocka(Map<String, Object> geometrija) {
        Object koord = geometrija.get("coordinates");
        String tip = String.valueOf(geometrija.get("type"));
        List<double[]> tocke = new ArrayList<>();
        skupi(koord, tocke);
        if (tocke.isEmpty()) {
            return Optional.empty();
        }
        double[] xy;
        if ("Point".equals(tip) || tocke.size() == 1) {
            xy = tocke.getFirst();
        } else {
            double sx = 0;
            double sy = 0;
            for (double[] t : tocke) {
                sx += t[0];
                sy += t[1];
            }
            xy = new double[] {sx / tocke.size(), sy / tocke.size()};
        }
        if (Htrs96Tm.izgledaKaoHtrs(xy[0], xy[1])) {
            return Optional.of(Htrs96Tm.izProjekcije(xy[0], xy[1]));
        }
        return PrepoznavanjePolja.uWgs84(xy[1], xy[0]);
    }

    private static void skupi(Object o, List<double[]> tocke) {
        if (o instanceof List<?> l && !l.isEmpty()) {
            if (l.get(0) instanceof Number && l.size() >= 2 && l.get(1) instanceof Number) {
                tocke.add(new double[] {((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue()});
            } else {
                for (Object e : l) {
                    skupi(e, tocke);
                }
            }
        }
    }

    private static double[][] prsten(Object o) {
        List<double[]> tocke = new ArrayList<>();
        skupi(o, tocke);
        double[][] p = new double[tocke.size()][];
        for (int i = 0; i < tocke.size(); i++) {
            double[] t = tocke.get(i);
            if (Htrs96Tm.izgledaKaoHtrs(t[0], t[1])) {
                double[] ll = Htrs96Tm.izProjekcije(t[0], t[1]);
                p[i] = new double[] {ll[1], ll[0]};
            } else {
                p[i] = t;
            }
        }
        return p;
    }
}
