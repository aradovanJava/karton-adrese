package hr.kartonadrese.core.model;

import java.util.Arrays;
import java.util.List;

/**
 * Zajednički šifrarnik kategorija objekata (semantička interoperabilnost).
 *
 * <p>Svaki izvor se pri uvozu mapira na jednu kategoriju. Kategorije označene kao
 * "svakodnevne" ulaze u indeks grada 15 minuta (0–6).
 */
public enum Kategorija {
    SKOLA("Osnovna škola", "Osnovne škole", true, "#2F63B0"),
    VRTIC("Dječji vrtić", "Dječji vrtići", true, "#2F63B0"),
    LJEKARNA("Ljekarna", "Ljekarne", true, "#1F7A4D"),
    ZDRAVSTVO("Dom zdravlja ili ambulanta", "Zdravstvene ustanove", true, "#1F7A4D"),
    STAJALISTE("Stajalište javnog prijevoza", "Stajališta", true, "#9A5B00"),
    TRGOVINA("Trgovina prehrambenim proizvodima", "Trgovine", true, "#7A4E9E"),
    SREDNJA_SKOLA("Srednja škola", "Srednje škole", false, "#2F63B0"),
    KNJIZNICA("Knjižnica", "Knjižnice", false, "#5B6474"),
    KULTURA("Kulturna ustanova", "Kulturne ustanove", false, "#5B6474"),
    PARK("Park", "Parkovi", false, "#3A7D44"),
    SPORT("Sportski objekt ili igralište", "Sportski objekti", false, "#3A7D44"),
    PRISTUPACNOST("Ustanova za osobe s invaliditetom", "Ustanove za osobe s invaliditetom", false, "#6A4BC4"),
    PJESACKA_ZONA("Pješačka zona", "Pješačke zone", false, "#6A4BC4"),
    ZBORNO_MJESTO("Zborno mjesto civilne zaštite", "Zborna mjesta", false, "#B42318"),
    EVAKUACIJA("Evakuacijska površina", "Evakuacijske površine", false, "#B42318"),
    GARAZA("Javna garaža", "Javne garaže", false, "#5B6474"),
    OSTALO("Ostalo", "Ostalo", false, "#5B6474");

    private final String naziv;
    private final String mnozina;
    private final boolean svakodnevna;
    private final String boja;

    Kategorija(String naziv, String mnozina, boolean svakodnevna, String boja) {
        this.naziv = naziv;
        this.mnozina = mnozina;
        this.svakodnevna = svakodnevna;
        this.boja = boja;
    }

    public String getNaziv() {
        return naziv;
    }

    public String getMnozina() {
        return mnozina;
    }

    public boolean isSvakodnevna() {
        return svakodnevna;
    }

    public String getBoja() {
        return boja;
    }

    /** Kategorije koje čine indeks grada 15 minuta. */
    public static List<Kategorija> svakodnevne() {
        return Arrays.stream(values()).filter(Kategorija::isSvakodnevna).toList();
    }

    public static Kategorija izTeksta(String s) {
        if (s == null || s.isBlank()) {
            return OSTALO;
        }
        try {
            return valueOf(s.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return OSTALO;
        }
    }
}
