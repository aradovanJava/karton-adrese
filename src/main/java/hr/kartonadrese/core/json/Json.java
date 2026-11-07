package hr.kartonadrese.core.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mali JSON parser i pisač bez vanjskih ovisnosti.
 *
 * <p>Koristi se za odgovore otvorenih API-ja (CKAN, Overpass, Nominatim, GeoJSON) i za
 * metapodatke ML modela, tako da jezgra aplikacije ne ovisi o verziji Jacksona.
 * Objekti postaju {@code LinkedHashMap<String, Object>}, nizovi {@code ArrayList<Object>},
 * brojevi {@code Long} ili {@code Double}.
 */
public final class Json {

    private final String tekst;
    private int pozicija;

    private Json(String tekst) {
        this.tekst = tekst;
    }

    public static Object parsiraj(String tekst) {
        if (tekst == null) {
            throw new JsonIznimka("Prazan JSON", 0);
        }
        Json p = new Json(tekst.startsWith("﻿") ? tekst.substring(1) : tekst);
        p.preskociRazmake();
        Object vrijednost = p.vrijednost();
        p.preskociRazmake();
        if (p.pozicija != p.tekst.length()) {
            throw new JsonIznimka("Neočekivani znakovi nakon JSON vrijednosti", p.pozicija);
        }
        return vrijednost;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objekt(String tekst) {
        Object o = parsiraj(tekst);
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new JsonIznimka("Očekivan JSON objekt", 0);
    }

    // ------------------------------------------------------------------ pomoćne metode

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapa(Object o, String kljuc) {
        Object v = o instanceof Map<?, ?> m ? m.get(kljuc) : null;
        return v instanceof Map<?, ?> r ? (Map<String, Object>) r : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> niz(Object o, String kljuc) {
        Object v = o instanceof Map<?, ?> m ? m.get(kljuc) : null;
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    public static Optional<String> tekst(Object o, String kljuc) {
        Object v = o instanceof Map<?, ?> m ? m.get(kljuc) : null;
        if (v == null) {
            return Optional.empty();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    public static Optional<Double> broj(Object o, String kljuc) {
        Object v = o instanceof Map<?, ?> m ? m.get(kljuc) : null;
        if (v instanceof Number n) {
            return Optional.of(n.doubleValue());
        }
        if (v instanceof String s) {
            try {
                return Optional.of(Double.parseDouble(s.trim().replace(',', '.')));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ pisanje

    public static String zapisi(Object vrijednost) {
        StringBuilder sb = new StringBuilder();
        zapisi(vrijednost, sb);
        return sb.toString();
    }

    private static void zapisi(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            zapisiTekst(s, sb);
        } else if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append(d.longValue());
            } else {
                sb.append(BigDecimal.valueOf(d).stripTrailingZeros().toPlainString());
            }
        } else if (v instanceof Float f) {
            zapisi(f.doubleValue(), sb);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean prvi = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!prvi) {
                    sb.append(',');
                }
                prvi = false;
                zapisiTekst(String.valueOf(e.getKey()), sb);
                sb.append(':');
                zapisi(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            boolean prvi = true;
            for (Object e : c) {
                if (!prvi) {
                    sb.append(',');
                }
                prvi = false;
                zapisi(e, sb);
            }
            sb.append(']');
        } else if (v instanceof double[] niz) {
            List<Object> l = new ArrayList<>();
            for (double d : niz) {
                l.add(d);
            }
            zapisi(l, sb);
        } else if (v instanceof Enum<?> e) {
            zapisiTekst(e.name(), sb);
        } else {
            zapisiTekst(v.toString(), sb);
        }
    }

    private static void zapisiTekst(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ parsiranje

    private Object vrijednost() {
        if (pozicija >= tekst.length()) {
            throw new JsonIznimka("Neočekivani kraj JSON-a", pozicija);
        }
        char c = tekst.charAt(pozicija);
        return switch (c) {
            case '{' -> objekt();
            case '[' -> niz();
            case '"' -> tekst();
            case 't' -> doslovno("true", Boolean.TRUE);
            case 'f' -> doslovno("false", Boolean.FALSE);
            case 'n' -> doslovno("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield broj();
                }
                throw new JsonIznimka("Neočekivani znak '" + c + "'", pozicija);
            }
        };
    }

    private Map<String, Object> objekt() {
        Map<String, Object> m = new LinkedHashMap<>();
        pozicija++;
        preskociRazmake();
        if (vidi('}')) {
            pozicija++;
            return m;
        }
        while (true) {
            preskociRazmake();
            if (!vidi('"')) {
                throw new JsonIznimka("Očekivan ključ objekta", pozicija);
            }
            String kljuc = tekst();
            preskociRazmake();
            ocekuj(':');
            preskociRazmake();
            m.put(kljuc, vrijednost());
            preskociRazmake();
            if (vidi(',')) {
                pozicija++;
                continue;
            }
            ocekuj('}');
            return m;
        }
    }

    private List<Object> niz() {
        List<Object> l = new ArrayList<>();
        pozicija++;
        preskociRazmake();
        if (vidi(']')) {
            pozicija++;
            return l;
        }
        while (true) {
            preskociRazmake();
            l.add(vrijednost());
            preskociRazmake();
            if (vidi(',')) {
                pozicija++;
                continue;
            }
            ocekuj(']');
            return l;
        }
    }

    private String tekst() {
        pozicija++;
        StringBuilder sb = new StringBuilder();
        while (pozicija < tekst.length()) {
            char c = tekst.charAt(pozicija++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pozicija >= tekst.length()) {
                    break;
                }
                char e = tekst.charAt(pozicija++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pozicija + 4 > tekst.length()) {
                            throw new JsonIznimka("Nepotpuni \\u zapis", pozicija);
                        }
                        sb.append((char) Integer.parseInt(tekst.substring(pozicija, pozicija + 4), 16));
                        pozicija += 4;
                    }
                    default -> throw new JsonIznimka("Nepoznati escape \\" + e, pozicija);
                }
            } else {
                sb.append(c);
            }
        }
        throw new JsonIznimka("Nezatvoreni niz znakova", pozicija);
    }

    private Object broj() {
        int pocetak = pozicija;
        if (vidi('-')) {
            pozicija++;
        }
        boolean decimalni = false;
        while (pozicija < tekst.length()) {
            char c = tekst.charAt(pozicija);
            if (c >= '0' && c <= '9') {
                pozicija++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                decimalni = true;
                pozicija++;
            } else {
                break;
            }
        }
        String s = tekst.substring(pocetak, pozicija);
        try {
            if (!decimalni) {
                return Long.parseLong(s);
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.parseDouble(s);
        }
    }

    private Object doslovno(String rijec, Object vrijednost) {
        if (!tekst.startsWith(rijec, pozicija)) {
            throw new JsonIznimka("Očekivano " + rijec, pozicija);
        }
        pozicija += rijec.length();
        return vrijednost;
    }

    private boolean vidi(char c) {
        return pozicija < tekst.length() && tekst.charAt(pozicija) == c;
    }

    private void ocekuj(char c) {
        if (!vidi(c)) {
            throw new JsonIznimka("Očekivan znak '" + c + "'", pozicija);
        }
        pozicija++;
    }

    private void preskociRazmake() {
        while (pozicija < tekst.length() && Character.isWhitespace(tekst.charAt(pozicija))) {
            pozicija++;
        }
    }

    /** Pogreška pri čitanju JSON-a s pozicijom znaka. */
    public static final class JsonIznimka extends RuntimeException {
        public JsonIznimka(String poruka, int pozicija) {
            super(poruka + " (znak " + pozicija + ")");
        }
    }
}
