package hr.kartonadrese.core;

import hr.kartonadrese.core.geo.Laea3035;
import hr.kartonadrese.core.uvoz.GeoJsonCitac;
import hr.kartonadrese.core.uvoz.PopisMrezaCitac;
import hr.kartonadrese.core.uvoz.PrepoznavanjePolja;
import hr.kartonadrese.core.uvoz.ZipImenik;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Isječci stvarnih skupova (provjereno sondom izvora 9. 11. 2026.) i njihove posebnosti. */
class StvarniIzvoriTest {

    /** Zborna mjesta civilne zaštite Grada Zagreba: naziv je u svojstvu „zboriste”, adrese nema. */
    private static final String ZBORNA_MJESTA = """
            {"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"EPSG:4326"}},"features":[
            {"type":"Feature","id":1,"geometry":{"type":"Point","coordinates":[15.9807010621406,45.8157108589494]},
             "properties":{"OBJECTID":1,"gradska_ce":"Gornji Grad-Medveščak","zboriste":"Park Ribnjak"}},
            {"type":"Feature","id":5,"geometry":{"type":"Point","coordinates":[16.0342159133018,45.8190329724061]},
             "properties":{"OBJECTID":5,"gradska_ce":"Maksimir","zboriste":"Tenis centar Maksimir"}},
            {"type":"Feature","id":17,"geometry":{"type":"Point","coordinates":[16.1056900533052,45.8306765852849]},
             "properties":{"OBJECTID":17,"gradska_ce":"Sesvete","zboriste":"OŠ Luka"}}]}""";

    @Test
    void zbornaMjestaTrazeSvojstvoZboriste() {
        PrepoznavanjePolja.Rezultat bez = GeoJsonCitac.procitaj(ZBORNA_MJESTA);
        assertEquals(0, bez.zapisi().size(), "bez mapiranja nijedan zapis nema naziv");
        assertEquals(3, bez.bezNaziva());

        PrepoznavanjePolja.Rezultat s = GeoJsonCitac.procitaj(ZBORNA_MJESTA, "zboriste");
        assertEquals(3, s.zapisi().size());
        assertEquals("Tenis centar Maksimir", s.zapisi().get(1).naziv());
        assertEquals(45.81903, s.zapisi().get(1).lat(), 1e-5);
        assertEquals("Maksimir", s.zapisi().get(1).atributi().get("gradska_ce"));
    }

    /** Eurostat Census grid 2021 (V3, 30. 5. 2026.): zaglavlje i stvarni retci za okolicu Gorjanovićeve ulice. */
    private static final String POPIS = """
            GRD_ID,T,M,F,Y_LT15,Y_1564,Y_GE65,EMP,NAT,EU_OTH,OTH,SAME,CHG_IN,CHG_OUT,LAND_SURFACE,POPULATED,CNTR_ID
            CRS3035RES1000mN2683000E4285000,0,0,0,0,0,0,0,0,0,0,0,0,0,0.9471130000000001,0,AT-CH-LI
            CRS3035RES1000mN2684000E4285000,25,14,12,6,18,3,2,22,0,1,23,0,0,0.903667,1,AT-CH-LI
            %s
            """;

    @Test
    void popisNaMreziCitaSamoCelijePodrucja() throws Exception {
        String csv = POPIS.formatted(String.join("\n", RETCI_ZAGREB));
        List<PopisMrezaCitac.Celija> c = PopisMrezaCitac.procitaj(new BufferedReader(new StringReader(csv)),
                45.62, 15.77, 45.97, 16.24);
        assertEquals(3, c.size(), "austrijske ćelije i ćelije bez stanovnika se preskaču");
        String adresa = Laea3035.oznakaCelije(45.8186, 16.0048, 1000);
        PopisMrezaCitac.Celija gorjanoviceva = c.stream().filter(x -> x.id().equals(adresa)).findFirst().orElseThrow();
        assertEquals(11022, gorjanoviceva.stanovnika());
        assertEquals(45.8169, gorjanoviceva.lat(), 1e-3);
    }

    @Test
    void izZipaSeCitaSamoTrazeniClanRasponima() throws Exception {
        String csv = POPIS.formatted(String.join("\n", RETCI_ZAGREB));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("Eurostat_Census-GRID_2021_V3/read.me"));
            z.write("opis".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("Eurostat_Census-GRID_2021_V3/ESTAT_Census_2021_V3.csv"));
            z.write(csv.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("Eurostat_Census-GRID_2021_V3/ESTAT_Census_2021_V3.gpkg"));
            z.write(new byte[20_000]);
            z.closeEntry();
        }
        byte[] zip = bos.toByteArray();
        int[] dohvata = {0};
        ZipImenik.Raspon raspon = (od, n) -> {
            dohvata[0]++;
            return Arrays.copyOfRange(zip, (int) od, (int) (od + n));
        };
        byte[] kraj = Arrays.copyOfRange(zip, zip.length - 300, zip.length);
        List<ZipImenik.Clan> clanovi = ZipImenik.clanovi(zip.length, kraj, raspon);
        assertEquals(3, clanovi.size());
        ZipImenik.Clan clan = clanovi.stream().filter(x -> x.naziv().endsWith("ESTAT_Census_2021_V3.csv")).findFirst()
                .orElseThrow();
        long pocetak = ZipImenik.pocetakPodataka(clan, raspon);
        assertTrue(pocetak + clan.sazeto() < zip.length);
        try (InflaterInputStream in = new InflaterInputStream(
                new ByteArrayInputStream(zip, (int) pocetak, (int) clan.sazeto()), new Inflater(true))) {
            List<PopisMrezaCitac.Celija> c = PopisMrezaCitac.procitaj(
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), 45.62, 15.77, 45.97, 16.24);
            assertEquals(3, c.size());
        }
    }

    private static final List<String> RETCI_ZAGREB = List.of(
            "CRS3035RES1000mN2541000E4786000,5665,2599,3065,735,3346,1584,2569,5009,96,560,5536,36,36,1.0,1,HR",
            "CRS3035RES1000mN2540000E4787000,7428,3279,4147,1138,4784,1505,3675,6468,137,822,7228,89,42,1.0,1,HR",
            "CRS3035RES1000mN2541000E4787000,11022,4963,6065,1400,6735,2889,5059,9675,206,1137,10772,87,68,1.0,1,HR",
            "CRS3035RES1000mN2542000E4787000,0,0,0,0,0,0,0,0,0,0,0,0,0,1.0,0,HR");
}
