package hr.kartonadrese.uvoz;

import hr.kartonadrese.analitika.Indeks15Servis;
import hr.kartonadrese.baza.AdresaRepozitorij;
import hr.kartonadrese.baza.CelijaRepozitorij;
import hr.kartonadrese.baza.IzvorRepozitorij;
import hr.kartonadrese.baza.PoiRepozitorij;
import hr.kartonadrese.baza.PostajaZrakaRepozitorij;
import hr.kartonadrese.baza.StajalisteRepozitorij;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.geo.Laea3035;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Lokacija;
import hr.kartonadrese.core.model.Poi;
import hr.kartonadrese.core.model.Stajaliste;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ogledni (sintetički) podaci za rad bez interneta: pet adresa u Zagrebu i objekti oko njih.
 * Učitavaju se samo ako je {@code karton.ogledni-podaci=true} i baza je prazna.
 * Svi ogledni izvori su na stranici "O podacima" jasno označeni kao ogledni.
 */
@Component
@Order(1)
public class OgledniPodaci implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(OgledniPodaci.class);

    private final KartonSvojstva svojstva;
    private final UvozServis uvoz;
    private final IzvorRepozitorij izvori;
    private final PoiRepozitorij poi;
    private final StajalisteRepozitorij stajalista;
    private final AdresaRepozitorij adrese;
    private final PostajaZrakaRepozitorij postaje;
    private final CelijaRepozitorij celije;
    private final Indeks15Servis indeks15;

    public OgledniPodaci(KartonSvojstva svojstva, UvozServis uvoz, IzvorRepozitorij izvori, PoiRepozitorij poi,
                         StajalisteRepozitorij stajalista, AdresaRepozitorij adrese, PostajaZrakaRepozitorij postaje,
                         CelijaRepozitorij celije, Indeks15Servis indeks15) {
        this.svojstva = svojstva;
        this.uvoz = uvoz;
        this.izvori = izvori;
        this.poi = poi;
        this.stajalista = stajalista;
        this.adrese = adrese;
        this.postaje = postaje;
        this.celije = celije;
        this.indeks15 = indeks15;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        uvoz.registrirajIzvore();
        if (!svojstva.ogledniPodaci() || poi.broj() > 0) {
            return;
        }
        ClassPathResource r = new ClassPathResource("ogledni/podaci.json");
        if (!r.exists()) {
            LOG.warn("Ogledni podaci nisu pronađeni (ogledni/podaci.json)");
            return;
        }
        String tekst;
        try (InputStream in = r.getInputStream()) {
            tekst = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, Object> podaci = Json.objekt(tekst);

        for (Object o : Json.niz(podaci, "izvori")) {
            String id = Json.tekst(o, "id").orElseThrow();
            izvori.registriraj(id, Json.tekst(o, "naziv").orElse(id), Json.tekst(o, "izdavatelj").orElse(null),
                    Json.tekst(o, "licenca").orElse(null), Json.tekst(o, "poveznica").orElse(null), "OGLEDNI");
        }

        Map<String, List<Poi>> poIzvoru = new LinkedHashMap<>();
        for (Object o : Json.niz(podaci, "objekti")) {
            Map<String, String> atributi = new LinkedHashMap<>();
            Json.mapa(o, "atributi").forEach((k, v) -> atributi.put(k, String.valueOf(v)));
            String izvor = Json.tekst(o, "izvor").orElse("ogledni");
            poIzvoru.computeIfAbsent(izvor, k -> new ArrayList<>()).add(new Poi(0, izvor,
                    Kategorija.izTeksta(Json.tekst(o, "kategorija").orElse(null)), Json.tekst(o, "naziv").orElse("?"),
                    Json.tekst(o, "adresa").orElse(null), Json.broj(o, "lat").orElseThrow(),
                    Json.broj(o, "lon").orElseThrow(), atributi, Json.tekst(o, "datum").map(LocalDate::parse).orElse(null)));
        }
        Map<String, Integer> brojPoIzvoru = new HashMap<>();
        poIzvoru.forEach((izvor, lista) -> brojPoIzvoru.put(izvor, poi.zamijeni(izvor, lista)));

        List<Stajaliste> s = new ArrayList<>();
        for (Object o : Json.niz(podaci, "stajalista")) {
            s.add(new Stajaliste(Json.tekst(o, "id").orElseThrow(), Json.tekst(o, "naziv").orElse("?"),
                    Json.broj(o, "lat").orElseThrow(), Json.broj(o, "lon").orElseThrow(),
                    Json.niz(o, "linije").stream().map(String::valueOf).toList(),
                    Boolean.TRUE.equals(((Map<?, ?>) o).get("tramvaj")), Boolean.TRUE.equals(((Map<?, ?>) o).get("autobus")),
                    Json.broj(o, "polazaka").orElse(0.0).intValue()));
        }
        stajalista.zamijeni(s);

        for (Object o : Json.niz(podaci, "adrese")) {
            adrese.spremi(new Lokacija(Json.tekst(o, "adresa").orElseThrow(), Json.broj(o, "lat").orElseThrow(),
                    Json.broj(o, "lon").orElseThrow(), Json.tekst(o, "gradska_cetvrt").orElse(null),
                    Json.tekst(o, "mjesni_odbor").orElse(null), "ogledni"));
        }
        adrese.zamijeniUlice(Json.niz(podaci, "ulice").stream().map(String::valueOf).toList());

        for (Object o : Json.niz(podaci, "postaje")) {
            postaje.spremi(new PostajaZrakaRepozitorij.PostajaZraka(Json.tekst(o, "id").orElseThrow(),
                    Json.tekst(o, "naziv").orElse("?"), Json.tekst(o, "mreza").orElse(null),
                    Json.broj(o, "lat").orElseThrow(), Json.broj(o, "lon").orElseThrow(),
                    Json.broj(o, "godina").map(Double::intValue).orElse(null), Json.broj(o, "prosjek_pm10").orElse(null),
                    Json.broj(o, "dana_iznad_50").map(Double::intValue).orElse(null), Json.tekst(o, "izvor").orElse(null)));
        }

        Map<String, double[]> sredista = new HashMap<>();
        Map<String, Integer> stanovnika = new HashMap<>();
        Json.mapa(podaci, "celije").forEach((id, n) -> {
            sredista.put(id, Laea3035.srediste(id));
            stanovnika.put(id, ((Number) n).intValue());
        });
        celije.zamijeniStanovnistvo(sredista, stanovnika);

        for (Map.Entry<String, Integer> e : brojPoIzvoru.entrySet()) {
            izvori.zabiljeziUspjeh(e.getKey(), "OGLEDNI", new IzvorRepozitorij.Statistika(e.getValue(), e.getValue(),
                    0, 0, 0, LocalDate.now(), "ogledni (sintetički) podaci za rad bez interneta"));
        }
        int n = indeks15.izracunajSve();
        LOG.info("Učitani ogledni podaci: {} objekata, {} stajališta, {} ćelija s indeksom",
                poi.broj(), stajalista.broj(), n);
    }
}
