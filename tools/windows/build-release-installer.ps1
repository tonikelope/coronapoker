[CmdletBinding()]
param(
    [string] $JarPath = "",
    [string] $Destination = "target",
    [string] $InnoSetupHome = "",
    [string] $SigningCertificateThumbprint = "",
    [string] $TimestampUrl = "http://timestamp.digicert.com"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$targetRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot "target"))
$toolchainRoot = [IO.Path]::GetFullPath((Join-Path $targetRoot `
        "windows-installer-jdk25"))
$stagingRoot = [IO.Path]::GetFullPath((Join-Path $targetRoot `
        "windows-installer-staging"))
$cacheRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot `
        ".toolchains\windows-installer-jdk25"))
$jdkArchive = Join-Path $toolchainRoot "microsoft-jdk-25-windows-x64.zip"
$checksumFile = "$jdkArchive.sha256sum.txt"
$cachedArchive = Join-Path $cacheRoot "microsoft-jdk-25-windows-x64.zip"
$extractRoot = Join-Path $cacheRoot "jdk"
$jdkUrl = "https://aka.ms/download-jdk/microsoft-jdk-25-windows-x64.zip"
$checksumUrl = "$jdkUrl.sha256sum.txt"

function Assert-TargetChild {
    param([Parameter(Mandatory = $true)] [string] $Path)
    $fullPath = [IO.Path]::GetFullPath($Path)
    $prefix = $targetRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) `
            + [IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith($prefix,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing temporary-file cleanup outside target: $fullPath"
    }
}

function Assert-CacheChild {
    param([Parameter(Mandatory = $true)] [string] $Path)
    $fullPath = [IO.Path]::GetFullPath($Path)
    $prefix = $cacheRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) `
            + [IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith($prefix,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing toolchain-cache cleanup outside its local cache: $fullPath"
    }
}

if ($env:OS -ne "Windows_NT" -or
        -not [Environment]::Is64BitOperatingSystem) {
    throw "The automated release installer requires 64-bit Windows."
}

Assert-TargetChild $toolchainRoot
Assert-TargetChild $stagingRoot
Assert-CacheChild $cachedArchive
Assert-CacheChild $extractRoot

try {
    if (Test-Path -LiteralPath $toolchainRoot) {
        Remove-Item -LiteralPath $toolchainRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $toolchainRoot, $cacheRoot -Force |
            Out-Null

    Write-Host "Checking the current Microsoft Build of OpenJDK 25..."
    Invoke-WebRequest -Uri $checksumUrl -OutFile $checksumFile

    $expectedHash = ((Get-Content -LiteralPath $checksumFile -Raw).Trim() `
            -split '\s+')[0].ToUpperInvariant()
    if ($expectedHash -notmatch '^[0-9A-F]{64}$') {
        throw "The official JDK checksum response is invalid."
    }
    $cachedHash = if (Test-Path -LiteralPath $cachedArchive -PathType Leaf) {
        (Get-FileHash -LiteralPath $cachedArchive -Algorithm SHA256).Hash
    } else {
        ""
    }
    if ($cachedHash -ne $expectedHash) {
        if (Test-Path -LiteralPath $cachedArchive) {
            Remove-Item -LiteralPath $cachedArchive -Force
        }
        if (Test-Path -LiteralPath $extractRoot) {
            Remove-Item -LiteralPath $extractRoot -Recurse -Force
        }
        Write-Host "Downloading the current Microsoft Build of OpenJDK 25..."
        Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkArchive
        $actualHash = (Get-FileHash -LiteralPath $jdkArchive `
                -Algorithm SHA256).Hash
        if ($actualHash -ne $expectedHash) {
            throw "JDK checksum mismatch: expected $expectedHash, got $actualHash."
        }
        Move-Item -LiteralPath $jdkArchive -Destination $cachedArchive
    } else {
        Write-Host "Reusing the verified local JDK 25 cache."
    }

    $jdkHomes = @(
        if (Test-Path -LiteralPath $extractRoot -PathType Container) {
            Get-ChildItem -LiteralPath $extractRoot -Directory |
                Where-Object {
                    Test-Path -LiteralPath (Join-Path $_.FullName `
                            "bin\jpackage.exe") -PathType Leaf
                }
        }
    )
    if ($jdkHomes.Count -ne 1) {
        if (Test-Path -LiteralPath $extractRoot) {
            Remove-Item -LiteralPath $extractRoot -Recurse -Force
        }
        New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
        Write-Host "Extracting the verified JDK 25 into the local cache..."
        Expand-Archive -LiteralPath $cachedArchive `
                -DestinationPath $extractRoot
        $jdkHomes = @(Get-ChildItem -LiteralPath $extractRoot -Directory |
                Where-Object {
                    Test-Path -LiteralPath (Join-Path $_.FullName `
                            "bin\jpackage.exe") -PathType Leaf
                })
    }
    if ($jdkHomes.Count -ne 1) {
        throw "Expected one cached JDK 25, found $($jdkHomes.Count)."
    }

    $arguments = @{
        PackageType = "exe"
        Destination = $Destination
        JdkHome = $jdkHomes[0].FullName
        InnoSetupHome = $InnoSetupHome
        SigningCertificateThumbprint = $SigningCertificateThumbprint
        TimestampUrl = $TimestampUrl
    }
    if (-not [string]::IsNullOrWhiteSpace($JarPath)) {
        $arguments.JarPath = $JarPath
    }
    & (Join-Path $PSScriptRoot "build-installer.ps1") @arguments
} finally {
    foreach ($temporaryPath in @($toolchainRoot, $stagingRoot)) {
        Assert-TargetChild $temporaryPath
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Recurse -Force
        }
    }
}
