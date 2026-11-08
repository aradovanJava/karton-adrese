# Priprema Python okruženja za treniranje modela kvalitete zraka (Windows).
# Stvara ml\.venv s Pythonom 3.11+ i instalira pakete iz ml\requirements.txt.
# Pokretanje iz IntelliJ-a: konfiguracija „Model 1 - priprema Pythona”
# ili ručno: powershell -ExecutionPolicy Bypass -File scripts\windows\priprema-ml.ps1

$ErrorActionPreference = 'Stop'
$env:PYTHONUTF8 = '1'
$ml = (Resolve-Path (Join-Path $PSScriptRoot '..\..\ml')).Path
Set-Location $ml
$venvPython = Join-Path $ml '.venv\Scripts\python.exe'

function Nadji-Python {
    # 1) py launcher, 2) uobičajene mape instalacije s python.org, 3) python na PATH-u
    $ErrorActionPreference = 'Continue'
    $verzije = @('3.12', '3.13', '3.11')
    if (Get-Command py -ErrorAction SilentlyContinue) {
        foreach ($v in $verzije) {
            $put = & py "-$v" -c 'import sys; print(sys.executable)' 2>$null
            if ($LASTEXITCODE -eq 0 -and $put) { return ([string]$put).Trim() }
        }
    }
    foreach ($v in $verzije) {
        $mapa = 'Python' + $v.Replace('.', '')
        foreach ($korijen in @("$env:LOCALAPPDATA\Programs\Python", "$env:ProgramFiles", "$env:ProgramFiles\Python")) {
            $kandidat = Join-Path $korijen "$mapa\python.exe"
            if (Test-Path $kandidat) { return $kandidat }
        }
    }
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        & $python.Source -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)' 2>$null
        if ($LASTEXITCODE -eq 0) { return $python.Source }
    }
    return $null
}

if (-not (Test-Path $venvPython)) {
    $python = Nadji-Python
    if (-not $python) {
        throw 'Nije pronađen Python 3.11 ili noviji. Instalirajte Python 3.12 s https://www.python.org/downloads/ i pokrenite ponovno.'
    }
    Write-Host "Stvaram virtualno okruženje ml\.venv ($python) ..."
    & $python -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw 'Stvaranje virtualnog okruženja nije uspjelo.' }
}

Write-Host 'Instaliram pakete (numpy, pandas, LightGBM, ONNX ...) - prvi put traje nekoliko minuta.'
& $venvPython -m pip install --disable-pip-version-check --upgrade pip
& $venvPython -m pip install --disable-pip-version-check -r requirements.txt
if ($LASTEXITCODE -ne 0) { throw 'Instalacija paketa nije uspjela.' }

& $venvPython -c "import sys, lightgbm, onnxruntime; print('Python', sys.version.split()[0], '| LightGBM', lightgbm.__version__, '| ONNX Runtime', onnxruntime.__version__)"
Write-Host ''
Write-Host 'Python okruženje je spremno. Sljedeći korak: „Model 2 - treniranje (ogledni podaci)”.'
