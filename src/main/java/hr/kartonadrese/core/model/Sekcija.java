package hr.kartonadrese.core.model;

/** Sekcije kartona adrese (redoslijed ovisi o profilu korisnika). */
public enum Sekcija {
    STUDENT("Studentski život", "student"),
    OBRAZOVANJE("Obrazovanje", "obrazovanje"),
    PRIJEVOZ("Javni prijevoz", "prijevoz"),
    ZDRAVLJE("Zdravlje", "zdravlje"),
    ZRAK("Kvaliteta zraka", "zrak"),
    PRISTUPACNOST("Pristupačnost", "pristupacnost"),
    POTRES("U slučaju potresa", "potres"),
    JAVNE_POVRSINE("Javne površine i igra", "javne-povrsine"),
    OKOLICA("Okolica u 15 minuta", "okolica"),
    STANOVNISTVO("Stanovništvo", "stanovnistvo");

    private final String naslov;
    private final String sidro;

    Sekcija(String naslov, String sidro) {
        this.naslov = naslov;
        this.sidro = sidro;
    }

    public String getNaslov() {
        return naslov;
    }

    public String getSidro() {
        return sidro;
    }
}
