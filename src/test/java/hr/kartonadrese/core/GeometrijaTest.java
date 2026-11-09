package hr.kartonadrese.core;

import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.geo.Geometrija;
import hr.kartonadrese.core.model.Interes;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;
import hr.kartonadrese.core.model.Postavke;
import hr.kartonadrese.core.model.Potreba;
import hr.kartonadrese.core.model.StanjePristupacnosti;
import hr.kartonadrese.core.uvoz.GeoJsonCitac;
import hr.kartonadrese.core.uvoz.PrepoznavanjePolja;
import hr.kartonadrese.core.uvoz.UvozniZapis;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Udaljenost do ruba linija i površina, prikaz u metrima, interesi i potrebe. */
class GeometrijaTest {

    private static final Geometrija KVADRAT = new Geometrija(Geometrija.Tip.POVRSINA, List.<double[][]>of(
            new double[][] {{45.82, 16.00}, {45.82, 16.01}, {45.83, 16.01}, {45.83, 16.00}, {45.82, 16.00}}));
    private static final Geometrija LINIJA = new Geometrija(Geometrija.Tip.LINIJA, List.<double[][]>of(
            new double[][] {{45.82, 16.00}, {45.82, 16.02}}));

    @Test
    void udaljenostDoRubaPovrsine() {
        assertEquals(0, KVADRAT.udaljenostM(45.825, 16.005), 1e-9, "točka unutar parka");
        // 0,0009° geografske širine ≈ 100 m sjeverno od ruba
        assertEquals(100, KVADRAT.udaljenostM(45.8309, 16.005), 1.5);
        assertTrue(KVADRAT.udaljenostM(45.8309, 16.005) < GeoUtil.udaljenostM(45.8309, 16.005, 45.825, 16.005),
                "do ruba je bliže nego do središta");
    }

    @Test
    void udaljenostDoLinijeIZapis() {
        assertEquals(111, LINIJA.udaljenostM(45.821, 16.01), 1.5);
        Geometrija procitana = Geometrija.izJsona(LINIJA.uJson());
        assertNotNull(procitana);
        assertEquals(Geometrija.Tip.LINIJA, procitana.tip());
        assertEquals(2, procitana.brojTocaka());
        assertNull(Geometrija.izJsona("nije json"));
        Poi staza = new Poi(1, "x", Kategorija.BICIKLISTICKA_STAZA, "Staza", null, 45.82, 16.01, Map.of(), null, LINIJA);
        assertEquals(111, staza.udaljenostOdM(45.821, 16.005), 1.5);
    }

    @Test
    void prikazUdaljenostiUMetrima() {
        assertEquals("260 m", GeoUtil.formatiraj(258));
        assertEquals("0 m", GeoUtil.formatiraj(0));
        assertEquals("1,0 km", GeoUtil.formatiraj(996));
        assertEquals("2,1 km", GeoUtil.formatiraj(2111));
        assertEquals(923, GeoUtil.dometM(15, 80), 1, "prag indeksa 15 minuta za opći profil");
    }

    @Test
    void geoJsonLinijeIPovrsineZadrzavajuGeometriju() {
        String gj = """
                {"type":"FeatureCollection","features":[
                 {"type":"Feature","properties":{"Naziv":"2023./2024. Odmorko","lokacija":"ŠRC Šalata"},
                  "geometry":{"type":"Point","coordinates":[15.98,45.81]}},
                 {"type":"Feature","properties":{"lokacija":"Maksimirska cesta - jug"},
                  "geometry":{"type":"MultiLineString","coordinates":[[[16.0,45.82],[16.01,45.82]]]}},
                 {"type":"Feature","properties":{"naziv":"Gradski vrt Mandlova"},
                  "geometry":{"type":"MultiPolygon","coordinates":[[[[16.0,45.82],[16.01,45.82],[16.01,45.83],[16.0,45.82]]]]}}]}""";
        List<UvozniZapis> z = GeoJsonCitac.procitaj(gj, "lokacija").zapisi();
        assertEquals("ŠRC Šalata", z.get(0).naziv(), "zadano svojstvo naziva ima prednost");
        assertNull(z.get(0).geometrija());
        assertEquals(Geometrija.Tip.LINIJA, z.get(1).geometrija().tip());
        assertEquals(Geometrija.Tip.POVRSINA, z.get(2).geometrija().tip());
        assertEquals(45.82, z.get(1).lat(), 1e-9);
    }

    @Test
    void nazivVelikimSlovimaUUobicajenomPravopisu() {
        assertEquals("Osnovna škola Vladimira Nazora", PrepoznavanjePolja.uredniNaziv("OSNOVNA ŠKOLA VLADIMIRA NAZORA"));
        assertEquals("Centar za odgoj i obrazovanje \"Goljak\"",
                PrepoznavanjePolja.uredniNaziv("CENTAR ZA ODGOJ I OBRAZOVANJE \"GOLJAK\""));
        assertEquals("Ekonomski fakultet", PrepoznavanjePolja.uredniNaziv("Ekonomski fakultet"));
    }

    @Test
    void postavkeIzPoveznice() {
        Postavke p = Postavke.iz("12", List.of("menza,bicikl", "nepoznato"), List.of("kolica"));
        assertEquals(12L, p.fakultetId());
        assertTrue(p.imaInteres(Interes.MENZA) && p.imaInteres(Interes.BICIKL));
        assertTrue(p.imaPotrebu(Potreba.KOLICA));
        assertEquals("menza,bicikl", p.interesiParam());
        assertNull(Postavke.iz("abc", null, null).fakultetId());
        assertEquals(StanjePristupacnosti.DJELOMICNO, StanjePristupacnosti.izOsm("limited"));
        assertEquals(StanjePristupacnosti.NEMA_PODATKA, StanjePristupacnosti.izOsm(null));
    }
}
