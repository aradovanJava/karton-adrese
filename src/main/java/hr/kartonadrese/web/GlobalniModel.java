package hr.kartonadrese.web;

import hr.kartonadrese.config.KartonSvojstva;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Atributi dostupni svim stranicama (npr. oznaka oglednog načina rada u podnožju). */
@ControllerAdvice(assignableTypes = StraniceKontroler.class)
public class GlobalniModel {

    private final KartonSvojstva svojstva;

    public GlobalniModel(KartonSvojstva svojstva) {
        this.svojstva = svojstva;
    }

    @ModelAttribute("ogledniNacin")
    public boolean ogledniNacin() {
        return svojstva.ogledniPodaci();
    }

    @ModelAttribute("nazivPodrucja")
    public String nazivPodrucja() {
        return svojstva.podrucje() == null ? "Grad Zagreb" : svojstva.podrucje().naziv();
    }
}
