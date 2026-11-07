package hr.kartonadrese.baza;

import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.model.Stajaliste;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Stajališta javnog prijevoza s linijama (ZET GTFS). */
@Repository
public class StajalisteRepozitorij {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public StajalisteRepozitorij(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    private static final RowMapper<Stajaliste> MAPER = (rs, i) -> new Stajaliste(
            rs.getString("id"), rs.getString("naziv"), rs.getDouble("lat"), rs.getDouble("lon"),
            linije(rs.getString("linije")), rs.getBoolean("tramvaj"), rs.getBoolean("autobus"), rs.getInt("polazaka"));

    private static List<String> linije(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::trim).toList();
    }

    @Transactional
    public int zamijeni(List<Stajaliste> stajalista) {
        jdbc.sql("DELETE FROM stajaliste").update();
        List<Object[]> redovi = new ArrayList<>();
        for (Stajaliste s : stajalista) {
            redovi.add(new Object[] {s.id(), PoiRepozitorij.skrati(s.naziv(), 200), s.lat(), s.lon(),
                PoiRepozitorij.skrati(String.join(",", s.linije()), 600), s.tramvaj(), s.autobus(), s.polazakaDnevno()});
        }
        if (!redovi.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO stajaliste (id, naziv, lat, lon, linije, tramvaj, autobus, polazaka)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", redovi);
        }
        return redovi.size();
    }

    public List<Stajaliste> uKrugu(double lat, double lon, double polumjerM) {
        GeoUtil.Okvir o = GeoUtil.okvir(lat, lon, polumjerM);
        return jdbc.sql("""
                        SELECT * FROM stajaliste
                         WHERE lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon""")
                .param("minLat", o.minLat()).param("maxLat", o.maxLat())
                .param("minLon", o.minLon()).param("maxLon", o.maxLon())
                .query(MAPER).list().stream()
                .filter(s -> GeoUtil.udaljenostM(lat, lon, s.lat(), s.lon()) <= polumjerM)
                .toList();
    }

    public int broj() {
        return jdbc.sql("SELECT COUNT(*) FROM stajaliste").query(Integer.class).single();
    }
}
