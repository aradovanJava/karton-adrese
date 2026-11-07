package hr.kartonadrese.baza;

import hr.kartonadrese.core.model.Lokacija;
import hr.kartonadrese.core.uvoz.PrepoznavanjePolja;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Geokodirane adrese (predmemorija) i registar naziva ulica za dovršavanje upisa.
 * Dovršavanje radi samo nad lokalnim podacima – Nominatim se ne smije koristiti za "autocomplete".
 */
@Repository
public class AdresaRepozitorij {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public AdresaRepozitorij(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    private static final RowMapper<Lokacija> MAPER = (rs, i) -> new Lokacija(rs.getString("naziv"),
            rs.getDouble("lat"), rs.getDouble("lon"), rs.getString("gradska_cetvrt"), rs.getString("mjesni_odbor"),
            rs.getString("izvor"));

    /** Normalizirani oblik adrese za usporedbu: mala slova, bez dijakritika, bez "ulica"/"ul.". */
    public static String normaliziraj(String adresa) {
        String n = PrepoznavanjePolja.normaliziraj(adresa == null ? "" : adresa)
                .replaceAll("[,.]", " ")
                .replaceAll("\\bzagreb\\b", " ")
                .replaceAll("\\b10\\s?\\d{3}\\b", " ")
                .replaceAll("\\bul\\b", "ulica")
                .replaceAll("\\s+", " ")
                .trim();
        return n.replaceAll("\\bulica\\b", "").replaceAll("\\s+", " ").trim();
    }

    public Optional<Lokacija> nadji(String adresa) {
        return jdbc.sql("SELECT * FROM adresa WHERE naziv_norm = :n")
                .param("n", normaliziraj(adresa)).query(MAPER).optional();
    }

    public void spremi(Lokacija l) {
        String norm = normaliziraj(l.adresa());
        int n = jdbc.sql("""
                        UPDATE adresa SET naziv = :naziv, lat = :lat, lon = :lon, gradska_cetvrt = :gc,
                               mjesni_odbor = :mo, izvor = :izvor, azurirano = :t
                         WHERE naziv_norm = :norm""")
                .param("naziv", l.adresa()).param("lat", l.lat()).param("lon", l.lon())
                .param("gc", l.gradskaCetvrt()).param("mo", l.mjesniOdbor()).param("izvor", l.izvor())
                .param("t", LocalDateTime.now()).param("norm", norm).update();
        if (n == 0) {
            jdbc.sql("""
                            INSERT INTO adresa (naziv, naziv_norm, lat, lon, gradska_cetvrt, mjesni_odbor, izvor, azurirano)
                            VALUES (:naziv, :norm, :lat, :lon, :gc, :mo, :izvor, :t)""")
                    .param("naziv", l.adresa()).param("norm", norm).param("lat", l.lat()).param("lon", l.lon())
                    .param("gc", l.gradskaCetvrt()).param("mo", l.mjesniOdbor()).param("izvor", l.izvor())
                    .param("t", LocalDateTime.now()).update();
        }
    }

    /** Prijedlozi za upisani tekst: poznate adrese pa nazivi ulica iz registra. */
    public List<String> prijedlozi(String upit, int najvise) {
        String n = normaliziraj(upit);
        if (n.length() < 2) {
            return List.of();
        }
        Set<String> r = new LinkedHashSet<>(jdbc.sql("""
                        SELECT naziv FROM adresa WHERE naziv_norm LIKE :p ORDER BY naziv""")
                .param("p", n + "%").query(String.class).list());
        r.addAll(jdbc.sql("SELECT naziv FROM adresa WHERE naziv_norm LIKE :p ORDER BY naziv")
                .param("p", "%" + n + "%").query(String.class).list());
        if (r.size() < najvise) {
            r.addAll(jdbc.sql("SELECT naziv FROM ulica WHERE naziv_norm LIKE :p ORDER BY naziv")
                    .param("p", n + "%").query(String.class).list());
        }
        return r.stream().limit(najvise).toList();
    }

    public List<Lokacija> sve() {
        return jdbc.sql("SELECT * FROM adresa ORDER BY naziv").query(MAPER).list();
    }

    @Transactional
    public int zamijeniUlice(List<String> nazivi) {
        jdbc.sql("DELETE FROM ulica").update();
        Set<String> vidjeno = new LinkedHashSet<>();
        List<Object[]> redovi = new ArrayList<>();
        for (String naziv : nazivi) {
            String norm = normaliziraj(naziv);
            if (!norm.isEmpty() && vidjeno.add(norm)) {
                redovi.add(new Object[] {norm, PoiRepozitorij.skrati(naziv.trim(), 200)});
            }
        }
        if (!redovi.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO ulica (naziv_norm, naziv) VALUES (?, ?)", redovi);
        }
        return redovi.size();
    }

    public int broj() {
        return jdbc.sql("SELECT COUNT(*) FROM adresa").query(Integer.class).single();
    }
}
