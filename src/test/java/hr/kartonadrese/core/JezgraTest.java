package hr.kartonadrese.core;

import hr.kartonadrese.core.analitika.Blizina;
import hr.kartonadrese.core.analitika.Indeks15;
import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.geo.Htrs96Tm;
import hr.kartonadrese.core.geo.Laea3035;
import hr.kartonadrese.core.geo.Poligon;
import hr.kartonadrese.core.gtfs.GtfsCitac;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;
import hr.kartonadrese.core.model.Stajaliste;
import hr.kartonadrese.core.tablica.CsvCitac;
import hr.kartonadrese.core.tablica.Tablica;
import hr.kartonadrese.core.uvoz.GeoJsonCitac;
import hr.kartonadrese.core.uvoz.PrepoznavanjePolja;
import hr.kartonadrese.core.uvoz.UvozniZapis;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testovi jezgre (bez Springa): projekcije, uvoz tablica, GeoJSON, GTFS, indeks 15 minuta. */
class JezgraTest {

    @Test
    void laeaOdgovaraPrimjeruIzEpsgSmjernica() {
        double[] p = Laea3035.uProjekciju(50, 5);
        assertEquals(3_962_799.45, p[0], 0.01);
        assertEquals(2_999_718.85, p[1], 0.01);
        double[] natrag = Laea3035.izProjekcije(p[0], p[1]);
        assertEquals(50.0, natrag[0], 1e-7);
        assertEquals(5.0, natrag[1], 1e-7);
    }

    @Test
    void oznakaCelijeZaTrgBanaJelacica() {
        String id = Laea3035.oznakaCelije(45.8131, 15.9775, 1000);
        assertEquals("CRS3035RES1000mN2540000E4785000", id);
        double[] s = Laea3035.srediste(id);
        assertTrue(GeoUtil.udaljenostM(45.8131, 15.9775, s[0], s[1]) < 710);
        assertEquals(5, Laea3035.prsten(id).length);
    }

    @Test
    void htrs96OdgovaraPyproju() {
        // referentne vrijednosti: pyproj, EPSG:4326 -> EPSG:3765
        double[] zg = Htrs96Tm.uProjekciju(45.8131, 15.9775);
        assertEquals(459_393.443, zg[0], 0.01);
        assertEquals(5_074_937.324, zg[1], 0.01);
        double[] du = Htrs96Tm.uProjekciju(42.65, 18.09);
        assertEquals(630_371.953, du[0], 0.01);
        assertEquals(4_724_592.002, du[1], 0.01);
        double[] natrag = Htrs96Tm.izProjekcije(zg[0], zg[1]);
        assertEquals(45.8131, natrag[0], 1e-7);
        assertEquals(15.9775, natrag[1], 1e-7);
    }

    @Test
    void jsonParsiranjeIPisanje() {
        Map<String, Object> o = Json.objekt("{\"a\": [1, 2.5, \"x\\u0161\"], \"b\": {\"c\": null, \"d\": true}}");
        assertEquals(List.of(1L, 2.5, "xš"), o.get("a"));
        assertNull(Json.mapa(o, "b").get("c"));
        assertEquals("{\"a\":[1,2.5,\"xš\"],\"b\":{\"c\":null,\"d\":true}}", Json.zapisi(o));
        assertThrows(Json.JsonIznimka.class, () -> Json.parsiraj("{\"a\": }"));
    }

    @Test
    void csvSHrvatskimZnakovimaUWindows1250INavodnicima() {
        String tekst = "Naziv;Adresa;Geografska širina;Geografska dužina\n"
                + "\"Ljekarna \"\"Centar\"\"\";Ilica 1;45,8130;15,9750\n"
                + "Dom zdravlja Črnomerec;Ilica 301;;\n";
        Tablica t = CsvCitac.procitaj(tekst.getBytes(Charset.forName("windows-1250")));
        assertEquals(List.of("Naziv", "Adresa", "Geografska širina", "Geografska dužina"), t.stupci());
        assertEquals(2, t.brojRedova());
        assertEquals("Ljekarna \"Centar\"", t.redovi().get(0).get(0));

        PrepoznavanjePolja.Rezultat r = PrepoznavanjePolja.izTablice(t, PrepoznavanjePolja.Mapiranje.AUTOMATSKI);
        assertEquals(2, r.zapisi().size());
        UvozniZapis prvi = r.zapisi().get(0);
        assertEquals(45.813, prvi.lat(), 1e-9);
        assertEquals(15.975, prvi.lon(), 1e-9);
        assertFalse(r.zapisi().get(1).imaKoordinate()); // ima adresu pa ostaje za geokodiranje
    }

    @Test
    void prepoznajeHtrsKoordinateIZamijenjeniRedoslijed() {
        Tablica t = new Tablica(List.of("NAZIV", "X", "Y"),
                List.of(List.of("Vrtić Bukovac", "459393,443", "5074937,324")));
        UvozniZapis z = PrepoznavanjePolja.izTablice(t, PrepoznavanjePolja.Mapiranje.AUTOMATSKI).zapisi().get(0);
        assertEquals(45.8131, z.lat(), 1e-6);
        assertEquals(15.9775, z.lon(), 1e-6);

        double[] zamijenjeno = PrepoznavanjePolja.uWgs84(15.9775, 45.8131).orElseThrow();
        assertEquals(45.8131, zamijenjeno[0], 1e-9);
        double[] wkt = PrepoznavanjePolja.izTeksta("POINT (15.9775 45.8131)").orElseThrow();
        assertEquals(15.9775, wkt[1], 1e-9);
        assertTrue(PrepoznavanjePolja.izTeksta("nema koordinata").isEmpty());
    }

    @Test
    void geoJsonTockeIPoligoni() {
        String gj = """
                {"type":"FeatureCollection","features":[
                 {"type":"Feature","properties":{"NAZIV":"OŠ Vladimira Nazora","ADRESA":"Kuščanova 1"},
                  "geometry":{"type":"Point","coordinates":[15.9935,45.8248]}},
                 {"type":"Feature","properties":{"naziv":"Park"},
                  "geometry":{"type":"Polygon","coordinates":[[[15.0,45.0],[15.2,45.0],[15.2,45.2],[15.0,45.2],[15.0,45.0]]]}},
                 {"type":"Feature","properties":{"opis":"bez naziva"},
                  "geometry":{"type":"Point","coordinates":[459393.443,5074937.324]}}
                ]}""";
        PrepoznavanjePolja.Rezultat r = GeoJsonCitac.procitaj(gj);
        assertEquals(2, r.zapisi().size());
        assertEquals(1, r.bezNaziva());
        assertEquals("Kuščanova 1", r.zapisi().get(0).adresa());
        assertEquals(45.8248, r.zapisi().get(0).lat(), 1e-9);
        assertEquals(45.08, r.zapisi().get(1).lat(), 1e-9); // središte vrhova poligona

        List<Poligon> poligoni = GeoJsonCitac.poligoni(gj);
        assertEquals(1, poligoni.size());
        assertTrue(poligoni.get(0).sadrzi(45.1, 15.1));
        assertFalse(poligoni.get(0).sadrzi(45.3, 15.1));
    }

    @Test
    void gtfsLinijePoStajalistu() throws Exception {
        Path zip = Path.of(getClass().getResource("/gtfs/mini-gtfs.zip").toURI());
        List<Stajaliste> s = GtfsCitac.procitaj(zip);
        assertEquals(2, s.size()); // kolodvor (location_type=1) se preskače
        Stajaliste jordanovac = s.stream().filter(x -> x.id().equals("100")).findFirst().orElseThrow();
        assertEquals(List.of("4"), jordanovac.linije());
        assertTrue(jordanovac.tramvaj());
        Stajaliste kvaternik = s.stream().filter(x -> x.id().equals("200")).findFirst().orElseThrow();
        assertEquals(List.of("4", "109"), kvaternik.linije());
        assertEquals("tramvaj i autobus", kvaternik.vrsta());
        assertEquals(2, kvaternik.polazakaDnevno());
    }

    @Test
    void indeks15BrojiDostupneSvakodnevneUsluge() {
        double lat = 45.8131;
        double lon = 15.9775;
        List<Poi> objekti = new ArrayList<>();
        objekti.add(poi(Kategorija.SKOLA, lat + 0.002, lon));        // ~220 m
        objekti.add(poi(Kategorija.VRTIC, lat, lon + 0.003));        // ~230 m
        objekti.add(poi(Kategorija.LJEKARNA, lat + 0.001, lon));     // ~110 m
        objekti.add(poi(Kategorija.STAJALISTE, lat - 0.001, lon));   // ~110 m
        objekti.add(poi(Kategorija.TRGOVINA, lat + 0.02, lon));      // ~2,2 km – predaleko
        objekti.add(poi(Kategorija.PARK, lat, lon + 0.001));         // nije svakodnevna kategorija
        List<Blizina.PoiUdaljenost> sortirano = Blizina.sortiraj(lat, lon, objekti, GeoUtil.BRZINA_HODA_M_MIN, 5000);
        Indeks15.Rezultat r = Indeks15.izracunaj(sortirano);
        assertEquals(4, r.indeks());
        assertEquals(6, r.najvise());
        assertEquals(List.of(Kategorija.ZDRAVSTVO, Kategorija.TRGOVINA), r.nedostaju());
        assertEquals("4/6", r.oznaka());
        assertTrue(r.minuta().get(Kategorija.TRGOVINA) > Indeks15.MINUTA);
    }

    @Test
    void minuteHodaUkljucujuZaobilazenje() {
        assertEquals(13, GeoUtil.minutaHoda(800, 80)); // 800 m × 1,3 / 80 m/min = 13 min
        assertEquals(1, GeoUtil.minutaHoda(10, 80));
        assertEquals(923.0, GeoUtil.dometM(15, 80), 0.1);
    }

    private static Poi poi(Kategorija k, double lat, double lon) {
        return new Poi(0, "test", k, k.getNaziv(), null, lat, lon, Map.of(), null);
    }
}
