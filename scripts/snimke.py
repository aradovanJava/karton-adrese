"""Snimke zaslona glavnih stranica (za README, video i provjeru izgleda).

    pip install playwright && playwright install chromium
    python scripts/snimke.py http://localhost:8080 docs/snimke
"""
import base64
import sys
from pathlib import Path
from urllib.parse import quote

from playwright.sync_api import sync_playwright

BAZA = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
IZLAZ = Path(sys.argv[2] if len(sys.argv) > 2 else "docs/snimke")
ISPIS_BASE64 = "--base64" in sys.argv
A = quote("Gorjanovićeva ulica 15")
B = quote("Ilica 1")

STRANICE = [
    ("01-pocetna", "/", {}),
    ("02-karton", f"/karton?adresa={A}", {"puna": True}),
    ("03-karton-umirovljenik", f"/karton?adresa={A}&profil=umirovljenik", {}),
    ("04-usporedba", f"/usporedba?a={A}&b={B}", {"puna": True}),
    ("05-karta-15-minuta", "/karta", {"klik_celija": True}),
    ("06-o-podacima", "/podaci", {"puna": True}),
    ("07-ugradnja", "/ugradnja", {}),
    ("08-prijava-pogreske", f"/karton?adresa={A}", {"dijalog": True}),
    ("09-ortofoto", f"/karton?adresa={A}", {"ortofoto": True}),
]

IZLAZ.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    preglednik = p.chromium.launch()
    for sirina, visina, sufiks in ((1440, 900, ""), (390, 844, "-mobitel")):
        kontekst = preglednik.new_context(viewport={"width": sirina, "height": visina}, device_scale_factor=1, locale="hr-HR")
        for ime, put, opcije in STRANICE:
            if sufiks and ime not in ("01-pocetna", "02-karton", "04-usporedba"):
                continue
            s = kontekst.new_page()
            s.goto(BAZA + put, wait_until="networkidle")
            s.wait_for_timeout(1500)
            if opcije.get("dijalog"):
                s.click("[data-prijava] >> nth=0")
                s.wait_for_timeout(300)
            if opcije.get("ortofoto"):
                s.click("text=Ortofoto DGU")
                s.wait_for_timeout(3000)
            if opcije.get("klik_celija"):
                s.mouse.click(sirina // 2 - 150, visina // 2 + 60)
                s.wait_for_timeout(500)
            datoteka = IZLAZ / f"{ime}{sufiks}.png"
            s.screenshot(path=str(datoteka), full_page=bool(opcije.get("puna")))
            print("snimljeno", datoteka)
            if ISPIS_BASE64:
                print(f"===SNIMKA {datoteka.name} POCETAK===")
                print(base64.b64encode(datoteka.read_bytes()).decode())
                print(f"===SNIMKA {datoteka.name} KRAJ===")
            s.close()
        kontekst.close()
    preglednik.close()
