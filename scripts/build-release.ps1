<#
.SYNOPSIS
Builds a signed release APK of LiftBook and puts it in dist\.

.DESCRIPTION
Signing reads keystore.properties in the repository root (README, "Signed release builds").
Without one, this script creates a signing key and writes the file, asking for a password - or
reading $env:LIFTBOOK_KEYSTORE_PASSWORD. Keep the keystore and the file somewhere safe: every
later update must be signed with the same key, or the phone refuses to install it over the old one.

.PARAMETER Install
After building, install the APK on the connected phone with adb.

.EXAMPLE
.\scripts\build-release.ps1

.EXAMPLE
.\scripts\build-release.ps1 -Install
#>
[CmdletBinding()]
param([switch]$Install)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$root = (Get-Location).Path

# Native programs write progress to stderr; that is not an error.
function Invoke-Native {
    param([string]$Command, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$([IO.Path]::GetFileName($Command)) failed (exit code $LASTEXITCODE)." }
    } finally {
        $ErrorActionPreference = $previous
    }
}

# --- Java ---------------------------------------------------------------------------------------
# Gradle needs a JDK. Android Studio ships one; fall back to it when nothing else is set up.
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    foreach ($dir in @("$env:ProgramFiles\Android\Android Studio\jbr", "$env:LOCALAPPDATA\Programs\Android Studio\jbr")) {
        if (Test-Path (Join-Path $dir "bin\java.exe")) {
            $env:JAVA_HOME = $dir
            break
        }
    }
}
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "No JDK found. Install one, or set JAVA_HOME (Android Studio's is its jbr folder)."
}
# keytool lives beside java. java may be on the PATH through a launcher folder that has no
# keytool, so as a last resort ask java where its home is.
function Find-Keytool {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\keytool.exe")) { return "$env:JAVA_HOME\bin\keytool.exe" }
    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $settings = & java -XshowSettings:properties -version 2>&1 | Out-String
    if ($settings -match '(?m)^\s*java\.home = (.+)$') {
        $candidate = Join-Path $Matches[1].Trim() "bin\keytool.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    return $null
}
$keytool = Find-Keytool

# --- Signing ------------------------------------------------------------------------------------
$keystoreFile = "liftbook-release.jks"
if (-not (Test-Path "keystore.properties")) {
    Write-Host "No keystore.properties yet; creating a signing key."
    if (-not $keytool) { throw "keytool not found (it comes with the JDK)." }
    $password = $env:LIFTBOOK_KEYSTORE_PASSWORD
    if (-not $password) {
        while ($true) {
            $first = Read-Host -AsSecureString "Choose a keystore password (6+ characters)"
            $second = Read-Host -AsSecureString "Type it again"
            $password = [Net.NetworkCredential]::new("", $first).Password
            if ($password -ne [Net.NetworkCredential]::new("", $second).Password) {
                Write-Host "They don't match; try again."
                continue
            }
            if ($password.Length -lt 6) {
                Write-Host "Six characters at least."
                continue
            }
            break
        }
    }
    if (-not (Test-Path $keystoreFile)) {
        Invoke-Native $keytool @(
            "-genkeypair", "-keystore", $keystoreFile, "-alias", "liftbook", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000", "-storepass", $password, "-keypass", $password, "-dname", "CN=LiftBook, OU=Personal, O=LiftBook"
        )
    }
    # app/build.gradle.kts resolves storeFile against the app module, hence the "../".
    @("storeFile=../$keystoreFile", "storePassword=$password", "keyAlias=liftbook", "keyPassword=$password") |
        Set-Content -Path "keystore.properties" -Encoding ascii
    Write-Host "Created $keystoreFile and keystore.properties. Back both up: every update must be signed with this key."
}

# --- Build --------------------------------------------------------------------------------------
Invoke-Native (Join-Path $root "gradlew.bat") @("--quiet", "assembleRelease")
$apk = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "The build produced no signed APK (looked for $apk). Check keystore.properties." }
$match = Select-String -Path "app\build.gradle.kts" -Pattern 'versionName = "([^"]+)"' | Select-Object -First 1
$version = if ($match) { $match.Matches[0].Groups[1].Value } else { "release" }
New-Item -ItemType Directory -Force -Path "dist" | Out-Null
$out = "dist\liftbook-$version-$(Get-Date -Format 'yyyyMMdd-HHmm').apk"
Copy-Item $apk $out -Force
$size = "{0:N1} MB" -f ((Get-Item $out).Length / 1MB)
Write-Host "Release APK: $(Join-Path $root $out) ($size)"

# --- Install (optional) -------------------------------------------------------------------------
if ($Install) {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if (-not $adb) {
        $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $null }
        if (-not $sdk -and (Test-Path "local.properties")) {
            # local.properties escapes ":" and "\" the Java way.
            $line = Select-String -Path "local.properties" -Pattern '^sdk\.dir=(.*)$' | Select-Object -First 1
            if ($line) { $sdk = $line.Matches[0].Groups[1].Value -replace '\\\\', '\' -replace '\\:', ':' }
        }
        if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
        $adb = Join-Path $sdk "platform-tools\adb.exe"
    }
    if (-not (Test-Path $adb)) { throw "adb not found; install $out by hand." }
    Invoke-Native $adb @("install", "-r", $out)
    Write-Host "Installed on the connected device."
}
