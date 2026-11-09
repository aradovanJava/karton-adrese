package hr.kartonadrese.baza;

import hr.kartonadrese.core.geo.GeoUtil;
import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Prenosiva izvedba: pravokutni predfiltar u SQL-u + točna udaljenost (haversine) u Javi. */
@Component
@Profile("!postgis")
public class PravokutnaPretraga implements ProstornaPretraga {

    private final PoiRepozitorij poi;

    public PravokutnaPretraga(PoiRepozitorij poi) {
        this.poi = poi;
    }

    @Override
    public List<Poi> uKrugu(double lat, double lon, double polumjerM, Collection<Kategorija> kategorije) {
        return poi.uOkviru(GeoUtil.okvir(lat, lon, polumjerM), kategorije).stream()
                .filter(p -> GeoUtil.udaljenostM(lat, lon, p.lat(), p.lon()) <= polumjerM)
                .toList();
    }
}
