$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$venvRoot = Join-Path $projectRoot ".venv"
$venvPython = Join-Path $venvRoot "Scripts\python.exe"
$venvPythonw = Join-Path $venvRoot "Scripts\pythonw.exe"
$requirements = Join-Path $projectRoot "requirements.txt"
$appPath = Join-Path $projectRoot "app.py"
$localDir = Join-Path $projectRoot ".local"
$statePath = Join-Path $localDir "server.json"
$launcherLog = Join-Path $localDir "launcher-error.log"

function Get-HealthyServerState {
    if (-not (Test-Path -LiteralPath $statePath)) {
        return $null
    }

    try {
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $healthUrl = "http://127.0.0.1:$($state.port)/api/health"
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
        if ($health.ok -and $health.instance -eq $state.instance) {
            return $state
        }
    }
    catch {
        return $null
    }

    return $null
}

try {
    New-Item -ItemType Directory -Path $localDir -Force | Out-Null

    if (-not (Test-Path -LiteralPath $venvPython)) {
        $systemPython = Get-Command python -ErrorAction SilentlyContinue
        if (-not $systemPython) {
            throw "Python 3.10 or newer was not found."
        }
        Write-Host "First launch: creating the private Python environment..."
        & $systemPython.Source -m venv $venvRoot
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create the Python environment."
        }
    }

    & $venvPython -c "import sticker_convert" 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "First launch: installing conversion components..."
        & $venvPython -m pip install --upgrade pip
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to update pip."
        }
        & $venvPython -m pip install -r $requirements
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to install conversion components."
        }
    }

    $state = Get-HealthyServerState
    if (-not $state) {
        $previousNoBrowser = $env:TGWA_NO_BROWSER
        $env:TGWA_NO_BROWSER = "1"
        try {
            Start-Process -FilePath $venvPythonw `
                -ArgumentList "`"$appPath`"" `
                -WorkingDirectory $projectRoot `
                -WindowStyle Hidden
        }
        finally {
            if ($null -eq $previousNoBrowser) {
                Remove-Item Env:TGWA_NO_BROWSER -ErrorAction SilentlyContinue
            }
            else {
                $env:TGWA_NO_BROWSER = $previousNoBrowser
            }
        }

        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            Start-Sleep -Milliseconds 250
            $state = Get-HealthyServerState
            if ($state) {
                break
            }
        }
    }

    if (-not $state) {
        $appError = Join-Path $localDir "app-error.log"
        if (Test-Path -LiteralPath $appError) {
            throw "The local server did not start. See .local\app-error.log."
        }
        throw "The local server did not start within 15 seconds."
    }

    $appUrl = "http://127.0.0.1:$($state.port)/?key=$($state.app_key)"
    Start-Process -FilePath $appUrl
    if (Test-Path -LiteralPath $launcherLog) {
        Remove-Item -LiteralPath $launcherLog -Force
    }
    exit 0
}
catch {
    New-Item -ItemType Directory -Path $localDir -Force | Out-Null
    ($_ | Out-String) | Set-Content -LiteralPath $launcherLog -Encoding UTF8
    Write-Host ""
    Write-Host "TG to WA startup failed." -ForegroundColor Red
    Write-Host "Details: $launcherLog"
    Write-Host $_.Exception.Message
    exit 1
}

