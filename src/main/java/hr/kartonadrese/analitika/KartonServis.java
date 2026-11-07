package hr.kartonadrese.analitika;

import hr.kartonadrese.baza.CelijaRepozitorij;
import hr.kartonadrese.baza.IzvorRepozitorij;
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
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Lokacija;
import hr.kartonadrese.core.model.Poi;
import hr.kartonadrese.core.model.Profil;
import hr.kartonadrese.core.model.Sekcija;
import hr.kartonadrese.core.model.Stajaliste;
import hr.kartonadrese.geokod.Geokoder;
import hr.kartonadrese.ml.ModelZraka;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** Sastavlja karton adrese iz svih slojeva (analitika nad usklađenim podacima). */
@Service
public class KartonServis {

    static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy.", Locale.forLanguageTag("hr"));
    private static final double POLUMJER_PRETRAGE_M = 2500;
    private static final double POLUMJER_STAJALISTA_M = 600;
    private static final List<String> ZANIMLJIVI_ATRIBUTI = List.of("smjen", "boravak", "slobod", "upisn",
            "radno vrijeme", "opening_hours", "wheelchair", "pristup", "kapacitet", "vrsta", "telefon");

    private final Geokoder geokoder;
    private final ProstornaPretraga pretraga;
    private final StajalisteRepozitorij stajalista;
    private final CelijaRepozitorij celije;
    private final PostajaZrakaRepozitorij postaje;
    private final IzvorRepozitorij izvori;
    private final Indeks15Servis indeks15;
    private final ModelZraka modelZraka;
    private final KartonSvojstva svojstva;

    public KartonServis(Geokoder geokoder, ProstornaPretraga pretraga, StajalisteRepozitorij stajalista,
                        CelijaRepozitorij celije, PostajaZrakaRepozitorij postaje, IzvorRepozitorij izvori,
                        Indeks15Servis indeks15, ModelZraka modelZraka, KartonSvojstva svojstva) {
        this.geokoder = geokoder;
        this.pretraga = pretraga;
        this.stajalista = stajalista;
        this.celije = celije;
        this.postaje = postaje;
        this.izvori = izvori;
        this.indeks15 = indeks15;
        this.modelZraka = modelZraka;
        this.svojstva = svojstva;
    }

    public Optional<Karton> karton(String adresa, Profil profil) {
        return geokoder.geokodiraj(adresa).map(l -> sastavi(l, profil));
    }

    Karton sastavi(Lokacija l, Profil profil) {
        Map<String, String> naziviIzvora = new HashMap<>();
        Map<String, LocalDate> datumiIzvora = new HashMap<>();
        for (IzvorRepozitorij.IzvorStanje s : izvori.svi()) {
            naziviIzvora.put(s.id(), s.izdavatelj() == null ? s.naziv() : s.izdavatelj());
            if (s.datumPodatka() != null) {
                datumiIzvora.put(s.id(), s.datumPodatka());
            }
        }
        double brzina = profil.getBrzinaHodaMMin();
        List<Poi> objekti = pretraga.uKrugu(l.lat(), l.lon(), POLUMJER_PRETRAGE_M, null);
        List<Blizina.PoiUdaljenost> sortirano = Blizina.sortiraj(l.lat(), l.lon(), objekti, brzina, POLUMJER_PRETRAGE_M);
        Pretvarac p = new Pretvarac(naziviIzvora, datumiIzvora, profil);

        Karton.Obrazovanje obrazovanje = new Karton.Obrazovanje(
                p.svi(Blizina.prvih(sortirano, Kategorija.SKOLA, 3)),
                p.svi(Blizina.prvih(sortirano, Kategorija.VRTIC, 3)),
                p.svi(Blizina.prvih(sortirano, Kategorija.SREDNJA_SKOLA, 2)));
        Karton.Prijevoz prijevoz = prijevoz(l, brzina, naziviIzvora, datumiIzvora);
        Karton.Zdravlje zdravlje = new Karton.Zdravlje(
                p.svi(Blizina.prvih(sortirano, Kategorija.ZDRAVSTVO, 3)),
                p.svi(Blizina.prvih(sortirano, Kategorija.LJEKARNA, 3)));
        Karton.Zrak zrak = zrak(l);
        Karton.Pristupacnost pristupacnost = new Karton.Pristupacnost(
                p.svi(Blizina.prvih(sortirano, Kategorija.PRISTUPACNOST, 3)),
                Blizina.prvih(sortirano, Kategorija.PJESACKA_ZONA, 1).stream().findFirst().map(p::objekt).orElse(null),
                prijevoz.stajalista().stream().filter(s -> s.vrsta().contains("tramvaj")).findFirst()
                        .map(s -> new Karton.Objekt(0, Kategorija.STAJALISTE.name(), "Tramvajsko stajalište",
                                Kategorija.STAJALISTE.getBoja(), s.naziv(), null, s.lat(), s.lon(), s.udaljenostM(),
                                s.minutaHoda(), null, prijevoz.izvor(), prijevoz.datum(),
                                List.of("linije " + String.join(", ", s.linije())), false))
                        .map(o -> List.of(o)).orElse(List.of()));
        Karton.Potres potres = new Karton.Potres(
                Blizina.prvih(sortirano, Kategorija.ZBORNO_MJESTO, 1).stream().findFirst().map(p::objekt).orElse(null),
                Blizina.prvih(sortirano, Kategorija.EVAKUACIJA, 1).stream().findFirst().map(p::objekt).orElse(null));

        Indeks15.Rezultat ind = indeks15.zaTocku(l.lat(), l.lon(), brzina);
        Map<Kategorija, Blizina.PoiUdaljenost> najblizi = Blizina.najblizi(sortirano);
        List<Karton.Objekt> najbliziSvakodnevni = new ArrayList<>();
        for (Kategorija k : Kategorija.svakodnevne()) {
            Blizina.PoiUdaljenost pu = najblizi.get(k);
            if (pu != null) {
                najbliziSvakodnevni.add(p.objekt(pu));
            }
        }
        List<Karton.Objekt> ostalo = new ArrayList<>();
        for (Kategorija k : List.of(Kategorija.PARK, Kategorija.KNJIZNICA, Kategorija.SPORT, Kategorija.KULTURA)) {
            Blizina.PoiUdaljenost pu = najblizi.get(k);
            if (pu != null) {
                ostalo.add(p.objekt(pu));
            }
        }
        Karton.Okolica okolica = new Karton.Okolica(ind.indeks(), ind.najvise(),
                ind.dostupne().stream().map(Kategorija::getNaziv).toList(),
                ind.nedostaju().stream().map(Kategorija::getNaziv).toList(), najbliziSvakodnevni, ostalo);

        String celijaId = Laea3035.oznakaCelije(l.lat(), l.lon(), 1000);
        Integer stanovnika = celije.nadji(celijaId).map(Celija::stanovnika).orElse(null);
        Karton.Stanovnistvo stanovnistvo = new Karton.Stanovnistvo(celijaId, stanovnika,
                "DZS, Popis stanovništva 2021. na mreži 1 km²");

        List<Karton.Objekt> naKarti = new ArrayList<>();
        sortirano.stream().filter(pu -> pu.udaljenostM() <= 1500).limit(150).forEach(pu -> naKarti.add(p.objekt(pu)));

        Karton k = new Karton(l.adresa(), l.lat(), l.lon(), l.gradskaCetvrt(), l.mjesniOdbor(), profil,
                profil.getRedoslijed(), List.of(), List.of(), obrazovanje, prijevoz, zdravlje, zrak, pristupacnost, potres,
                okolica, stanovnistvo, naKarti, GeoUtil.dometM(Indeks15.MINUTA, brzina), svojstva.ogledniPodaci());
        return new Karton(k.adresa(), k.lat(), k.lon(), k.gradskaCetvrt(), k.mjesniOdbor(), profil, k.redoslijed(),
                Sazetak.izradi(k), kartice(k), obrazovanje, prijevoz, zdravlje, zrak, pristupacnost, potres, okolica,
                stanovnistvo, naKarti, k.polumjer15M(), k.ogledni());
    }

    private Karton.Prijevoz prijevoz(Lokacija l, double brzina, Map<String, String> nazivi,
                                     Map<String, LocalDate> datumi) {
        List<Stajaliste> blizu = new ArrayList<>(stajalista.uKrugu(l.lat(), l.lon(), POLUMJER_STAJALISTA_M));
        blizu.sort(Comparator.comparingDouble(s -> GeoUtil.udaljenostM(l.lat(), l.lon(), s.lat(), s.lon())));
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
        String izvor = nazivi.getOrDefault("zet-gtfs", "ZET (GTFS)");
        LocalDate datum = datumi.get("zet-gtfs");
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
        k.obrazovanje().skole().stream().findFirst().ifPresentOrElse(
                s -> r.add(new Karton.Kartica("Najbliža škola", s.udaljenostM() + " m · " + s.minutaHoda() + " min", "skola")),
                () -> r.add(new Karton.Kartica("Najbliža škola", "nema podatka", "skola")));
        k.obrazovanje().vrtici().stream().findFirst().ifPresentOrElse(
                v -> r.add(new Karton.Kartica("Vrtić", v.udaljenostM() + " m · " + v.minutaHoda() + " min", "vrtic")),
                () -> r.add(new Karton.Kartica("Vrtić", "nema podatka", "vrtic")));
        int n = k.prijevoz().brojLinija();
        r.add(new Karton.Kartica("Javni prijevoz", n == 0 ? "nema linija u 600 m" : n + " " + linija(n) + " ZET-a u 600 m",
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

    /** Pretvara objekte u DTO s izvorom, datumom i odabranim atributima. */
    private record Pretvarac(Map<String, String> nazivi, Map<String, LocalDate> datumi, Profil profil) {

        Karton.Objekt objekt(Blizina.PoiUdaljenost pu) {
            Poi p = pu.poi();
            LocalDate d = p.azurirano() != null ? p.azurirano() : datumi.get(p.izvorId());
            List<String> detalji = new ArrayList<>();
            if (p.atributi() != null) {
                p.atributi().forEach((kljuc, v) -> {
                    String n = kljuc.toLowerCase(Locale.ROOT);
                    if (detalji.size() < 3 && ZANIMLJIVI_ATRIBUTI.stream().anyMatch(n::contains)) {
                        detalji.add(kljuc + ": " + v);
                    }
                });
            }
            return new Karton.Objekt(p.id(), p.kategorija().name(), p.kategorija().getNaziv(), p.kategorija().getBoja(),
                    p.naziv(), p.adresa(), p.lat(), p.lon(), (int) Math.round(pu.udaljenostM()), pu.minutaHoda(),
                    p.izvorId(), nazivi.getOrDefault(p.izvorId(), p.izvorId()), d == null ? null : DATUM.format(d),
                    detalji, profil.getIstaknute().contains(p.kategorija()));
        }

        List<Karton.Objekt> svi(List<Blizina.PoiUdaljenost> l) {
            return l.stream().map(this::objekt).toList();
        }
    }

    /** Redoslijed sekcija za profil (izloženo zbog testova). */
    static List<Sekcija> redoslijed(Profil p) {
        return p.getRedoslijed();
    }
}
