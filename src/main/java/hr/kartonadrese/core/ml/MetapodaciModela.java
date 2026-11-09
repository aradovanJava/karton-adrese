package hr.kartonadrese.core.ml;

import hr.kartonadrese.core.json.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metapodaci modela PM10 ({@code model/<postaja>/metapodaci.json}) koje zapisuje
 * Python skripta {@code ml/karton_ml/treniranje.py}.
 */
public record MetapodaciModela(
        String postajaId,
        String postajaNaziv,
        String mreza,
        double lat,
        double lon,
        boolean ogledni,
        List<String> znacajke,
        String ulaz,
        String transformacija,
        Map<Integer, Map<String, Double>> klimatologija,
        double donjiOmjer,
        double gornjiOmjer,
        Map<Integer, Double> mjesecniProsjekMjerenja,
        Statistika statistika,
        Metrike metrike,
        List<Vaznost> vaznost,
        int kontrolnaGodina,
        List<SezonskaProcjena.Mjesec> kontrolniProfil,
        String razdobljeOd,
        String razdobljeDo,
        List<String> izvoriPm10,
        List<String> izvoriMeteo,
        String stvoreno) {

    public record Statistika(int godina, double prosjekPm10, int danaIznad50, int brojDana) {
    }

    public record Metrike(double mae, double maeOsnovni, Double poboljsanje, double rmse, double r2) {
    }

    public record Vaznost(String znacajka, double udio) {
    }

    public static MetapodaciModela izJsona(String tekst) {
        Map<String, Object> m = Json.objekt(tekst);
        Map<String, Object> postaja = Json.mapa(m, "postaja");
        Map<String, Object> pojas = Json.mapa(m, "pojas_mjesecni");
        Map<String, Object> stat = Json.mapa(m, "statistika_mjerenja");
        Map<String, Object> met = Json.mapa(m, "metrike");
        Map<String, Object> razdoblje = Json.mapa(m, "razdoblje");
        Map<String, Object> izvori = Json.mapa(m, "izvori");

        List<String> znacajke = new ArrayList<>();
        for (Object z : Json.niz(m, "znacajke")) {
            znacajke.add(String.valueOf(z));
        }

        Map<Integer, Map<String, Double>> klima = new HashMap<>();
        for (Object zapis : Json.niz(m, "klimatologija")) {
            int dan = Json.broj(zapis, "dan").orElseThrow().intValue();
            Map<String, Double> vrijednosti = new LinkedHashMap<>();
            for (String z : znacajke) {
                Json.broj(zapis, z).ifPresent(v -> vrijednosti.put(z, v));
            }
            klima.put(dan, vrijednosti);
        }

        Map<Integer, Double> mjesecno = new HashMap<>();
        Json.mapa(met, "mjesecni_prosjek_mjerenja").forEach((k, v) -> {
            if (v instanceof Number n) {
                mjesecno.put(Integer.parseInt(k), n.doubleValue());
            }
        });

        List<Vaznost> vaznost = new ArrayList<>();
        for (Object v : Json.niz(m, "vaznost_znacajki")) {
            vaznost.add(new Vaznost(Json.tekst(v, "znacajka").orElse("?"), Json.broj(v, "udio").orElse(0.0)));
        }

        Map<String, Object> kontrolni = Json.mapa(m, "kontrolni_primjer");
        List<SezonskaProcjena.Mjesec> profil = new ArrayList<>();
        for (Object o : Json.niz(kontrolni, "mjesecno")) {
            profil.add(new SezonskaProcjena.Mjesec(Json.broj(o, "mjesec").orElseThrow().intValue(),
                    Json.broj(o, "pm10").orElseThrow(), Json.broj(o, "donja").orElseThrow(),
                    Json.broj(o, "gornja").orElseThrow()));
        }

        return new MetapodaciModela(
                Json.tekst(postaja, "id").orElse("postaja"),
                Json.tekst(postaja, "naziv").orElse("Mjerna postaja"),
                Json.tekst(postaja, "mreza").orElse(""),
                Json.broj(postaja, "lat").orElse(0.0),
                Json.broj(postaja, "lon").orElse(0.0),
                Boolean.TRUE.equals(m.get("ogledni")),
                List.copyOf(znacajke),
                Json.tekst(m, "ulaz_onnx").orElse("input"),
                Json.tekst(m, "transformacija_cilja").orElse("log1p"),
                klima,
                Json.broj(pojas, "donji_omjer").orElse(0.75),
                Json.broj(pojas, "gornji_omjer").orElse(1.3),
                mjesecno,
                new Statistika(Json.broj(stat, "godina").orElse(0.0).intValue(),
                        Json.broj(stat, "prosjek_pm10").orElse(Double.NaN),
                        Json.broj(stat, "dana_iznad_50").orElse(0.0).intValue(),
                        Json.broj(stat, "broj_dana_s_mjerenjem").orElse(0.0).intValue()),
                new Metrike(Json.broj(met, "mae").orElse(Double.NaN),
                        Json.broj(met, "mae_osnovni_model").orElse(Double.NaN),
                        Json.broj(met, "poboljsanje_u_odnosu_na_osnovni").orElse(null),
                        Json.broj(met, "rmse").orElse(Double.NaN),
                        Json.broj(met, "r2").orElse(Double.NaN)),
                vaznost,
                Json.broj(kontrolni, "godina").orElse(0.0).intValue(),
                profil,
                Json.tekst(razdoblje, "od").orElse(""),
                Json.tekst(razdoblje, "do").orElse(""),
                Json.niz(izvori, "pm10").stream().map(String::valueOf).toList(),
                Json.niz(izvori, "meteo").stream().map(String::valueOf).toList(),
                Json.tekst(m, "stvoreno").orElse(""));
    }
}
