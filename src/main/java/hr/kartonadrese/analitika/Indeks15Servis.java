package hr.kartonadrese.analitika;

import hr.kartonadrese.baza.CelijaRepozitorij;
import hr.kartonadrese.baza.ProstornaPretraga;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.analitika.Blizina;
import hr.kartonadrese.core.analitika.Indeks15;
import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.geo.Laea3035;
import hr.kartonadrese.core.model.Celija;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indeks grada 15 minuta po ćeliji mreže 1 km² – izlazni otvoreni skup podataka (CC BY 4.0).
 *
 * <p>Ako mreža stanovništva (DZS) nije uvezena, ćelije se stvaraju nad područjem grada i
 * zadržavaju samo one u kojima postoji barem jedan objekt u dometu (izbacuju se šume i polja).
 */
@Service
public class Indeks15Servis {

    private static final Logger LOG = LoggerFactory.getLogger(Indeks15Servis.class);

    private final ProstornaPretraga pretraga;
    private final CelijaRepozitorij celije;
    private final KartonSvojstva svojstva;

    public Indeks15Servis(ProstornaPretraga pretraga, CelijaRepozitorij celije, KartonSvojstva svojstva) {
        this.pretraga = pretraga;
        this.celije = celije;
        this.svojstva = svojstva;
    }

    public Indeks15.Rezultat zaTocku(double lat, double lon, double brzinaHoda) {
        List<Poi> uDometu = pretraga.uKrugu(lat, lon, GeoUtil.dometM(Indeks15.MINUTA, brzinaHoda) + 50,
                Kategorija.svakodnevne());
        return Indeks15.izracunaj(Blizina.sortiraj(lat, lon, uDometu, brzinaHoda, Double.MAX_VALUE));
    }

    public int izracunajSve() {
        Map<String, double[]> sredista = new LinkedHashMap<>();
        for (Celija c : celije.sve()) {
            sredista.put(c.id(), new double[] {c.lat(), c.lon()});
        }
        boolean imaStanovnistvo = !sredista.isEmpty();
        if (!imaStanovnistvo) {
            sredista.putAll(mrezaPodrucja());
        }
        int n = 0;
        for (Map.Entry<String, double[]> e : sredista.entrySet()) {
            double[] s = e.getValue();
            Indeks15.Rezultat r = zaTocku(s[0], s[1], GeoUtil.BRZINA_HODA_M_MIN);
            if (!imaStanovnistvo && r.indeks() == 0) {
                continue;
            }
            celije.spremiIndeks(e.getKey(), s[0], s[1], r.indeks(), r.nedostaju());
            n++;
        }
        LOG.info("Indeks grada 15 minuta izračunat za {} ćelija", n);
        return n;
    }

    /** Ćelije 1 km² (EPSG:3035) čija su središta unutar područja grada. */
    Map<String, double[]> mrezaPodrucja() {
        KartonSvojstva.Podrucje p = svojstva.podrucje();
        double[][] uglovi = {
            Laea3035.uProjekciju(p.minLat(), p.minLon()), Laea3035.uProjekciju(p.minLat(), p.maxLon()),
            Laea3035.uProjekciju(p.maxLat(), p.minLon()), Laea3035.uProjekciju(p.maxLat(), p.maxLon())
        };
        double minE = Double.MAX_VALUE;
        double maxE = -Double.MAX_VALUE;
        double minN = Double.MAX_VALUE;
        double maxN = -Double.MAX_VALUE;
        for (double[] u : uglovi) {
            minE = Math.min(minE, u[0]);
            maxE = Math.max(maxE, u[0]);
            minN = Math.min(minN, u[1]);
            maxN = Math.max(maxN, u[1]);
        }
        Map<String, double[]> r = new LinkedHashMap<>();
        List<long[]> kutovi = new ArrayList<>();
        for (long e = (long) Math.floor(minE / 1000) * 1000; e <= maxE; e += 1000) {
            for (long nn = (long) Math.floor(minN / 1000) * 1000; nn <= maxN; nn += 1000) {
                kutovi.add(new long[] {e, nn});
            }
        }
        for (long[] k : kutovi) {
            double[] s = Laea3035.izProjekcije(k[0] + 500.0, k[1] + 500.0);
            if (p.okvir().sadrzi(s[0], s[1])) {
                r.put("CRS3035RES1000mN" + k[1] + "E" + k[0], s);
            }
        }
        return r;
    }
}
