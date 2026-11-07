package hr.kartonadrese.baza;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prijave pogrešaka u podacima. Povratna petlja prema izdavateljima: prijave se mogu izvesti
 * i proslijediti nadležnom tijelu (vidi /api/prijave u administrativnom API-ju).
 */
@Repository
public class PrijavaRepozitorij {

    private final JdbcClient jdbc;

    public PrijavaRepozitorij(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public record Prijava(long id, String objekt, String izvor, String adresa, String opis, String email,
                          LocalDateTime vrijeme, String status) {
    }

    public void spremi(String objekt, String izvor, String adresa, String opis, String email) {
        jdbc.sql("""
                        INSERT INTO prijava (objekt, izvor, adresa, opis, email, vrijeme, status)
                        VALUES (:objekt, :izvor, :adresa, :opis, :email, :vrijeme, 'NOVA')""")
                .param("objekt", PoiRepozitorij.skrati(objekt, 400))
                .param("izvor", PoiRepozitorij.skrati(izvor, 300))
                .param("adresa", PoiRepozitorij.skrati(adresa, 300))
                .param("opis", PoiRepozitorij.skrati(opis, 2000))
                .param("email", email == null || email.isBlank() ? null : PoiRepozitorij.skrati(email.trim(), 200))
                .param("vrijeme", LocalDateTime.now())
                .update();
    }

    public List<Prijava> zadnje(int n) {
        return jdbc.sql("SELECT * FROM prijava ORDER BY vrijeme DESC")
                .query((rs, i) -> new Prijava(rs.getLong("id"), rs.getString("objekt"), rs.getString("izvor"),
                        rs.getString("adresa"), rs.getString("opis"), rs.getString("email"),
                        rs.getObject("vrijeme", LocalDateTime.class), rs.getString("status")))
                .list().stream().limit(n).toList();
    }

    public int broj() {
        return jdbc.sql("SELECT COUNT(*) FROM prijava").query(Integer.class).single();
    }
}
