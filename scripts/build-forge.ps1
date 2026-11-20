# build-forge.ps1 -- materialize the Forge cell (FG7 / 1.21.11) from _codegen, then gradlew clean build.
# 1.21.11 = mojmap => JDK 21, no renamer, plain jar is production. First mavenizer run is slow
# (~5-6 min: decompile+patch+recompile Forge into a local MC maven, cached after).
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $repoRoot 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$jdk21 = ((Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21.*' | Sort-Object { [version]($_.Name -replace '^jdk-|-hotspot$','') } -Descending | Select-Object -First 1).FullName)
$cell = Join-Path $repoRoot 'Forge'

& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'cog-gen.ps1') -Cell 'Forge'
if ($LASTEXITCODE -ne 0) { throw 'Forge cog-gen failed' }

$env:JAVA_HOME = $jdk21
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
    Write-Host "Forge OK -> $($jar.Name) (javac warnings: $warn)"
} else {
    Write-Host "Forge FAIL exit=$code (see Forge\_build.log)"
    exit 1
}
