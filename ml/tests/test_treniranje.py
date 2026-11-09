import json

import numpy as np

from karton_ml.podaci import ogledni_podaci, ucitaj_pm10
from karton_ml.treniranje import matrica_tipicne_godine, onnx_predvidanje, treniraj


def test_cijeli_tok_na_oglednim_podacima(tmp_path):
    pm10, meteo = ogledni_podaci("2019-01-01", "2023-12-31")
    postaja = {"id": "test", "naziv": "Test", "mreza": "test", "lat": 45.81, "lon": 15.98}
    meta = treniraj(pm10, meteo, postaja, tmp_path, True, {"pm10": ["test"], "meteo": ["test"]}, 2025)

    for datoteka in ("pm10.onnx", "metapodaci.json", "kartica-modela.md"):
        assert (tmp_path / datoteka).exists()
    spremljeno = json.loads((tmp_path / "metapodaci.json").read_text(encoding="utf-8"))
    assert spremljeno["ogledni"] is True
    assert len(spremljeno["klimatologija"]) == 366
    profil = spremljeno["kontrolni_primjer"]["mjesecno"]
    assert len(profil) == 12
    # zimi više PM10 nego ljeti
    assert profil[0]["pm10"] > profil[6]["pm10"]
    assert all(m["donja"] <= m["pm10"] <= m["gornja"] for m in profil)
    assert meta["metrike"]["mae"] < meta["metrike"]["mae_osnovni_model"]

    _, matrica = matrica_tipicne_godine(spremljeno, 2025)
    assert matrica.shape == (365, len(spremljeno["znacajke"]))
    assert np.isfinite(onnx_predvidanje(tmp_path / "pm10.onnx", matrica)).all()


def test_ucitavanje_satnih_vrijednosti_s_decimalnim_zarezom(tmp_path):
    redovi = ["Datum;PM10 (µg/m3)"]
    for sat in range(24):
        redovi.append(f"01.02.2024 {sat:02d}:00;{20 + sat},5")
    for sat in range(10):  # nepotpun dan (manje od 18 sati) – izostavlja se
        redovi.append(f"02.02.2024 {sat:02d}:00;30,0")
    datoteka = tmp_path / "pm10.csv"
    datoteka.write_text("\n".join(redovi), encoding="utf-8")
    df = ucitaj_pm10(datoteka)
    assert len(df) == 1
    assert abs(df.loc[0, "pm10"] - (20.5 + 11.5)) < 1e-9
