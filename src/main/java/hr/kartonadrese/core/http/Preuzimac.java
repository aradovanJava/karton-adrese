package hr.kartonadrese.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP preuzimanje otvorenih podataka (JDK HttpClient, bez dodatnih ovisnosti).
 *
 * <p>Šalje prepoznatljiv User-Agent s kontaktom (obavezno za Nominatim i Overpass),
 * ponavlja zahtjev kod privremenih pogrešaka i poštuje najmanji razmak između
 * zahtjeva prema istom poslužitelju (npr. Nominatim: najviše 1 zahtjev u sekundi).
 */
public class Preuzimac {

    private final HttpClient klijent;
    private final String userAgent;
    private final Duration vrijemeCekanja;
    private final Map<String, Long> minRazmakMs = new ConcurrentHashMap<>();
    private final Map<String, Long> zadnjiZahtjev = new ConcurrentHashMap<>();

    public Preuzimac(String userAgent, Duration vrijemeCekanja) {
        this.userAgent = userAgent;
        this.vrijemeCekanja = vrijemeCekanja;
        this.klijent = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .proxy(ProxySelector.getDefault())
                .build();
    }

    /** Najmanji razmak između zahtjeva prema poslužitelju (npr. "nominatim.openstreetmap.org"). */
    public void ograniciBrzinu(String host, long razmakMs) {
        minRazmakMs.put(host, razmakMs);
    }

    public byte[] preuzmi(String url) throws IOException {
        return posalji(HttpRequest.newBuilder(URI.create(url)).GET(), HttpResponse.BodyHandlers.ofByteArray(), url);
    }

    public String preuzmiTekst(String url) throws IOException {
        return new String(preuzmi(url), StandardCharsets.UTF_8);
    }

    /** Preuzimanje velike datoteke izravno na disk (npr. GTFS ZIP). */
    public Path preuzmiUDatoteku(String url, Path odrediste) throws IOException {
        return posalji(HttpRequest.newBuilder(URI.create(url)).GET(), HttpResponse.BodyHandlers.ofFile(odrediste), url);
    }

    /** Dio datoteke dohvaćen HTTP rasponom: podaci, položaj prvog bajta i ukupna veličina datoteke. */
    public record Dio(byte[] podaci, long pocetak, long ukupno) {
    }

    /**
     * Dohvat raspona bajtova ({@code Range: bytes=…}). Vraća {@code null} ako poslužitelj ne podržava
     * raspone (odgovori cijelom datotekom), bez čitanja tijela odgovora.
     *
     * @param raspon npr. {@code "bytes=-65536"} (zadnjih 64 KB) ili {@code "bytes=100-199"}
     */
    public Dio preuzmiRaspon(String url, String raspon) throws IOException {
        Tok t = otvoriRaspon(url, raspon);
        if (t == null) {
            return null;
        }
        try (InputStream tok = t.tok()) {
            return new Dio(tok.readAllBytes(), t.pocetak(), t.ukupno());
        }
    }

    /** Tok podataka za raspon bajtova [od, od + duljina), ili {@code null} ako raspon nije podržan. */
    public InputStream otvoriRaspon(String url, long od, long duljina) throws IOException {
        Tok t = otvoriRaspon(url, "bytes=" + od + "-" + (od + duljina - 1));
        return t == null ? null : t.tok();
    }

    private record Tok(InputStream tok, long pocetak, long ukupno) {
    }

    private static final java.util.regex.Pattern CONTENT_RANGE =
            java.util.regex.Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+|\\*)");

    private Tok otvoriRaspon(String url, String raspon) throws IOException {
        HttpRequest zahtjev = HttpRequest.newBuilder(URI.create(url)).GET()
                .header("User-Agent", userAgent)
                .header("Range", raspon)
                .timeout(vrijemeCekanja)
                .build();
        pricekajRed(zahtjev.uri().getHost());
        HttpResponse<InputStream> odgovor;
        try {
            odgovor = klijent.send(zahtjev, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Prekinuto preuzimanje " + url, e);
        }
        if (odgovor.statusCode() != 206) {
            odgovor.body().close();
            if (odgovor.statusCode() == 200) {
                return null;
            }
            throw new IOException("HTTP " + odgovor.statusCode() + " za " + url + " (" + raspon + ")");
        }
        // Content-Range: bytes 500-999/1234
        java.util.regex.Matcher m = CONTENT_RANGE.matcher(odgovor.headers().firstValue("Content-Range").orElse(""));
        if (!m.find()) {
            odgovor.body().close();
            throw new IOException("Neispravno zaglavlje Content-Range za " + url);
        }
        long ukupno = "*".equals(m.group(3)) ? -1 : Long.parseLong(m.group(3));
        return new Tok(odgovor.body(), Long.parseLong(m.group(1)), ukupno);
    }

    /** POST obrasca (application/x-www-form-urlencoded), npr. upit prema Overpass API-ju. */
    public String posaljiObrazac(String url, Map<String, String> polja) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(upit(polja)));
        return new String(posalji(b, HttpResponse.BodyHandlers.ofByteArray(), url), StandardCharsets.UTF_8);
    }

    /** Gradi query string s ispravnim kodiranjem (UTF-8). */
    public static String upit(Map<String, String> parametri) {
        StringJoiner sj = new StringJoiner("&");
        parametri.forEach((k, v) -> sj.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8)));
        return sj.toString();
    }

    private <T> T posalji(HttpRequest.Builder b, HttpResponse.BodyHandler<T> rukovatelj, String url) throws IOException {
        HttpRequest zahtjev = b.header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .timeout(vrijemeCekanja)
                .build();
        IOException zadnja = null;
        for (int pokusaj = 1; pokusaj <= 3; pokusaj++) {
            pricekajRed(zahtjev.uri().getHost());
            try {
                HttpResponse<T> odgovor = klijent.send(zahtjev, rukovatelj);
                int s = odgovor.statusCode();
                if (s >= 200 && s < 300) {
                    return odgovor.body();
                }
                zadnja = new IOException("HTTP " + s + " za " + url);
                if (s != 429 && s < 500) {
                    break; // trajna pogreška (404, 403 …) – nema smisla ponavljati
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Prekinuto preuzimanje " + url, e);
            } catch (IOException e) {
                zadnja = e;
            }
            spavaj(2000L * pokusaj);
        }
        throw zadnja;
    }

    private void pricekajRed(String host) {
        Long razmak = minRazmakMs.get(host);
        if (razmak == null) {
            return;
        }
        synchronized (this) {
            long sada = System.currentTimeMillis();
            long zadnji = zadnjiZahtjev.getOrDefault(host, 0L);
            long cekaj = zadnji + razmak - sada;
            if (cekaj > 0) {
                spavaj(cekaj);
            }
            zadnjiZahtjev.put(host, System.currentTimeMillis());
        }
    }

    private static void spavaj(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
