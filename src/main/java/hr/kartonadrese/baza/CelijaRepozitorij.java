package hr.kartonadrese.baza;

import hr.kartonadrese.core.model.Celija;
import hr.kartonadrese.core.model.Kategorija;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ćelije mreže 1 km² sa stanovništvom (DZS) i indeksom grada 15 minuta. */
@Repository
public class CelijaRepozitorij {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public CelijaRepozitorij(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    private static final RowMapper<Celija> MAPER = (rs, i) -> new Celija(
            rs.getString("id"), rs.getDouble("lat"), rs.getDouble("lon"),
            (Integer) rs.getObject("stanovnika"),
            rs.getObject("indeks15") == null ? -1 : rs.getInt("indeks15"),
            nedostaje(rs.getString("nedostaje")));

    private static List<Kategorija> nedostaje(String s) {
        return s == null || s.isBlank() ? List.of()
                : Arrays.stream(s.split(",")).map(String::trim).map(Kategorija::izTeksta).toList();
    }

    /** Sprema broj stanovnika po ćeliji (zamjenjuje prethodni uvoz mreže). */
    @Transactional
    public int zamijeniStanovnistvo(Map<String, double[]> sredista, Map<String, Integer> stanovnika) {
        jdbc.sql("DELETE FROM celija").update();
        List<Object[]> redovi = new ArrayList<>();
        stanovnika.forEach((id, n) -> {
            double[] ll = sredista.get(id);
            redovi.add(new Object[] {id, ll[0], ll[1], n});
        });
        if (!redovi.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO celija (id, lat, lon, stanovnika) VALUES (?, ?, ?, ?)", redovi);
        }
        return redovi.size();
    }

    /** Upisuje indeks ćelije (i stvara ćeliju ako ne postoji). */
    public void spremiIndeks(String id, double lat, double lon, int indeks, List<Kategorija> nedostaje) {
        String n = String.join(",", nedostaje.stream().map(Enum::name).toList());
        int promijenjeno = jdbc.sql("UPDATE celija SET indeks15 = :i, nedostaje = :n, izracunato = :t WHERE id = :id")
                .param("i", indeks).param("n", n).param("t", LocalDateTime.now()).param("id", id).update();
        if (promijenjeno == 0) {
            jdbc.sql("""
                            INSERT INTO celija (id, lat, lon, indeks15, nedostaje, izracunato)
                            VALUES (:id, :lat, :lon, :i, :n, :t)""")
                    .param("id", id).param("lat", lat).param("lon", lon).param("i", indeks).param("n", n)
                    .param("t", LocalDateTime.now()).update();
        }
    }

    public List<Celija> sve() {
        return jdbc.sql("SELECT * FROM celija ORDER BY id").query(MAPER).list();
    }

    public Optional<Celija> nadji(String id) {
        return jdbc.sql("SELECT * FROM celija WHERE id = :id").param("id", id).query(MAPER).optional();
    }

    public int broj() {
        return jdbc.sql("SELECT COUNT(*) FROM celija").query(Integer.class).single();
    }
}
