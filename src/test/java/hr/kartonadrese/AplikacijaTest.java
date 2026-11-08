package hr.kartonadrese;

import hr.kartonadrese.core.ml.SezonskaProcjena;
import hr.kartonadrese.ml.ModelZraka;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cijela aplikacija u oglednom načinu (H2 + ogledni podaci + ogledni ONNX model):
 * REST API, Thymeleaf stranice, otvoreni skup podataka i model kvalitete zraka.
 */
@SpringBootTest
class AplikacijaTest {

    private static final String ADRESA = "Gorjanovićeva ulica 15";

    @Autowired
    private WebApplicationContext kontekst;

    @Autowired
    private ModelZraka modelZraka;

    private MockMvc mvc;

    @BeforeEach
    void priprema() {
        mvc = MockMvcBuilders.webAppContextSetup(kontekst).build();
    }

    @Test
    void kartonApiVracaPodatkeSIzvorima() throws Exception {
        mvc.perform(get("/api/karton").param("adresa", ADRESA).param("profil", "obitelj"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adresa").value(ADRESA))
                .andExpect(jsonPath("$.gradskaCetvrt").value("Maksimir"))
                .andExpect(jsonPath("$.profil").value("OBITELJ"))
                .andExpect(jsonPath("$.obrazovanje.skole[0].naziv").value("Osnovna škola Vladimira Nazora"))
                .andExpect(jsonPath("$.obrazovanje.skole[0].izvor").value("Grad Zagreb"))
                .andExpect(jsonPath("$.obrazovanje.skole[0].udaljenostM").value(260))
                .andExpect(jsonPath("$.javnePovrsine.igralista.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.mojIndeks.stavke.length()").value(greaterThan(2)))
                .andExpect(jsonPath("$.prijevoz.stajalista.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.okolica.najvise").value(6))
                .andExpect(jsonPath("$.zrak.procjena.length()").value(12))
                .andExpect(jsonPath("$.sazetak.length()").value(greaterThan(0)));
    }

    @Test
    void studentskiInteresiIFakultet() throws Exception {
        String popis = mvc.perform(get("/api/visoka-ucilista")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\{\"id\":(\\d+),\"naziv\":\"Ekonomski fakultet\"").matcher(popis);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(), "Ekonomski fakultet je u popisu visokih učilišta");
        String id = m.group(1);
        mvc.perform(get("/api/karton").param("adresa", ADRESA).param("profil", "student")
                        .param("interes", "menza").param("fakultet", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.stavke[0].kategorija").value("MENZA"))
                .andExpect(jsonPath("$.student.stavke[0].razlog").value(containsString("Menza")))
                .andExpect(jsonPath("$.student.fakultet.naziv").value("Ekonomski fakultet"))
                .andExpect(jsonPath("$.student.fakultet.udaljenostM").value(greaterThan(500)))
                .andExpect(jsonPath("$.redoslijed[0]").value("STUDENT"));
        mvc.perform(get("/karton").param("adresa", ADRESA).param("profil", "student").param("interes", "bicikl"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Studentski život")))
                .andExpect(content().string(containsString("Istaknuto jer ste označili: Bicikl")))
                .andExpect(content().string(containsString("Fakultet ili visoko učilište")));
    }

    @Test
    void prikazUMetrimaJavnePovrsineIPristupacnost() throws Exception {
        mvc.perform(get("/karton").param("adresa", ADRESA).param("profil", "obitelj"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("260 m")))
                .andExpect(content().string(containsString("zračne")))
                .andExpect(content().string(containsString("Javne površine i igra")))
                .andExpect(content().string(containsString("Moj indeks")));
        mvc.perform(get("/karton").param("adresa", ADRESA).param("profil", "osi").param("potreba", "kolica"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pristupačnost stajališta i vozila")))
                .andExpect(content().string(containsString("nema podatka")))
                .andExpect(content().string(containsString("Krećem se u kolicima")));
    }

    @Test
    void nepoznataAdresaVraca404SObjasnjenjem() throws Exception {
        mvc.perform(get("/api/karton").param("adresa", "Nepostojeća ulica 999"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/karton").param("adresa", "Nepostojeća ulica 999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nismo pronašli")));
    }

    @Test
    void usporedbaDvijuAdresa() throws Exception {
        mvc.perform(get("/api/usporedba").param("a", ADRESA).param("b", "Ilica 1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redovi.length()").value(greaterThan(5)));
        mvc.perform(get("/api/usporedba").param("a", ADRESA).param("b", ADRESA))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/usporedba").param("a", ADRESA).param("b", "Ilica 1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bliže / dostupnije")))
                .andExpect(content().string(containsString("Igralište")));
    }

    @Test
    void stranicePrikazujuSadrzaj() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Što oglas ne piše")));
        mvc.perform(get("/karton").param("adresa", ADRESA)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Vladimira Nazora")))
                .andExpect(content().string(containsString("Vrijedi znati")));
        for (String stranica : List.of("/usporedba", "/karta", "/podaci", "/ugradnja")) {
            mvc.perform(get(stranica)).andExpect(status().isOk());
        }
        mvc.perform(get("/widget").param("adresa", ADRESA)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Cijeli karton")));
    }

    @Test
    void otvoreniSkupPodatakaIDcat() throws Exception {
        mvc.perform(get("/api/otvoreni-podaci/indeks15.geojson"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/geo+json")))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(greaterThan(10)));
        mvc.perform(get("/api/otvoreni-podaci/dcat.jsonld"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['dct:license']").value("https://creativecommons.org/licenses/by/4.0/"));
        mvc.perform(get("/api/otvoreni-podaci/indeks15.csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("celija;lat;lon;indeks15")));
    }

    @Test
    void prijavaPogreske() throws Exception {
        mvc.perform(post("/api/prijava").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objekt\":\"Dječji vrtić Bukovac\",\"izvor\":\"Ogledni podaci\",\"opis\":\"Vrtić je preseljen.\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/prijava").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objekt\":\"X\",\"opis\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/prijava").param("objekt", "Dječji vrtić Bukovac").param("opis", "Vrtić je preseljen.")
                        .param("povratak", "/karton?adresa=Ilica%201"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void adminApiJeIskljucenBezTokena() throws Exception {
        mvc.perform(post("/api/admin/uvoz")).andExpect(status().isForbidden());
    }

    @Test
    void onnxModelUJaviDajeIstiProfilKaoPython() {
        ModelZraka.UcitaniModel m = modelZraka.zaPostaju("djordjiceva").orElseThrow();
        assertNotNull(m.sesija(), "ONNX sesija nije učitana");
        List<SezonskaProcjena.Mjesec> java = modelZraka.profil(m, m.meta().kontrolnaGodina());
        List<SezonskaProcjena.Mjesec> python = m.meta().kontrolniProfil();
        assertEquals(12, java.size());
        for (int i = 0; i < 12; i++) {
            assertEquals(python.get(i).pm10(), java.get(i).pm10(), 0.05, "mjesec " + (i + 1));
        }
    }
}
