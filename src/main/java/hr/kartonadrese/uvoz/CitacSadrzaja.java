package hr.kartonadrese.uvoz;

import hr.kartonadrese.config.IzvorKonfiguracija;
import hr.kartonadrese.core.tablica.CsvCitac;
import hr.kartonadrese.core.tablica.Tablica;
import hr.kartonadrese.core.uvoz.GeoJsonCitac;
import hr.kartonadrese.core.uvoz.PrepoznavanjePolja;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

/** Pretvara preuzeti sadržaj (GeoJSON, CSV, XLSX) u uvozne zapise (sintaktička interoperabilnost). */
final class CitacSadrzaja {

    private CitacSadrzaja() {
    }

    static UvozniRezultat procitaj(byte[] podaci, String format, IzvorKonfiguracija k, LocalDate datum)
            throws IOException {
        String f = format == null ? "" : format.toUpperCase(Locale.ROOT);
        PrepoznavanjePolja.Rezultat r;
        if (f.contains("JSON") || pocinjeVitickom(podaci)) {
            r = GeoJsonCitac.procitaj(new String(podaci, StandardCharsets.UTF_8));
        } else {
            Tablica t = tablica(podaci, f);
            r = PrepoznavanjePolja.izTablice(t, new PrepoznavanjePolja.Mapiranje(k.stupacNaziv(), k.stupacAdresa(),
                    k.stupacLat(), k.stupacLon()));
        }
        int procitano = r.zapisi().size() + r.bezNaziva() + r.bezLokacije();
        return new UvozniRezultat(r.zapisi(), procitano, r.bezNaziva(), r.bezLokacije(), datum,
                "format " + (f.isEmpty() ? "?" : f) + "; " + r.opisMapiranja());
    }

    static Tablica tablica(byte[] podaci, String format) throws IOException {
        if (format.startsWith("XLS") || jeZip(podaci) || jeOle(podaci)) {
            return XlsxCitac.procitaj(podaci);
        }
        return CsvCitac.procitaj(podaci);
    }

    private static boolean pocinjeVitickom(byte[] p) {
        for (byte b : p) {
            if (b == '{' || b == '[') {
                return true;
            }
            if (!Character.isWhitespace(b) && b != (byte) 0xEF && b != (byte) 0xBB && b != (byte) 0xBF) {
                return false;
            }
        }
        return false;
    }

    private static boolean jeZip(byte[] p) {
        return p.length > 3 && p[0] == 'P' && p[1] == 'K';
    }

    private static boolean jeOle(byte[] p) {
        return p.length > 7 && (p[0] & 0xFF) == 0xD0 && (p[1] & 0xFF) == 0xCF;
    }
}
