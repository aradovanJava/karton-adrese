import math

import pandas as pd

from karton_ml.znacajke import KALENDARSKE, kalendarske_znacajke, klimatologija


def test_kalendarske_znacajke_za_poznati_datum():
    # 7. 11. 2026. je subota, 311. dan u godini
    z = kalendarske_znacajke(pd.Series(pd.to_datetime(["2026-11-07"]))).iloc[0]
    assert list(z.index) == KALENDARSKE
    assert z["mjesec"] == 11
    assert z["dan_u_tjednu"] == 5
    assert z["vikend"] == 1
    assert z["sezona_grijanja"] == 1
    assert math.isclose(z["dan_sin"], math.sin(2 * math.pi * 311 / 365.25), rel_tol=1e-9)
    assert math.isclose(z["dan_cos"], math.cos(2 * math.pi * 311 / 365.25), rel_tol=1e-9)


def test_klimatologija_ima_366_dana_i_bez_praznina():
    datumi = pd.date_range("2019-01-01", "2021-12-31", freq="D")
    meteo = pd.DataFrame({"datum": datumi, "t2m_sr": datumi.dayofyear.astype(float)})
    k = klimatologija(meteo, ["t2m_sr"])
    assert len(k) == 366
    assert [z["dan"] for z in k] == list(range(1, 367))
    assert all(z["t2m_sr"] == z["t2m_sr"] for z in k)  # nema NaN
