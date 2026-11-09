"""Učitavanje i preuzimanje podataka za model kvalitete zraka.

Izvori:
  * PM10 – mjerenja gradske i državne mreže za praćenje kvalitete zraka.
      - datoteke s Portala otvorenih podataka Grada Zagreba (data.zagreb.hr, skup
        „Kvaliteta zraka – gradske mjerne postaje", mjesečne XLSX/CSV datoteke) ili
      - izvoz iz informacijskog sustava kvalitete zraka (iszz.azo.hr/iskzl).
    ``ucitaj_pm10`` prepoznaje stupac s datumom i stupac s PM10 (decimalni zarez,
    satne ili dnevne vrijednosti) pa ne treba ručno pripremati datoteke.
  * Meteorologija – Copernicus ERA5 (DOI 10.24381/cds.adbb2d47):
      - ``preuzmi_era5_openmeteo``: ERA5 preko Open-Meteo arhive (bez ključa) ili
      - ``ucitaj_era5_netcdf``: NetCDF preuzet iz Climate Data Storea (cdsapi).
  * ``ogledni_podaci``: sintetički podaci za provjeru cijelog toka bez interneta.
    Model treniran na njima NIJE stvarna procjena i u metapodacima je označen kao ogledni.
"""

from __future__ import annotations

import io
import re
from pathlib import Path

import numpy as np
import pandas as pd

USER_AGENT = "karton-adrese/1.0 (datathon; github.com/[tim]/karton-adrese)"

_DATUM_IMENA = ("datum", "date", "vrijeme", "time", "datum i vrijeme", "termin", "dan")
_PM10_IMENA = ("pm10", "pm 10", "pm₁₀", "vrijednost", "value", "koncentracija")


# ---------------------------------------------------------------------------
# PM10
# ---------------------------------------------------------------------------

def _broj(serija: pd.Series) -> pd.Series:
    """Pretvara tekst s decimalnim zarezom, razmacima i oznakama u broj."""
    if serija.dtype.kind in "fi":
        return serija.astype(float)
    tekst = (
        serija.astype(str)
        .str.replace(" ", "", regex=False)
        .str.replace(" ", "", regex=False)
        .str.replace(",", ".", regex=False)
        .str.extract(r"(-?\d+(?:\.\d+)?)", expand=False)
    )
    return pd.to_numeric(tekst, errors="coerce")


def _nadji_stupac(stupci: list[str], kandidati: tuple[str, ...]) -> str | None:
    norm = {s: re.sub(r"\s+", " ", str(s)).strip().lower() for s in stupci}
    for kandidat in kandidati:
        for izvorni, n in norm.items():
            if n == kandidat:
                return izvorni
    for kandidat in kandidati:
        for izvorni, n in norm.items():
            if kandidat in n:
                return izvorni
    return None


def _procitaj_tablicu(putanja: Path) -> pd.DataFrame:
    if putanja.suffix.lower() in (".xlsx", ".xls"):
        return pd.read_excel(putanja)
    sirovo = putanja.read_bytes()
    for kodiranje in ("utf-8-sig", "cp1250", "latin-1"):
        try:
            tekst = sirovo.decode(kodiranje)
            break
        except UnicodeDecodeError:
            continue
    separator = ";" if tekst.count(";") > tekst.count(",") else ","
    return pd.read_csv(io.StringIO(tekst), sep=separator)


def ucitaj_pm10(*putanje: str | Path, stupac_datum: str | None = None, stupac_pm10: str | None = None) -> pd.DataFrame:
    """Učitava jednu ili više datoteka (CSV/XLSX/JSON) i vraća dnevne prosjeke PM10.

    Rezultat: DataFrame sa stupcima ``datum`` (datetime64, bez vremena) i ``pm10``.
    Dnevni prosjek računa se samo ako dan ima barem 18 satnih vrijednosti (75 %),
    kao što traži Direktiva 2008/50/EZ za valjani dnevni podatak.
    """
    dijelovi = []
    for p in putanje:
        p = Path(p)
        if p.suffix.lower() == ".json":
            df = ucitaj_iskzl_json(p)
        else:
            df = _procitaj_tablicu(p)
        sd = stupac_datum or _nadji_stupac(list(df.columns), _DATUM_IMENA)
        sp = stupac_pm10 or _nadji_stupac(list(df.columns), _PM10_IMENA)
        if sd is None or sp is None:
            raise ValueError(
                f"{p.name}: ne prepoznajem stupce datuma i PM10 (stupci: {list(df.columns)}). "
                "Zadajte ih parametrima --stupac-datum i --stupac-pm10."
            )
        vrijeme = pd.to_datetime(df[sd], dayfirst=True, errors="coerce")
        dio = pd.DataFrame({"vrijeme": vrijeme, "pm10": _broj(df[sp])}).dropna()
        dijelovi.append(dio)
    if not dijelovi:
        raise ValueError("Nije zadana nijedna datoteka s mjerenjima PM10.")
    sve = pd.concat(dijelovi).drop_duplicates(subset="vrijeme")
    sve = sve[(sve["pm10"] >= 0) & (sve["pm10"] < 1500)]
    sve["datum"] = sve["vrijeme"].dt.normalize()
    satno = sve.groupby("datum")["pm10"].count().max() > 1
    if satno:
        agr = sve.groupby("datum")["pm10"].agg(["mean", "count"])
        agr = agr[agr["count"] >= 18]
        rezultat = agr["mean"].rename("pm10").reset_index()
    else:
        rezultat = sve.groupby("datum")["pm10"].mean().reset_index()
    return rezultat.sort_values("datum").reset_index(drop=True)


def ucitaj_iskzl_json(putanja: Path) -> pd.DataFrame:
    """Učitava JSON izvoz iz sustava ISKZL (popis zapisa s vrijednošću i vremenom).

    Očekivani oblik zapisa: {"vrijednost": 23.4, "vrijeme": 1704067200000, ...}
    (vrijeme u milisekundama od 1. 1. 1970.). Ako se oblik izvoza promijeni,
    ``ucitaj_pm10`` i dalje radi s CSV/XLSX izvozom istog sustava.
    """
    zapisi = pd.read_json(putanja)
    if "vrijeme" in zapisi.columns and np.issubdtype(zapisi["vrijeme"].dtype, np.number):
        zapisi["vrijeme"] = pd.to_datetime(zapisi["vrijeme"], unit="ms", utc=True).dt.tz_convert("Europe/Zagreb").dt.tz_localize(None)
    return zapisi


def preuzmi_iskzl(postaja: int, polutant: int, od: str, do: str, odrediste: Path, tip_podatka: int = 0) -> Path:
    """Preuzima mjerenja iz sustava ISKZL (iszz.azo.hr) u JSON datoteku.

    EKSPERIMENTALNO: adresa i parametri sučelja nisu službeno dokumentirani; prije
    korištenja provjerite ih u pregledniku na https://iszz.azo.hr/iskzl/ (izvoz podataka).
    od/do: 'dd.mm.yyyy'. Šifre postaje i polutanta preuzmite s istog portala.
    """
    import requests

    url = "https://iszz.azo.hr/iskzl/rs/podatak/export/json"
    params = {"postaja": postaja, "polutant": polutant, "tipPodatka": tip_podatka, "vrijemeOd": od, "vrijemeDo": do}
    odgovor = requests.get(url, params=params, headers={"User-Agent": USER_AGENT}, timeout=120)
    odgovor.raise_for_status()
    odrediste.parent.mkdir(parents=True, exist_ok=True)
    odrediste.write_bytes(odgovor.content)
    return odrediste


# ---------------------------------------------------------------------------
# Meteorologija (ERA5)
# ---------------------------------------------------------------------------

_OPENMETEO_SATNO = {
    "temperature_2m": "t2m",
    "relative_humidity_2m": "rh",
    "precipitation": "oborine",
    "wind_speed_10m": "vjetar",
    "boundary_layer_height": "pblh",
    "surface_pressure": "tlak",
}


def _dnevno_iz_satnog(satno: pd.DataFrame) -> pd.DataFrame:
    satno = satno.copy()
    satno["datum"] = pd.to_datetime(satno["vrijeme"]).dt.normalize()
    g = satno.groupby("datum")
    dnevno = pd.DataFrame(index=g.size().index)
    if "t2m" in satno:
        dnevno["t2m_sr"] = g["t2m"].mean()
        dnevno["t2m_min"] = g["t2m"].min()
    if "rh" in satno:
        dnevno["rh_sr"] = g["rh"].mean()
    if "oborine" in satno:
        dnevno["oborine"] = g["oborine"].sum(min_count=1)
    if "vjetar" in satno:
        dnevno["vjetar_sr"] = g["vjetar"].mean()
    if "pblh" in satno:
        dnevno["pblh_sr"] = g["pblh"].mean()
    if "tlak" in satno:
        dnevno["tlak_sr"] = g["tlak"].mean()
    return dnevno.reset_index()


def preuzmi_era5_openmeteo(lat: float, lon: float, od: str, do: str, odrediste: Path) -> pd.DataFrame:
    """Preuzima satne ERA5 vrijednosti preko Open-Meteo arhive i sprema dnevne prosjeke u CSV.

    od/do: 'YYYY-MM-DD'. Open-Meteo poslužuje ERA5 reanalizu (Copernicus) bez ključa;
    navedite izvor: "Copernicus Climate Change Service (ERA5), preko Open-Meteo".
    Brzina vjetra traži se u m/s.
    """
    import requests

    params = {
        "latitude": lat,
        "longitude": lon,
        "start_date": od,
        "end_date": do,
        "hourly": ",".join(_OPENMETEO_SATNO),
        "wind_speed_unit": "ms",
        "timezone": "Europe/Zagreb",
    }
    odgovor = requests.get(
        "https://archive-api.open-meteo.com/v1/era5", params=params, headers={"User-Agent": USER_AGENT}, timeout=300
    )
    odgovor.raise_for_status()
    satno_json = odgovor.json()["hourly"]
    satno = pd.DataFrame({"vrijeme": satno_json["time"]})
    for izvorni, nas in _OPENMETEO_SATNO.items():
        if izvorni in satno_json:
            satno[nas] = pd.to_numeric(pd.Series(satno_json[izvorni]), errors="coerce")
    dnevno = _dnevno_iz_satnog(satno)
    odrediste.parent.mkdir(parents=True, exist_ok=True)
    dnevno.to_csv(odrediste, index=False)
    return dnevno


def ucitaj_era5_netcdf(putanja: Path, lat: float, lon: float) -> pd.DataFrame:
    """Čita NetCDF preuzet iz Copernicus CDS-a (reanalysis-era5-single-levels).

    Potrebni paketi: xarray i netCDF4. Varijable: t2m (K), d2m (K, za vlažnost), tp (m),
    u10/v10 (m/s), blh (m), sp (Pa).
    """
    import xarray as xr

    ds = xr.open_dataset(putanja)
    vrem = "valid_time" if "valid_time" in ds.coords else "time"
    tocka = ds.sel(latitude=lat, longitude=lon, method="nearest").to_dataframe().reset_index()
    satno = pd.DataFrame({"vrijeme": pd.to_datetime(tocka[vrem]).dt.tz_localize("UTC").dt.tz_convert("Europe/Zagreb").dt.tz_localize(None)})
    if "t2m" in tocka:
        satno["t2m"] = tocka["t2m"] - 273.15
    if "t2m" in tocka and "d2m" in tocka:
        t = tocka["t2m"] - 273.15
        td = tocka["d2m"] - 273.15
        satno["rh"] = 100 * np.exp(17.625 * td / (243.04 + td)) / np.exp(17.625 * t / (243.04 + t))
    if "tp" in tocka:
        satno["oborine"] = tocka["tp"] * 1000
    if "u10" in tocka and "v10" in tocka:
        satno["vjetar"] = np.hypot(tocka["u10"], tocka["v10"])
    if "blh" in tocka:
        satno["pblh"] = tocka["blh"]
    if "sp" in tocka:
        satno["tlak"] = tocka["sp"] / 100
    return _dnevno_iz_satnog(satno)


def ucitaj_meteo_csv(putanja: str | Path) -> pd.DataFrame:
    df = pd.read_csv(putanja)
    df["datum"] = pd.to_datetime(df["datum"]).dt.normalize()
    return df


# ---------------------------------------------------------------------------
# Ogledni (sintetički) podaci
# ---------------------------------------------------------------------------

def ogledni_podaci(od: str = "2016-01-01", do: str = "2025-12-31", seed: int = 42) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Sintetički, ali fizikalno uvjerljivi dnevni podaci za Zagreb.

    PM10 raste zimi (grijanje, inverzije), uz slab vjetar i nizak granični sloj,
    a pada nakon kiše. Služi samo za provjeru da cijeli tok radi.
    """
    rng = np.random.default_rng(seed)
    datumi = pd.date_range(od, do, freq="D")
    n = len(datumi)
    dan = datumi.day_of_year.to_numpy()
    sezona = np.cos(2 * np.pi * (dan - 15) / 365.25)  # 1 sredinom siječnja, -1 sredinom srpnja

    t2m_sr = 12.0 - 11.0 * sezona + rng.normal(0, 3.0, n)
    t2m_min = t2m_sr - rng.uniform(3, 8, n)
    vjetar = rng.gamma(2.2, 1.0, n) * (1.0 + 0.2 * (1 - sezona))
    kisa_dan = rng.random(n) < (0.28 + 0.05 * sezona)
    oborine = np.where(kisa_dan, rng.gamma(1.2, 5.0, n), 0.0)
    pblh = np.clip(650 - 330 * sezona + 120 * vjetar + rng.normal(0, 90, n), 80, 2200)
    rh = np.clip(72 + 10 * sezona - 0.8 * (t2m_sr - 12) + rng.normal(0, 7, n), 30, 100)
    tlak = 1003 + 4 * sezona + rng.normal(0, 6, n)

    grijanje = np.isin(datumi.month, [10, 11, 12, 1, 2, 3, 4]).astype(float)
    vikend = (datumi.day_of_week >= 5).astype(float)
    log_pm = (
        np.log(26)
        + 0.36 * sezona
        + 0.18 * grijanje
        - 0.10 * vjetar
        - 0.00045 * (pblh - 600)
        - 0.012 * oborine
        - 0.07 * vikend
        + rng.normal(0, 0.22, n)
    )
    pm10 = np.exp(log_pm)
    pm10[rng.random(n) < 0.04] = np.nan  # nedostajući dani

    meteo = pd.DataFrame(
        {
            "datum": datumi,
            "t2m_sr": t2m_sr,
            "t2m_min": t2m_min,
            "rh_sr": rh,
            "oborine": oborine,
            "vjetar_sr": vjetar,
            "pblh_sr": pblh,
            "tlak_sr": tlak,
        }
    )
    pm = pd.DataFrame({"datum": datumi, "pm10": pm10}).dropna()
    return pm, meteo
