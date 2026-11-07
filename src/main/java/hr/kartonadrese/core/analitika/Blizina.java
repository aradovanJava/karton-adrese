package hr.kartonadrese.core.analitika;

import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Udaljenosti od točke do objekata i najbliži objekt po kategoriji. */
public final class Blizina {

    private Blizina() {
    }

    /** Objekt s udaljenošću i procijenjenim vremenom hoda. */
    public record PoiUdaljenost(Poi poi, double udaljenostM, int minutaHoda) {
    }

    public static List<PoiUdaljenost> sortiraj(double lat, double lon, List<Poi> objekti, double brzinaHoda,
                                               double najviseM) {
        List<PoiUdaljenost> r = new ArrayList<>();
        for (Poi p : objekti) {
            double d = GeoUtil.udaljenostM(lat, lon, p.lat(), p.lon());
            if (d <= najviseM) {
                r.add(new PoiUdaljenost(p, d, GeoUtil.minutaHoda(d, brzinaHoda)));
            }
        }
        r.sort(Comparator.comparingDouble(PoiUdaljenost::udaljenostM));
        return r;
    }

    /** Najbliži objekt svake kategorije (iz već sortiranog popisa). */
    public static Map<Kategorija, PoiUdaljenost> najblizi(List<PoiUdaljenost> sortirano) {
        Map<Kategorija, PoiUdaljenost> m = new EnumMap<>(Kategorija.class);
        for (PoiUdaljenost pu : sortirano) {
            m.putIfAbsent(pu.poi().kategorija(), pu);
        }
        return m;
    }

    /** Prvih n objekata zadane kategorije (iz sortiranog popisa). */
    public static List<PoiUdaljenost> prvih(List<PoiUdaljenost> sortirano, Kategorija k, int n) {
        return sortirano.stream().filter(pu -> pu.poi().kategorija() == k).limit(n).toList();
    }
}
