"""Sonda izvora: provjerava nove skupove s portala otvorenih podataka i ZET GTFS.

Pokreće se u GitHub Actions (workflow "Sonda izvora") jer razvojno okruženje nema pristup
portalima. Ispisuje metapodatke, stupce i primjere zapisa, a preuzete datoteke pakira u
tar.gz + base64 između oznaka ===SONDA-POCETAK=== / ===SONDA-KRAJ=== kako bi se iz zapisa
posla mogle rekonstruirati i upotrijebiti za ogledne podatke.

Samo standardna biblioteka.
"""
import base64
import collections
import csv
import io
import json
import os
import sys
import tarfile
import time
import urllib.parse
import urllib.request
import zipfile

UA = "KartonAdrese-sonda/1.0 (+https://github.com/aradovanJava/karton-adrese)"
ZG = "https://data.zagreb.hr"
GOV = "https://data.gov.hr/ckan"

PAKETI = [
    (ZG, "geoportal-visokoskolske-ustanove"),
    (ZG, "geoportal-studentski-restoran"),
    (ZG, "geoportal-studentsko-naselje"),
    (GOV, "geoportal-studentska-naselja"),
    (ZG, "geoportal-sustav-javnih-bicikala"),
    (ZG, "geoportal-biciklisticke-staze"),
    (ZG, "geoportal-javna-igralista"),
    (ZG, "sportski-objekti-grad-zagreb"),
    (ZG, "geoportal-odmorko"),
    (ZG, "geoportal-gradski-vrt"),
    (GOV, "geoportal-osnovne-skole"),
    (ZG, "geoportal-osnovne-skole"),
    (ZG, "geoportal-ustanove-za-osobe-s-invaliditetom"),
    (ZG, "geoportal-pjesacka-zona"),
    (GOV, "knjiznice-grada-zagreba1"),
]
PRETRAGE = [
    (ZG, "sportski objekti"),
    (GOV, "geoportal sportski objekti"),
    (ZG, "igrališta"),
    (ZG, "parkovi"),
    (ZG, "zelene površine"),
]
IZLAZ = "sonda-izlaz"
NAJVISE = 8 * 1024 * 1024


def dohvati(url, najvise=NAJVISE):
    zahtjev = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(zahtjev, timeout=90) as odg:
        podaci = odg.read(najvise + 1)
    return podaci[:najvise], len(podaci) > najvise


def ckan(portal, akcija, **parametri):
    url = f"{portal}/api/3/action/{akcija}?{urllib.parse.urlencode(parametri)}"
    podaci, _ = dohvati(url)
    return json.loads(podaci.decode("utf-8"))["result"]


def tekst(podaci):
    for kod in ("utf-8-sig", "cp1250"):
        try:
            return podaci.decode(kod)
        except UnicodeDecodeError:
            continue
    return podaci.decode("latin-1")


def opisi_geojson(podaci):
    gj = json.loads(tekst(podaci))
    znacajke = gj.get("features", [])
    tipovi = collections.Counter((f.get("geometry") or {}).get("type") for f in znacajke)
    kljucevi = collections.Counter(k for f in znacajke for k in (f.get("properties") or {}))
    print(f"    GeoJSON: {len(znacajke)} značajki, geometrije {dict(tipovi)}, crs={gj.get('crs')}")
    print(f"    svojstva: {list(kljucevi)}")
    for f in znacajke[:2]:
        g = f.get("geometry") or {}
        koord = json.dumps(g.get("coordinates"))[:160]
        print(f"    primjer: {json.dumps(f.get('properties'), ensure_ascii=False)[:600]}")
        print(f"             {g.get('type')} {koord}")


def opisi_csv(podaci):
    t = tekst(podaci)
    prvi = t.splitlines()[0] if t else ""
    razdjelnik = max([",", ";", "\t", "|"], key=prvi.count)
    redovi = list(csv.reader(io.StringIO(t), delimiter=razdjelnik))
    print(f"    CSV: {len(redovi) - 1} redaka, razdjelnik {razdjelnik!r}")
    if redovi:
        print(f"    stupci: {redovi[0]}")
    for r in redovi[1:4]:
        print(f"    redak: {r}")


def opisi_kml(podaci):
    t = tekst(podaci)
    print(f"    KML: {t.count('<Placemark')} Placemark elemenata, {len(t)} znakova")
    i = t.find("<Placemark")
    print("    " + t[i:i + 1500].replace("\n", " ") if i >= 0 else "    (nema Placemark)")


def sonda_paketa(portal, paket):
    print(f"\n=== {portal} :: {paket}")
    try:
        p = ckan(portal, "package_show", id=paket)
    except Exception as e:  # noqa: BLE001
        print(f"  NEDOSTUPNO: {e}")
        return
    print(f"  naslov: {p.get('title')} | licenca: {p.get('license_title')} | metapodaci: {p.get('metadata_modified')}")
    print(f"  opis: {(p.get('notes') or '')[:300]!r}")
    resursi = p.get("resources", [])
    for r in resursi:
        print(f"  - [{r.get('format')}] {r.get('name')} | izmjena={r.get('last_modified')} | izrađen={r.get('created')}")
        print(f"    {r.get('url')}")
    poredak = ["GEOJSON", "CSV", "KML"]
    odabrani = []
    for f in poredak:
        for r in resursi:
            if (r.get("format") or "").strip().upper() == f and r not in odabrani:
                odabrani.append(r)
                break
    for r in odabrani:
        fmt = (r.get("format") or "").strip().upper()
        try:
            podaci, odrezano = dohvati(r["url"])
        except Exception as e:  # noqa: BLE001
            print(f"    preuzimanje {fmt} nije uspjelo: {e}")
            continue
        naziv = f"{paket}__{portal.split('//')[1].split('/')[0]}.{fmt.lower()}"
        with open(os.path.join(IZLAZ, naziv), "wb") as d:
            d.write(podaci)
        print(f"  >> {fmt}: {len(podaci)} B{' (ODREZANO)' if odrezano else ''} -> {naziv}")
        try:
            if fmt == "GEOJSON":
                opisi_geojson(podaci)
            elif fmt == "CSV":
                opisi_csv(podaci)
            else:
                opisi_kml(podaci)
        except Exception as e:  # noqa: BLE001
            print(f"    raščlamba nije uspjela: {e}")
        time.sleep(0.5)


def sonda_pretrage(portal, upit):
    print(f"\n=== PRETRAGA {portal} :: {upit!r}")
    try:
        rez = ckan(portal, "package_search", q=upit, rows=15)
    except Exception as e:  # noqa: BLE001
        print(f"  NEDOSTUPNO: {e}")
        return
    for p in rez.get("results", []):
        formati = sorted({(r.get("format") or "").upper() for r in p.get("resources", [])})
        print(f"  - {p.get('name')} | {p.get('title')} | {formati} | {p.get('metadata_modified')}")


def sonda_gtfs():
    print("\n=== ZET GTFS")
    try:
        podaci, odrezano = dohvati("https://zet.hr/gtfs-scheduled/latest", 200 * 1024 * 1024)
    except Exception as e:  # noqa: BLE001
        print(f"  NEDOSTUPNO: {e}")
        return
    z = zipfile.ZipFile(io.BytesIO(podaci))
    print(f"  datoteke: {z.namelist()}")
    for datoteka, polje in (("stops.txt", "wheelchair_boarding"), ("trips.txt", "wheelchair_accessible")):
        if datoteka not in z.namelist():
            continue
        with z.open(datoteka) as f:
            citac = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))
            brojac = collections.Counter(r.get(polje, "<nema stupca>") for r in citac)
            print(f"  {datoteka} stupci: {citac.fieldnames}")
            print(f"  {datoteka} {polje}: {dict(brojac)}")


def main():
    os.makedirs(IZLAZ, exist_ok=True)
    for portal, paket in PAKETI:
        sonda_paketa(portal, paket)
    for portal, upit in PRETRAGE:
        sonda_pretrage(portal, upit)
    sonda_gtfs()
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        tar.add(IZLAZ, arcname=IZLAZ)
    b = base64.b64encode(buf.getvalue()).decode("ascii")
    print(f"\n===SONDA-POCETAK=== {len(buf.getvalue())} B")
    for i in range(0, len(b), 4000):
        print(b[i:i + 4000])
    print("===SONDA-KRAJ===")


if __name__ == "__main__":
    sys.exit(main())
