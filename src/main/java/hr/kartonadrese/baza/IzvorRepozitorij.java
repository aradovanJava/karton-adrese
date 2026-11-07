package hr.kartonadrese.baza;

import hr.kartonadrese.config.IzvorKonfiguracija;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Registar izvora s datumom zadnjeg preuzimanja i izvješćem o kvaliteti (organizacijska interoperabilnost). */
@Repository
public class IzvorRepozitorij {

    private final JdbcClient jdbc;

    public IzvorRepozitorij(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** Stanje izvora za stranicu "O podacima" i API /kvaliteta-podataka. */
    public record IzvorStanje(String id, String naziv, String izdavatelj, String licenca, String poveznica,
                              String vrsta, String status, String poruka, LocalDateTime zadnjePreuzimanje,
                              LocalDate datumPodatka, Integer procitano, Integer spremljeno, Integer bezLokacije,
                              Integer geokodirano, Integer izvanPodrucja) {
    }

    /** Statistika jednog uvoza. */
    public record Statistika(int procitano, int spremljeno, int bezLokacije, int geokodirano, int izvanPodrucja,
                             LocalDate datumPodatka, String poruka) {
    }

    public void registriraj(IzvorKonfiguracija k) {
        registriraj(k.id(), k.naziv(), k.izdavatelj(), k.licenca(), k.stranica(),
                k.vrsta() == null ? null : k.vrsta().name());
    }

    public void registriraj(String id, String naziv, String izdavatelj, String licenca, String poveznica, String vrsta) {
        int n = jdbc.sql("""
                        UPDATE izvor SET naziv = :naziv, izdavatelj = :izdavatelj, licenca = :licenca,
                               poveznica = :poveznica, vrsta = :vrsta
                         WHERE id = :id""")
                .param("id", id).param("naziv", naziv).param("izdavatelj", izdavatelj)
                .param("licenca", licenca).param("poveznica", poveznica).param("vrsta", vrsta)
                .update();
        if (n == 0) {
            jdbc.sql("""
                            INSERT INTO izvor (id, naziv, izdavatelj, licenca, poveznica, vrsta, status)
                            VALUES (:id, :naziv, :izdavatelj, :licenca, :poveznica, :vrsta, 'NIJE_PREUZETO')""")
                    .param("id", id).param("naziv", naziv).param("izdavatelj", izdavatelj)
                    .param("licenca", licenca).param("poveznica", poveznica).param("vrsta", vrsta)
                    .update();
        }
    }

    public void zabiljeziUspjeh(String id, String status, Statistika s) {
        jdbc.sql("""
                        UPDATE izvor SET status = :status, poruka = :poruka, zadnje_preuzimanje = :vrijeme,
                               datum_podatka = :datum, procitano = :procitano, spremljeno = :spremljeno,
                               bez_lokacije = :bezLokacije, geokodirano = :geokodirano, izvan_podrucja = :izvan
                         WHERE id = :id""")
                .param("id", id).param("status", status).param("poruka", s.poruka())
                .param("vrijeme", LocalDateTime.now()).param("datum", s.datumPodatka())
                .param("procitano", s.procitano()).param("spremljeno", s.spremljeno())
                .param("bezLokacije", s.bezLokacije()).param("geokodirano", s.geokodirano())
                .param("izvan", s.izvanPodrucja())
                .update();
    }

    public void zabiljeziPogresku(String id, String poruka) {
        jdbc.sql("UPDATE izvor SET status = 'POGRESKA', poruka = :poruka, zadnje_preuzimanje = :vrijeme WHERE id = :id")
                .param("id", id).param("poruka", poruka == null ? "Nepoznata pogreška" : poruka)
                .param("vrijeme", LocalDateTime.now())
                .update();
    }

    public List<IzvorStanje> svi() {
        return jdbc.sql("SELECT * FROM izvor ORDER BY izdavatelj, naziv")
                .query((rs, i) -> new IzvorStanje(rs.getString("id"), rs.getString("naziv"),
                        rs.getString("izdavatelj"), rs.getString("licenca"), rs.getString("poveznica"),
                        rs.getString("vrsta"), rs.getString("status"), rs.getString("poruka"),
                        rs.getObject("zadnje_preuzimanje", LocalDateTime.class),
                        rs.getObject("datum_podatka", LocalDate.class),
                        (Integer) rs.getObject("procitano"), (Integer) rs.getObject("spremljeno"),
                        (Integer) rs.getObject("bez_lokacije"), (Integer) rs.getObject("geokodirano"),
                        (Integer) rs.getObject("izvan_podrucja")))
                .list();
    }

    public Optional<IzvorStanje> nadji(String id) {
        return svi().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public int broj() {
        return jdbc.sql("SELECT COUNT(*) FROM izvor").query(Integer.class).single();
    }
}
