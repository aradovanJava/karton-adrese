"""Dopunjuje ogledne podatke stvarnim isječcima s Portala otvorenih podataka Grada Zagreba.

    python scripts/ogledni_stvarni.py <mapa-s-preuzetim-skupovima> [<osm-parkovi-igralista.json>]

Mapa sadrži GeoJSON datoteke koje preuzima sonda izvora (scripts/sonda.py, GitHub Actions):
geoportal-osnovne-skole__data.zagreb.hr.geojson, geoportal-studentski-restoran__…, itd.

Sintetički objekti istih kategorija (škole, ljekarne, ustanove za osobe s invaliditetom,
sport, parkovi) zamjenjuju se stvarnima, a izvori dobivaju iste identifikatore kao u
application.yml – pravi uvoz (--uvoz) kasnije ih jednostavno zamijeni svježim podacima.
Biciklističke staze, gradski vrtovi i parkovi zadržavaju geometriju (udaljenost do ruba).
"""
import json
import math
import sys
from pathlib import Path

KORIJEN = Path(__file__).resolve().parent.parent
PODACI = KORIJEN / "src/main/resources/ogledni/podaci.json"
OKVIR = (45.62, 15.77, 45.97, 16.24)
PORTAL = "https://data.zagreb.hr/dataset/"

# id izvora, datoteka, kategorija, naziv izvora, datum podataka, svojstvo naziva, atributi koje zadržavamo
SKUPOVI = [
    ("zg-osnovne-skole", "geoportal-osnovne-skole", "SKOLA", "Geoportal – osnovne škole", "2022-07-14", "naziv",
     ["smjenski_rad", "produzeni_boravak", "sport_dvor", "br_uc", "telefon"]),
    ("zg-ljekarne", "geoportal-ljekarne", "LJEKARNA", "Geoportal – ljekarne", "2022-06-23", "naziv", ["telefon"]),
    ("zg-osi", "geoportal-ustanove-za-osobe-s-invaliditetom", "PRISTUPACNOST",
     "Geoportal – ustanove za osobe s invaliditetom", "2022-12-09", "naziv", ["vrsta", "telefon"]),
    ("zg-visoka-ucilista", "geoportal-visokoskolske-ustanove", "VISOKO_UCILISTE", "Geoportal – visokoškolske ustanove",
     "2022-12-09", "naziv", ["vrsta", "kampus", "web"]),
    ("zg-studentski-restoran", "geoportal-studentski-restoran", "MENZA", "Geoportal – studentski restorani",
     "2026-10-28", "naziv", ["web"]),
    ("zg-studentsko-naselje", "geoportal-studentsko-naselje", "STUDENTSKI_DOM", "Geoportal – studentska naselja",
     "2026-10-28", "naziv", ["web"]),
    ("zg-javni-bicikli", "geoportal-sustav-javnih-bicikala", "JAVNI_BICIKL", "Geoportal – sustav javnih bicikala",
     "2022-12-08", "naziv", ["broj_stalaka"]),
    ("zg-biciklisticke-staze", "geoportal-biciklisticke-staze", "BICIKLISTICKA_STAZA",
     "Geoportal – biciklističke staze (linije)", "2022-06-23", "lokacija", ["zavrsni_sloj", "duljina"]),
    ("zg-igralista", "geoportal-javna-igralista", "IGRALISTE", "Geoportal – javna sportska i dječja igrališta",
     "2023-09-25", "lokacija", ["Vrsta_objekta"]),
    ("zg-sportski-objekti", "geoportal-sportski-objekti", "SPORT", "Geoportal – sportski objekti", "2022-12-01",
     "naziv", ["objekt", "sportovi"]),
    ("zg-odmorko", "geoportal-odmorko", "ODMORKO", "Geoportal – Odmorko (besplatne sportske aktivnosti za učenike)",
     "2022-07-14", "lokacija", ["sport", "radno_vrijeme", "Školska_godina"]),
    ("zg-gradski-vrtovi", "geoportal-gradski-vrt", "GRADSKI_VRT", "Geoportal – gradski vrtovi (površine)",
     "2022-06-23", "naziv", ["br_vr_parcela", "adresa"]),
]
PAKETI = {"zg-osnovne-skole": "geoportal-osnovne-skole", "zg-ljekarne": "geoportal-ljekarne",
          "zg-osi": "geoportal-ustanove-za-osobe-s-invaliditetom"}
ZAMJENA_KATEGORIJA = {"SKOLA", "LJEKARNA", "PRISTUPACNOST", "SPORT"}

MALE = {"i", "u", "na", "za", "s", "sa", "od", "do", "iz", "pri", "kod", "nad", "pod", "o", "te", "ili", "bb"}
POCECI = ["osnovna glazbena škola", "osnovna škola", "glazbena škola", "centar za odgoj i obrazovanje",
          "poliklinika za rehabilitaciju", "dječji vrtić"]


def uredni_naziv(n):
    """Isto pravilo kao PrepoznavanjePolja.uredniNaziv u Javi (samo za nazive pisane velikim slovima)."""
    t = " ".join(str(n).split())
    slova = [c for c in t if c.isalpha()]
    if len(slova) < 4 or sum(c.isupper() for c in slova) < 0.9 * len(slova):
        return t
    mala = t.lower()
    rezultat, ostatak = "", mala
    for p in POCECI:
        if mala.startswith(p):
            rezultat, ostatak = p[0].upper() + p[1:], mala[len(p):]
            break
    for r in ostatak.split():
        prva = rezultat == ""
        rijec = r if (not prva and r in MALE) else veliko(r)
        rezultat += ("" if prva else " ") + rijec
    return rezultat


def veliko(rijec):
    out, gore = [], True
    for c in rijec:
        out.append(c.upper() if gore else c)
        gore = not c.isalnum() and c != "'"
    return "".join(out)


def dijelovi(geom):
    t, k = geom.get("type"), geom.get("coordinates") or []
    if t == "LineString":
        return "L", [k]
    if t in ("MultiLineString", "Polygon"):
        return ("P" if t == "Polygon" else "L"), list(k)
    if t == "MultiPolygon":
        return "P", [prsten for poligon in k for prsten in poligon]
    return None, []


def kompaktno(tip, dio):
    """Isti zapis kao Geometrija.uJson() u Javi, s 5 decimala ({lat, lon})."""
    d = []
    for niz in dio:
        tocke, prethodna = [], None
        for x, y in niz:
            t = [round(y, 5), round(x, 5)]
            if t != prethodna:
                tocke.append(t)
            prethodna = t
        if len(tocke) >= 2 or (tip == "L" and tocke):
            d.append(tocke)
    return json.dumps({"t": tip, "d": d}, separators=(",", ":")) if d else None


def srediste(dio):
    tocke = [t for niz in dio for t in niz]
    return sum(t[1] for t in tocke) / len(tocke), sum(t[0] for t in tocke) / len(tocke)


def u_okviru(lat, lon):
    return OKVIR[0] <= lat <= OKVIR[2] and OKVIR[1] <= lon <= OKVIR[3]


def ucitaj_skup(mapa, ime, kategorija, svojstvo, kljucevi, izvor, datum):
    put = mapa / f"{ime}__data.zagreb.hr.geojson"
    if not put.exists():
        print(f"  preskačem {ime}: nema datoteke {put.name}")
        return []
    objekti = []
    for f in json.loads(put.read_text(encoding="utf-8")).get("features", []):
        p, g = f.get("properties") or {}, f.get("geometry") or {}
        naziv = p.get(svojstvo) or p.get("naziv") or p.get("lokacija")
        if not naziv:
            continue
        if kategorija == "SKOLA" and p.get("vrsta_odg_ust") not in (None, "REDOVNA ŠKOLA"):
            continue  # posebne ustanove i umjetničke škole nisu "najbliža osnovna škola"
        geometrija = None
        if g.get("type") == "Point":
            lon, lat = g["coordinates"][:2]
        else:
            tip, dio = dijelovi(g)
            if not dio:
                continue
            geometrija = kompaktno(tip, dio)
            lat, lon = srediste(dio)
        if not u_okviru(lat, lon):
            continue
        atributi = {k: p[k] for k in kljucevi if p.get(k) not in (None, "", " ")}
        adresa = p.get("adresa") or (p.get("lokacija") if svojstvo != "lokacija" else None)
        o = {"izvor": izvor, "kategorija": kategorija, "naziv": uredni_naziv(naziv), "adresa": adresa,
             "lat": round(lat, 6), "lon": round(lon, 6), "atributi": atributi, "datum": datum}
        if geometrija:
            o["geometrija"] = geometrija
        objekti.append(o)
    return objekti


def osm_objekti(put):
    if not put or not Path(put).exists():
        return [], []
    parkovi, igralista = [], []
    for e in json.loads(Path(put).read_text(encoding="utf-8")).get("elements", []):
        tags = e.get("tags", {})
        dio = []
        if e.get("geometry"):
            dio.append([[t["lon"], t["lat"]] for t in e["geometry"]])
        for m in e.get("members", []):
            if m.get("role") != "inner" and m.get("geometry"):
                dio.append([[t["lon"], t["lat"]] for t in m["geometry"]])
        if e.get("type") == "node":
            lat, lon = e["lat"], e["lon"]
        elif dio:
            lat, lon = srediste(dio)
        else:
            continue
        atributi = {k: tags[k] for k in ("wheelchair", "opening_hours") if k in tags}
        atributi["osm_id"] = f"{e['type']}/{e['id']}"
        if tags.get("leisure") == "park":
            zatvoreno = all(len(d) >= 4 and d[0] == d[-1] for d in dio)
            o = {"izvor": "osm-parkovi", "kategorija": "PARK", "naziv": tags.get("name", "Park"), "adresa": None,
                 "lat": round(lat, 6), "lon": round(lon, 6), "atributi": atributi, "datum": "2026-11-08"}
            g = kompaktno("P" if zatvoreno else "L", dio)
            if g:
                o["geometrija"] = g
            parkovi.append(o)
        elif tags.get("leisure") == "playground":
            igralista.append({"izvor": "osm-igralista", "kategorija": "IGRALISTE",
                              "naziv": tags.get("name", "Dječje igralište"), "adresa": None,
                              "lat": round(lat, 6), "lon": round(lon, 6), "atributi": atributi,
                              "datum": "2026-11-08"})
    return parkovi, igralista


def main():
    mapa = Path(sys.argv[1])
    osm = sys.argv[2] if len(sys.argv) > 2 else None
    podaci = json.loads(PODACI.read_text(encoding="utf-8"))
    novi, izvori = [], []
    for izvor, ime, kategorija, naziv, datum, svojstvo, kljucevi in SKUPOVI:
        objekti = ucitaj_skup(mapa, ime, kategorija, svojstvo, kljucevi, izvor, datum)
        print(f"  {izvor}: {len(objekti)}")
        if objekti:
            novi.extend(objekti)
            izvori.append({"id": izvor, "naziv": naziv + " (isječak preuzet 8. 11. 2026.)", "izdavatelj": "Grad Zagreb",
                           "licenca": "Otvorena dozvola", "poveznica": PORTAL + PAKETI.get(izvor, ime)})
    parkovi, igralista = osm_objekti(osm)
    print(f"  osm-parkovi: {len(parkovi)}, osm-igralista: {len(igralista)}")
    zamjena = set(ZAMJENA_KATEGORIJA)
    if parkovi:
        zamjena.add("PARK")
        novi.extend(parkovi)
        izvori.append({"id": "osm-parkovi", "naziv": "Parkovi (OpenStreetMap, isječak 8. 11. 2026.)",
                       "izdavatelj": "Suradnici OpenStreetMapa", "licenca": "ODbL",
                       "poveznica": "https://www.openstreetmap.org/copyright"})
    if igralista:
        novi.extend(igralista)
        izvori.append({"id": "osm-igralista", "naziv": "Dječja igrališta (OpenStreetMap, isječak 8. 11. 2026.)",
                       "izdavatelj": "Suradnici OpenStreetMapa", "licenca": "ODbL",
                       "poveznica": "https://www.openstreetmap.org/copyright"})
    stari = [o for o in podaci["objekti"]
             if not (o["izvor"].startswith("ogledni") and o["kategorija"] in zamjena) and not
             any(o["izvor"] == i["id"] for i in izvori)]
    print(f"  sintetičkih zadržano: {len(stari)} (uklonjeno {len(podaci['objekti']) - len(stari)})")
    podaci["objekti"] = stari + novi
    ids = {i["id"] for i in izvori}
    podaci["izvori"] = [i for i in podaci["izvori"] if i["id"] not in ids] + izvori
    podaci["napomena"] = ("Ogledni podaci za rad bez interneta: vrtići, zdravstvo, trgovine i stajališta su "
                          "sintetički; škole, ljekarne, ustanove za osobe s invaliditetom, studentski i sportski "
                          "objekti, igrališta, gradski vrtovi i biciklističke staze stvarni su isječci s Portala "
                          "otvorenih podataka Grada Zagreba (preuzeto 8. 11. 2026.).")
    PODACI.write_text(json.dumps(podaci, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"  ukupno objekata: {len(podaci['objekti'])}, veličina {PODACI.stat().st_size // 1024} kB")


if __name__ == "__main__":
    main()
