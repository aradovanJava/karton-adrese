package hr.kartonadrese.core.analitika;

import hr.kartonadrese.core.model.Kategorija;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Indeks "grada 15 minuta": koliko od šest vrsta svakodnevnih usluga (škola, vrtić, ljekarna,
 * dom zdravlja ili ambulanta, stajalište, trgovina) je dostupno pješice unutar 15 minuta.
 */
public final class Indeks15 {

    public static final int MINUTA = 15;

    private Indeks15() {
    }

    public record Rezultat(int indeks, int najvise, List<Kategorija> dostupne, List<Kategorija> nedostaju,
                           Map<Kategorija, Integer> minuta) {

        public String oznaka() {
            return indeks + "/" + najvise;
        }
    }

    public static Rezultat izracunaj(List<Blizina.PoiUdaljenost> sortirano) {
        Map<Kategorija, Blizina.PoiUdaljenost> najblizi = Blizina.najblizi(sortirano);
        List<Kategorija> dostupne = new ArrayList<>();
        List<Kategorija> nedostaju = new ArrayList<>();
        Map<Kategorija, Integer> minuta = new EnumMap<>(Kategorija.class);
        for (Kategorija k : Kategorija.svakodnevne()) {
            Blizina.PoiUdaljenost pu = najblizi.get(k);
            if (pu != null) {
                minuta.put(k, pu.minutaHoda());
            }
            if (pu != null && pu.minutaHoda() <= MINUTA) {
                dostupne.add(k);
            } else {
                nedostaju.add(k);
            }
        }
        return new Rezultat(dostupne.size(), Kategorija.svakodnevne().size(), dostupne, nedostaju, minuta);
    }
}
