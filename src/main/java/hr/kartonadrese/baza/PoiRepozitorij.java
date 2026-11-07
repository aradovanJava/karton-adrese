package hr.kartonadrese.baza;

import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.json.Json;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Objekti od interesa (ustanove, stajališta, trgovine …) iz svih izvora. */
@Repository
public class PoiRepozitorij {

    static final String STUPCI = "id, izvor_id, kategorija, naziv, adresa, lat, lon, atributi, azurirano";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public PoiRepozitorij(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    static final RowMapper<Poi> MAPER = (rs, i) -> new Poi(
            rs.getLong("id"),
            rs.getString("izvor_id"),
            Kategorija.izTeksta(rs.getString("kategorija")),
            rs.getString("naziv"),
            rs.getString("adresa"),
            rs.getDouble("lat"),
            rs.getDouble("lon"),
            atributi(rs.getString("atributi")),
            rs.getObject("azurirano", LocalDate.class));

    static Map<String, String> atributi(String json) {
        Map<String, String> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return m;
        }
        Json.objekt(json).forEach((k, v) -> {
            if (v != null) {
                m.put(k, String.valueOf(v));
            }
        });
        return m;
    }

    /** Zamjenjuje sve objekte jednog izvora (uvoz je idempotentan). */
    @Transactional
    public int zamijeni(String izvorId, List<Poi> objekti) {
        jdbc.sql("DELETE FROM poi WHERE izvor_id = :izvor").param("izvor", izvorId).update();
        List<Object[]> redovi = new ArrayList<>();
        for (Poi p : objekti) {
            redovi.add(new Object[] {izvorId, p.kategorija().name(), skrati(p.naziv(), 400), skrati(p.adresa(), 400),
                p.lat(), p.lon(), p.atributi() == null || p.atributi().isEmpty() ? null : Json.zapisi(p.atributi()),
                p.azurirano()});
        }
        if (!redovi.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO poi (izvor_id, kategorija, naziv, adresa, lat, lon, atributi, azurirano)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", redovi);
        }
        return redovi.size();
    }

    /** Objekti unutar pravokutnika (brzi predfiltar preko indeksa lat/lon). */
    public List<Poi> uOkviru(GeoUtil.Okvir o, Collection<Kategorija> kategorije) {
        String sql = "SELECT " + STUPCI + " FROM poi WHERE lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon";
        JdbcClient.StatementSpec upit;
        if (kategorije == null || kategorije.isEmpty()) {
            upit = jdbc.sql(sql);
        } else {
            upit = jdbc.sql(sql + " AND kategorija IN (:kategorije)")
                    .param("kategorije", kategorije.stream().map(Enum::name).toList());
        }
        return upit.param("minLat", o.minLat()).param("maxLat", o.maxLat())
                .param("minLon", o.minLon()).param("maxLon", o.maxLon())
                .query(MAPER).list();
    }

    public Map<Kategorija, Integer> brojPoKategoriji() {
        Map<Kategorija, Integer> m = new LinkedHashMap<>();
        jdbc.sql("SELECT kategorija, COUNT(*) AS n FROM poi GROUP BY kategorija ORDER BY kategorija")
                .query((rs, i) -> Map.entry(Kategorija.izTeksta(rs.getString("kategorija")), rs.getInt("n")))
                .list()
                .forEach(e -> m.merge(e.getKey(), e.getValue(), Integer::sum));
        return m;
    }

    public int broj() {
        return jdbc.sql("SELECT COUNT(*) FROM poi").query(Integer.class).single();
    }

    static String skrati(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }
}
