package hr.kartonadrese.core.tablica;

import java.util.List;

/** Jednostavna tablica: zaglavlje i redovi teksta (iz CSV-a ili XLSX-a). */
public record Tablica(List<String> stupci, List<List<String>> redovi) {

    public String vrijednost(List<String> red, int stupac) {
        if (stupac < 0 || stupac >= red.size()) {
            return "";
        }
        String v = red.get(stupac);
        return v == null ? "" : v.trim();
    }

    public int brojRedova() {
        return redovi.size();
    }
}
