[CmdletBinding()]
param([string] $MavenPath = '')

$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
[xml] $pom = Get-Content -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -Raw
$version = $pom.project.version
$releaseRoot = Join-Path $repositoryRoot "target\release-$version"
if ([string]::IsNullOrWhiteSpace($MavenPath)) {
    $command = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    $MavenPath = if ($null -ne $command) { $command.Source } else {
        'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd'
    }
}
if (-not (Test-Path -LiteralPath $MavenPath)) { throw 'Maven was not found.' }
$platforms = @(
    @{ Profile='jcef-windows-amd64'; Suffix=''; Cef='windows-amd64'; Lwjgl='windows/x64'; Gdx='gdx64.dll'; Freetype='gdx-freetype64.dll' },
    @{ Profile='jcef-linux-amd64'; Suffix='_Linux_x64'; Cef='linux-amd64'; Lwjgl='linux/x64'; Gdx='libgdx64.so'; Freetype='libgdx-freetype64.so' },
    @{ Profile='jcef-linux-arm64'; Suffix='_Linux_arm64'; Cef='linux-arm64'; Lwjgl='linux/arm64'; Gdx='libgdxarm64.so'; Freetype='libgdx-freetypearm64.so' },
    @{ Profile='jcef-macos-amd64'; Suffix='_macOS_x64'; Cef='macosx-amd64'; Lwjgl='macos/x64'; Gdx='libgdx64.dylib'; Freetype='libgdx-freetype64.dylib' },
    @{ Profile='jcef-macos-arm64'; Suffix='_macOS_arm64'; Cef='macosx-arm64'; Lwjgl='macos/arm64'; Gdx='libgdxarm64.dylib'; Freetype='libgdx-freetypearm64.dylib' }
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
$manifest = @()
Push-Location $repositoryRoot
try {
    foreach ($platform in $platforms) {
        # Explicit cross-builds must disable every foreign auto-activated OS
        # profile, including the Windows host's profile on this release machine.
        $profiles = @($platforms | Where-Object Profile -ne $platform.Profile |
                ForEach-Object { '!' + $_.Profile }) + $platform.Profile
        $output = Join-Path $releaseRoot $platform.Profile
        & $MavenPath '-B' '-f' 'modules/pom.xml' '-pl' 'coronapoker-gdx' '-am' `
            "-P$($profiles -join ',')" '-DskipTests' `
            "-Ddistribution.directory=$output" 'package'
        if ($LASTEXITCODE -ne 0) { throw "Build failed: $($platform.Profile)" }
        $source = Join-Path $output "CoronaPoker_$version.jar"
        $destination = Join-Path $releaseRoot "CoronaPoker_$version$($platform.Suffix).jar"
        $archive = [IO.Compression.ZipFile]::OpenRead($source)
        try {
            $entries = @($archive.Entries | ForEach-Object FullName)
            $cef = @($entries | Where-Object { $_ -match '^jcef-natives-.+\.tar\.gz$' })
            if ($cef.Count -ne 1 -or -not $cef[0].StartsWith("jcef-natives-$($platform.Cef)-")) {
                throw "Incorrect JCEF payload in $source : $($cef -join ', ')"
            }
            $gdx = @($entries | Where-Object { $_ -match '^(?:lib)?gdx(?:-freetype)?[^/]*\.(?:dll|so|dylib)$' })
            if ($gdx.Count -ne 2 -or $platform.Gdx -notin $gdx -or $platform.Freetype -notin $gdx) {
                throw "Incorrect libGDX payload in $source : $($gdx -join ', ')"
            }
            $lwjgl = @($entries | Where-Object { $_ -match '^(?:windows|linux|macos)/[^/]+/org/lwjgl/.+\.(?:dll|so|dylib)$' })
            $foreign = @($lwjgl | Where-Object { -not $_.StartsWith("$($platform.Lwjgl)/org/lwjgl/") })
            # macOS GLFW ships both the regular and async dylib.
            $nativeCount = if ($platform.Lwjgl.StartsWith('macos/')) { 7 } else { 6 }
            if ($foreign.Count -or $lwjgl.Count -ne $nativeCount) {
                throw "Incorrect LWJGL native family in $source : $($lwjgl -join ', ')"
            }
        } finally { $archive.Dispose() }
        Copy-Item -LiteralPath $source -Destination $destination -Force
        $manifest += [pscustomobject]@{
            File = [IO.Path]::GetFileName($destination)
            SHA256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
            Bytes = (Get-Item -LiteralPath $destination).Length
        }
    }
    # Keep the user's canonical local JAR and the installer input on Windows.
    Copy-Item -LiteralPath (Join-Path $releaseRoot "CoronaPoker_$version.jar") `
        -Destination (Join-Path $repositoryRoot "target\CoronaPoker_$version.jar") -Force
    $manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $releaseRoot 'artifacts.json') -Encoding UTF8
    $manifest | Format-Table -AutoSize
} finally { Pop-Location }
