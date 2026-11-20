# build-fabric.ps1 -- cog-gen the Fabric cell, then gradlew clean build on JDK 25.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $repoRoot 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$jdk25 = ((Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-25.*' | Sort-Object { [version]($_.Name -replace '^jdk-|-hotspot$','') } -Descending | Select-Object -First 1).FullName)
$cell = Join-Path $repoRoot 'Fabric'

& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'cog-gen.ps1') -Cell 'Fabric'
if ($LASTEXITCODE -ne 0) { throw 'Fabric cog-gen failed' }

$env:JAVA_HOME = $jdk25
Push-Location $cell
Get-ChildItem "$cell\build\libs\*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
& "$cell\gradlew.bat" clean build --console=plain *> "$cell\_build.log"
$code = $LASTEXITCODE
Pop-Location

$jar = Get-ChildItem "$cell\build\libs\*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|dev' } | Select-Object -First 1
$warn = (Select-String -Path "$cell\_build.log" -Pattern '\.java.*warning' -ErrorAction SilentlyContinue | Measure-Object).Count
if ($code -eq 0 -and $jar) {
    Copy-Item $jar.FullName (Join-Path $dist $jar.Name) -Force
    Write-Host "Fabric OK -> $($jar.Name) (javac warnings: $warn)"
} else {
    Write-Host "Fabric FAIL exit=$code (see Fabric\_build.log)"
    exit 1
}
