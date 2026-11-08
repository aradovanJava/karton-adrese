# Treniranje modela kvalitete zraka (PM10) na Windowsima.
#
#   -Ogledni                        sintetički podaci, provjera cijelog toka (bez interneta) + testovi
#   bez -Ogledni                    stvarna mjerenja iz ml\podaci\<PostajaId>\ i ERA5 preko Open-Meteo arhive
#
# Primjeri (iz korijena projekta):
#   powershell -ExecutionPolicy Bypass -File scripts\windows\treniraj-model.ps1 -Ogledni
#   powershell -ExecutionPolicy Bypass -File scripts\windows\treniraj-model.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\windows\treniraj-model.ps1 -PostajaId zagreb-1 `
#       -Naziv "Zagreb-1" -Mreza "Državna mreža za trajno praćenje kvalitete zraka" -Lat 45.8000 -Lon 15.9740
#
# Rezultat: model\<PostajaId>\pm10.onnx, metapodaci.json, kartica-modela.md.
# Aplikacija ga učitava pri pokretanju, pa je nakon treniranja ponovno pokrenite.

param(
    [switch]$Ogledni,
    [string]$PostajaId = 'djordjiceva',
    [string]$Naziv = 'Đorđićeva ulica',
    [string]$Mreza = 'Gradska mreža za praćenje kvalitete zraka',
    [double]$Lat = 45.8131,
    [double]$Lon = 15.9846,
    [string]$Pm10 = '',
    [string]$StupacDatum = '',
    [string]$StupacPm10 = ''
)

$ErrorActionPreference = 'Stop'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ml = (Resolve-Path (Join-Path $PSScriptRoot '..\..\ml')).Path
Set-Location $ml
$python = Join-Path $ml '.venv\Scripts\python.exe'
if (-not (Test-Path $python)) {
    Write-Host 'Python okruženje još ne postoji - pokrećem pripremu.'
    & (Join-Path $PSScriptRoot 'priprema-ml.ps1')
}

$kultura = [System.Globalization.CultureInfo]::InvariantCulture

if ($Ogledni) {
    Write-Host '=== Treniranje na oglednim (sintetičkim) podacima ==='
    & $python -m karton_ml.treniranje --ogledni
    if ($LASTEXITCODE -ne 0) { throw 'Treniranje nije uspjelo.' }
    Write-Host ''
    Write-Host '=== Testovi Python dijela ==='
    & $python -m pytest -q
    if ($LASTEXITCODE -ne 0) { throw 'Testovi nisu prošli.' }
} else {
    $mapa = Join-Path $ml "podaci\$PostajaId"
    if (-not $Pm10) {
        if (-not (Test-Path $mapa)) {
            New-Item -ItemType Directory -Path $mapa | Out-Null
        }
        $datoteke = Get-ChildItem $mapa -File | Where-Object { $_.Extension -in '.csv', '.xlsx', '.xls', '.json' }
        if (-not $datoteke) {
            Write-Host "U mapi $mapa nema datoteka s mjerenjima PM10."
            Write-Host 'Preuzmite satne ili dnevne podatke postaje (CSV/XLSX) s Portala otvorenih podataka Grada Zagreba'
            Write-Host 'ili iz sustava ISKZL (https://iszz.azo.hr/iskzl/), spremite ih u tu mapu i pokrenite ponovno.'
            exit 1
        }
        $Pm10 = Join-Path $mapa '*'
    }
    $argumenti = @('-m', 'karton_ml.treniranje', '--pm10', $Pm10, '--preuzmi-meteo',
        '--postaja-id', $PostajaId, '--naziv', $Naziv, '--mreza', $Mreza,
        '--lat', $Lat.ToString($kultura), '--lon', $Lon.ToString($kultura))
    if ($StupacDatum) { $argumenti += @('--stupac-datum', $StupacDatum) }
    if ($StupacPm10) { $argumenti += @('--stupac-pm10', $StupacPm10) }
    Write-Host "=== Treniranje na stvarnim mjerenjima: $Naziv ($PostajaId) ==="
    & $python @argumenti
    if ($LASTEXITCODE -ne 0) { throw 'Treniranje nije uspjelo.' }
}

Write-Host ''
Write-Host 'Model je spremljen u mapu model\. Ponovno pokrenite aplikaciju da ga učita.'
