"""Značajke modela.

Isti izračun mora postojati i u Javi (``hr.kartonadrese.core.ml.Znacajke``), zato je
popis značajki zapisan u metapodatke modela, a kalendarske značajke računaju se
jednostavnim, dokumentiranim formulama.

Kalendarske značajke (računaju se iz datuma):
    dan_sin, dan_cos   – godišnji ciklus: sin/cos(2π · dan_u_godini / 365.25)
    mjesec             – 1–12
    dan_u_tjednu       – 0 = ponedjeljak … 6 = nedjelja
    vikend             – 1 za subotu i nedjelju
    sezona_grijanja    – 1 od listopada do travnja

Meteorološke značajke (dnevne vrijednosti iz ERA5):
    t2m_sr, t2m_min    – srednja i najniža temperatura zraka na 2 m (°C)
    rh_sr              – srednja relativna vlažnost (%)
    oborine            – dnevna količina oborine (mm)
    vjetar_sr          – srednja brzina vjetra na 10 m (m/s)
    pblh_sr            – srednja visina graničnog sloja (m) – važna za zimske inverzije
    tlak_sr            – srednji tlak zraka (hPa)
"""

from __future__ import annotations

import math

import numpy as np
import pandas as pd

KALENDARSKE = ["dan_sin", "dan_cos", "mjesec", "dan_u_tjednu", "vikend", "sezona_grijanja"]
METEO = ["t2m_sr", "t2m_min", "rh_sr", "oborine", "vjetar_sr", "pblh_sr", "tlak_sr"]


def kalendarske_znacajke(datumi: pd.Series) -> pd.DataFrame:
    d = pd.to_datetime(datumi)
    dan = d.dt.day_of_year.astype(float)
    mjesec = d.dt.month
    dow = d.dt.day_of_week
    return pd.DataFrame(
        {
            "dan_sin": np.sin(2 * math.pi * dan / 365.25),
            "dan_cos": np.cos(2 * math.pi * dan / 365.25),
            "mjesec": mjesec.astype(float),
            "dan_u_tjednu": dow.astype(float),
            "vikend": (dow >= 5).astype(float),
            "sezona_grijanja": mjesec.isin([10, 11, 12, 1, 2, 3, 4]).astype(float),
        },
        index=datumi.index,
    )


def dostupne_meteo(meteo: pd.DataFrame) -> list[str]:
    """Meteorološke značajke koje postoje i nisu potpuno prazne."""
    return [m for m in METEO if m in meteo.columns and meteo[m].notna().mean() > 0.8]


def izgradi_skup(pm10: pd.DataFrame, meteo: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series, list[str]]:
    """Spaja dnevna mjerenja PM10 i meteorologiju te vraća (X, y, popis_značajki).

    pm10:  stupci ``datum`` (datum) i ``pm10`` (µg/m³, dnevni prosjek)
    meteo: stupac ``datum`` i meteorološki stupci iz ``METEO``
    """
    meteo_stupci = dostupne_meteo(meteo)
    spojeno = pm10.merge(meteo[["datum", *meteo_stupci]], on="datum", how="inner")
    spojeno = spojeno.dropna(subset=["pm10", *meteo_stupci]).sort_values("datum").reset_index(drop=True)
    kal = kalendarske_znacajke(spojeno["datum"])
    znacajke = KALENDARSKE + meteo_stupci
    X = pd.concat([kal, spojeno[meteo_stupci]], axis=1)[znacajke].astype("float32")
    X.index = pd.to_datetime(spojeno["datum"])
    y = spojeno["pm10"].astype(float)
    y.index = X.index
    return X, y, znacajke


def klimatologija(meteo: pd.DataFrame, stupci: list[str]) -> list[dict]:
    """Prosječne dnevne meteorološke vrijednosti po danu u godini (1–366).

    Java aplikacija iz njih slaže "tipičnu godinu" i za nju računa sezonsku procjenu.
    Vrijednosti se zaglađuju pomičnim prosjekom od 15 dana (kružno preko kraja godine).
    """
    m = meteo.copy()
    m["dan"] = pd.to_datetime(m["datum"]).dt.day_of_year
    po_danu = m.groupby("dan")[stupci].mean().reindex(range(1, 367))
    po_danu.loc[366] = po_danu.loc[366].fillna(po_danu.loc[365])
    po_danu = po_danu.interpolate(limit_direction="both")
    prosireno = pd.concat([po_danu.iloc[-7:], po_danu, po_danu.iloc[:7]])
    zagladeno = prosireno.rolling(15, center=True).mean().iloc[7:-7]
    zagladeno.index = range(1, 367)
    return [
        {"dan": int(dan), **{s: round(float(zagladeno.loc[dan, s]), 4) for s in stupci}}
        for dan in zagladeno.index
    ]
