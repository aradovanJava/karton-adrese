package hr.kartonadrese.analitika;

import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.ml.SezonskaProcjena;
import hr.kartonadrese.core.model.Postavke;
import hr.kartonadrese.core.model.Profil;
import hr.kartonadrese.core.model.Sekcija;

import java.util.List;

/**
 * Karton adrese – sve što REST API vraća i što Thymeleaf prikazuje.
 * Uz svaki podatak su izvor i datum (transparentnost, H10). Udaljenosti su zračne, u metrima.
 */
public record Karton(
        String adresa,
        double lat,
        double lon,
        String gradskaCetvrt,
        String mjesniOdbor,
        Profil profil,
        List<Sekcija> redoslijed,
        List<Stavka> sazetak,
        List<Kartica> kartice,
        Obrazovanje obrazovanje,
        Prijevoz prijevoz,
        Zdravlje zdravlje,
        Zrak zrak,
        Pristupacnost pristupacnost,
        Potres potres,
        Okolica okolica,
        Stanovnistvo stanovnistvo,
        List<Objekt> naKarti,
        double polumjer15M,
        boolean ogledni,
        JavnePovrsine javnePovrsine,
        Student student,
        MojIndeks mojIndeks,
        Postavke postavke) {

    /**
     * Objekt u blizini adrese. {@code razlog} objašnjava isticanje (npr. odabrani interes),
     * a {@code pristupacnost} je stanje pristupačnosti (samo za profil osobe s invaliditetom).
     */
    public record Objekt(long id, String kategorija, String kategorijaNaziv, String boja, String naziv, String adresa,
                         double lat, double lon, int udaljenostM, int minutaHoda, String izvorId, String izvor,
                         String datum, List<String> detalji, boolean istaknut, String razlog, String pristupacnost) {

        /** Udaljenost za prikaz ("260 m", "1,2 km"). */
        public String udaljenost() {
            return GeoUtil.formatiraj(udaljenostM);
        }
    }

    /** Rečenica sažetka s izvorom. */
    public record Stavka(String tekst, String izvor, String datum) {
    }

    /** Kartica u gornjem dijelu kartona. */
    public record Kartica(String oznaka, String vrijednost, String ikona) {
    }

    public record Obrazovanje(List<Objekt> skole, List<Objekt> vrtici, List<Objekt> srednjeSkole) {
    }

    public record StajalisteBlizu(String naziv, String vrsta, List<String> linije, int udaljenostM, int minutaHoda,
                                  double lat, double lon) {

        public String udaljenost() {
            return GeoUtil.formatiraj(udaljenostM);
        }
    }

    public record Prijevoz(List<StajalisteBlizu> stajalista, int brojLinija, String izvor, String datum) {
    }

    public record Zdravlje(List<Objekt> zdravstvo, List<Objekt> ljekarne) {
    }

    public record Zrak(String postajaId, String postaja, String mreza, Integer udaljenostM, Double prosjekPm10,
                       Integer danaIznad50, Integer godina, List<SezonskaProcjena.Mjesec> procjena, boolean izModela,
                       boolean ogledniModel, String izvorMjerenja, String izvorProcjene) {

        public String udaljenost() {
            return udaljenostM == null ? "nema podatka" : GeoUtil.formatiraj(udaljenostM);
        }
    }

    /**
     * Pristupačnost: ustanove za osobe s invaliditetom, najbliža pješačka zona i stanje
     * pristupačnosti stajališta (ZET-ov GTFS danas ne sadrži polja o pristupačnosti).
     */
    public record Pristupacnost(List<Objekt> ustanove, Objekt pjesackaZona, String stajalista,
                                String napomenaStajalista) {
    }

    /** Javne površine: igrališta, park (udaljenost do ruba), sportski objekti, Odmorko, gradski vrt. */
    public record JavnePovrsine(List<Objekt> igralista, Objekt park, List<Objekt> sport, Objekt odmorko,
                                Objekt gradskiVrt) {

        public boolean prazno() {
            return igralista.isEmpty() && park == null && sport.isEmpty() && odmorko == null && gradskiVrt == null;
        }
    }

    /** Put do odabranog visokog učilišta: zračna udaljenost i linije ZET-a bez presjedanja. */
    public record DoFakulteta(long id, String naziv, String adresa, int udaljenostM, List<String> linijeBezPresjedanja,
                              boolean stajalistaUBlizini, double lat, double lon) {

        public String udaljenost() {
            return GeoUtil.formatiraj(udaljenostM);
        }
    }

    /** Studentski život: put do fakulteta i objekti po interesima (odabrani interesi prvi). */
    public record Student(DoFakulteta fakultet, List<Objekt> stavke, Integer biciklistickihStazaU1kmM) {

        public String staze() {
            return biciklistickihStazaU1kmM == null ? null : GeoUtil.formatiraj(biciklistickihStazaU1kmM);
        }
    }

    /** Jedna stavka osobnog indeksa ("Moj indeks"). */
    public record StavkaIndeksa(String naziv, Integer udaljenostM, boolean dostupno) {

        public String udaljenost() {
            return udaljenostM == null ? "nema podatka" : GeoUtil.formatiraj(udaljenostM);
        }
    }

    /** Osobni indeks za profil: što je unutar praga od 15 minuta hoda (zračno {@code pragM}). */
    public record MojIndeks(String naziv, List<StavkaIndeksa> stavke, int pragM) {

        public long dostupno() {
            return stavke.stream().filter(StavkaIndeksa::dostupno).count();
        }

        public String prag() {
            return GeoUtil.formatiraj(pragM);
        }
    }

    public record Potres(Objekt zbornoMjesto, Objekt evakuacija) {
    }

    public record Okolica(int indeks, int najvise, List<String> dostupne, List<String> nedostaju,
                          List<Objekt> najblizi, List<Objekt> ostalo, int pragM) {

        public String oznaka() {
            return indeks + "/" + najvise;
        }

        /** Prag zračne udaljenosti koji odgovara 15 minuta hoda za profil ("920 m"). */
        public String prag() {
            return GeoUtil.formatiraj(pragM);
        }
    }

    public record Stanovnistvo(String celija, Integer stanovnika, String izvor) {
    }
}
