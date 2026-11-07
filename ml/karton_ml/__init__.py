"""Karton adrese – model kvalitete zraka (PM10).

Tok rada:
    1. podaci.py      – učitavanje mjerenja PM10 i meteoroloških podataka (ERA5)
    2. znacajke.py    – izračun značajki (kalendar + meteorologija)
    3. treniranje.py  – vremenska unakrsna provjera, usporedba s osnovnim modelom,
                        završni model, procjena nesigurnosti, ONNX izvoz, kartica modela

Java aplikacija učitava izvezeni model (ONNX) i metapodatke (JSON) iz mape ``model/``.
"""

__version__ = "1.0.0"
