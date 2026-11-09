package hr.kartonadrese.web;

import hr.kartonadrese.analitika.Karton;
import hr.kartonadrese.analitika.KartonServis;
import hr.kartonadrese.analitika.UsporedbaServis;
import hr.kartonadrese.baza.AdresaRepozitorij;
import hr.kartonadrese.baza.CelijaRepozitorij;
import hr.kartonadrese.baza.IzvorRepozitorij;
import hr.kartonadrese.baza.PoiRepozitorij;
import hr.kartonadrese.baza.PrijavaRepozitorij;
import hr.kartonadrese.baza.StajalisteRepozitorij;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.model.Interes;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Lokacija;
import hr.kartonadrese.core.model.Postavke;
import hr.kartonadrese.core.model.Potreba;
import hr.kartonadrese.core.model.Profil;
import hr.kartonadrese.ml.ModelZraka;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Thymeleaf stranice: početna, karton, usporedba, karta 15 minuta, o podacima, ugradnja. */
@Controller
public class StraniceKontroler {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final KartonServis kartoni;
    private final UsporedbaServis usporedba;
    private final AdresaRepozitorij adrese;
    private final IzvorRepozitorij izvori;
    private final PoiRepozitorij poi;
    private final StajalisteRepozitorij stajalista;
    private final CelijaRepozitorij celije;
    private final PrijavaRepozitorij prijave;
    private final ModelZraka modelZraka;
    private final KartonSvojstva svojstva;

    public StraniceKontroler(KartonServis kartoni, UsporedbaServis usporedba, AdresaRepozitorij adrese,
                             IzvorRepozitorij izvori, PoiRepozitorij poi, StajalisteRepozitorij stajalista,
                             CelijaRepozitorij celije, PrijavaRepozitorij prijave, ModelZraka modelZraka,
                             KartonSvojstva svojstva) {
        this.kartoni = kartoni;
        this.usporedba = usporedba;
        this.adrese = adrese;
        this.izvori = izvori;
        this.poi = poi;
        this.stajalista = stajalista;
        this.celije = celije;
        this.prijave = prijave;
        this.modelZraka = modelZraka;
        this.svojstva = svojstva;
    }

    @GetMapping("/")
    public String pocetna(Model model) {
        model.addAttribute("aktivno", "pocetna");
        model.addAttribute("profili", Profil.values());
        model.addAttribute("odabraniProfil", Profil.OPCENITO);
        model.addAttribute("primjeri", primjeri());
        return "index";
    }

    @GetMapping("/karton")
    public String karton(@RequestParam(required = false) String adresa,
                         @RequestParam(defaultValue = "opcenito") String profil,
                         @RequestParam(required = false) String fakultet,
                         @RequestParam(required = false) List<String> interes,
                         @RequestParam(required = false) List<String> potreba, Model model) {
        Profil p = Profil.izTeksta(profil);
        if (adresa == null || adresa.isBlank()) {
            return "redirect:/";
        }
        // Interesi i potrebe dolaze kao više parametara (obrazac) ili popis odvojen zarezom (poveznica).
        Postavke postavke = Postavke.iz(fakultet, interes, potreba);
        Optional<Karton> k = kartoni.karton(adresa, p, postavke);
        if (k.isEmpty()) {
            model.addAttribute("aktivno", "pocetna");
            model.addAttribute("profili", Profil.values());
            model.addAttribute("odabraniProfil", p);
            model.addAttribute("primjeri", primjeri());
            model.addAttribute("upit", adresa);
            model.addAttribute("greska", "Adresu „" + adresa + "” nismo pronašli. Provjerite kućni broj ili odaberite "
                    + "adresu s popisa prijedloga.");
            model.addAttribute("prijedlozi", adrese.prijedlozi(adresa, 5));
            return "index";
        }
        model.addAttribute("aktivno", "karton");
        model.addAttribute("karton", k.get());
        model.addAttribute("profili", Profil.values());
        model.addAttribute("postavke", postavke);
        model.addAttribute("sviInteresi", Interes.values());
        model.addAttribute("svePotrebe", Potreba.values());
        model.addAttribute("visokaUcilista", p == Profil.STUDENT ? kartoni.visokaUcilista() : List.of());
        model.addAttribute("kartonJson", jsonZaKartu(k.get()));
        return "karton";
    }

    @GetMapping("/usporedba")
    public String usporedba(@RequestParam(required = false) String a, @RequestParam(required = false) String b,
                            @RequestParam(defaultValue = "opcenito") String profil, Model model) {
        model.addAttribute("aktivno", "usporedba");
        model.addAttribute("a", a == null ? "" : a);
        model.addAttribute("b", b == null ? "" : b);
        model.addAttribute("profil", Profil.izTeksta(profil));
        model.addAttribute("primjeri", primjeri());
        if (a != null && !a.isBlank() && b != null && !b.isBlank()) {
            if (AdresaRepozitorij.normaliziraj(a).equals(AdresaRepozitorij.normaliziraj(b))) {
                model.addAttribute("greska", "Odabrali ste istu adresu dvaput. Za usporedbu odaberite dvije različite adrese.");
            } else {
                Optional<UsporedbaServis.Usporedba> u = usporedba.usporedi(a, b, Profil.izTeksta(profil));
                if (u.isPresent()) {
                    model.addAttribute("usporedba", u.get());
                } else {
                    model.addAttribute("greska", "Jednu od adresa nismo pronašli. Provjerite kućni broj ili odaberite adresu s popisa.");
                }
            }
        }
        return "usporedba";
    }

    @GetMapping("/karta")
    public String karta(Model model) {
        model.addAttribute("aktivno", "karta");
        model.addAttribute("podrucje", svojstva.podrucje());
        model.addAttribute("kategorije", Kategorija.svakodnevne());
        return "karta";
    }

    @GetMapping("/podaci")
    public String podaci(Model model) {
        model.addAttribute("aktivno", "podaci");
        model.addAttribute("izvori", izvori.svi());
        model.addAttribute("modeli", modelZraka.modeli().stream().map(ModelZraka.UcitaniModel::meta).toList());
        Map<String, Integer> brojevi = new LinkedHashMap<>();
        brojevi.put("Objekata (ustanove, trgovine, stajališta …)", poi.broj());
        brojevi.put("Stajališta s linijama (GTFS)", stajalista.broj());
        brojevi.put("Ćelija mreže 1 km² s indeksom", celije.broj());
        brojevi.put("Poznatih adresa (predmemorija geokodiranja)", adrese.broj());
        brojevi.put("Prijava pogrešaka", prijave.broj());
        model.addAttribute("brojevi", brojevi);
        model.addAttribute("poKategoriji", poi.brojPoKategoriji());
        return "podaci";
    }

    @GetMapping("/ugradnja")
    public String ugradnja(Model model, HttpServletRequest zahtjev) {
        String primjer = primjeri().isEmpty() ? "Gorjanovićeva ulica 15" : primjeri().getFirst();
        String bazniUrl = zahtjev.getScheme() + "://" + zahtjev.getServerName()
                + (zahtjev.getServerPort() == 80 || zahtjev.getServerPort() == 443 ? "" : ":" + zahtjev.getServerPort());
        model.addAttribute("aktivno", "ugradnja");
        model.addAttribute("primjer", primjer);
        model.addAttribute("bazniUrl", bazniUrl);
        model.addAttribute("kodUgradnje", "<iframe src=\"" + bazniUrl + "/widget?adresa="
                + URLEncoder.encode(primjer, StandardCharsets.UTF_8) + "\" title=\"Karton adrese\" width=\"380\" "
                + "height=\"420\" style=\"border:0\" loading=\"lazy\"></iframe>");
        return "ugradnja";
    }

    /** Sažeti karton za ugradnju u oglas za nekretninu (iframe). */
    @GetMapping("/widget")
    public String widget(@RequestParam String adresa, @RequestParam(defaultValue = "opcenito") String profil, Model model) {
        kartoni.karton(adresa, Profil.izTeksta(profil)).ifPresent(k -> model.addAttribute("karton", k));
        model.addAttribute("adresa", adresa);
        return "widget";
    }

    @PostMapping("/prijava")
    public String prijava(@RequestParam String objekt, @RequestParam(required = false) String izvor,
                          @RequestParam(required = false) String adresa, @RequestParam(defaultValue = "") String opis,
                          @RequestParam(required = false) String email,
                          @RequestParam(defaultValue = "/") String povratak, RedirectAttributes ra) {
        String sigurniPovratak = povratak.startsWith("/") && !povratak.startsWith("//") ? povratak : "/";
        if (opis.trim().length() < 5) {
            ra.addFlashAttribute("greskaPrijave", "Opišite što nije točno (barem nekoliko riječi).");
            return "redirect:" + sigurniPovratak;
        }
        if (email != null && !email.isBlank() && !EMAIL.matcher(email.trim()).matches()) {
            ra.addFlashAttribute("greskaPrijave", "E-mail adresa nije ispravna. Polje je neobavezno – možete ga ostaviti prazno.");
            return "redirect:" + sigurniPovratak;
        }
        prijave.spremi(objekt, izvor, adresa, opis.trim(), email);
        ra.addFlashAttribute("poruka", "Hvala! Prijava je zaprimljena.");
        return "redirect:" + sigurniPovratak;
    }

    private List<String> primjeri() {
        if (svojstva.primjeriAdresa() != null && !svojstva.primjeriAdresa().isEmpty()) {
            return svojstva.primjeriAdresa();
        }
        return adrese.sve().stream().map(Lokacija::adresa).limit(3).toList();
    }

    /** Podaci za JavaScript kartu (siguran unos u &lt;script type="application/json"&gt;). */
    static String jsonZaKartu(Karton k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("adresa", k.adresa());
        m.put("lat", k.lat());
        m.put("lon", k.lon());
        m.put("polumjer", k.polumjer15M());
        List<Object> objekti = new ArrayList<>();
        for (Karton.Objekt o : k.naKarti()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("kategorija", o.kategorija());
            x.put("kategorijaNaziv", o.kategorijaNaziv());
            x.put("boja", o.boja());
            x.put("naziv", o.naziv());
            x.put("lat", o.lat());
            x.put("lon", o.lon());
            x.put("udaljenost", o.udaljenostM());
            x.put("udaljenostTekst", o.udaljenost());
            x.put("izvor", o.izvor());
            objekti.add(x);
        }
        m.put("objekti", objekti);
        if (k.zrak() != null) {
            List<Object> mjeseci = new ArrayList<>();
            k.zrak().procjena().forEach(p -> mjeseci.add(Map.of("mjesec", p.mjesec(), "pm10", p.pm10(),
                    "donja", p.donja(), "gornja", p.gornja())));
            m.put("zrak", mjeseci);
        }
        return Json.zapisi(m).replace("</", "<\\/");
    }
}
