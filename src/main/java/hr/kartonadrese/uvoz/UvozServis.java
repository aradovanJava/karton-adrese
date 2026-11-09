package hr.kartonadrese.uvoz;

import hr.kartonadrese.analitika.Indeks15Servis;
import hr.kartonadrese.baza.AdresaRepozitorij;
import hr.kartonadrese.baza.CelijaRepozitorij;
import hr.kartonadrese.baza.GranicaRepozitorij;
import hr.kartonadrese.baza.IzvorRepozitorij;
import hr.kartonadrese.baza.PoiRepozitorij;
import hr.kartonadrese.baza.StajalisteRepozitorij;
import hr.kartonadrese.config.IzvorKonfiguracija;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.config.VrstaIzvora;
import hr.kartonadrese.core.geo.Poligon;
import hr.kartonadrese.core.gtfs.GtfsCitac;
import hr.kartonadrese.core.http.Preuzimac;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;
import hr.kartonadrese.core.model.Stajaliste;
import hr.kartonadrese.core.tablica.Tablica;
import hr.kartonadrese.core.uvoz.GeoJsonCitac;
import hr.kartonadrese.core.uvoz.PrepoznavanjePolja;
import hr.kartonadrese.core.uvoz.UvozniZapis;
import hr.kartonadrese.geokod.Geokoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sloj prikupljanja i usklađivanja: za svaki izvor iz konfiguracije preuzima podatke,
 * prepoznaje stupce i koordinate, geokodira objekte bez koordinata, odbacuje objekte izvan
 * područja i duplikate, sprema ih i bilježi izvješće o kvaliteti. Na kraju računa indeks
 * grada 15 minuta.
 */
@Service
public class UvozServis {

    private static final Logger LOG = LoggerFactory.getLogger(UvozServis.class);

    private final KartonSvojstva svojstva;
    private final CkanKlijent ckan;
    private final OverpassUvoz overpass;
    private final PopisWfsUvoz popis;
    private final PopisMrezaUvoz popisMreza;
    private final Preuzimac preuzimac;
    private final Geokoder geokoder;
    private final IzvorRepozitorij izvori;
    private final PoiRepozitorij poi;
    private final StajalisteRepozitorij stajalista;
    private final CelijaRepozitorij celije;
    private final GranicaRepozitorij granice;
    private final AdresaRepozitorij adrese;
    private final Indeks15Servis indeks15;
    private final AtomicBoolean uTijeku = new AtomicBoolean(false);

    public UvozServis(KartonSvojstva svojstva, CkanKlijent ckan, OverpassUvoz overpass, PopisWfsUvoz popis,
                      PopisMrezaUvoz popisMreza, Preuzimac preuzimac, Geokoder geokoder, IzvorRepozitorij izvori, PoiRepozitorij poi,
                      StajalisteRepozitorij stajalista, CelijaRepozitorij celije, GranicaRepozitorij granice,
                      AdresaRepozitorij adrese, Indeks15Servis indeks15) {
        this.svojstva = svojstva;
        this.ckan = ckan;
        this.overpass = overpass;
        this.popis = popis;
        this.popisMreza = popisMreza;
        this.preuzimac = preuzimac;
        this.geokoder = geokoder;
        this.izvori = izvori;
        this.poi = poi;
        this.stajalista = stajalista;
        this.celije = celije;
        this.granice = granice;
        this.adrese = adrese;
        this.indeks15 = indeks15;
    }

    /** Sažetak jednog pokretanja uvoza. */
    public record Izvjestaj(int uspjesno, int neuspjesno, List<String> poruke) {
    }

    public List<IzvorKonfiguracija> konfigurirani() {
        return svojstva.izvori() == null ? List.of() : svojstva.izvori();
    }

    /** Registrira sve izvore iz konfiguracije (da se vide na stranici "O podacima" i prije uvoza). */
    public void registrirajIzvore() {
        konfigurirani().forEach(izvori::registriraj);
    }

    @Scheduled(cron = "${karton.uvoz.raspored:-}", zone = "Europe/Zagreb")
    public void planirano() {
        LOG.info("Planirano osvježavanje podataka");
        pokreni(null);
    }

    /**
     * Pokreće uvoz svih omogućenih izvora ili samo jednog (ako je {@code samoId} zadan).
     * Redoslijed: granice i ulice → objekti → GTFS → stanovništvo → indeks 15 minuta.
     */
    public Izvjestaj pokreni(String samoId) {
        if (!uTijeku.compareAndSet(false, true)) {
            return new Izvjestaj(0, 0, List.of("Uvoz je već u tijeku"));
        }
        try {
            registrirajIzvore();
            List<IzvorKonfiguracija> redom = new ArrayList<>(konfigurirani().stream()
                    .filter(IzvorKonfiguracija::jeOmogucen)
                    .filter(k -> samoId == null || samoId.equalsIgnoreCase("sve") || k.id().equals(samoId))
                    .toList());
            redom.sort((x, y) -> Integer.compare(redoslijed(x.vrsta()), redoslijed(y.vrsta())));
            if (redom.isEmpty()) {
                return new Izvjestaj(0, 0, List.of("Nema izvora za uvoz: " + samoId));
            }
            int ok = 0;
            int ko = 0;
            List<String> poruke = new ArrayList<>();
            for (IzvorKonfiguracija k : redom) {
                try {
                    String poruka = uvezi(k);
                    poruke.add(k.id() + ": " + poruka);
                    LOG.info("Uvoz {}: {}", k.id(), poruka);
                    ok++;
                } catch (Exception e) {
                    String poruka = e.getClass().getSimpleName() + ": " + e.getMessage();
                    izvori.zabiljeziPogresku(k.id(), poruka);
                    poruke.add(k.id() + ": POGREŠKA – " + poruka);
                    LOG.warn("Uvoz {} nije uspio: {}", k.id(), poruka);
                    ko++;
                }
            }
            int n = indeks15.izracunajSve();
            poruke.add("Indeks grada 15 minuta: " + n + " ćelija");
            return new Izvjestaj(ok, ko, poruke);
        } finally {
            uTijeku.set(false);
        }
    }

    private static int redoslijed(VrstaIzvora v) {
        if (v == null) {
            return 9;
        }
        return switch (v) {
            case GRANICE -> 0;
            case ULICE -> 1;
            case CKAN, GEOJSON_URL, TABLICA_URL, OVERPASS -> 2;
            case GTFS -> 3;
            case WFS_POPIS, POPIS_MREZA -> 4;
        };
    }

    String uvezi(IzvorKonfiguracija k) throws IOException {
        if (k.vrsta() == null) {
            throw new IOException("Izvor " + k.id() + " nema zadanu vrstu");
        }
        return switch (k.vrsta()) {
            case CKAN, GEOJSON_URL, TABLICA_URL, OVERPASS -> uvoziObjekte(k);
            case GTFS -> uvoziGtfs(k);
            case WFS_POPIS, POPIS_MREZA -> uvoziPopis(k);
            case GRANICE -> uvoziGranice(k);
            case ULICE -> uvoziUlice(k);
        };
    }

    // ------------------------------------------------------------------ objekti (ustanove, trgovine …)

    private UvozniRezultat procitaj(IzvorKonfiguracija k) throws IOException {
        return switch (k.vrsta()) {
            case CKAN -> {
                CkanKlijent.Sadrzaj s = ckan.preuzmi(k);
                yield CitacSadrzaja.procitaj(s.podaci(), s.resurs().format(), k, s.resurs().izmijenjeno());
            }
            case GEOJSON_URL -> CitacSadrzaja.procitaj(preuzimac.preuzmi(k.url()), "GEOJSON", k, LocalDate.now());
            case TABLICA_URL -> CitacSadrzaja.procitaj(preuzimac.preuzmi(k.url()), CkanKlijent.formatIzUrla(k.url()), k,
                    LocalDate.now());
            case OVERPASS -> overpass.uvezi(k);
            default -> throw new IOException("Vrsta " + k.vrsta() + " nije izvor objekata");
        };
    }

    private String uvoziObjekte(IzvorKonfiguracija k) throws IOException {
        if (k.kategorija() == null) {
            throw new IOException("Izvor " + k.id() + " nema zadanu kategoriju");
        }
        UvozniRezultat r = procitaj(k);
        int najviseGeokodiranja = svojstva.geokodiranje() == null ? 0 : svojstva.geokodiranje().najvisePoUvozu();
        boolean geokodiranje = svojstva.geokodiranje() != null && svojstva.geokodiranje().nominatim();
        int geokodirano = 0;
        int bezLokacije = r.bezLokacije();
        int izvan = 0;
        Set<String> vidjeno = new HashSet<>();
        List<Poi> objekti = new ArrayList<>();
        for (UvozniZapis z : r.zapisi()) {
            UvozniZapis zapis = z;
            if (!zapis.imaKoordinate()) {
                if (geokodiranje && geokodirano < najviseGeokodiranja && zapis.adresa() != null) {
                    Optional<double[]> t = geokoder.koordinate(zapis.adresa());
                    if (t.isPresent()) {
                        zapis = zapis.sKoordinatama(t.get()[0], t.get()[1]);
                        geokodirano++;
                    }
                }
                if (!zapis.imaKoordinate()) {
                    bezLokacije++;
                    continue;
                }
            }
            if (!svojstva.podrucje().okvir().sadrzi(zapis.lat(), zapis.lon())) {
                izvan++;
                continue;
            }
            String kljuc = PrepoznavanjePolja.normaliziraj(zapis.naziv()) + "|"
                    + String.format(Locale.ROOT, "%.4f,%.4f", zapis.lat(), zapis.lon());
            if (!vidjeno.add(kljuc)) {
                continue; // duplikat unutar istog izvora
            }
            objekti.add(new Poi(0, k.id(), k.kategorija(), PrepoznavanjePolja.uredniNaziv(zapis.naziv()), zapis.adresa(),
                    zapis.lat(), zapis.lon(), zapis.atributi(), r.datumPodatka(), zapis.geometrija()));
        }
        int spremljeno = poi.zamijeni(k.id(), objekti);
        String poruka = String.format(Locale.ROOT, "pročitano %d, spremljeno %d, bez lokacije %d, geokodirano %d, "
                + "izvan područja %d, bez naziva %d (%s)", r.procitano(), spremljeno, bezLokacije, geokodirano, izvan,
                r.bezNaziva(), r.napomena());
        izvori.zabiljeziUspjeh(k.id(), spremljeno == 0 ? "UPOZORENJE" : "OK", new IzvorRepozitorij.Statistika(
                r.procitano(), spremljeno, bezLokacije, geokodirano, izvan, r.datumPodatka(), poruka));
        return poruka;
    }

    // ------------------------------------------------------------------ javni prijevoz (GTFS)

    private String uvoziGtfs(IzvorKonfiguracija k) throws IOException {
        Path mapa = Path.of(svojstva.uvoz() == null || svojstva.uvoz().privremenaMapa() == null
                ? System.getProperty("java.io.tmpdir") : svojstva.uvoz().privremenaMapa());
        Files.createDirectories(mapa);
        Path zip = mapa.resolve("gtfs-" + k.id() + ".zip");
        preuzimac.preuzmiUDatoteku(k.url(), zip);
        List<Stajaliste> s = GtfsCitac.procitaj(zip).stream()
                .filter(x -> svojstva.podrucje().okvir().sadrzi(x.lat(), x.lon()))
                .toList();
        int n = stajalista.zamijeni(s);
        List<Poi> kaoObjekti = s.stream()
                .map(x -> new Poi(0, k.id(), Kategorija.STAJALISTE, x.naziv(), null, x.lat(), x.lon(),
                        x.linije().isEmpty() ? Map.of() : Map.of("linije", String.join(", ", x.linije()),
                                "vrsta", x.vrsta()), LocalDate.now()))
                .toList();
        poi.zamijeni(k.id(), kaoObjekti);
        String poruka = "stajališta: " + n;
        izvori.zabiljeziUspjeh(k.id(), "OK", new IzvorRepozitorij.Statistika(n, n, 0, 0, 0, LocalDate.now(), poruka));
        return poruka;
    }

    // ------------------------------------------------------------------ stanovništvo (DZS WFS)

    private String uvoziPopis(IzvorKonfiguracija k) throws IOException {
        PopisWfsUvoz.Mreza m = k.vrsta() == VrstaIzvora.POPIS_MREZA ? popisMreza.uvezi(k) : popis.uvezi(k);
        if (m.stanovnika().isEmpty()) {
            throw new IOException("Izvor nije dao nijednu ćeliju sa stanovništvom za područje "
                    + svojstva.podrucje().naziv() + " (" + m.sloj() + ")");
        }
        int n = celije.zamijeniStanovnistvo(m.sredista(), m.stanovnika());
        long ukupno = m.stanovnika().values().stream().mapToLong(Integer::longValue).sum();
        String poruka = "ćelija sa stanovništvom: " + n + ", ukupno " + ukupno + " stanovnika (" + m.sloj() + ")";
        izvori.zabiljeziUspjeh(k.id(), n == 0 ? "UPOZORENJE" : "OK",
                new IzvorRepozitorij.Statistika(n, n, 0, 0, 0, LocalDate.of(2021, 8, 31), poruka));
        return poruka;
    }

    // ------------------------------------------------------------------ granice četvrti i odbora

    private String uvoziGranice(IzvorKonfiguracija k) throws IOException {
        byte[] podaci;
        LocalDate datum = LocalDate.now();
        if (k.portal() != null && k.paket() != null) {
            CkanKlijent.Sadrzaj s = ckan.preuzmi(k);
            podaci = s.podaci();
            datum = s.resurs().izmijenjeno() == null ? datum : s.resurs().izmijenjeno();
        } else {
            podaci = preuzimac.preuzmi(k.url());
        }
        List<Poligon> poligoni = GeoJsonCitac.poligoni(new String(podaci, StandardCharsets.UTF_8));
        String vrsta = Boolean.TRUE.equals(k.mjesniOdbor()) ? GranicaRepozitorij.MJESNI_ODBOR : GranicaRepozitorij.GRADSKA_CETVRT;
        int n = granice.zamijeni(vrsta, poligoni);
        String poruka = "poligona: " + n;
        izvori.zabiljeziUspjeh(k.id(), n == 0 ? "UPOZORENJE" : "OK",
                new IzvorRepozitorij.Statistika(n, n, 0, 0, 0, datum, poruka));
        return poruka;
    }

    // ------------------------------------------------------------------ registar naziva ulica

    private String uvoziUlice(IzvorKonfiguracija k) throws IOException {
        CkanKlijent.Sadrzaj s = ckan.preuzmi(k);
        Tablica t = CitacSadrzaja.tablica(s.podaci(), s.resurs().format());
        int stupac = -1;
        for (int i = 0; i < t.stupci().size(); i++) {
            String n = PrepoznavanjePolja.normaliziraj(t.stupci().get(i));
            if (n.contains("ulic") || n.equals("naziv")) {
                stupac = i;
                break;
            }
        }
        if (stupac < 0) {
            throw new IOException("Ne prepoznajem stupac s nazivom ulice: " + t.stupci());
        }
        List<String> nazivi = new ArrayList<>();
        for (List<String> red : t.redovi()) {
            String v = t.vrijednost(red, stupac);
            if (!v.isEmpty()) {
                nazivi.add(v);
            }
        }
        int n = adrese.zamijeniUlice(nazivi);
        String poruka = "naziva ulica: " + n;
        izvori.zabiljeziUspjeh(k.id(), "OK", new IzvorRepozitorij.Statistika(t.brojRedova(), n, 0, 0, 0,
                s.resurs().izmijenjeno(), poruka));
        return poruka;
    }
}
