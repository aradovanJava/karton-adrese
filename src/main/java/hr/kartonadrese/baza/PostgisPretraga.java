package hr.kartonadrese.baza;

import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** PostGIS izvedba: ST_DWithin nad geografijom koristi prostorni (GiST) indeks. */
@Component
@Profile("postgis")
public class PostgisPretraga implements ProstornaPretraga {

    private final JdbcClient jdbc;

    public PostgisPretraga(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Override
    public List<Poi> uKrugu(double lat, double lon, double polumjerM, Collection<Kategorija> kategorije) {
        String sql = "SELECT " + PoiRepozitorij.STUPCI + " FROM poi WHERE ST_DWithin(geom::geography, "
                + "ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :r)";
        JdbcClient.StatementSpec upit = kategorije == null || kategorije.isEmpty()
                ? jdbc.sql(sql)
                : jdbc.sql(sql + " AND kategorija IN (:kategorije)")
                        .param("kategorije", kategorije.stream().map(Enum::name).toList());
        return upit.param("lat", lat).param("lon", lon).param("r", polumjerM)
                .query(PoiRepozitorij.MAPER).list();
    }
}
