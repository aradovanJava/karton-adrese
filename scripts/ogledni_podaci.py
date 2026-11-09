"""Generira ogledne (sintetičke) podatke za rad Kartona adrese bez interneta.

    python scripts/ogledni_podaci.py   ->  src/main/resources/ogledni/podaci.json

Svi objekti imaju oznaku "(ogledno)" u nazivu i pripadaju izvorima "Ogledni podaci".
Iznimka su vrijednosti za Gorjanovićevu 15 preuzete iz izvornog prototipa tima
(OŠ Vladimira Nazora 260 m, DV Bukovac 229 m, stajalište Jordanovac – sjever 497 m),
ali i njihov je položaj na karti samo približan.
"""
import json
import math
import random
from pathlib import Path

from pyproj import Transformer

random.seed(2026)
U3035 = Transformer.from_crs(4326, 3035, always_xy=True)
IZ3035 = Transformer.from_crs(3035, 4326, always_xy=True)
CENTAR = (45.8131, 15.9775)
OKVIR = (45.62, 15.77, 45.97, 16.24)
DANAS = "2026-10-12"


def pomak(lat, lon, udaljenost_m, smjer_stupnjeva):
    dlat = udaljenost_m * math.cos(math.radians(smjer_stupnjeva)) / 111_320
    dlon = udaljenost_m * math.sin(math.radians(smjer_stupnjeva)) / (111_320 * math.cos(math.radians(lat)))
    return round(lat + dlat, 6), round(lon + dlon, 6)


IZVORI = [
    ("ogledni-obrazovanje", "Ogledni podaci – škole i vrtići"),
    ("ogledni-zdravlje", "Ogledni podaci – zdravstvo i ljekarne"),
    ("ogledni-prijevoz", "Ogledni podaci – stajališta javnog prijevoza"),
    ("ogledni-svakodnevno", "Ogledni podaci – trgovine"),
    ("ogledni-sigurnost", "Ogledni podaci – civilna zaštita"),
    ("ogledni-okolica", "Ogledni podaci – parkovi, sport, kultura, pristupačnost"),
]
IZVOR_KATEGORIJE = {
    "SKOLA": "ogledni-obrazovanje", "VRTIC": "ogledni-obrazovanje", "SREDNJA_SKOLA": "ogledni-obrazovanje",
    "LJEKARNA": "ogledni-zdravlje", "ZDRAVSTVO": "ogledni-zdravlje", "STAJALISTE": "ogledni-prijevoz",
    "TRGOVINA": "ogledni-svakodnevno", "ZBORNO_MJESTO": "ogledni-sigurnost", "EVAKUACIJA": "ogledni-sigurnost",
    "PARK": "ogledni-okolica", "SPORT": "ogledni-okolica", "KNJIZNICA": "ogledni-okolica", "KULTURA": "ogledni-okolica",
    "PRISTUPACNOST": "ogledni-okolica", "PJESACKA_ZONA": "ogledni-okolica",
}
TIP = {
    "SKOLA": "Osnovna škola", "VRTIC": "Dječji vrtić", "LJEKARNA": "Ljekarna", "ZDRAVSTVO": "Ambulanta",
    "STAJALISTE": "Stajalište", "TRGOVINA": "Trgovina", "SREDNJA_SKOLA": "Srednja škola", "KNJIZNICA": "Knjižnica",
    "PARK": "Park", "SPORT": "Igralište", "PRISTUPACNOST": "Centar za osobe s invaliditetom",
    "PJESACKA_ZONA": "Pješačka zona", "ZBORNO_MJESTO": "Zborno mjesto", "EVAKUACIJA": "Evakuacijska površina",
    "KULTURA": "Kulturni centar",
}

ADRESE = [
    {"adresa": "Gorjanovićeva ulica 15", "lat": 45.822021, "lon": 16.006415, "gradska_cetvrt": "Maksimir", "mjesni_odbor": "Bukovac"},
    {"adresa": "Ilica 1", "lat": 45.81305, "lon": 15.97640, "gradska_cetvrt": "Donji grad", "mjesni_odbor": None},
    {"adresa": "Trg bana Josipa Jelačića 5", "lat": 45.81290, "lon": 15.97755, "gradska_cetvrt": "Donji grad", "mjesni_odbor": None},
    {"adresa": "Savska cesta 41", "lat": 45.80060, "lon": 15.96720, "gradska_cetvrt": "Trnje", "mjesni_odbor": None},
    {"adresa": "Avenija Dubrovnik 15", "lat": 45.77580, "lon": 15.98270, "gradska_cetvrt": "Novi Zagreb – istok", "mjesni_odbor": None},
    {"adresa": "Maksimirska cesta 128", "lat": 45.82280, "lon": 16.01730, "gradska_cetvrt": "Maksimir", "mjesni_odbor": None},
]
OKO_ADRESE = [  # kategorija, broj, min m, max m
    ("SKOLA", 2, 300, 1300), ("VRTIC", 2, 200, 900), ("LJEKARNA", 2, 150, 700), ("ZDRAVSTVO", 2, 350, 1500),
    ("TRGOVINA", 3, 100, 600), ("STAJALISTE", 4, 120, 560), ("SREDNJA_SKOLA", 1, 600, 1800),
    ("KNJIZNICA", 1, 400, 1400), ("PARK", 2, 150, 900), ("SPORT", 1, 200, 900), ("PRISTUPACNOST", 1, 500, 2000),
    ("ZBORNO_MJESTO", 1, 200, 700), ("EVAKUACIJA", 1, 300, 1200), ("KULTURA", 1, 500, 1600),
]
LINIJE_TRAM = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "11", "12", "13", "14", "17"]
LINIJE_BUS = ["101", "102", "106", "109", "110", "118", "121", "201", "215", "219", "221", "268", "34", "36"]

objekti, stajalista = [], []
brojac = {}


def dodaj(kategorija, naziv, lat, lon, adresa=None, atributi=None):
    objekti.append({
        "izvor": IZVOR_KATEGORIJE[kategorija], "kategorija": kategorija, "naziv": naziv, "adresa": adresa,
        "lat": lat, "lon": lon, "atributi": atributi or {}, "datum": DANAS,
    })


def stajaliste(naziv, lat, lon, tramvaj, linije):
    sid = f"S{len(stajalista) + 1:04d}"
    stajalista.append({"id": sid, "naziv": naziv, "lat": lat, "lon": lon, "linije": linije,
                       "tramvaj": tramvaj, "autobus": not tramvaj or random.random() < 0.3,
                       "polazaka": random.randint(80, 900)})
    dodaj("STAJALISTE", naziv, lat, lon, atributi={"linije": ", ".join(linije), "vrsta": "tramvaj" if tramvaj else "autobus"})


# Vrijednosti iz prototipa za Gorjanovićevu 15
g = ADRESE[0]
lat, lon = pomak(g["lat"], g["lon"], 260, 200)
dodaj("SKOLA", "Osnovna škola Vladimira Nazora", lat, lon, "Kuščanova ulica 1 (približno)",
      {"Smjene": "2 smjene", "Produženi boravak": "da"})
lat, lon = pomak(g["lat"], g["lon"], 229, 60)
dodaj("VRTIC", "Dječji vrtić Bukovac", lat, lon, None, {"Slobodna mjesta": "da", "Upisno područje": "da"})
lat, lon = pomak(g["lat"], g["lon"], 497, 300)
stajaliste("Jordanovac – sjever", lat, lon, True, ["4", "5", "7", "11", "12", "34"])

for a in ADRESE:
    cetvrt = a["gradska_cetvrt"]
    for kat, n, dmin, dmax in OKO_ADRESE:
        for _ in range(n):
            brojac[(cetvrt, kat)] = brojac.get((cetvrt, kat), 0) + 1
            lat, lon = pomak(a["lat"], a["lon"], random.uniform(dmin, dmax), random.uniform(0, 360))
            naziv = f"{TIP[kat]} {cetvrt} {brojac[(cetvrt, kat)]} (ogledno)"
            if kat == "STAJALISTE":
                tram = random.random() < 0.55
                izbor = LINIJE_TRAM if tram else LINIJE_BUS
                stajaliste(naziv, lat, lon, tram, sorted(random.sample(izbor, random.randint(1, 4)), key=lambda x: int(x)))
            else:
                atr = {}
                if kat == "SKOLA":
                    atr = {"Smjene": random.choice(["1 smjena", "2 smjene"]), "Produženi boravak": random.choice(["da", "ne"])}
                if kat == "VRTIC":
                    atr = {"Slobodna mjesta": random.choice(["da", "ne"])}
                if kat in ("LJEKARNA", "ZDRAVSTVO"):
                    atr = {"Radno vrijeme": random.choice(["7–20", "8–16", "0–24"])}
                dodaj(kat, naziv, lat, lon, atributi=atr)
    if cetvrt == "Donji grad":
        lat, lon = pomak(a["lat"], a["lon"], 120, 90)
        dodaj("PJESACKA_ZONA", "Pješačka zona Donji grad (ogledno)", lat, lon)

# Objekti po cijelom gradu – gušći u središtu, rjeđi prema rubovima (za kartu grada 15 minuta)
SREDISTA = [((45.8131, 15.9775), 2600, 0.45), ((45.7800, 15.9800), 2400, 0.2), ((45.8250, 16.0500), 2600, 0.15),
            ((45.8050, 15.9200), 2300, 0.12), ((45.8300, 16.1100), 2000, 0.08)]
GRAD = {"SKOLA": 110, "VRTIC": 170, "LJEKARNA": 130, "ZDRAVSTVO": 70, "TRGOVINA": 380, "STAJALISTE": 650}


def nasumicna_tocka():
    r = random.random()
    for (c, sigma, udio) in SREDISTA:
        r -= udio
        if r <= 0:
            break
    while True:
        lat, lon = pomak(c[0], c[1], abs(random.gauss(0, sigma)), random.uniform(0, 360))
        if OKVIR[0] < lat < OKVIR[2] and OKVIR[1] < lon < OKVIR[3]:
            return lat, lon


for kat, n in GRAD.items():
    for i in range(1, n + 1):
        lat, lon = nasumicna_tocka()
        naziv = f"{TIP[kat]} (ogledno) {i}"
        if kat == "STAJALISTE":
            tram = random.random() < 0.3 and abs(lat - CENTAR[0]) < 0.03
            izbor = LINIJE_TRAM if tram else LINIJE_BUS
            stajaliste(naziv, lat, lon, tram, sorted(random.sample(izbor, random.randint(1, 3)), key=lambda x: int(x)))
        else:
            dodaj(kat, naziv, lat, lon)

# Stanovništvo po ćeliji 1 km² (EPSG:3035)
x0, y0 = U3035.transform(OKVIR[1], OKVIR[0])
x1, y1 = U3035.transform(OKVIR[3], OKVIR[2])
celije = {}
for e in range(int(min(x0, x1) // 1000 * 1000), int(max(x0, x1)) + 1, 1000):
    for n in range(int(min(y0, y1) // 1000 * 1000), int(max(y0, y1)) + 1, 1000):
        lon, lat = IZ3035.transform(e + 500, n + 500)
        if not (OKVIR[0] < lat < OKVIR[2] and OKVIR[1] < lon < OKVIR[3]):
            continue
        gustoca = 0.0
        for (c, sigma, udio) in SREDISTA:
            d = math.hypot((lat - c[0]) * 111_320, (lon - c[1]) * 77_800)
            gustoca += udio * math.exp(-(d / (sigma * 1.6)) ** 2)
        st = int(round(16000 * gustoca * random.uniform(0.75, 1.25) / 10) * 10)
        if st >= 150:
            celije[f"CRS3035RES1000mN{n}E{e}"] = st

POSTAJE = [
    {"id": "djordjiceva", "naziv": "Đorđićeva ulica", "mreza": "Gradska mreža za praćenje kvalitete zraka",
     "lat": 45.8131, "lon": 15.9846, "godina": 2025, "prosjek_pm10": 21.1, "dana_iznad_50": 4,
     "izvor": "Ogledni podaci (struktura kao Grad Zagreb – mreža za praćenje kvalitete zraka)"},
    {"id": "zagreb-1", "naziv": "Zagreb-1 (ogledno)", "mreza": "Državna mreža za trajno praćenje kvalitete zraka",
     "lat": 45.7995, "lon": 15.9740, "godina": 2025, "prosjek_pm10": 24.3, "dana_iznad_50": 9, "izvor": "Ogledni podaci"},
    {"id": "zagreb-3", "naziv": "Zagreb-3 (ogledno)", "mreza": "Državna mreža za trajno praćenje kvalitete zraka",
     "lat": 45.8360, "lon": 15.9773, "godina": 2025, "prosjek_pm10": 18.7, "dana_iznad_50": 3, "izvor": "Ogledni podaci"},
]
ULICE = sorted({"Gorjanovićeva ulica", "Ilica", "Trg bana Josipa Jelačića", "Savska cesta", "Avenija Dubrovnik",
                "Maksimirska cesta", "Vukovarska avenija", "Heinzelova ulica", "Zvonimirova ulica", "Draškovićeva ulica",
                "Frankopanska ulica", "Selska cesta", "Ulica grada Vukovara", "Kvaternikov trg", "Ulica kneza Branimira",
                "Ozaljska ulica", "Avenija Većeslava Holjevca", "Jordanovac", "Bukovačka cesta", "Petrova ulica"})

podaci = {
    "napomena": "OGLEDNI (sintetički) podaci za rad bez interneta. Ne prikazuju stvarno stanje.",
    "izvori": [{"id": i, "naziv": n, "izdavatelj": "Ogledni podaci", "licenca": "CC BY 4.0", "poveznica": None} for i, n in IZVORI],
    "adrese": ADRESE, "objekti": objekti, "stajalista": stajalista, "postaje": POSTAJE, "celije": celije, "ulice": ULICE,
}
izlaz = Path(__file__).resolve().parents[1] / "src/main/resources/ogledni/podaci.json"
izlaz.parent.mkdir(parents=True, exist_ok=True)
izlaz.write_text(json.dumps(podaci, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"{izlaz}: {len(objekti)} objekata, {len(stajalista)} stajališta, {len(celije)} ćelija, {izlaz.stat().st_size // 1024} kB")
