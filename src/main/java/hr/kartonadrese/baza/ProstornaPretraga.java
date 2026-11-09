package hr.kartonadrese.baza;

import hr.kartonadrese.core.model.Kategorija;
import hr.kartonadrese.core.model.Poi;

import java.util.Collection;
import java.util.List;

/**
 * Prostorni upit "objekti unutar polumjera". Dvije izvedbe:
 * {@link PravokutnaPretraga} (H2, ogledni način) i {@link PostgisPretraga} (profil {@code postgis}).
 */
public interface ProstornaPretraga {

    List<Poi> uKrugu(double lat, double lon, double polumjerM, Collection<Kategorija> kategorije);
}
