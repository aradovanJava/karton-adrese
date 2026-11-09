# Kartica modela – sezonska procjena PM10 (Đorđićeva ulica)

> **OGLEDNI MODEL.** Treniran je na sintetičkim podacima samo radi provjere tehničkog toka. Ne prikazuje stvarno stanje kvalitete zraka. Prije prikaza građanima istrenirajte ga na stvarnim mjerenjima.

## Namjena
Procjena **tipičnog mjesečnog prosjeka koncentracije PM10** na mjernoj postaji Đorđićeva ulica za prikaz
u Kartonu adrese (sekcija „Kvaliteta zraka”). Model **ne prognozira** vrijednost za određeni dan,
ne služi za upozorenja o onečišćenju i ne zamjenjuje službene podatke mreže za praćenje kvalitete zraka.

## Podaci
- Mjerenja PM10: sintetički ogledni podaci
- Meteorologija: sintetički ogledni podaci
- Razdoblje: 2016-01-01 – 2025-12-31 (3506 dana s potpunim podacima)
- Osobni podaci: nema.

## Metoda
- LightGBM regresija nad log(1 + PM10); 13 značajki: dan_sin, dan_cos, mjesec, dan_u_tjednu, vikend, sezona_grijanja, t2m_sr, t2m_min, rh_sr, oborine, vjetar_sr, pblh_sr, tlak_sr.
- Vremenska unakrsna provjera u 5 dijelova (model uvijek uči samo na prošlosti).
- Osnovni model za usporedbu: prosjek PM10 po mjesecu iz skupa za učenje.
- Sezonska procjena: model se primjenjuje na „tipičnu godinu” (prosječna meteorologija po danu u godini, zaglađena 15 dana).
- Pojas nesigurnosti: 10. i 90. percentil omjera stvarnog i procijenjenog mjesečnog prosjeka izvan uzorka (≈ 80 % mjeseci).
- Izvoz u ONNX (onnxmltools); Java aplikacija koristi ONNX Runtime.

## Rezultati (izvan uzorka)
| Mjera | Vrijednost |
|---|---|
| MAE modela | 4.164 µg/m³ |
| MAE osnovnog modela | 5.666 µg/m³ |
| Poboljšanje u odnosu na osnovni model | 26.5 % |
| RMSE | 5.948 µg/m³ |
| R² | 0.757 |
| Pojas mjesečnog prosjeka | × 0.9518 – × 1.083 |

## Važnost značajki (prosječni apsolutni doprinos, TreeSHAP)
| Značajka | Udio |
|---|---|
| pblh_sr | 43.4 % |
| sezona_grijanja | 12.1 % |
| dan_cos | 10.7 % |
| vjetar_sr | 10.2 % |
| oborine | 4.5 % |
| dan_sin | 4.2 % |
| t2m_sr | 3.2 % |
| dan_u_tjednu | 2.7 % |
| tlak_sr | 2.6 % |
| t2m_min | 2.6 % |
| rh_sr | 2.2 % |
| mjesec | 1.3 % |
| vikend | 0.3 % |

## Ograničenja
- Procjena vrijedi za lokaciju mjerne postaje; za adresu se prikazuje najbliža postaja s modelom i udaljenost do nje.
- Model ne poznaje promjene izvora onečišćenja (novi promet, gradilišta, promjene grijanja) nakon razdoblja učenja.
- Kvaliteta ovisi o potpunosti mjerenja; dani s manje od 75 % satnih vrijednosti izostavljeni su.

## Ponovno treniranje
```
cd ml
python -m karton_ml.treniranje --pm10 <datoteke> --preuzmi-meteo --postaja-id djordjiceva --naziv "Đorđićeva ulica" --lat 45.8131 --lon 15.9846
```
Nastalo: 2026-11-09T00:44:40+01:00 · karton-ml 1.0.0 · LightGBM 4.7.0
