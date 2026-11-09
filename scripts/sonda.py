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
    (ZG, "geoportal-sportski-objekti"),
    (ZG, "geoportal-pjesacka-zona"),
    (ZG, "geoportal-ljekarne"),
]
PRETRAGE = [
    (ZG, "knjižnice"),
    (ZG, "knjiznice grada zagreba"),
    (ZG, "zelene površine parkovi"),
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


def sonda_overpass():
    """Parkovi i dječja igrališta iz OpenStreetMapa s geometrijom (za ogledne podatke)."""
    okvir = "(45.70,15.85,45.90,16.15)"
    upit = ("[out:json][timeout:180];(way[\"leisure\"=\"park\"][\"name\"]" + okvir
            + ";relation[\"leisure\"=\"park\"][\"name\"]" + okvir
            + ";nwr[\"leisure\"=\"playground\"]" + okvir + ";);out geom tags;")
    print("\n=== OVERPASS parkovi i igrališta")
    podaci = None
    for posluzitelj in ("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
                        "https://overpass.private.coffee/api/interpreter", "https://overpass-api.de/api/interpreter"):
        zahtjev = urllib.request.Request(posluzitelj, data=urllib.parse.urlencode({"data": upit}).encode(),
                                         headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(zahtjev, timeout=240) as odg:
                podaci = odg.read()
            print(f"  poslužitelj: {posluzitelj}")
            break
        except Exception as e:  # noqa: BLE001
            print(f"  {posluzitelj}: {e}")
            time.sleep(20)
    if podaci is None:
        return
    with open(os.path.join(IZLAZ, "osm-parkovi-igralista.json"), "wb") as d:
        d.write(podaci)
    el = json.loads(podaci).get("elements", [])
    print(f"  elemenata: {len(el)}", collections.Counter((e.get("type"), e.get("tags", {}).get("leisure")) for e in el))


DZS_WFS = "https://geostat.dzs.hr/inspire/pd-2021/wfs"


def ispis_odgovora(naslov, url, najvise=6000):
    print(f"\n--- {naslov}\n    {url}")
    try:
        zahtjev = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(zahtjev, timeout=120) as odg:
            vrsta = odg.headers.get("Content-Type")
            podaci = odg.read(NAJVISE)
        print(f"    HTTP 200, Content-Type={vrsta}, {len(podaci)} B")
    except urllib.error.HTTPError as e:
        podaci = e.read()[:najvise]
        print(f"    HTTP {e.code}: {tekst(podaci)[:najvise]}")
        return None
    except Exception as e:  # noqa: BLE001
        print(f"    GREŠKA: {e!r}")
        return None
    print("    " + tekst(podaci)[:najvise].replace("\n", "\n    "))
    return podaci


def sonda_dodatno():
    """Metapodaci DZS-a i mogući srodni servisi (statističke jedinice – mreža)."""
    for uuid in ("005ef965-6254-4ef3-82ba-ad918021ef88", "7db501e5-45fd-4748-a2e2-2dc32117af34"):
        ispis_odgovora(f"metapodaci {uuid}", f"https://geostat.gov.hr/geonetwork/srv/hrv/xml.metadata.get?uuid={uuid}", 9000)
    for put in ("inspire/su-grid/wfs", "inspire/su-grid-2021/wfs", "inspire/su-2021/wfs", "inspire/su/wfs",
                "inspire/wfs", "geoserver/wfs", "geoserver/ows"):
        ispis_odgovora(f"GetCapabilities {put}", f"https://geostat.dzs.hr/{put}?service=WFS&request=GetCapabilities", 1500)


def sonda_range():
    url = "https://gisco-services.ec.europa.eu/census/2021/Eurostat_Census-GRID_2021_V3.zip"
    for raspon in ("bytes=-70000", "bytes=0-99"):
        zahtjev = urllib.request.Request(url, headers={"User-Agent": UA, "Range": raspon})
        try:
            with urllib.request.urlopen(zahtjev, timeout=120) as odg:
                tijelo = odg.read(200000)
                print(f"  Range {raspon}: HTTP {odg.status}, Content-Range={odg.headers.get('Content-Range')}, "
                      f"Accept-Ranges={odg.headers.get('Accept-Ranges')}, {len(tijelo)} B")
        except Exception as e:  # noqa: BLE001
            print(f"  Range {raspon}: {e!r}")


def sonda_gisco_eu(u_zagrebu_fn=None):
    import re
    url = "https://gisco-services.ec.europa.eu/census/2021/Eurostat_Census-GRID_2021_V3.zip"
    put = os.path.join("/tmp", "grid.zip")
    zahtjev = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(zahtjev, timeout=300) as odg, open(put, "wb") as d:
            print(f"  {url}: Content-Length={odg.headers.get('Content-Length')}")
            while True:
                b = odg.read(1 << 20)
                if not b:
                    break
                d.write(b)
    except Exception as e:  # noqa: BLE001
        print(f"  {url}: {e!r}")
        return
    print(f"  preuzeto {os.path.getsize(put)} B")
    z = zipfile.ZipFile(put)
    for info in z.infolist():
        print(f"    {info.filename} {info.file_size} B (sažeto {info.compress_size})")

    def u_zagrebu(r):
        m = re.search(r"N(\d+)E(\d+)", r)
        if not m:
            return False
        n, e = int(m.group(1)), int(m.group(2))
        return 2500000 <= n <= 2570000 and 4755000 <= e <= 4825000

    for info in z.infolist():
        ime = info.filename.lower()
        if ime.endswith((".txt", ".md", ".xml", ".json")) and info.file_size < 300000:
            print("    --- " + info.filename + "\n    " + tekst(z.read(info))[:3000].replace("\n", "\n    "))
        if not ime.endswith(".csv"):
            continue
        with z.open(info) as f:
            tok = io.TextIOWrapper(f, encoding="utf-8-sig", errors="replace")
            zaglavlje = tok.readline().rstrip("\n")
            print(f"    --- {info.filename}\n    {zaglavlje}")
            isjecak, hr, ukupno = [zaglavlje], 0, 0
            for i, r in enumerate(tok):
                ukupno += 1
                if i < 5:
                    print("    " + r.rstrip()[:300])
                if u_zagrebu(r):
                    isjecak.append(r.rstrip("\n"))
            print(f"    redaka: {ukupno}, u isječku Zagreba: {len(isjecak) - 1}")
        with open(os.path.join(IZLAZ, "gisco-zagreb-" + os.path.basename(info.filename)), "w", encoding="utf-8") as d:
            d.write("\n".join(isjecak))


def sonda_gisco():
    """Eurostat GISCO – popis 2021. na mreži 1 km² za Hrvatsku (INSPIRE paket po državi)."""
    import re
    import sqlite3
    print("\n=== GISCO popis 2021. – Hrvatska")
    atom = tekst(dohvati("https://gisco-services.ec.europa.eu/census/2021/INSPIRE/PD.atom")[0])
    unos = next((u for u in re.findall(r"<entry>.*?</entry>", atom, re.S) if "Croatia" in u or "HR_PD" in u), "")
    print("  ATOM unos za Hrvatsku:\n    " + unos.replace("\n", "\n    "))
    ispis_odgovora("indeks pub/population-grid/2021", "https://gisco-services.ec.europa.eu/pub/population-grid/2021/", 1500)
    poveznice = re.findall(r'href="([^"]+)"', unos)
    # INSPIRE paketi po državi vraćaju 503 – provjera cjelovite mreže EU (verzija 3, 30. 5. 2026.)
    sonda_gisco_eu(u_zagrebu_fn=None)
    return
    poveznice.sort(key=lambda u: 0 if u.endswith("_CSV.zip") else 1 if u.endswith("_GPKG.zip") else 2)

    def u_zagrebu(r):
        m = re.search(r"N(\d+)E(\d+)", r)
        if not m:
            return False
        n, e = int(m.group(1)), int(m.group(2))
        n, e = (n * 1000, e * 1000) if n < 100000 else (n, e)
        return 2450000 <= n <= 2550000 and 4760000 <= e <= 4860000

    for url in poveznice:
        if not url.endswith((".zip",)) or "_GML" in url:
            continue
        podaci = None
        for pokusaj in range(4):
            try:
                podaci, odrezano = dohvati(url, 400 * 1024 * 1024)
                break
            except Exception as e:  # noqa: BLE001
                print(f"  {url}: pokušaj {pokusaj + 1}: {e!r}")
                time.sleep(15)
        if podaci is None:
            continue
        print(f"  {url}: {len(podaci)} B{' (ODREZANO)' if odrezano else ''}")
        z = zipfile.ZipFile(io.BytesIO(podaci))
        for info in z.infolist():
            print(f"    {info.filename} {info.file_size} B")
        for info in z.infolist():
            ime = info.filename.lower()
            if ime.endswith((".xml", ".txt", ".json", ".md")) and info.file_size < 200000:
                print("    --- " + info.filename + "\n    " + tekst(z.read(info))[:2500].replace("\n", "\n    "))
            elif ime.endswith(".csv"):
                redovi = tekst(z.read(info)).splitlines()
                print(f"    --- {info.filename}: {len(redovi)} redaka")
                for r in redovi[:8]:
                    print("    " + r[:400])
                isjecak = [redovi[0]] + [r for r in redovi[1:] if u_zagrebu(r)]
                print(f"    redaka u isječku Zagreba: {len(isjecak) - 1}")
                with open(os.path.join(IZLAZ, "gisco-" + os.path.basename(info.filename)), "w", encoding="utf-8") as d:
                    d.write("\n".join(isjecak))
            elif ime.endswith(".gpkg"):
                put = os.path.join(IZLAZ, "privremeno.gpkg")
                with open(put, "wb") as d:
                    d.write(z.read(info))
                veza = sqlite3.connect(put)
                for (tablica,) in veza.execute("select table_name from gpkg_contents"):
                    stupci = [r[1] for r in veza.execute(f'pragma table_info("{tablica}")')]
                    n = veza.execute(f'select count(*) from "{tablica}"').fetchone()[0]
                    print(f"    GPKG tablica {tablica}: {n} redaka, stupci {stupci}")
                    bez_geom = [c for c in stupci if c.lower() not in ("geom", "geometry", "shape")]
                    redovi = veza.execute(f'select {",".join(chr(34) + c + chr(34) for c in bez_geom)} from "{tablica}"').fetchall()
                    for r in redovi[:5]:
                        print(f"    {r}")
                    isjecak = [r for r in redovi if u_zagrebu(" ".join(str(x) for x in r))]
                    print(f"    redaka u isječku Zagreba: {len(isjecak)}")
                    with open(os.path.join(IZLAZ, f"gisco-{tablica}.csv"), "w", encoding="utf-8", newline="") as d:
                        w = csv.writer(d)
                        w.writerow(bez_geom)
                        w.writerows(isjecak)
                veza.close()
                os.remove(put)
        break


def sonda_popis():
    """DZS INSPIRE WFS – popis 2021. na mreži 1 km² (GetCapabilities, opis sloja, primjeri)."""
    import re
    print("\n=== DZS WFS popis 2021.")
    cap = ispis_odgovora("GetCapabilities", DZS_WFS + "?service=WFS&version=2.0.0&request=GetCapabilities", 2500)
    if cap is None:
        cap = ispis_odgovora("GetCapabilities 1.1.0", DZS_WFS + "?service=WFS&version=1.1.0&request=GetCapabilities", 2500)
    if cap is None:
        return
    t = tekst(cap)
    with open(os.path.join(IZLAZ, "dzs-capabilities.xml"), "w", encoding="utf-8") as d:
        d.write(t)
    slojevi = re.findall(r"<(?:wfs:)?FeatureType\b.*?<(?:wfs:)?Name>([^<]+)</", t, re.S)
    print(f"    SLOJEVI: {slojevi}")
    print(f"    outputFormat: {sorted(set(re.findall(r'<(?:ows:)?Value>([^<]*(?:json|gml|xml)[^<]*)</', t, re.I)))[:30]}")
    print(f"    CRS: {sorted(set(re.findall(r'<(?:wfs:)?(?:Default|Other)CRS>([^<]+)<', t)))}")
    for sloj in slojevi[:4]:
        q = urllib.parse.quote(sloj)
        ispis_odgovora(f"DescribeFeatureType {sloj}",
                       f"{DZS_WFS}?service=WFS&version=2.0.0&request=DescribeFeatureType&typeNames={q}", 4000)
        gml = ispis_odgovora(f"GetFeature GML {sloj} (count=2)",
                             f"{DZS_WFS}?service=WFS&version=2.0.0&request=GetFeature&typeNames={q}&count=2", 5000)
        if gml:
            with open(os.path.join(IZLAZ, f"dzs-{sloj.replace(':', '_')}-primjer.gml"), "wb") as d:
                d.write(gml)
        ispis_odgovora(f"GetFeature JSON {sloj} (count=2)",
                       f"{DZS_WFS}?service=WFS&version=2.0.0&request=GetFeature&typeNames={q}&count=2"
                       "&outputFormat=application/json", 3000)
        bbox = urllib.parse.quote("45.70000,15.80000,45.90000,16.20000,urn:ogc:def:crs:EPSG::4326")
        upit = (f"{DZS_WFS}?service=WFS&version=2.0.0&request=GetFeature&typeNames={q}&outputFormat=application%2Fjson"
                f"&srsName=urn%3Aogc%3Adef%3Acrs%3AEPSG%3A%3A4326&bbox={bbox}&count=50000")
        cijeli = ispis_odgovora(f"GetFeature kao u aplikaciji {sloj}", upit, 1500)
        if cijeli:
            with open(os.path.join(IZLAZ, f"dzs-{sloj.replace(':', '_')}-zagreb.json"), "wb") as d:
                d.write(cijeli)
        bbox3035 = urllib.parse.quote("4790000,2490000,4840000,2530000,urn:ogc:def:crs:EPSG::3035")
        cijeli = ispis_odgovora(f"GetFeature GML bbox EPSG:3035 {sloj}",
                                f"{DZS_WFS}?service=WFS&version=2.0.0&request=GetFeature&typeNames={q}"
                                f"&bbox={bbox3035}&count=5000", 1500)
        if cijeli:
            with open(os.path.join(IZLAZ, f"dzs-{sloj.replace(':', '_')}-zagreb-3035.gml"), "wb") as d:
                d.write(cijeli)


def sonda_sve_resurse(portal, paket):
    """Kao sonda_paketa, ali preuzima i prikazuje početak svakog resursa (bilo kojeg formata)."""
    print(f"\n=== SVI RESURSI {portal} :: {paket}")
    try:
        p = ckan(portal, "package_show", id=paket)
    except Exception as e:  # noqa: BLE001
        print(f"  NEDOSTUPNO: {e}")
        return
    print(f"  naslov: {p.get('title')} | metapodaci: {p.get('metadata_modified')} | organizacija: {(p.get('organization') or {}).get('title')}")
    print(f"  opis: {(p.get('notes') or '')[:500]!r}")
    for r in p.get("resources", []):
        print(f"  - [{r.get('format')}] {r.get('name')} | mimetype={r.get('mimetype')} | izmjena={r.get('last_modified')}")
        print(f"    {r.get('url')}")
        try:
            podaci, odrezano = dohvati(r["url"])
        except Exception as e:  # noqa: BLE001
            print(f"    preuzimanje nije uspjelo: {e!r}")
            continue
        ime = os.path.basename(urllib.parse.urlparse(r["url"]).path) or "resurs"
        with open(os.path.join(IZLAZ, f"{paket}__{ime}"), "wb") as d:
            d.write(podaci)
        print(f"    {len(podaci)} B{' (ODREZANO)' if odrezano else ''}, početak: {podaci[:12]!r}")
        if podaci[:2] != b"PK" and not podaci.startswith(b"%PDF"):
            print("    " + tekst(podaci)[:1200].replace("\n", "\n    "))
        elif podaci[:2] == b"PK":
            try:
                z = zipfile.ZipFile(io.BytesIO(podaci))
                print(f"    ZIP: {z.namelist()[:20]}")
            except Exception as e:  # noqa: BLE001
                print(f"    nije ZIP: {e}")
        time.sleep(0.5)


def main():
    os.makedirs(IZLAZ, exist_ok=True)
    if os.environ.get("SONDA_POPIS"):
        print("\n=== GISCO Range")
        sonda_range()
        print("\n=== GISCO V3 – isječak Zagreba")
        sonda_gisco_eu()
        for paket in ("zborna-mjesta-civilne-zastite-grada-zagreba", "evakuacijske-povrsine"):
            sonda_sve_resurse(ZG, paket)
        PAKETI.clear()
        PRETRAGE[:] = [(ZG, "zborna mjesta"), (ZG, "civilne zaštite"), (ZG, "evakuacij"), (ZG, "potres"),
                       (ZG, "sklonište"), (GOV, "zborna mjesta"), (GOV, "popis stanovništva 2021 mreža"),
                       (GOV, "prostorna distribucija stanovništva")]
    if os.environ.get("SONDA_SAMO_OSM"):
        sonda_overpass()
        PAKETI.clear()
        PRETRAGE.clear()
    for portal, paket in PAKETI:
        sonda_paketa(portal, paket)
    for portal, upit in PRETRAGE:
        sonda_pretrage(portal, upit)
    if os.environ.get("SONDA_GTFS"):
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
