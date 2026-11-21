# cog-gen.ps1 -- MATERIALIZE an entire build cell from the ONE edit surface (_codegen).
# Usage: pwsh -File scripts\cog-gen.ps1 -Cell Fabric   (or NeoForge, Forge, Plugin)
#
# CHANGE 1 (fully-generated cells): a cell dir is 100% disposable. cog-gen wipes it and rebuilds
# the WHOLE thing from _codegen: build.gradle + settings.gradle + gradle.properties +
# src\main\resources\<manifest> + the gradle wrapper + the generated gen\ source tree. Nothing in
# a cell folder is hand-kept -- the cells (Fabric/ NeoForge/ Forge/ Plugin/) are gitignored.
#
# EDIT SURFACE (committed): _codegen\** , scripts\** , README.md, publish.json, .gitignore.
# D16: every Java file lives exactly ONCE under _codegen\cog_sources (common = MC-agnostic pure Java;
# shared = loader-agnostic MC code with Cog markers at version-drift points; fabric/neoforge/forge/
# plugin = the per-loader registration seams) OR is emitted from _codegen\compat.py (the per-loader
# ENTRYPOINTS, D15). Version drift (26.2 vs 1.21.11) is isolated in Cog blocks resolved by compat.py.
param(
    [Parameter(Mandatory)][string]$Cell,  # Fabric | NeoForge | Forge | Plugin
    [string]$Line = ''                    # Fabric only: a key of _codegen\matrix.json (1.21.11 | 26.1 | 26.2 | 26.3 | 26.4)
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$loader = $Cell.ToLower()
$cellNames = @{ fabric='Fabric'; neoforge='NeoForge'; forge='Forge'; plugin='Plugin' }
if (-not $cellNames.ContainsKey($loader)) { throw "unknown cell: $Cell (Fabric|NeoForge|Forge|Plugin)" }
$CellName = $cellNames[$loader]

# per-loader parameters (the single place versions/gradle live for cell materialization)
$verMap    = @{ fabric='26.3'; neoforge='26.3'; forge='1.21.11'; plugin='26.1.2' }
$gradleMap = @{ fabric='9.4.1'; neoforge='9.2.1'; forge='9.6.0';  plugin='9.4.1' }
# JDK MAJOR each cell's Gradle daemon runs on -- a major, never a path: Gradle finds the installed
# JDK itself (gradle/gradle-daemon-jvm.properties). A pinned path broke every build on a JDK update (2026-09-24).
$daemonJdkMap = @{ fabric='25'; neoforge='25'; forge='21'; plugin='25' }
$ver = $verMap[$loader]
$mx = $null
if ($loader -eq 'fabric') {
    if (-not $Line) { $Line = '26.3' }
    $mx = (Get-Content (Join-Path $repoRoot '_codegen\matrix.json') -Raw | ConvertFrom-Json).fabric.$Line
    if (-not $mx) { throw "no matrix row for fabric line $Line" }
    $ver = $mx.mc
    $daemonJdkMap['fabric'] = $mx.jdk
}

$cg   = Join-Path $repoRoot '_codegen'
$cs   = Join-Path $cg 'cog_sources'
$ct   = Join-Path $cg (Join-Path 'cell_templates' $CellName)
$cell = Join-Path $repoRoot $CellName

# ---- 1. wipe the ENTIRE cell dir (100% generated / gitignored) ----
Remove-Item $cell -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $cell | Out-Null

# ---- 2. build wiring from the cell template (build.gradle + settings + properties + manifest) ----
Copy-Item (Join-Path $ct 'build.gradle')      $cell -Force
Copy-Item (Join-Path $ct 'settings.gradle')   $cell -Force
Copy-Item (Join-Path $ct 'gradle.properties') $cell -Force
Copy-Item (Join-Path $ct 'src')               $cell -Recurse -Force   # the ONE loader manifest

# ---- 2b. Fabric: stamp the matrix row into the cell's build files ----
if ($mx) {
    $gp = Join-Path $cell 'gradle.properties'
    $t = Get-Content $gp -Raw
    $t = $t -replace '(?m)^minecraft_version=.*$', "minecraft_version=$($mx.mc)"
    $t = $t -replace '(?m)^fabric_api_version=.*$', "fabric_api_version=$($mx.api)"
    $t = $t -replace '(?m)^fabric_api_min=.*$', ("fabric_api_min=" + ($mx.api -split '\+')[0])
    $t = $t -replace '(?m)^mc_lower=.*$', "mc_lower=$($mx.lower)"
    $t = $t -replace '(?m)^mc_upper=.*$', "mc_upper=$($mx.upper)"
    $t = $t -replace '(?m)^mc_line=.*$', "mc_line=$($mx.line)"
    Set-Content $gp -Value $t.TrimEnd() -Encoding ascii
    $bg = Join-Path $cell 'build.gradle'
    $b = Get-Content $bg -Raw
    $remap = $mx.plugin -like '*remap'
    $b = $b.Replace('__PLUGIN__', $mx.plugin).Replace('__JDK__', $mx.jdk)
    if ($remap) {
        $b = $b.Replace('__MAPPINGS__', "    mappings loom.officialMojangMappings()`n")
        $b = $b.Replace('compileOnly localRuntime(', 'modCompileOnly modLocalRuntime(')
    } else { $b = $b.Replace('__MAPPINGS__', '') }
    Set-Content $bg -Value $b -Encoding utf8NoBOM
}

# ---- 3. gradle wrapper (shared bootstrap; distributionUrl per loader) ----
$gw = Join-Path $cell 'gradle\wrapper'
New-Item -ItemType Directory -Force -Path $gw | Out-Null
Copy-Item (Join-Path $cg 'wrapper\gradlew')            $cell -Force
Copy-Item (Join-Path $cg 'wrapper\gradlew.bat')        $cell -Force
Copy-Item (Join-Path $cg 'wrapper\gradle-wrapper.jar') $gw   -Force
(Get-Content (Join-Path $cg 'wrapper\gradle-wrapper.properties.tmpl') -Raw).Replace('__GRADLE_DIST__', $gradleMap[$loader]) |
    Set-Content (Join-Path $gw 'gradle-wrapper.properties') -Encoding utf8NoBOM
Set-Content (Join-Path $cell 'gradle\gradle-daemon-jvm.properties') -Encoding ascii -Value @(
    '#This file is generated by updateDaemonJvm', "toolchainVersion=$($daemonJdkMap[$loader])")

# ---- 4. gen/ Java from the ONE cog source ----
$gen  = Join-Path $cell 'gen'
$genJ = Join-Path $gen 'src\main\java'
$genR = Join-Path $gen 'src\main\resources'
New-Item -ItemType Directory -Force -Path $genJ | Out-Null
Copy-Item (Join-Path $cs 'common\com') $genJ -Recurse -Force          # MC-agnostic pure Java (all cells)
if ($loader -eq 'plugin') {
    Copy-Item (Join-Path $cs 'plugin\com') $genJ -Recurse -Force
} else {
    Copy-Item (Join-Path $cs 'shared\com') $genJ -Recurse -Force      # loader-agnostic MC code (Cog markers)
    Copy-Item (Join-Path $cs "$loader\com") $genJ -Recurse -Force     # per-loader registration seams
}

# ---- 5. entrypoints: single-sourced in compat.py (D15); version-aware ----
& python (Join-Path $cg 'compat.py') $loader $genJ $ver
if ($LASTEXITCODE -ne 0) { throw "compat.py failed for $loader" }

# ---- 6. resolve version-drift Cog blocks in the shared MC code (mod cells only) ----
if ($loader -ne 'plugin') {
    $env:PYTHONDONTWRITEBYTECODE = '1'
    Get-ChildItem $genJ -Recurse -File -Filter *.java |
        Where-Object { (Get-Content $_.FullName -Raw) -match '\[\[\[cog' } | ForEach-Object {
            & cog -r -D ver=$ver -D loader=$loader -D codegen=$cg $_.FullName | Out-Null
            if ($LASTEXITCODE -ne 0) { throw ("cog failed: " + $_.FullName) }
        }
}

# ---- 7. resources + D4 pack.mcmeta (mod loaders only; the plugin ships no MC assets/data) ----
if ($loader -ne 'plugin') {
    New-Item -ItemType Directory -Force -Path $genR | Out-Null
    Copy-Item (Join-Path $cs 'resources\assets') (Join-Path $genR 'assets') -Recurse -Force
    if (Test-Path (Join-Path $cs 'resources\data')) { Copy-Item (Join-Path $cs 'resources\data') (Join-Path $genR 'data') -Recurse -Force }
    # D4: 26.3 Fabric/NeoForge => RESOURCE major 97 exact-range (min=max=97). Forge 1.21.11 is in the
    # resource DEAD ZONE (pf 75, 65..81) => ship the exact range on the DATA major 94 instead.
    $pf = if ($loader -eq 'forge') { '94' } elseif ($mx) { "$($mx.pack)" } else { '97' }
    if ($pf) { ('{"pack":{"description":"Golem Doctor resources","pack_format":' + $pf + ',"min_format":' + $pf + ',"max_format":' + $pf + '}}') |
        Set-Content (Join-Path $genR 'pack.mcmeta') -Encoding utf8NoBOM }
}

Write-Host ("cog-gen OK: {0} (loader={1} ver={2})" -f $CellName, $loader, $ver)
