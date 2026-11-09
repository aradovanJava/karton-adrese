package hr.kartonadrese.core.uvoz;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Čitanje središnjeg imenika ZIP datoteke na udaljenom poslužitelju pomoću HTTP raspona (Range).
 *
 * <p>Služi za izdvajanje jedne datoteke iz velike arhive bez preuzimanja cijele arhive: npr. iz
 * Eurostatove mreže popisa 2021. (≈ 570 MB) preuzima se samo CSV (≈ 50 MB sažeto). Podržava ZIP64.
 */
public final class ZipImenik {

    private static final int EOCD = 0x06054b50;
    private static final int ZIP64_LOKATOR = 0x07064b50;
    private static final int ZIP64_EOCD = 0x06064b50;
    private static final int SREDISNJI = 0x02014b50;
    private static final int LOKALNI = 0x04034b50;
    private static final long MAX32 = 0xFFFFFFFFL;

    private ZipImenik() {
    }

    /** Dohvat raspona bajtova [od, od + duljina) s poslužitelja. */
    @FunctionalInterface
    public interface Raspon {
        byte[] dohvati(long od, int duljina) throws IOException;
    }

    /** Član arhive: naziv, metoda sažimanja (0 = bez, 8 = deflate), veličine i položaj lokalnog zaglavlja. */
    public record Clan(String naziv, int metoda, long sazeto, long izvorno, long lokalnoZaglavlje) {
    }

    /**
     * Čita članove arhive.
     *
     * @param ukupno ukupna veličina arhive u bajtovima (iz zaglavlja Content-Range)
     * @param kraj   zadnjih nekoliko desetaka KB arhive (mora sadržavati zapis EOCD)
     */
    public static List<Clan> clanovi(long ukupno, byte[] kraj, Raspon raspon) throws IOException {
        long pocetakKraja = ukupno - kraj.length;
        int e = -1;
        for (int i = kraj.length - 22; i >= 0; i--) {
            if (u32(kraj, i) == EOCD) {
                e = i;
                break;
            }
        }
        if (e < 0) {
            throw new IOException("ZIP: nije pronađen kraj središnjeg imenika");
        }
        long brojUnosa = u16(kraj, e + 10);
        long velicina = u32(kraj, e + 12);
        long pomak = u32(kraj, e + 16);
        if (pomak == MAX32 || velicina == MAX32 || brojUnosa == 0xFFFF) {
            int l = e - 20;
            if (l < 0 || u32(kraj, l) != ZIP64_LOKATOR) {
                throw new IOException("ZIP64: nije pronađen lokator");
            }
            long zapis64 = u64(kraj, l + 8);
            byte[] z = zapis64 >= pocetakKraja
                    ? java.util.Arrays.copyOfRange(kraj, (int) (zapis64 - pocetakKraja), (int) (zapis64 - pocetakKraja) + 56)
                    : raspon.dohvati(zapis64, 56);
            if (u32(z, 0) != ZIP64_EOCD) {
                throw new IOException("ZIP64: neispravan zapis kraja imenika");
            }
            velicina = u64(z, 40);
            pomak = u64(z, 48);
        }
        byte[] imenik = pomak >= pocetakKraja
                ? java.util.Arrays.copyOfRange(kraj, (int) (pomak - pocetakKraja), (int) (pomak - pocetakKraja + velicina))
                : raspon.dohvati(pomak, (int) velicina);
        List<Clan> clanovi = new ArrayList<>();
        int i = 0;
        while (i + 46 <= imenik.length && u32(imenik, i) == SREDISNJI) {
            int metoda = (int) u16(imenik, i + 10);
            long sazeto = u32(imenik, i + 20);
            long izvorno = u32(imenik, i + 24);
            int duljinaNaziva = (int) u16(imenik, i + 28);
            int duljinaDodatka = (int) u16(imenik, i + 30);
            int duljinaKomentara = (int) u16(imenik, i + 32);
            long lokalno = u32(imenik, i + 42);
            String naziv = new String(imenik, i + 46, duljinaNaziva, StandardCharsets.UTF_8);
            int d = i + 46 + duljinaNaziva;
            int krajDodatka = d + duljinaDodatka;
            while (d + 4 <= krajDodatka) {
                int id = (int) u16(imenik, d);
                int vel = (int) u16(imenik, d + 2);
                if (id == 0x0001) {
                    int p = d + 4;
                    if (izvorno == MAX32) {
                        izvorno = u64(imenik, p);
                        p += 8;
                    }
                    if (sazeto == MAX32) {
                        sazeto = u64(imenik, p);
                        p += 8;
                    }
                    if (lokalno == MAX32) {
                        lokalno = u64(imenik, p);
                    }
                }
                d += 4 + vel;
            }
            clanovi.add(new Clan(naziv, metoda, sazeto, izvorno, lokalno));
            i = krajDodatka + duljinaKomentara;
        }
        return clanovi;
    }

    /** Položaj prvog bajta sažetih podataka člana (iza lokalnog zaglavlja). */
    public static long pocetakPodataka(Clan c, Raspon raspon) throws IOException {
        byte[] z = raspon.dohvati(c.lokalnoZaglavlje(), 30);
        if (u32(z, 0) != LOKALNI) {
            throw new IOException("ZIP: neispravno lokalno zaglavlje za " + c.naziv());
        }
        return c.lokalnoZaglavlje() + 30 + u16(z, 26) + u16(z, 28);
    }

    private static long u16(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8;
    }

    private static long u32(byte[] b, int i) {
        return (b[i] & 0xFFL) | (b[i + 1] & 0xFFL) << 8 | (b[i + 2] & 0xFFL) << 16 | (b[i + 3] & 0xFFL) << 24;
    }

    private static long u64(byte[] b, int i) {
        return u32(b, i) | u32(b, i + 4) << 32;
    }
}
