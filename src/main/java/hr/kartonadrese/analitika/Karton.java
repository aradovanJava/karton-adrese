package hr.kartonadrese.analitika;

import hr.kartonadrese.core.ml.SezonskaProcjena;
import hr.kartonadrese.core.model.Profil;
import hr.kartonadrese.core.model.Sekcija;

import java.util.List;

/**
 * Karton adrese – sve što REST API vraća i što Thymeleaf prikazuje.
 * Uz svaki podatak su izvor i datum (transparentnost, H10).
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
        boolean ogledni) {

    /** Objekt u blizini adrese. */
    public record Objekt(long id, String kategorija, String kategorijaNaziv, String boja, String naziv, String adresa,
                         double lat, double lon, int udaljenostM, int minutaHoda, String izvorId, String izvor,
                         String datum, List<String> detalji, boolean istaknut) {
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
    }

    public record Prijevoz(List<StajalisteBlizu> stajalista, int brojLinija, String izvor, String datum) {
    }

    public record Zdravlje(List<Objekt> zdravstvo, List<Objekt> ljekarne) {
    }

    public record Zrak(String postajaId, String postaja, String mreza, Integer udaljenostM, Double prosjekPm10,
                       Integer danaIznad50, Integer godina, List<SezonskaProcjena.Mjesec> procjena, boolean izModela,
                       boolean ogledniModel, String izvorMjerenja, String izvorProcjene) {
    }

    public record Pristupacnost(List<Objekt> ustanove, Objekt pjesackaZona, List<Objekt> tramvajska) {
    }

    public record Potres(Objekt zbornoMjesto, Objekt evakuacija) {
    }

    public record Okolica(int indeks, int najvise, List<String> dostupne, List<String> nedostaju,
                          List<Objekt> najblizi, List<Objekt> ostalo) {

        public String oznaka() {
            return indeks + "/" + najvise;
        }
    }

    public record Stanovnistvo(String celija, Integer stanovnika, String izvor) {
    }
}
