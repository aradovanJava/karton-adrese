package hr.kartonadrese.baza;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Mjerne postaje kvalitete zraka s godišnjim pokazateljima PM10. */
@Repository
public class PostajaZrakaRepozitorij {

    private final JdbcClient jdbc;

    public PostajaZrakaRepozitorij(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public record PostajaZraka(String id, String naziv, String mreza, double lat, double lon, Integer godina,
                               Double prosjekPm10, Integer danaIznad50, String izvor) {
    }

    public void spremi(PostajaZraka p) {
        int n = jdbc.sql("""
                        UPDATE postaja_zraka SET naziv = :naziv, mreza = :mreza, lat = :lat, lon = :lon,
                               godina = :godina, prosjek_pm10 = :prosjek, dana_iznad_50 = :dana, izvor = :izvor
                         WHERE id = :id""")
                .param("id", p.id()).param("naziv", p.naziv()).param("mreza", p.mreza()).param("lat", p.lat())
                .param("lon", p.lon()).param("godina", p.godina()).param("prosjek", p.prosjekPm10())
                .param("dana", p.danaIznad50()).param("izvor", p.izvor())
                .update();
        if (n == 0) {
            jdbc.sql("""
                            INSERT INTO postaja_zraka (id, naziv, mreza, lat, lon, godina, prosjek_pm10, dana_iznad_50, izvor)
                            VALUES (:id, :naziv, :mreza, :lat, :lon, :godina, :prosjek, :dana, :izvor)""")
                    .param("id", p.id()).param("naziv", p.naziv()).param("mreza", p.mreza()).param("lat", p.lat())
                    .param("lon", p.lon()).param("godina", p.godina()).param("prosjek", p.prosjekPm10())
                    .param("dana", p.danaIznad50()).param("izvor", p.izvor())
                    .update();
        }
    }

    public List<PostajaZraka> sve() {
        return jdbc.sql("SELECT * FROM postaja_zraka ORDER BY naziv")
                .query((rs, i) -> new PostajaZraka(rs.getString("id"), rs.getString("naziv"), rs.getString("mreza"),
                        rs.getDouble("lat"), rs.getDouble("lon"), (Integer) rs.getObject("godina"),
                        (Double) rs.getObject("prosjek_pm10"), (Integer) rs.getObject("dana_iznad_50"),
                        rs.getString("izvor")))
                .list();
    }
}
