package hr.kartonadrese.analitika;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KartonServisTest {

    @Test
    void hrvatskaMnozinaLinija() {
        assertEquals("linija", KartonServis.linija(1));
        assertEquals("linije", KartonServis.linija(3));
        assertEquals("linija", KartonServis.linija(5));
        assertEquals("linija", KartonServis.linija(12));
        assertEquals("linije", KartonServis.linija(22));
        assertEquals("linija", KartonServis.linija(21));
    }
}
