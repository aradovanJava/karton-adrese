package hr.kartonadrese.uvoz;

import hr.kartonadrese.config.KartonSvojstva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pokretanje uvoza iz naredbenog retka:
 * <pre>
 *   java -jar karton-adrese.jar --uvoz=sve            # uvoz svih izvora, zatim aplikacija radi dalje
 *   java -jar karton-adrese.jar --uvoz=zet-gtfs --zatvori   # samo jedan izvor, pa izlaz
 * </pre>
 * Ako je {@code karton.uvoz.pri-pokretanju=true}, uvoz se pokreće u pozadini pri svakom startu.
 */
@Component
@Order(2)
public class UvozPokretac implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(UvozPokretac.class);

    private final UvozServis uvoz;
    private final KartonSvojstva svojstva;
    private final ApplicationContext kontekst;

    public UvozPokretac(UvozServis uvoz, KartonSvojstva svojstva, ApplicationContext kontekst) {
        this.uvoz = uvoz;
        this.svojstva = svojstva;
        this.kontekst = kontekst;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("uvoz")) {
            List<String> trazeno = args.getOptionValues("uvoz");
            String izvor = trazeno == null || trazeno.isEmpty() || trazeno.getFirst().isBlank() ? "sve" : trazeno.getFirst();
            UvozServis.Izvjestaj r = uvoz.pokreni(izvor);
            r.poruke().forEach(p -> LOG.info("  {}", p));
            LOG.info("Uvoz završen: {} uspješno, {} s pogreškom", r.uspjesno(), r.neuspjesno());
            if (args.containsOption("zatvori")) {
                int kod = r.neuspjesno() > 0 && r.uspjesno() == 0 ? 1 : 0;
                System.exit(SpringApplication.exit(kontekst, () -> kod));
            }
            return;
        }
        if (svojstva.uvoz() != null && svojstva.uvoz().priPokretanju()) {
            Thread t = new Thread(() -> uvoz.pokreni(null), "uvoz-pri-pokretanju");
            t.setDaemon(true);
            t.start();
        }
    }
}
