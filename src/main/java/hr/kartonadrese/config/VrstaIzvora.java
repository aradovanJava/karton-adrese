package hr.kartonadrese.config;

/** Način spajanja na izvor podataka. */
public enum VrstaIzvora {
    /** Skup na CKAN portalu (data.zagreb.hr, data.gov.hr): package_show → resurs → GeoJSON/CSV/XLSX. */
    CKAN,
    /** Izravna poveznica na GeoJSON datoteku. */
    GEOJSON_URL,
    /** Izravna poveznica na CSV ili XLSX datoteku. */
    TABLICA_URL,
    /** OpenStreetMap Overpass API (pomoćni izvor, ODbL). */
    OVERPASS,
    /** Statički GTFS (ZIP) – stajališta i linije javnog prijevoza. */
    GTFS,
    /** WFS servis s mrežom stanovništva 1 km² (DZS, Popis 2021.). */
    WFS_POPIS,
    /** GeoJSON s granicama gradskih četvrti ili mjesnih odbora (CKAN ili URL). */
    GRANICE,
    /** Registar naziva ulica (CKAN tablica) – za dovršavanje upisa adrese. */
    ULICE
}
