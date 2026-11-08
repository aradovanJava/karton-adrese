package hr.kartonadrese.core.uvoz;

import hr.kartonadrese.core.geo.Geometrija;

import java.util.Map;

/**
 * Jedan objekt pročitan iz izvora prije spremanja (ustanova, stajalište …).
 * Koordinate su u WGS84; mogu nedostajati ako izvor ima samo adresu (tada se geokodira).
 */
public record UvozniZapis(String naziv, String adresa, Double lat, Double lon, Map<String, String> atributi,
                          Geometrija geometrija) {

    public UvozniZapis(String naziv, String adresa, Double lat, Double lon, Map<String, String> atributi) {
        this(naziv, adresa, lat, lon, atributi, null);
    }

    public boolean imaKoordinate() {
        return lat != null && lon != null;
    }

    public UvozniZapis sKoordinatama(double noviLat, double noviLon) {
        return new UvozniZapis(naziv, adresa, noviLat, noviLon, atributi, geometrija);
    }
}
