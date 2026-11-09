package hr.kartonadrese.baza;

import hr.kartonadrese.core.geo.Poligon;
import hr.kartonadrese.core.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Granice gradskih četvrti i mjesnih odbora (za pridruživanje adrese prostornoj jedinici). */
@Repository
public class GranicaRepozitorij {

    public static final String GRADSKA_CETVRT = "GRADSKA_CETVRT";
    public static final String MJESNI_ODBOR = "MJESNI_ODBOR";

    private final JdbcClient jdbc;
    private final Map<String, List<Poligon>> predmemorija = new ConcurrentHashMap<>();

    public GranicaRepozitorij(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Transactional
    public int zamijeni(String vrsta, List<Poligon> poligoni) {
        jdbc.sql("DELETE FROM granica WHERE vrsta = :v").param("v", vrsta).update();
        for (Poligon p : poligoni) {
            List<Object> prstenovi = new ArrayList<>();
            for (double[][] prsten : p.prstenovi()) {
                List<Object> tocke = new ArrayList<>();
                for (double[] t : prsten) {
                    tocke.add(List.of(t[0], t[1]));
                }
                prstenovi.add(tocke);
            }
            jdbc.sql("INSERT INTO granica (vrsta, naziv, geojson) VALUES (:v, :n, :g)")
                    .param("v", vrsta).param("n", p.naziv()).param("g", Json.zapisi(prstenovi)).update();
        }
        predmemorija.remove(vrsta);
        return poligoni.size();
    }

    public List<Poligon> sve(String vrsta) {
        return predmemorija.computeIfAbsent(vrsta, v -> jdbc.sql("SELECT naziv, geojson FROM granica WHERE vrsta = :v")
                .param("v", v)
                .query((rs, i) -> new Poligon(rs.getString("naziv"), prstenovi(rs.getString("geojson"))))
                .list());
    }

    public Optional<String> nadji(String vrsta, double lat, double lon) {
        return sve(vrsta).stream().filter(p -> p.sadrzi(lat, lon)).map(Poligon::naziv).findFirst();
    }

    private static List<double[][]> prstenovi(String json) {
        List<double[][]> r = new ArrayList<>();
        Object o = Json.parsiraj(json);
        if (o instanceof List<?> lista) {
            for (Object prsten : lista) {
                if (prsten instanceof List<?> tocke) {
                    double[][] p = new double[tocke.size()][];
                    for (int i = 0; i < tocke.size(); i++) {
                        List<?> t = (List<?>) tocke.get(i);
                        p[i] = new double[] {((Number) t.get(0)).doubleValue(), ((Number) t.get(1)).doubleValue()};
                    }
                    r.add(p);
                }
            }
        }
        return r;
    }
}
