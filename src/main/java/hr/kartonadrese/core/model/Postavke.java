package hr.kartonadrese.core.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Osobne postavke prikaza: odabrano visoko učilište, interesi i potrebe. Žive samo u
 * poveznici (parametri URL-a) – aplikacija ih ne sprema.
 *
 * @param fakultetId identifikator objekta kategorije {@link Kategorija#VISOKO_UCILISTE} ili {@code null}
 */
public record Postavke(Long fakultetId, Set<Interes> interesi, Set<Potreba> potrebe) {

    public static final Postavke PRAZNE = new Postavke(null, EnumSet.noneOf(Interes.class), EnumSet.noneOf(Potreba.class));

    public Postavke {
        interesi = interesi == null || interesi.isEmpty() ? EnumSet.noneOf(Interes.class) : EnumSet.copyOf(interesi);
        potrebe = potrebe == null || potrebe.isEmpty() ? EnumSet.noneOf(Potreba.class) : EnumSet.copyOf(potrebe);
    }

    public static Postavke iz(String fakultet, List<String> interesi, List<String> potrebe) {
        Long id = null;
        if (fakultet != null && !fakultet.isBlank()) {
            try {
                id = Long.parseLong(fakultet.trim());
            } catch (NumberFormatException e) {
                id = null;
            }
        }
        return new Postavke(id, Interes.izTeksta(interesi), Potreba.izTeksta(potrebe));
    }

    public boolean imaInteres(Interes i) {
        return interesi.contains(i);
    }

    public boolean imaPotrebu(Potreba p) {
        return potrebe.contains(p);
    }

    /** Vrijednost parametra "interesi" za poveznicu ("menza,bicikl"). */
    public String interesiParam() {
        return interesi.stream().map(Interes::getOznaka).collect(Collectors.joining(","));
    }

    public String potrebeParam() {
        return potrebe.stream().map(Potreba::getOznaka).collect(Collectors.joining(","));
    }
}
