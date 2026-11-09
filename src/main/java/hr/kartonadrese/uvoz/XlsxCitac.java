package hr.kartonadrese.uvoz;

import hr.kartonadrese.core.tablica.Tablica;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Čita XLSX/XLS (Apache POI) u {@link Tablica}. Zaglavlje je prvi red s barem dvije
 * popunjene ćelije (mnoge tablice na portalima imaju naslov iznad zaglavlja).
 * Brojevi se zapisuju s decimalnom točkom, bez lokalnog oblikovanja, da se koordinate ne izgube.
 */
public final class XlsxCitac {

    private XlsxCitac() {
    }

    public static Tablica procitaj(byte[] sadrzaj) throws IOException {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(sadrzaj))) {
            Sheet list = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<List<String>> svi = new ArrayList<>();
            for (Row red : list) {
                List<String> vrijednosti = new ArrayList<>();
                short zadnja = red.getLastCellNum();
                for (int i = 0; i < Math.max(0, zadnja); i++) {
                    Cell c = red.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    vrijednosti.add(c == null ? "" : tekst(c, formatter));
                }
                svi.add(vrijednosti);
            }
            int zaglavlje = 0;
            while (zaglavlje < svi.size() && svi.get(zaglavlje).stream().filter(s -> !s.isBlank()).count() < 2) {
                zaglavlje++;
            }
            if (zaglavlje >= svi.size()) {
                return new Tablica(List.of(), List.of());
            }
            List<String> stupci = svi.get(zaglavlje).stream().map(String::trim).toList();
            List<List<String>> redovi = new ArrayList<>();
            for (int i = zaglavlje + 1; i < svi.size(); i++) {
                if (svi.get(i).stream().anyMatch(s -> !s.isBlank())) {
                    redovi.add(svi.get(i));
                }
            }
            return new Tablica(stupci, redovi);
        }
    }

    private static String tekst(Cell c, DataFormatter formatter) {
        CellType tip = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (tip == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            double d = c.getNumericCellValue();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
        }
        if (tip == CellType.STRING) {
            return c.getStringCellValue().trim();
        }
        if (tip == CellType.BOOLEAN) {
            return Boolean.toString(c.getBooleanCellValue());
        }
        return formatter.formatCellValue(c).trim();
    }
}
