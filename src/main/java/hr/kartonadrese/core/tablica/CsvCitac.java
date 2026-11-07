package hr.kartonadrese.core.tablica;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Čitač CSV datoteka s hrvatskih portala: prepoznaje kodiranje (UTF-8 ili Windows-1250),
 * razdjelnik (; , ili tabulator), navodnike i prijelome reda unutar navodnika.
 */
public final class CsvCitac {

    private CsvCitac() {
    }

    public static Tablica procitaj(byte[] sadrzaj) {
        return procitaj(dekodiraj(sadrzaj));
    }

    public static Tablica procitaj(String tekst) {
        if (tekst.startsWith("﻿")) {
            tekst = tekst.substring(1);
        }
        char razdjelnik = prepoznajRazdjelnik(tekst);
        List<List<String>> svi = parsiraj(tekst, razdjelnik);
        if (svi.isEmpty()) {
            return new Tablica(List.of(), List.of());
        }
        List<String> zaglavlje = svi.getFirst().stream().map(String::trim).toList();
        List<List<String>> redovi = new ArrayList<>();
        for (int i = 1; i < svi.size(); i++) {
            List<String> red = svi.get(i);
            if (red.stream().anyMatch(s -> !s.isBlank())) {
                redovi.add(red);
            }
        }
        return new Tablica(zaglavlje, redovi);
    }

    static String dekodiraj(byte[] sadrzaj) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(sadrzaj))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(sadrzaj, Charset.forName("windows-1250"));
        }
    }

    static char prepoznajRazdjelnik(String tekst) {
        int kraj = tekst.indexOf('\n');
        String prvi = kraj < 0 ? tekst : tekst.substring(0, kraj);
        int tockaZarez = broji(prvi, ';');
        int zarez = broji(prvi, ',');
        int tab = broji(prvi, '\t');
        if (tab > tockaZarez && tab > zarez) {
            return '\t';
        }
        return tockaZarez >= zarez && tockaZarez > 0 ? ';' : ',';
    }

    private static int broji(String s, char c) {
        int n = 0;
        boolean uNavodnicima = false;
        for (int i = 0; i < s.length(); i++) {
            char z = s.charAt(i);
            if (z == '"') {
                uNavodnicima = !uNavodnicima;
            } else if (z == c && !uNavodnicima) {
                n++;
            }
        }
        return n;
    }

    private static List<List<String>> parsiraj(String t, char razdjelnik) {
        List<List<String>> redovi = new ArrayList<>();
        List<String> red = new ArrayList<>();
        StringBuilder polje = new StringBuilder();
        boolean uNavodnicima = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (uNavodnicima) {
                if (c == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') {
                        polje.append('"');
                        i++;
                    } else {
                        uNavodnicima = false;
                    }
                } else {
                    polje.append(c);
                }
            } else if (c == '"') {
                uNavodnicima = true;
            } else if (c == razdjelnik) {
                red.add(polje.toString());
                polje.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') {
                    i++;
                }
                red.add(polje.toString());
                polje.setLength(0);
                redovi.add(red);
                red = new ArrayList<>();
            } else {
                polje.append(c);
            }
        }
        if (polje.length() > 0 || !red.isEmpty()) {
            red.add(polje.toString());
            redovi.add(red);
        }
        return redovi;
    }
}
