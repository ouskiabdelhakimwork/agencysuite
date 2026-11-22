# Bascule la session PowerShell courante sur Java 25, sans droits administrateur
# et sans toucher au reste du poste.
#
# Usage (a la racine du depot) :   . .\env.ps1
#                          ou      . .\env.ps1 -JdkPath "C:\Users\moi\.jdks\temurin-25.0.1"
#
# Si PowerShell refuse d'executer le script :
#   Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
param([string]$JdkPath)

function Test-Jdk25([string]$Path) {
    if (-not $Path) { return $false }
    $javaExe = Join-Path $Path "bin\java.exe"
    if (-not (Test-Path $javaExe)) { return $false }
    $output = & $javaExe -version 2>&1 | Out-String
    return $output -match 'version "25'
}

if (-not $JdkPath) {
    if (Test-Jdk25 $env:JAVA_HOME) {
        $JdkPath = $env:JAVA_HOME
    } else {
        $candidates = @("$env:USERPROFILE\.jdks", "$env:USERPROFILE\java25") |
            Where-Object { Test-Path $_ } |
            ForEach-Object { Get-ChildItem $_ -Directory } |
            Where-Object { Test-Jdk25 $_.FullName } |
            Select-Object -First 1
        if ($candidates) { $JdkPath = $candidates.FullName }
    }
}

if (-not (Test-Jdk25 $JdkPath)) {
    Write-Error "Aucun JDK 25 trouve. Indiquez son dossier : . .\env.ps1 -JdkPath <dossier du JDK>"
    return
}

$env:JAVA_HOME = $JdkPath
$env:Path = "$JdkPath\bin;$env:Path"
Write-Host "JAVA_HOME = $env:JAVA_HOME"
& java -version
