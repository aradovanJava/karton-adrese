"""Treniranje modela PM10 i izvoz u ONNX za Java aplikaciju.

Primjeri (pokrenuti iz mape ``ml``):

    # 1) Provjera cijelog toka na oglednim (sintetičkim) podacima – bez interneta
    python -m karton_ml.treniranje --ogledni

    # 2) Stvarni podaci: datoteke s mjerenjima + ERA5 preuzet preko Open-Meteo
    python -m karton_ml.treniranje --pm10 podaci/djordjiceva/*.xlsx --preuzmi-meteo \\
        --postaja-id djordjiceva --naziv "Đorđićeva ulica" --lat 45.8131 --lon 15.9846

    # 3) Stvarni podaci: vlastiti CSV s dnevnom meteorologijom (stupci kao u znacajke.METEO)
    python -m karton_ml.treniranje --pm10 podaci/pm10.csv --meteo podaci/era5_zagreb.csv ...

Rezultat se sprema u ``../model/<postaja-id>/``:
    pm10.onnx           – model (ulaz "input": float32 [N, broj_značajki])
    metapodaci.json     – značajke, klimatologija, pojas nesigurnosti, metrike, kontrolni primjer
    kartica-modela.md   – kartica modela (model card) na hrvatskom
"""

from __future__ import annotations

import argparse
import datetime as dt
import glob
import json
import math
import platform
from pathlib import Path

import lightgbm as lgb
import numpy as np
import onnx
import onnxmltools
import onnxruntime as ort
import pandas as pd
import sklearn
from onnxmltools.convert.common.data_types import FloatTensorType
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import TimeSeriesSplit

from . import __version__
from .podaci import ogledni_podaci, preuzmi_era5_openmeteo, ucitaj_meteo_csv, ucitaj_pm10
from .znacajke import KALENDARSKE, izgradi_skup, kalendarske_znacajke, klimatologija

GRANICA_PM10_DNEVNO = 50.0  # µg/m³, Direktiva 2008/50/EZ
PARAMETRI = dict(
    objective="regression",
    n_estimators=600,
    learning_rate=0.03,
    num_leaves=31,
    min_child_samples=20,
    subsample=0.8,
    subsample_freq=1,
    colsample_bytree=0.9,
    reg_lambda=1.0,
    random_state=42,
    verbose=-1,
)


def _model() -> lgb.LGBMRegressor:
    return lgb.LGBMRegressor(**PARAMETRI)


def unakrsna_provjera(X: pd.DataFrame, y: pd.Series, n_dijelova: int = 5) -> tuple[dict, pd.Series]:
    """Vremenska unakrsna provjera (bez miješanja budućnosti u prošlost).

    Osnovni model (baseline) = prosjek PM10 po mjesecu iz skupa za učenje.
    Vraća metrike i predviđanja izvan uzorka (out-of-fold) za procjenu nesigurnosti.
    """
    tss = TimeSeriesSplit(n_splits=n_dijelova)
    oof = pd.Series(np.nan, index=y.index)
    dijelovi = []
    for i, (uci, test) in enumerate(tss.split(X), start=1):
        m = _model().fit(X.iloc[uci], np.log1p(y.iloc[uci]))
        pred = np.clip(np.expm1(m.predict(X.iloc[test])), 0, None)
        oof.iloc[test] = pred
        mj_prosjek = y.iloc[uci].groupby(X.iloc[uci]["mjesec"]).mean()
        baseline = X.iloc[test]["mjesec"].map(mj_prosjek).fillna(y.iloc[uci].mean()).to_numpy()
        dijelovi.append(
            {
                "dio": i,
                "test_od": str(y.index[test[0]].date()),
                "test_do": str(y.index[test[-1]].date()),
                "mae_model": round(float(mean_absolute_error(y.iloc[test], pred)), 3),
                "mae_osnovni": round(float(mean_absolute_error(y.iloc[test], baseline)), 3),
            }
        )
    valjano = oof.notna()
    y_v, p_v = y[valjano], oof[valjano]
    mj_ukupno = y.groupby(X["mjesec"]).mean()
    mae_model = float(mean_absolute_error(y_v, p_v))
    mae_osnovni = float(np.mean([d["mae_osnovni"] for d in dijelovi]))
    metrike = {
        "mae": round(mae_model, 3),
        "rmse": round(float(math.sqrt(mean_squared_error(y_v, p_v))), 3),
        "r2": round(float(r2_score(y_v, p_v)), 3),
        "mae_osnovni_model": round(mae_osnovni, 3),
        "poboljsanje_u_odnosu_na_osnovni": round(1 - mae_model / mae_osnovni, 3) if mae_osnovni > 0 else None,
        "osnovni_model": "prosjek PM10 po mjesecu iz skupa za učenje",
        "tocnost_dana_iznad_granice": _tocnost_prekoracenja(y_v, p_v),
        "dijelovi": dijelovi,
        "mjesecni_prosjek_mjerenja": {int(k): round(float(v), 2) for k, v in mj_ukupno.items()},
    }
    return metrike, oof


def _tocnost_prekoracenja(y: pd.Series, p: pd.Series) -> dict:
    stvarno = y > GRANICA_PM10_DNEVNO
    predvideno = p > GRANICA_PM10_DNEVNO
    tp = int((stvarno & predvideno).sum())
    return {
        "granica_ugm3": GRANICA_PM10_DNEVNO,
        "stvarnih_dana": int(stvarno.sum()),
        "pogodeno": tp,
        "lazne_uzbune": int((~stvarno & predvideno).sum()),
        "odziv": round(tp / int(stvarno.sum()), 3) if stvarno.sum() else None,
    }


def mjesecni_pojas(y: pd.Series, oof: pd.Series) -> dict:
    """Pojas nesigurnosti mjesečnog prosjeka iz omjera stvarno/predviđeno (izvan uzorka).

    Jednostavna konformna procjena: 10. i 90. percentil omjera mjesečnih prosjeka
    daju pojas koji u prošlosti obuhvaća oko 80 % mjeseci.
    """
    df = pd.DataFrame({"y": y, "p": oof}).dropna()
    mj = df.groupby([df.index.year, df.index.month]).mean()
    mj = mj[mj["p"] > 0]
    omjer = (mj["y"] / mj["p"]).to_numpy()
    if len(omjer) < 12:
        return {"donji_omjer": 0.75, "gornji_omjer": 1.30, "pokrivenost": 0.8, "broj_mjeseci": int(len(omjer)), "napomena": "premalo mjeseci, zadane vrijednosti"}
    return {
        "donji_omjer": round(float(np.quantile(omjer, 0.10)), 4),
        "gornji_omjer": round(float(np.quantile(omjer, 0.90)), 4),
        "pokrivenost": 0.8,
        "broj_mjeseci": int(len(omjer)),
    }


def vaznost_znacajki(model: lgb.LGBMRegressor, X: pd.DataFrame) -> list[dict]:
    """Prosječni apsolutni doprinos značajke (TreeSHAP iz LightGBM-a, pred_contrib=True)."""
    doprinosi = model.predict(X, pred_contrib=True)[:, :-1]
    sr = np.abs(doprinosi).mean(axis=0)
    udio = sr / sr.sum()
    poredak = np.argsort(-udio)
    return [{"znacajka": X.columns[i], "udio": round(float(udio[i]), 4)} for i in poredak]


def izvezi_onnx(model: lgb.LGBMRegressor, broj_znacajki: int, putanja: Path) -> onnx.ModelProto:
    onnx_model = onnxmltools.convert_lightgbm(
        model, initial_types=[("input", FloatTensorType([None, broj_znacajki]))], target_opset=15
    )
    putanja.parent.mkdir(parents=True, exist_ok=True)
    onnx.save_model(onnx_model, str(putanja))
    return onnx_model


def onnx_predvidanje(putanja: Path, matrica: np.ndarray) -> np.ndarray:
    sesija = ort.InferenceSession(str(putanja), providers=["CPUExecutionProvider"])
    izlaz = sesija.run(None, {"input": matrica.astype(np.float32)})[0]
    return np.asarray(izlaz).reshape(-1)


def matrica_tipicne_godine(metapodaci: dict, godina: int) -> tuple[pd.DatetimeIndex, np.ndarray]:
    """Značajke za svaki dan zadane godine uz klimatološku meteorologiju (kao u Javi)."""
    datumi = pd.date_range(f"{godina}-01-01", f"{godina}-12-31", freq="D")
    kal = kalendarske_znacajke(pd.Series(datumi))
    klima = {zapis["dan"]: zapis for zapis in metapodaci["klimatologija"]}
    stupci = []
    for z in metapodaci["znacajke"]:
        if z in KALENDARSKE:
            stupci.append(kal[z].to_numpy())
        else:
            stupci.append(np.array([klima[d.dayofyear][z] for d in datumi], dtype=float))
    return datumi, np.column_stack(stupci).astype(np.float32)


def sezonski_profil(predvidi_log, metapodaci: dict, godina: int) -> list[dict]:
    """Mjesečni prosjek procijenjenog PM10 za "tipičnu" godinu i pojas nesigurnosti."""
    datumi, matrica = matrica_tipicne_godine(metapodaci, godina)
    dnevno = np.clip(np.expm1(predvidi_log(matrica)), 0, None)
    po_mjesecu = pd.Series(dnevno, index=datumi).groupby(datumi.month).mean()
    pojas = metapodaci["pojas_mjesecni"]
    return [
        {
            "mjesec": int(m),
            "pm10": round(float(v), 2),
            "donja": round(float(v * pojas["donji_omjer"]), 2),
            "gornja": round(float(v * pojas["gornji_omjer"]), 2),
        }
        for m, v in po_mjesecu.items()
    ]


def statistika_mjerenja(y: pd.Series) -> dict:
    godine = y.groupby(y.index.year).count()
    potpune = godine[godine >= 300]
    godina = int(potpune.index.max()) if len(potpune) else int(y.index.year.max())
    g = y[y.index.year == godina]
    return {
        "godina": godina,
        "prosjek_pm10": round(float(g.mean()), 1),
        "dana_iznad_50": int((g > GRANICA_PM10_DNEVNO).sum()),
        "broj_dana_s_mjerenjem": int(g.count()),
    }


def kartica_modela(meta: dict) -> str:
    m = meta["metrike"]
    p = meta["postaja"]
    vaz = "\n".join(f"| {v['znacajka']} | {v['udio'] * 100:.1f} % |" for v in meta["vaznost_znacajki"])
    upozorenje = (
        "\n> **OGLEDNI MODEL.** Treniran je na sintetičkim podacima samo radi provjere tehničkog toka. "
        "Ne prikazuje stvarno stanje kvalitete zraka. Prije prikaza građanima istrenirajte ga na stvarnim mjerenjima.\n"
        if meta["ogledni"]
        else ""
    )
    poboljsanje = m.get("poboljsanje_u_odnosu_na_osnovni")
    poboljsanje_tekst = f"{poboljsanje * 100:.1f} %" if poboljsanje is not None else "—"
    return f"""# Kartica modela – sezonska procjena PM10 ({p['naziv']})
{upozorenje}
## Namjena
Procjena **tipičnog mjesečnog prosjeka koncentracije PM10** na mjernoj postaji {p['naziv']} za prikaz
u Kartonu adrese (sekcija „Kvaliteta zraka”). Model **ne prognozira** vrijednost za određeni dan,
ne služi za upozorenja o onečišćenju i ne zamjenjuje službene podatke mreže za praćenje kvalitete zraka.

## Podaci
- Mjerenja PM10: {', '.join(meta['izvori']['pm10'])}
- Meteorologija: {', '.join(meta['izvori']['meteo'])}
- Razdoblje: {meta['razdoblje']['od']} – {meta['razdoblje']['do']} ({meta['razdoblje']['broj_dana']} dana s potpunim podacima)
- Osobni podaci: nema.

## Metoda
- LightGBM regresija nad log(1 + PM10); {len(meta['znacajke'])} značajki: {', '.join(meta['znacajke'])}.
- Vremenska unakrsna provjera u 5 dijelova (model uvijek uči samo na prošlosti).
- Osnovni model za usporedbu: {m['osnovni_model']}.
- Sezonska procjena: model se primjenjuje na „tipičnu godinu” (prosječna meteorologija po danu u godini, zaglađena 15 dana).
- Pojas nesigurnosti: 10. i 90. percentil omjera stvarnog i procijenjenog mjesečnog prosjeka izvan uzorka (≈ 80 % mjeseci).
- Izvoz u ONNX (onnxmltools); Java aplikacija koristi ONNX Runtime.

## Rezultati (izvan uzorka)
| Mjera | Vrijednost |
|---|---|
| MAE modela | {m['mae']} µg/m³ |
| MAE osnovnog modela | {m['mae_osnovni_model']} µg/m³ |
| Poboljšanje u odnosu na osnovni model | {poboljsanje_tekst} |
| RMSE | {m['rmse']} µg/m³ |
| R² | {m['r2']} |
| Pojas mjesečnog prosjeka | × {meta['pojas_mjesecni']['donji_omjer']} – × {meta['pojas_mjesecni']['gornji_omjer']} |

## Važnost značajki (prosječni apsolutni doprinos, TreeSHAP)
| Značajka | Udio |
|---|---|
{vaz}

## Ograničenja
- Procjena vrijedi za lokaciju mjerne postaje; za adresu se prikazuje najbliža postaja s modelom i udaljenost do nje.
- Model ne poznaje promjene izvora onečišćenja (novi promet, gradilišta, promjene grijanja) nakon razdoblja učenja.
- Kvaliteta ovisi o potpunosti mjerenja; dani s manje od 75 % satnih vrijednosti izostavljeni su.

## Ponovno treniranje
```
cd ml
python -m karton_ml.treniranje --pm10 <datoteke> --preuzmi-meteo --postaja-id {p['id']} --naziv "{p['naziv']}" --lat {p['lat']} --lon {p['lon']}
```
Nastalo: {meta['stvoreno']} · karton-ml {meta['verzije']['karton_ml']} · LightGBM {meta['verzije']['lightgbm']}
"""


def treniraj(
    pm10: pd.DataFrame,
    meteo: pd.DataFrame,
    postaja: dict,
    izlaz: Path,
    ogledni: bool,
    izvori: dict,
    kontrolna_godina: int | None = None,
) -> dict:
    X, y, znacajke = izgradi_skup(pm10, meteo)
    if len(X) < 365:
        raise ValueError(f"Premalo dana s potpunim podacima ({len(X)}); potrebno je barem 365.")
    print(f"Skup: {len(X)} dana, {len(znacajke)} značajki ({X.index.min().date()} – {X.index.max().date()})")

    metrike, oof = unakrsna_provjera(X, y)
    print(f"MAE model {metrike['mae']} · osnovni {metrike['mae_osnovni_model']} · R² {metrike['r2']}")

    model = _model().fit(X, np.log1p(y))
    izlaz.mkdir(parents=True, exist_ok=True)
    onnx_putanja = izlaz / "pm10.onnx"
    izvezi_onnx(model, len(znacajke), onnx_putanja)

    # ONNX stabla računaju u float32, LightGBM u double: male razlike na pragovima su očekivane.
    razlike = np.abs(onnx_predvidanje(onnx_putanja, X.to_numpy()) - model.predict(X))
    if float(np.max(razlike)) > 0.02:
        raise RuntimeError(f"ONNX i LightGBM se razlikuju za {float(np.max(razlike)):.2e} (log skala)")
    print(f"ONNX provjeren (log skala: najveća razlika {float(np.max(razlike)):.1e}, prosječna {float(np.mean(razlike)):.1e})")

    meta = {
        "verzija_formata": 1,
        "postaja": postaja,
        "ogledni": ogledni,
        "izvori": izvori,
        "razdoblje": {"od": str(X.index.min().date()), "do": str(X.index.max().date()), "broj_dana": int(len(X))},
        "znacajke": znacajke,
        "ulaz_onnx": "input",
        "transformacija_cilja": "log1p",
        "granica_pm10_dnevno": GRANICA_PM10_DNEVNO,
        "metrike": metrike,
        "pojas_mjesecni": mjesecni_pojas(y, oof),
        "vaznost_znacajki": vaznost_znacajki(model, X),
        "klimatologija": klimatologija(meteo, [z for z in znacajke if z not in KALENDARSKE]),
        "statistika_mjerenja": statistika_mjerenja(y),
        "stvoreno": dt.datetime.now().astimezone().isoformat(timespec="seconds"),
        "verzije": {
            "karton_ml": __version__,
            "python": platform.python_version(),
            "lightgbm": lgb.__version__,
            "onnxmltools": onnxmltools.__version__,
            "onnxruntime": ort.__version__,
            "scikit_learn": sklearn.__version__,
        },
    }
    godina = kontrolna_godina or (X.index.max().year + 1)
    profil = sezonski_profil(lambda m: onnx_predvidanje(onnx_putanja, m), meta, godina)
    meta["kontrolni_primjer"] = {"godina": godina, "mjesecno": profil}

    (izlaz / "metapodaci.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    (izlaz / "kartica-modela.md").write_text(kartica_modela(meta), encoding="utf-8")
    print(f"Spremljeno u {izlaz}")
    return meta


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description="Treniranje modela PM10 (Karton adrese)")
    ap.add_argument("--ogledni", action="store_true", help="sintetički podaci (provjera toka, bez interneta)")
    ap.add_argument("--pm10", nargs="*", default=[], help="datoteke s mjerenjima (CSV/XLSX/JSON, može glob)")
    ap.add_argument("--stupac-datum")
    ap.add_argument("--stupac-pm10")
    ap.add_argument("--meteo", help="CSV s dnevnom meteorologijom (stupac datum + značajke)")
    ap.add_argument("--meteo-netcdf", help="NetCDF iz Copernicus CDS-a (potrebni xarray i netCDF4)")
    ap.add_argument("--preuzmi-meteo", action="store_true", help="preuzmi ERA5 preko Open-Meteo arhive")
    ap.add_argument("--postaja-id", default="djordjiceva")
    ap.add_argument("--naziv", default="Đorđićeva ulica")
    ap.add_argument("--mreza", default="Gradska mreža za praćenje kvalitete zraka")
    ap.add_argument("--lat", type=float, default=45.8131)
    ap.add_argument("--lon", type=float, default=15.9846)
    ap.add_argument("--izlaz", default=str(Path(__file__).resolve().parents[2] / "model"))
    ap.add_argument("--kontrolna-godina", type=int)
    a = ap.parse_args(argv)

    postaja = {"id": a.postaja_id, "naziv": a.naziv, "mreza": a.mreza, "lat": a.lat, "lon": a.lon}
    if a.ogledni:
        pm10, meteo = ogledni_podaci()
        izvori = {"pm10": ["sintetički ogledni podaci"], "meteo": ["sintetički ogledni podaci"]}
    else:
        datoteke = sorted({f for uzorak in a.pm10 for f in glob.glob(uzorak)})
        if not datoteke:
            ap.error("zadajte --pm10 <datoteke> ili --ogledni")
        pm10 = ucitaj_pm10(*datoteke, stupac_datum=a.stupac_datum, stupac_pm10=a.stupac_pm10)
        if a.meteo:
            meteo = ucitaj_meteo_csv(a.meteo)
            meteo_izvor = f"ERA5 (datoteka {Path(a.meteo).name})"
        elif a.meteo_netcdf:
            from .podaci import ucitaj_era5_netcdf

            meteo = ucitaj_era5_netcdf(Path(a.meteo_netcdf), a.lat, a.lon)
            meteo_izvor = "Copernicus ERA5 (Climate Data Store)"
        elif a.preuzmi_meteo:
            od, do = str(pm10["datum"].min().date()), str(pm10["datum"].max().date())
            meteo = preuzmi_era5_openmeteo(a.lat, a.lon, od, do, Path("podaci") / f"era5_{a.postaja_id}.csv")
            meteo_izvor = "Copernicus ERA5 preko Open-Meteo arhive"
        else:
            ap.error("zadajte --meteo, --meteo-netcdf ili --preuzmi-meteo")
        meteo["datum"] = pd.to_datetime(meteo["datum"]).dt.normalize()
        izvori = {"pm10": [Path(f).name for f in datoteke][:5] + (["…"] if len(datoteke) > 5 else []), "meteo": [meteo_izvor]}
    treniraj(pm10, meteo, postaja, Path(a.izlaz) / a.postaja_id, a.ogledni, izvori, a.kontrolna_godina)


if __name__ == "__main__":
    main()
