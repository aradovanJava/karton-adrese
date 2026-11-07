package hr.kartonadrese.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import hr.kartonadrese.config.KartonSvojstva;
import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.ml.MetapodaciModela;
import hr.kartonadrese.core.ml.SezonskaProcjena;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Učitava LightGBM modele izvezene u ONNX ({@code model/<postaja>/pm10.onnx}) i računa
 * sezonsku procjenu PM10. Ako model ne postoji ili se ne može učitati, aplikacija i dalje
 * radi – sekcija "Kvaliteta zraka" tada prikazuje samo izmjerene vrijednosti.
 */
@Component
public class ModelZraka {

    private static final Logger LOG = LoggerFactory.getLogger(ModelZraka.class);

    private final Path mapa;
    private final List<UcitaniModel> modeli = new ArrayList<>();
    private final Map<String, List<SezonskaProcjena.Mjesec>> predmemorija = new ConcurrentHashMap<>();
    private OrtEnvironment okruzenje;

    public ModelZraka(KartonSvojstva svojstva) {
        this.mapa = Path.of(svojstva.model() == null || svojstva.model().mapa() == null ? "model" : svojstva.model().mapa());
    }

    /** Model jedne postaje: metapodaci i (ako je učitan) ONNX sesija. */
    public record UcitaniModel(MetapodaciModela meta, OrtSession sesija) {
    }

    /** Najbliža postaja s modelom i udaljenost do nje u metrima. */
    public record NajblizaPostaja(UcitaniModel model, double udaljenostM) {
    }

    @PostConstruct
    void ucitaj() {
        if (!Files.isDirectory(mapa)) {
            LOG.warn("Mapa s modelima {} ne postoji – procjena PM10 nije dostupna", mapa.toAbsolutePath());
            return;
        }
        try (DirectoryStream<Path> postaje = Files.newDirectoryStream(mapa, p -> Files.isDirectory(p))) {
            for (Path p : postaje) {
                Path metaDatoteka = p.resolve("metapodaci.json");
                if (!Files.exists(metaDatoteka)) {
                    continue;
                }
                MetapodaciModela meta = MetapodaciModela.izJsona(Files.readString(metaDatoteka));
                OrtSession sesija = null;
                Path onnx = p.resolve("pm10.onnx");
                if (Files.exists(onnx)) {
                    try {
                        if (okruzenje == null) {
                            okruzenje = OrtEnvironment.getEnvironment();
                        }
                        sesija = okruzenje.createSession(onnx.toString(), new OrtSession.SessionOptions());
                    } catch (OrtException | RuntimeException | UnsatisfiedLinkError e) {
                        LOG.warn("ONNX model {} nije učitan: {}", onnx, e.getMessage());
                    }
                }
                modeli.add(new UcitaniModel(meta, sesija));
                LOG.info("Model PM10 za postaju {} ({}){}", meta.postajaNaziv(), sesija != null ? "ONNX" : "bez ONNX-a",
                        meta.ogledni() ? " – OGLEDNI, treniran na sintetičkim podacima" : "");
            }
        } catch (IOException e) {
            LOG.warn("Modeli nisu učitani: {}", e.getMessage());
        }
    }

    public List<UcitaniModel> modeli() {
        return List.copyOf(modeli);
    }

    public Optional<NajblizaPostaja> najblizi(double lat, double lon) {
        return modeli.stream()
                .map(m -> new NajblizaPostaja(m, GeoUtil.udaljenostM(lat, lon, m.meta().lat(), m.meta().lon())))
                .min(Comparator.comparingDouble(NajblizaPostaja::udaljenostM));
    }

    public Optional<UcitaniModel> zaPostaju(String postajaId) {
        return modeli.stream().filter(m -> m.meta().postajaId().equals(postajaId)).findFirst();
    }

    /** Sezonska procjena za tekuću godinu (rezultat se pamti po postaji). */
    public List<SezonskaProcjena.Mjesec> sezonskaProcjena(UcitaniModel m) {
        int godina = Year.now().getValue();
        return predmemorija.computeIfAbsent(m.meta().postajaId() + "@" + godina, k -> profil(m, godina));
    }

    /** Mjesečni profil za zadanu godinu (bez predmemorije; koristi se i u testu usporedbe s Pythonom). */
    public List<SezonskaProcjena.Mjesec> profil(UcitaniModel m, int godina) {
        if (m.sesija() == null) {
            return SezonskaProcjena.izMjerenja(m.meta());
        }
        return SezonskaProcjena.izracunaj(m.meta(), godina, x -> predvidi(m, x));
    }

    private float[] predvidi(UcitaniModel m, float[][] x) {
        try (OnnxTensor ulaz = OnnxTensor.createTensor(okruzenje, x);
             OrtSession.Result rezultat = m.sesija().run(Map.of(m.meta().ulaz(), ulaz))) {
            Object vrijednost = rezultat.get(0).getValue();
            float[] p = new float[x.length];
            if (vrijednost instanceof float[][] dvodim) {
                for (int i = 0; i < p.length; i++) {
                    p[i] = dvodim[i][0];
                }
            } else if (vrijednost instanceof float[] jednodim) {
                System.arraycopy(jednodim, 0, p, 0, p.length);
            } else {
                throw new IllegalStateException("Neočekivani izlaz modela: " + vrijednost.getClass());
            }
            return p;
        } catch (OrtException e) {
            throw new IllegalStateException("Pogreška ONNX modela: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void zatvori() {
        for (UcitaniModel m : modeli) {
            if (m.sesija() != null) {
                try {
                    m.sesija().close();
                } catch (OrtException e) {
                    LOG.debug("Zatvaranje sesije: {}", e.getMessage());
                }
            }
        }
    }
}
