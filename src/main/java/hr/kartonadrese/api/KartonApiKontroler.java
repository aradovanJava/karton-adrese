package hr.kartonadrese.api;

import hr.kartonadrese.analitika.Karton;
import hr.kartonadrese.analitika.KartonServis;
import hr.kartonadrese.analitika.UsporedbaServis;
import hr.kartonadrese.baza.CelijaRepozitorij;
import hr.kartonadrese.baza.IzvorRepozitorij;
import hr.kartonadrese.baza.PrijavaRepozitorij;
import hr.kartonadrese.baza.ProstornaPretraga;
import hr.kartonadrese.core.analitika.Blizina;
import hr.kartonadrese.core.model.Celija;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Profil;
import hr.kartonadrese.core.ml.SezonskaProcjena;
import hr.kartonadrese.geokod.Geokoder;
import hr.kartonadrese.ml.ModelZraka;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Karton API (OpenAPI 3, dokumentacija na /swagger-ui.html). */
@RestController
@RequestMapping("/api")
@Tag(name = "Karton adrese", description = "Javni podaci spojeni po adresi")
public class KartonApiKontroler {

    private final KartonServis kartoni;
    private final UsporedbaServis usporedba;
    private final Geokoder geokoder;
    private final ProstornaPretraga pretraga;
    private final CelijaRepozitorij celije;
    private final IzvorRepozitorij izvori;
    private final PrijavaRepozitorij prijave;
    private final ModelZraka modelZraka;

    public KartonApiKontroler(KartonServis kartoni, UsporedbaServis usporedba, Geokoder geokoder,
                              ProstornaPretraga pretraga, CelijaRepozitorij celije, IzvorRepozitorij izvori,
                              PrijavaRepozitorij prijave, ModelZraka modelZraka) {
        this.kartoni = kartoni;
        this.usporedba = usporedba;
        this.geokoder = geokoder;
        this.pretraga = pretraga;
        this.celije = celije;
        this.izvori = izvori;
        this.prijave = prijave;
        this.modelZraka = modelZraka;
    }

    @GetMapping("/karton")
    @Operation(summary = "Karton adrese", description = "Obrazovanje, prijevoz, zdravlje, kvaliteta zraka, pristupačnost, "
            + "sigurnost, okolica u 15 minuta i stanovništvo za zadanu adresu u Zagrebu.")
    public Karton karton(@Parameter(example = "Gorjanovićeva ulica 15") @RequestParam String adresa,
                         @Parameter(description = "opcenito, obitelj, student, umirovljenik, osi")
                         @RequestParam(defaultValue = "opcenito") String profil) {
        return kartoni.karton(adresa, Profil.izTeksta(profil))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Adresu nismo pronašli. Provjerite kućni broj ili odaberite adresu s popisa."));
    }

    @GetMapping("/usporedba")
    @Operation(summary = "Usporedba dviju adresa po istim kriterijima")
    public UsporedbaServis.Usporedba usporedba(@RequestParam String a, @RequestParam String b,
                                               @RequestParam(defaultValue = "opcenito") String profil) {
        if (a.trim().equalsIgnoreCase(b.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Odaberite dvije različite adrese.");
        }
        return usporedba.usporedi(a, b, Profil.izTeksta(profil))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jednu od adresa nismo pronašli."));
    }

    /** Objekt u okolici točke. */
    public record ObjektOkolice(String kategorija, String naziv, String adresa, double lat, double lon, int udaljenostM,
                                int minutaHoda, String izvor) {
    }

    @GetMapping("/okolica")
    @Operation(summary = "Objekti u zadanom polumjeru oko točke (WGS84)")
    public List<ObjektOkolice> okolica(@RequestParam double lat, @RequestParam double lon,
                                       @Parameter(description = "polumjer u metrima, najviše 3000")
                                       @RequestParam(defaultValue = "800") double r,
                                       @RequestParam(required = false) List<Kategorija> kategorije) {
        double polumjer = Math.min(Math.max(r, 50), 3000);
        return Blizina.sortiraj(lat, lon, pretraga.uKrugu(lat, lon, polumjer, kategorije),
                        Profil.OPCENITO.getBrzinaHodaMMin(), polumjer).stream()
                .map(pu -> new ObjektOkolice(pu.poi().kategorija().name(), pu.poi().naziv(), pu.poi().adresa(),
                        pu.poi().lat(), pu.poi().lon(), (int) Math.round(pu.udaljenostM()), pu.minutaHoda(),
                        pu.poi().izvorId()))
                .toList();
    }

    @GetMapping("/indeks15")
    @Operation(summary = "Indeks grada 15 minuta po ćeliji 1 km² (sve ćelije ili jedna)")
    public Object indeks15(@Parameter(example = "CRS3035RES1000mN2540000E4785000")
                           @RequestParam(required = false) String celija) {
        if (celija == null || celija.isBlank()) {
            return celije.sve();
        }
        Celija c = celije.nadji(celija).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Ćelija " + celija + " ne postoji"));
        return Map.of("celija", c, "nedostaje", c.nedostaje().stream().map(Kategorija::getNaziv).toList());
    }

    /** Sezonska procjena PM10 za postaju. */
    public record PrognozaZraka(String postajaId, String postaja, boolean ogledniModel, List<SezonskaProcjena.Mjesec> mjesecno,
                                String metoda) {
    }

    @GetMapping("/prognoza/zrak")
    @Operation(summary = "Sezonska procjena PM10 (LightGBM, ONNX) za najbližu postaju ili zadanu postaju")
    public PrognozaZraka prognozaZraka(@RequestParam(required = false) String postaja,
                                       @RequestParam(required = false) Double lat,
                                       @RequestParam(required = false) Double lon) {
        ModelZraka.UcitaniModel m;
        if (postaja != null) {
            m = modelZraka.zaPostaju(postaja).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Nema modela za postaju " + postaja));
        } else if (lat != null && lon != null) {
            m = modelZraka.najblizi(lat, lon).map(ModelZraka.NajblizaPostaja::model)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nijedan model nije učitan"));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zadajte 'postaja' ili 'lat' i 'lon'");
        }
        return new PrognozaZraka(m.meta().postajaId(), m.meta().postajaNaziv(), m.meta().ogledni(),
                modelZraka.sezonskaProcjena(m),
                "LightGBM nad log(1+PM10), tipična godina (klimatologija ERA5), pojas ≈80 % iz vremenske unakrsne provjere");
    }

    @GetMapping("/adrese")
    @Operation(summary = "Prijedlozi adresa za upisani tekst (lokalni registar, bez vanjskih poziva)")
    public List<String> adrese(@RequestParam("q") String upit) {
        return geokoder.prijedlozi(upit);
    }

    @GetMapping("/kvaliteta-podataka")
    @Operation(summary = "Izvješće o kvaliteti: stanje i statistika uvoza svakog izvora")
    public List<IzvorRepozitorij.IzvorStanje> kvaliteta() {
        return izvori.svi();
    }

    /** Prijava pogreške u podacima. */
    public record PrijavaZahtjev(@NotBlank @Size(max = 400) String objekt, @Size(max = 300) String izvor,
                                 @Size(max = 300) String adresa, @NotBlank @Size(min = 5, max = 2000) String opis,
                                 @Email @Size(max = 200) String email) {
    }

    @PostMapping("/prijava")
    @Operation(summary = "Prijava pogreške u podacima (povratna informacija izdavatelju)")
    public ResponseEntity<Map<String, String>> prijava(@Valid @RequestBody PrijavaZahtjev z) {
        prijave.spremi(z.objekt(), z.izvor(), z.adresa(), z.opis(), z.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("poruka", "Hvala! Prijava je zaprimljena."));
    }
}
