package hr.kartonadrese.api;

import hr.kartonadrese.baza.PrijavaRepozitorij;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.uvoz.UvozServis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Administrativni API (pokretanje uvoza, pregled prijava). Isključen je dok se ne postavi
 * {@code karton.admin.token} (npr. varijabla okruženja KARTON_ADMIN_TOKEN); zahtjev mora
 * poslati zaglavlje {@code X-Admin-Token}.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administracija", description = "Zahtijeva zaglavlje X-Admin-Token")
public class AdminKontroler {

    private final UvozServis uvoz;
    private final PrijavaRepozitorij prijave;
    private final KartonSvojstva svojstva;

    public AdminKontroler(UvozServis uvoz, PrijavaRepozitorij prijave, KartonSvojstva svojstva) {
        this.uvoz = uvoz;
        this.prijave = prijave;
        this.svojstva = svojstva;
    }

    @PostMapping("/uvoz")
    @Operation(summary = "Pokreni uvoz svih izvora ili jednog izvora (izvor=<id>)")
    public UvozServis.Izvjestaj uvoz(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                     @RequestParam(defaultValue = "sve") String izvor) {
        provjeri(token);
        return uvoz.pokreni(izvor);
    }

    @GetMapping("/prijave")
    @Operation(summary = "Zadnjih 200 prijava pogrešaka (za prosljeđivanje izdavateljima)")
    public List<PrijavaRepozitorij.Prijava> prijave(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        provjeri(token);
        return prijave.zadnje(200);
    }

    private void provjeri(String token) {
        String ocekivani = svojstva.admin() == null ? null : svojstva.admin().token();
        if (ocekivani == null || ocekivani.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrativni API je isključen (karton.admin.token)");
        }
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                ocekivani.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Neispravan X-Admin-Token");
        }
    }
}
