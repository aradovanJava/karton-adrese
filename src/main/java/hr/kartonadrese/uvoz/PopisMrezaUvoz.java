package hr.kartonadrese.uvoz;

import hr.kartonadrese.config.IzvorKonfiguracija;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.http.Preuzimac;
import hr.kartonadrese.core.uvoz.PopisMrezaCitac;
import hr.kartonadrese.core.uvoz.ZipImenik;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Popis stanovništva 2021. na mreži 1 km² iz Eurostatove „Census grid 2021” (CSV: GRD_ID, T, …).
 *
 * <p>Izvor ({@code url}) može biti:
 * <ul>
 *   <li>ZIP na poslužitelju – iz njega se HTTP rasponima preuzima samo traženi CSV ({@code upit} = naziv
 *       datoteke u arhivi); ako poslužitelj ne podržava raspone, preuzima se cijela arhiva;</li>
 *   <li>CSV (ili .csv.gz) na poslužitelju;</li>
 *   <li>{@code classpath:…} – isječak priložen uz aplikaciju (npr. za rad bez interneta).</li>
 * </ul>
 * Čitaju se samo ćelije unutar područja iz {@code karton.podrucje}.
 */
@Component
public class PopisMrezaUvoz {

    private static final Logger LOG = LoggerFactory.getLogger(PopisMrezaUvoz.class);
    private static final int KRAJ_ARHIVE = 128 * 1024;

    private final Preuzimac preuzimac;
    private final KartonSvojstva svojstva;

    public PopisMrezaUvoz(Preuzimac preuzimac, KartonSvojstva svojstva) {
        this.preuzimac = preuzimac;
        this.svojstva = svojstva;
    }

    public PopisWfsUvoz.Mreza uvezi(IzvorKonfiguracija k) throws IOException {
        String url = k.url();
        if (url == null || url.isBlank()) {
            throw new IOException("Izvor " + k.id() + " nema zadan url");
        }
        String opis;
        List<PopisMrezaCitac.Celija> celije;
        if (url.startsWith("classpath:")) {
            String put = url.substring("classpath:".length());
            try (InputStream in = new ClassPathResource(put).getInputStream()) {
                celije = procitaj(put.endsWith(".gz") ? new GZIPInputStream(in) : in);
            }
            opis = put;
        } else if (url.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            ZipRezultat z = izArhive(url, k.upit());
            celije = z.celije();
            opis = z.clan();
        } else {
            Path dat = Files.createTempFile("popis-", ".csv");
            try {
                preuzimac.preuzmiUDatoteku(url, dat);
                try (InputStream in = Files.newInputStream(dat)) {
                    celije = procitaj(url.endsWith(".gz") ? new GZIPInputStream(in) : in);
                }
            } finally {
                Files.deleteIfExists(dat);
            }
            opis = url.substring(url.lastIndexOf('/') + 1);
        }
        Map<String, double[]> sredista = new HashMap<>();
        Map<String, Integer> stanovnika = new HashMap<>();
        for (PopisMrezaCitac.Celija c : celije) {
            sredista.put(c.id(), new double[] {c.lat(), c.lon()});
            stanovnika.merge(c.id(), c.stanovnika(), Integer::sum);
        }
        LOG.info("Popis 2021.: {} ćelija sa stanovništvom, ukupno {} stanovnika ({})", celije.size(),
                stanovnika.values().stream().mapToLong(Integer::longValue).sum(), opis);
        return new PopisWfsUvoz.Mreza(sredista, stanovnika, opis);
    }

    private record ZipRezultat(List<PopisMrezaCitac.Celija> celije, String clan) {
    }

    private ZipRezultat izArhive(String url, String trazeni) throws IOException {
        Preuzimac.Dio kraj = preuzimac.preuzmiRaspon(url, "bytes=-" + KRAJ_ARHIVE);
        if (kraj != null && kraj.ukupno() > 0) {
            ZipImenik.Raspon raspon = (od, duljina) -> {
                Preuzimac.Dio d = preuzimac.preuzmiRaspon(url, "bytes=" + od + "-" + (od + duljina - 1));
                if (d == null) {
                    throw new IOException("Poslužitelj je prestao podržavati raspone: " + url);
                }
                return d.podaci();
            };
            List<ZipImenik.Clan> clanovi = ZipImenik.clanovi(kraj.ukupno(), kraj.podaci(), raspon);
            ZipImenik.Clan c = odaberi(clanovi.stream().map(x -> new Kandidat<>(x.naziv(), x.izvorno(), x)).toList(),
                    trazeni).izvor();
            if (c.metoda() != 0 && c.metoda() != 8) {
                throw new IOException("Nepodržana metoda sažimanja " + c.metoda() + " za " + c.naziv());
            }
            long pocetak = ZipImenik.pocetakPodataka(c, raspon);
            LOG.info("Popis 2021.: iz {} preuzimam samo {} ({} MB sažeto)", url, c.naziv(), c.sazeto() / 1_000_000);
            try (InputStream tok = preuzimac.otvoriRaspon(url, pocetak, c.sazeto())) {
                if (tok == null) {
                    throw new IOException("Poslužitelj je prestao podržavati raspone: " + url);
                }
                InputStream podaci = c.metoda() == 8 ? new InflaterInputStream(tok, new Inflater(true), 1 << 16) : tok;
                return new ZipRezultat(procitaj(podaci), c.naziv());
            }
        }
        LOG.info("Poslužitelj ne podržava HTTP raspone – preuzimam cijelu arhivu {}", url);
        Path dat = Files.createTempFile("popis-", ".zip");
        try {
            preuzimac.preuzmiUDatoteku(url, dat);
            try (ZipFile zip = new ZipFile(dat.toFile())) {
                ZipEntry e = odaberi(zip.stream().map(x -> new Kandidat<>(x.getName(), x.getSize(), x)).toList(),
                        trazeni).izvor();
                try (InputStream in = zip.getInputStream(e)) {
                    return new ZipRezultat(procitaj(in), e.getName());
                }
            }
        } finally {
            Files.deleteIfExists(dat);
        }
    }

    private record Kandidat<T>(String naziv, long velicina, T izvor) {
    }

    /** Traženi član po nazivu (završetak puta), inače najveći CSV u arhivi. */
    private static <T> Kandidat<T> odaberi(List<Kandidat<T>> kandidati, String trazeni) throws IOException {
        if (trazeni != null && !trazeni.isBlank()) {
            return kandidati.stream().filter(c -> c.naziv().endsWith(trazeni)).findFirst()
                    .orElseThrow(() -> new IOException("U arhivi nema datoteke " + trazeni + " (ima: "
                            + kandidati.stream().map(Kandidat::naziv).toList() + ")"));
        }
        return kandidati.stream().filter(c -> c.naziv().toLowerCase(Locale.ROOT).endsWith(".csv"))
                .max(Comparator.comparingLong(Kandidat::velicina))
                .orElseThrow(() -> new IOException("U arhivi nema CSV datoteke"));
    }

    private List<PopisMrezaCitac.Celija> procitaj(InputStream in) throws IOException {
        KartonSvojstva.Podrucje p = svojstva.podrucje();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        return PopisMrezaCitac.procitaj(r, p.minLat(), p.minLon(), p.maxLat(), p.maxLon());
    }
}
