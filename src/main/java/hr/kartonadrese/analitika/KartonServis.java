package hr.kartonadrese.analitika;

import hr.kartonadrese.baza.CelijaRepozitorij;
import hr.kartonadrese.baza.IzvorRepozitorij;
import hr.kartonadrese.baza.PoiRepozitorij;
import hr.kartonadrese.baza.PostajaZrakaRepozitorij;
import hr.kartonadrese.baza.ProstornaPretraga;
import hr.kartonadrese.baza.StajalisteRepozitorij;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.analitika.Blizina;
import hr.kartonadrese.core.analitika.Indeks15;
import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.geo.Laea3035;
import hr.kartonadrese.core.ml.MetapodaciModela;
import hr.kartonadrese.core.ml.SezonskaProcjena;
import hr.kartonadrese.core.model.Celija;
import hr.kartonadrese.core.model.Interes;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Lokacija;
import hr.kartonadrese.core.model.Poi;
import hr.kartonadrese.core.model.Postavke;
import hr.kartonadrese.core.model.Profil;
import hr.kartonadrese.core.model.Sekcija;
import hr.kartonadrese.core.model.StanjePristupacnosti;
import hr.kartonadrese.core.model.Stajaliste;
import hr.kartonadrese.geokod.Geokoder;
import hr.kartonadrese.ml.ModelZraka;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Sastavlja karton adrese iz svih slojeva (analitika nad usklađenim podacima). */
@Service
public class KartonServis {

    static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy.", Locale.forLanguageTag("hr"));
    private static final double POLUMJER_PRETRAGE_M = 2500;
    /** Rjeđi objekti (menze, studentska naselja, gradski vrtovi, Odmorko) traže se u širem krugu. */
    private static final double POLUMJER_RIJETKIH_M = 6000;
    private static final List<Kategorija> RIJETKI = List.of(Kategorija.MENZA, Kategorija.STUDENTSKI_DOM,
            Kategorija.GRADSKI_VRT, Kategorija.ODMORKO, Kategorija.KNJIZNICA);
    private static final double POLUMJER_STAJALISTA_M = 600;
    private static final double POLUMJER_STAJALISTA_FAKULTETA_M = 400;
    private static final List<String> ZANIMLJIVI_ATRIBUTI = List.of("smjen", "boravak", "slobod", "upisn",
            "radno vrijeme", "opening_hours", "pristup", "kapacitet", "vrsta", "telefon");
    /** Čitljive oznake poznatih atributa iz skupova Geoportala Grada Zagreba. */
    private static final Map<String, String> OZNAKE_ATRIBUTA = oznake();
    static final String NAPOMENA_STAJALISTA = "ZET u GTFS-u ne objavljuje polja wheelchair_boarding i "
            + "wheelchair_accessible (provjereno 8. 11. 2026.), pa pristupačnost stajališta i vozila ne možemo potvrditi.";

    private final Geokoder geokoder;
    private final ProstornaPretraga pretraga;
    private final PoiRepozitorij poi;
    private final StajalisteRepozitorij stajalista;
    private final CelijaRepozitorij celije;
    private final PostajaZrakaRepozitorij postaje;
    private final IzvorRepozitorij izvori;
    private final Indeks15Servis indeks15;
    private final ModelZraka modelZraka;
    private final KartonSvojstva svojstva;

    public KartonServis(Geokoder geokoder, ProstornaPretraga pretraga, PoiRepozitorij poi,
                        StajalisteRepozitorij stajalista, CelijaRepozitorij celije, PostajaZrakaRepozitorij postaje,
                        IzvorRepozitorij izvori, Indeks15Servis indeks15, ModelZraka modelZraka,
                        KartonSvojstva svojstva) {
        this.geokoder = geokoder;
        this.pretraga = pretraga;
        this.poi = poi;
        this.stajalista = stajalista;
        this.celije = celije;
        this.postaje = postaje;
        this.izvori = izvori;
        this.indeks15 = indeks15;
        this.modelZraka = modelZraka;
        this.svojstva = svojstva;
    }

    public Optional<Karton> karton(String adresa, Profil profil) {
        return karton(adresa, profil, Postavke.PRAZNE);
    }

    public Optional<Karton> karton(String adresa, Profil profil, Postavke postavke) {
        return geokoder.geokodiraj(adresa).map(l -> sastavi(l, profil, postavke == null ? Postavke.PRAZNE : postavke));
    }

    /** Visoka učilišta za odabir "Moj fakultet" (abecedno). */
    public List<Poi> visokaUcilista() {
        return poi.kategorija(Kategorija.VISOKO_UCILISTE);
    }

    Karton sastavi(Lokacija l, Profil profil, Postavke postavke) {
        Map<String, String> naziviIzvora = new HashMap<>();
        Map<String, LocalDate> datumiIzvora = new HashMap<>();
        Map<String, String> stanjaIzvora = new HashMap<>();
        for (IzvorRepozitorij.IzvorStanje s : izvori.svi()) {
            stanjaIzvora.put(s.id(), s.status());
            naziviIzvora.put(s.id(), s.izdavatelj() == null ? s.naziv() : s.izdavatelj());
            if (s.datumPodatka() != null) {
                datumiIzvora.put(s.id(), s.datumPodatka());
            }
        }
        double brzina = profil.getBrzinaHodaMMin();
        List<Poi> objekti = new ArrayList<>(pretraga.uKrugu(l.lat(), l.lon(), POLUMJER_PRETRAGE_M, null));
        Set<Long> vec = new HashSet<>();
        objekti.forEach(o -> vec.add(o.id()));
        pretraga.uKrugu(l.lat(), l.lon(), POLUMJER_RIJETKIH_M, RIJETKI).stream()
                .filter(o -> vec.add(o.id())).forEach(objekti::add);
        List<Blizina.PoiUdaljenost> sortirano = Blizina.sortiraj(l.lat(), l.lon(), objekti, brzina, POLUMJER_RIJETKIH_M);
        List<Blizina.PoiUdaljenost> blizu = sortirano.stream().filter(pu -> pu.udaljenostM() <= POLUMJER_PRETRAGE_M
                || RIJETKI.contains(pu.poi().kategorija())).toList();
        Pretvarac p = new Pretvarac(naziviIzvora, datumiIzvora, profil, postavke);

        Karton.Obrazovanje obrazovanje = new Karton.Obrazovanje(
                p.svi(Blizina.prvih(blizu, Kategorija.SKOLA, 3)),
                p.svi(Blizina.prvih(blizu, Kategorija.VRTIC, 3)),
                p.svi(Blizina.prvih(blizu, Kategorija.SREDNJA_SKOLA, 2)));
        List<Stajaliste> stajalistaBlizu = stajalistaUKrugu(l.lat(), l.lon(), POLUMJER_STAJALISTA_M);
        Karton.Prijevoz prijevoz = prijevoz(l, stajalistaBlizu, brzina, naziviIzvora, datumiIzvora, stanjaIzvora);
        Karton.Zdravlje zdravlje = new Karton.Zdravlje(
                p.svi(Blizina.prvih(blizu, Kategorija.ZDRAVSTVO, 3)),
                p.svi(Blizina.prvih(blizu, Kategorija.LJEKARNA, 3)));
        Karton.Zrak zrak = zrak(l);
        Karton.Pristupacnost pristupacnost = new Karton.Pristupacnost(
                p.svi(Blizina.prvih(blizu, Kategorija.PRISTUPACNOST, 3)),
                prvi(blizu, Kategorija.PJESACKA_ZONA).map(p::objekt).orElse(null),
                StanjePristupacnosti.NEMA_PODATKA.getNaziv(), NAPOMENA_STAJALISTA);
        Karton.Potres potres = new Karton.Potres(
                prvi(blizu, Kategorija.ZBORNO_MJESTO).map(p::objekt).orElse(null),
                prvi(blizu, Kategorija.EVAKUACIJA).map(p::objekt).orElse(null));
        Karton.JavnePovrsine javnePovrsine = new Karton.JavnePovrsine(
                p.svi(Blizina.prvih(blizu, Kategorija.IGRALISTE, 2)),
                prvi(blizu, Kategorija.PARK).map(p::objekt).orElse(null),
                p.svi(Blizina.prvih(blizu, Kategorija.SPORT, 2)),
                prvi(blizu, Kategorija.ODMORKO).map(p::objekt).orElse(null),
                prvi(blizu, Kategorija.GRADSKI_VRT).map(p::objekt).orElse(null));

        Indeks15.Rezultat ind = indeks15.zaTocku(l.lat(), l.lon(), brzina);
        Map<Kategorija, Blizina.PoiUdaljenost> najblizi = Blizina.najblizi(blizu);
        List<Karton.Objekt> najbliziSvakodnevni = new ArrayList<>();
        for (Kategorija k : Kategorija.svakodnevne()) {
            Blizina.PoiUdaljenost pu = najblizi.get(k);
            if (pu != null) {
                najbliziSvakodnevni.add(p.objekt(pu));
            }
        }
        List<Karton.Objekt> ostalo = new ArrayList<>();
        for (Kategorija k : List.of(Kategorija.KNJIZNICA, Kategorija.KULTURA)) {
            Blizina.PoiUdaljenost pu = najblizi.get(k);
            if (pu != null) {
                ostalo.add(p.objekt(pu));
            }
        }
        int prag = (int) Math.round(GeoUtil.dometM(Indeks15.MINUTA, brzina));
        Karton.Okolica okolica = new Karton.Okolica(ind.indeks(), ind.najvise(),
                ind.dostupne().stream().map(Kategorija::getNaziv).toList(),
                ind.nedostaju().stream().map(Kategorija::getNaziv).toList(), najbliziSvakodnevni, ostalo, prag);

        Karton.Student student = profil == Profil.STUDENT ? student(l, blizu, stajalistaBlizu, p, postavke) : null;
        Karton.MojIndeks mojIndeks = mojIndeks(profil, najblizi, student, prag);

        String celijaId = Laea3035.oznakaCelije(l.lat(), l.lon(), 1000);
        Integer stanovnika = celije.nadji(celijaId).map(Celija::stanovnika).orElse(null);
        Karton.Stanovnistvo stanovnistvo = new Karton.Stanovnistvo(celijaId, stanovnika,
                "DZS, Popis stanovništva 2021. na mreži 1 km²");

        List<Karton.Objekt> naKarti = new ArrayList<>();
        blizu.stream().filter(pu -> pu.udaljenostM() <= 1500
                        && pu.poi().kategorija() != Kategorija.BICIKLISTICKA_STAZA)
                .limit(150).forEach(pu -> naKarti.add(p.objekt(pu)));
        if (student != null && student.fakultet() != null) {
            Karton.DoFakulteta f = student.fakultet();
            naKarti.add(new Karton.Objekt(f.id(), Kategorija.VISOKO_UCILISTE.name(),
                    Kategorija.VISOKO_UCILISTE.getNaziv(), Kategorija.VISOKO_UCILISTE.getBoja(), f.naziv(), f.adresa(),
                    f.lat(), f.lon(), f.udaljenostM(), GeoUtil.minutaHoda(f.udaljenostM(), brzina), null,
                    "Grad Zagreb – Geoportal visokoškolske ustanove", null, List.of(), true, "Vaš fakultet", null));
        }

        Karton k = new Karton(l.adresa(), l.lat(), l.lon(), l.gradskaCetvrt(), l.mjesniOdbor(), profil,
                profil.getRedoslijed(), List.of(), List.of(), obrazovanje, prijevoz, zdravlje, zrak, pristupacnost, potres,
                okolica, stanovnistvo, naKarti, GeoUtil.dometM(Indeks15.MINUTA, brzina), svojstva.ogledniPodaci(),
                javnePovrsine, student, mojIndeks, postavke);
        return new Karton(k.adresa(), k.lat(), k.lon(), k.gradskaCetvrt(), k.mjesniOdbor(), profil, k.redoslijed(),
                Sazetak.izradi(k), kartice(k), obrazovanje, prijevoz, zdravlje, zrak, pristupacnost, potres, okolica,
                stanovnistvo, naKarti, k.polumjer15M(), k.ogledni(), javnePovrsine, student, mojIndeks, postavke);
    }

    private static Optional<Blizina.PoiUdaljenost> prvi(List<Blizina.PoiUdaljenost> sortirano, Kategorija k) {
        return Blizina.prvih(sortirano, k, 1).stream().findFirst();
    }

    private List<Stajaliste> stajalistaUKrugu(double lat, double lon, double polumjerM) {
        List<Stajaliste> r = new ArrayList<>(stajalista.uKrugu(lat, lon, polumjerM));
        r.sort(Comparator.comparingDouble(s -> GeoUtil.udaljenostM(lat, lon, s.lat(), s.lon())));
        return r;
    }

    // ------------------------------------------------------------------ studentski život

    private Karton.Student student(Lokacija l, List<Blizina.PoiUdaljenost> blizu, List<Stajaliste> stajalistaBlizu,
                                   Pretvarac p, Postavke postavke) {
        Karton.DoFakulteta fakultet = null;
        if (postavke.fakultetId() != null) {
            fakultet = poi.poId(postavke.fakultetId())
                    .filter(f -> f.kategorija() == Kategorija.VISOKO_UCILISTE)
                    .map(f -> doFakulteta(l, f, stajalistaBlizu))
                    .orElse(null);
        }
        // Redoslijed: najprije odabrani interesi, zatim ostali; studentsko naselje na kraju.
        List<Karton.Objekt> odabrano = new ArrayList<>();
        List<Karton.Objekt> ostalo = new ArrayList<>();
        for (Interes i : Interes.values()) {
            for (Kategorija k : i.getKategorije()) {
                prvi(blizu, k).map(p::objekt).ifPresent(o -> (postavke.imaInteres(i) ? odabrano : ostalo).add(o));
            }
        }
        prvi(blizu, Kategorija.STUDENTSKI_DOM).map(p::objekt).ifPresent(ostalo::add);
        List<Karton.Objekt> stavke = new ArrayList<>(odabrano);
        stavke.addAll(ostalo);
        double staze = blizu.stream()
                .filter(pu -> pu.poi().kategorija() == Kategorija.BICIKLISTICKA_STAZA && pu.poi().geometrija() != null)
                .filter(pu -> pu.udaljenostM() <= 1000)
                .mapToDouble(pu -> pu.poi().geometrija().duljinaUKruguM(l.lat(), l.lon(), 1000))
                .sum();
        return new Karton.Student(fakultet, stavke, staze > 0 ? (int) Math.round(staze) : null);
    }

    Karton.DoFakulteta doFakulteta(Lokacija l, Poi f, List<Stajaliste> stajalistaBlizu) {
        Set<String> linijeAdrese = new HashSet<>();
        stajalistaBlizu.forEach(s -> linijeAdrese.addAll(s.linije()));
        List<Stajaliste> kodFakulteta = stajalistaUKrugu(f.lat(), f.lon(), POLUMJER_STAJALISTA_FAKULTETA_M);
        TreeSet<String> zajednicke = new TreeSet<>(KartonServis::usporediLinije);
        kodFakulteta.forEach(s -> s.linije().stream().filter(linijeAdrese::contains).forEach(zajednicke::add));
        int d = (int) Math.round(GeoUtil.udaljenostM(l.lat(), l.lon(), f.lat(), f.lon()));
        return new Karton.DoFakulteta(f.id(), f.naziv(), f.adresa(), d, List.copyOf(zajednicke),
                !kodFakulteta.isEmpty() && !stajalistaBlizu.isEmpty(), f.lat(), f.lon());
    }

    /** Linije redom po broju (4 prije 13), a slovne oznake abecedno. */
    static int usporediLinije(String a, String b) {
        Integer x = broj(a);
        Integer y = broj(b);
        if (x != null && y != null) {
            return x.equals(y) ? a.compareTo(b) : Integer.compare(x, y);
        }
        return x != null ? -1 : (y != null ? 1 : a.compareTo(b));
    }

    private static Integer broj(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ osobni indeks

    private static Karton.MojIndeks mojIndeks(Profil profil, Map<Kategorija, Blizina.PoiUdaljenost> najblizi,
                                              Karton.Student student, int prag) {
        Map<String, Integer> stavke = new LinkedHashMap<>();
        switch (profil) {
            case OBITELJ -> {
                stavke.put("Igralište", udaljenost(najblizi, Kategorija.IGRALISTE));
                stavke.put("Park", udaljenost(najblizi, Kategorija.PARK));
                stavke.put("Sportski objekt", udaljenost(najblizi, Kategorija.SPORT));
                stavke.put("Dječji vrtić", udaljenost(najblizi, Kategorija.VRTIC));
                stavke.put("Osnovna škola", udaljenost(najblizi, Kategorija.SKOLA));
            }
            case STUDENT -> {
                stavke.put("Menza", udaljenost(najblizi, Kategorija.MENZA));
                stavke.put("Knjižnica", udaljenost(najblizi, Kategorija.KNJIZNICA));
                stavke.put("Biciklistička staza", udaljenost(najblizi, Kategorija.BICIKLISTICKA_STAZA));
                stavke.put("Stajalište", udaljenost(najblizi, Kategorija.STAJALISTE));
                if (student != null && student.fakultet() != null) {
                    stavke.put("Moj fakultet", student.fakultet().udaljenostM());
                }
            }
            default -> {
                return null;
            }
        }
        List<Karton.StavkaIndeksa> r = new ArrayList<>();
        stavke.forEach((naziv, d) -> r.add(new Karton.StavkaIndeksa(naziv, d, d != null && d <= prag)));
        return new Karton.MojIndeks("Moj indeks · " + profil.getNaziv(), r, prag);
    }

    private static Integer udaljenost(Map<Kategorija, Blizina.PoiUdaljenost> najblizi, Kategorija k) {
        Blizina.PoiUdaljenost pu = najblizi.get(k);
        return pu == null ? null : (int) Math.round(pu.udaljenostM());
    }

    // ------------------------------------------------------------------ prijevoz i zrak

    private Karton.Prijevoz prijevoz(Lokacija l, List<Stajaliste> blizu, double brzina, Map<String, String> nazivi,
                                     Map<String, LocalDate> datumi, Map<String, String> stanja) {
        TreeSet<String> sveLinije = new TreeSet<>();
        List<Karton.StajalisteBlizu> r = new ArrayList<>();
        for (Stajaliste s : blizu) {
            sveLinije.addAll(s.linije());
            if (r.size() < 5) {
                double d = GeoUtil.udaljenostM(l.lat(), l.lon(), s.lat(), s.lon());
                r.add(new Karton.StajalisteBlizu(s.naziv(), s.vrsta(), s.linije(), (int) Math.round(d),
                        GeoUtil.minutaHoda(d, brzina), s.lat(), s.lon()));
            }
        }
        // Izvor stajališta je ZET GTFS samo ako je stvarno preuzet; inače su to ogledni podaci.
        boolean gtfsPreuzet = "OK".equals(stanja.get("zet-gtfs"));
        String izvor = gtfsPreuzet ? nazivi.getOrDefault("zet-gtfs", "ZET") + " (GTFS)"
                : nazivi.getOrDefault("ogledni-prijevoz", "Ogledni podaci");
        LocalDate datum = datumi.get(gtfsPreuzet ? "zet-gtfs" : "ogledni-prijevoz");
        return new Karton.Prijevoz(r, sveLinije.size(), izvor, datum == null ? null : DATUM.format(datum));
    }

    private Karton.Zrak zrak(Lokacija l) {
        Optional<ModelZraka.NajblizaPostaja> model = modelZraka.najblizi(l.lat(), l.lon());
        List<PostajaZrakaRepozitorij.PostajaZraka> izmjerene = postaje.sve();
        if (model.isPresent()) {
            MetapodaciModela m = model.get().model().meta();
            Optional<PostajaZrakaRepozitorij.PostajaZraka> izBaze = izmjerene.stream()
                    .filter(p -> p.id().equals(m.postajaId())).findFirst();
            List<SezonskaProcjena.Mjesec> procjena = modelZraka.sezonskaProcjena(model.get().model());
            boolean izModela = model.get().model().sesija() != null;
            return new Karton.Zrak(m.postajaId(), m.postajaNaziv(), m.mreza(), (int) Math.round(model.get().udaljenostM()),
                    izBaze.map(PostajaZrakaRepozitorij.PostajaZraka::prosjekPm10).orElse(m.statistika().prosjekPm10()),
                    izBaze.map(PostajaZrakaRepozitorij.PostajaZraka::danaIznad50).orElse(m.statistika().danaIznad50()),
                    izBaze.map(PostajaZrakaRepozitorij.PostajaZraka::godina).orElse(m.statistika().godina()),
                    procjena, izModela, m.ogledni(),
                    izBaze.map(PostajaZrakaRepozitorij.PostajaZraka::izvor).orElse(String.join(", ", m.izvoriPm10())),
                    izModela ? "Model strojnog učenja (LightGBM) · meteorologija Copernicus ERA5"
                            : "Izmjereni mjesečni prosjeci (model nije učitan)");
        }
        return izmjerene.stream()
                .min(Comparator.comparingDouble(p -> GeoUtil.udaljenostM(l.lat(), l.lon(), p.lat(), p.lon())))
                .map(p -> new Karton.Zrak(p.id(), p.naziv(), p.mreza(),
                        (int) Math.round(GeoUtil.udaljenostM(l.lat(), l.lon(), p.lat(), p.lon())), p.prosjekPm10(),
                        p.danaIznad50(), p.godina(), List.of(), false, false, p.izvor(), null))
                .orElse(null);
    }

    private static List<Karton.Kartica> kartice(Karton k) {
        List<Karton.Kartica> r = new ArrayList<>();
        if (k.student() != null && k.student().fakultet() != null) {
            Karton.DoFakulteta f = k.student().fakultet();
            r.add(new Karton.Kartica("Do fakulteta", f.udaljenost() + (f.linijeBezPresjedanja().isEmpty() ? ""
                    : " · " + f.linijeBezPresjedanja().size() + " " + linija(f.linijeBezPresjedanja().size())), "skola"));
        } else {
            k.obrazovanje().skole().stream().findFirst().ifPresentOrElse(
                    s -> r.add(new Karton.Kartica("Najbliža škola", s.udaljenost(), "skola")),
                    () -> r.add(new Karton.Kartica("Najbliža škola", "nema podatka", "skola")));
        }
        if (k.profil() == Profil.OBITELJ && !k.javnePovrsine().igralista().isEmpty()) {
            r.add(new Karton.Kartica("Igralište", k.javnePovrsine().igralista().getFirst().udaljenost(), "igraliste"));
        } else {
            k.obrazovanje().vrtici().stream().findFirst().ifPresentOrElse(
                    v -> r.add(new Karton.Kartica("Vrtić", v.udaljenost(), "vrtic")),
                    () -> r.add(new Karton.Kartica("Vrtić", "nema podatka", "vrtic")));
        }
        int n = k.prijevoz().brojLinija();
        r.add(new Karton.Kartica("Javni prijevoz", n == 0 ? "nema linija u 600 m" : n + " " + linija(n) + " u 600 m",
                "prijevoz"));
        r.add(new Karton.Kartica("Indeks 15 minuta", k.okolica().oznaka(), "indeks"));
        return r;
    }

    /** Hrvatska množina: 1 linija, 2–4 linije, 5+ linija (11–14 linija). */
    static String linija(int n) {
        int z = n % 10;
        int zz = n % 100;
        if (z >= 2 && z <= 4 && (zz < 12 || zz > 14)) {
            return "linije";
        }
        return "linija";
    }

    private static Map<String, String> oznake() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("smjenski_rad", "Smjene");
        m.put("produzeni_boravak", "Produženi boravak");
        m.put("sport_dvor", "Sportska dvorana");
        m.put("br_uc", "Učenika");
        m.put("vrsta_objekta", "Vrsta");
        m.put("objekt", "Vrsta objekta");
        m.put("sportovi", "Sportovi");
        m.put("sport", "Sport");
        m.put("radno_vrijeme", "Termini");
        m.put("školska_godina", "Školska godina");
        m.put("br_vr_parcela", "Vrtnih parcela");
        m.put("broj_stalaka", "Stalaka za bicikle");
        m.put("kampus", "Kampus");
        m.put("zavrsni_sloj", "Podloga");
        m.put("duljina", "Duljina dionice (m)");
        return m;
    }

    /** Pretvara objekte u DTO s izvorom, datumom, odabranim atributima i razlogom isticanja. */
    private record Pretvarac(Map<String, String> nazivi, Map<String, LocalDate> datumi, Profil profil,
                             Postavke postavke) {

        Karton.Objekt objekt(Blizina.PoiUdaljenost pu) {
            Poi p = pu.poi();
            LocalDate d = p.azurirano() != null ? p.azurirano() : datumi.get(p.izvorId());
            List<String> detalji = new ArrayList<>();
            if (p.atributi() != null) {
                p.atributi().forEach((kljuc, v) -> {
                    String n = kljuc.toLowerCase(Locale.ROOT);
                    if (detalji.size() >= 4 || v == null || v.isBlank() || "null".equals(v)) {
                        return;
                    }
                    String oznaka = OZNAKE_ATRIBUTA.get(n);
                    if (oznaka != null) {
                        detalji.add(oznaka + ": " + vrijednost(v));
                    } else if (!n.contains("wheelchair") && ZANIMLJIVI_ATRIBUTI.stream().anyMatch(n::contains)) {
                        detalji.add(kljuc + ": " + vrijednost(v));
                    }
                });
            }
            String razlog = null;
            for (Interes i : postavke.interesi()) {
                if (profil == Profil.STUDENT && i.getKategorije().contains(p.kategorija())) {
                    razlog = "Istaknuto jer ste označili: " + i.getNaziv();
                }
            }
            boolean istaknut = razlog != null || profil.getIstaknute().contains(p.kategorija());
            String pristupacnost = profil == Profil.OSI
                    ? StanjePristupacnosti.izOsm(p.atribut("wheelchair")).getNaziv() : null;
            return new Karton.Objekt(p.id(), p.kategorija().name(), p.kategorija().getNaziv(), p.kategorija().getBoja(),
                    p.naziv(), p.adresa(), p.lat(), p.lon(), (int) Math.round(pu.udaljenostM()), pu.minutaHoda(),
                    p.izvorId(), nazivi.getOrDefault(p.izvorId(), p.izvorId()), d == null ? null : DATUM.format(d),
                    detalji, istaknut, razlog, pristupacnost);
        }

        List<Karton.Objekt> svi(List<Blizina.PoiUdaljenost> l) {
            return l.stream().map(this::objekt).toList();
        }

        private static String vrijednost(String v) {
            String t = v.trim().length() > 140 ? v.trim().substring(0, 139) + "…" : v.trim();
            return switch (t.toUpperCase(Locale.ROOT)) {
                case "DA", "IMA" -> "da";
                case "NE", "NEMA" -> "ne";
                default -> t.chars().anyMatch(Character::isLowerCase) ? t : t.toLowerCase(Locale.forLanguageTag("hr"));
            };
        }
    }

    /** Redoslijed sekcija za profil (izloženo zbog testova). */
    static List<Sekcija> redoslijed(Profil p) {
        return p.getRedoslijed();
    }
}
