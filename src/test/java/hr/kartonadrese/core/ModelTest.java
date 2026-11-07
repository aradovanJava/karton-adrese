package hr.kartonadrese.core;

import hr.kartonadrese.core.ml.MetapodaciModela;
import hr.kartonadrese.core.ml.SezonskaProcjena;
import hr.kartonadrese.core.ml.Znacajke;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Značajke u Javi moraju biti iste kao u Pythonu (ml/karton_ml/znacajke.py). */
class ModelTest {

    private static final Path METAPODACI = Path.of("model/djordjiceva/metapodaci.json");

    @Test
    void kalendarskeZnacajkeKaoUPythonu() {
        LocalDate d = LocalDate.of(2026, 11, 7); // subota, 311. dan
        assertEquals(Math.sin(2 * Math.PI * 311 / 365.25), Znacajke.kalendarska("dan_sin", d), 1e-12);
        assertEquals(11.0, Znacajke.kalendarska("mjesec", d));
        assertEquals(5.0, Znacajke.kalendarska("dan_u_tjednu", d));
        assertEquals(1.0, Znacajke.kalendarska("vikend", d));
        assertEquals(1.0, Znacajke.kalendarska("sezona_grijanja", d));
        assertEquals(0.0, Znacajke.kalendarska("sezona_grijanja", LocalDate.of(2026, 7, 1)));
        assertNull(Znacajke.kalendarska("t2m_sr", d));
    }

    @Test
    void metapodaciIMatricaTipicneGodine() throws Exception {
        MetapodaciModela meta = MetapodaciModela.izJsona(Files.readString(METAPODACI));
        assertEquals(366, meta.klimatologija().size());
        assertEquals(12, meta.kontrolniProfil().size());
        float[][] x = Znacajke.matricaTipicneGodine(meta, meta.kontrolnaGodina());
        assertEquals(LocalDate.of(meta.kontrolnaGodina(), 1, 1).lengthOfYear(), x.length);
        assertEquals(meta.znacajke().size(), x[0].length);
    }

    @Test
    void sezonskaProcjenaPrimjenjujePojasIZaokruzuje() throws Exception {
        MetapodaciModela meta = MetapodaciModela.izJsona(Files.readString(METAPODACI));
        // prediktor koji uvijek vraća log1p(20) daje 20 µg/m³ u svakom mjesecu
        SezonskaProcjena.Prediktor konstanta = x -> {
            float[] p = new float[x.length];
            java.util.Arrays.fill(p, (float) Math.log1p(20));
            return p;
        };
        List<SezonskaProcjena.Mjesec> r = SezonskaProcjena.izracunaj(meta, 2027, konstanta);
        assertEquals(12, r.size());
        for (SezonskaProcjena.Mjesec m : r) {
            assertEquals(20.0, m.pm10(), 0.01);
            assertTrue(m.donja() <= m.pm10() && m.pm10() <= m.gornja());
        }
    }
}
