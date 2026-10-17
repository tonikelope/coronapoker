[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$imageRoot = Join-Path $repositoryRoot "src\main\resources\images"
$outputRoot = Join-Path $repositoryRoot "packaging\windows"
$feltPath = Join-Path $imageRoot "tapete_verde.jpg"
$logoPath = Join-Path $imageRoot "corona_poker_splash.png"
$avatarPath = Join-Path $imageRoot "avatar_default.png"
$chipsPath = Join-Path $imageRoot "chips.png"
$wizardPath = Join-Path $outputRoot "installer-wizard.png"
$smallPath = Join-Path $outputRoot "installer-small.png"

function New-Canvas {
    param([int] $Width, [int] $Height)
    return [Drawing.Bitmap]::new($Width, $Height,
            [Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Set-Quality {
    param([Drawing.Graphics] $Graphics)
    $Graphics.CompositingQuality =
            [Drawing.Drawing2D.CompositingQuality]::HighQuality
    $Graphics.InterpolationMode =
            [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $Graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $Graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
}

function Draw-Cover {
    param(
        [Drawing.Graphics] $Graphics,
        [Drawing.Image] $Image,
        [Drawing.Rectangle] $Destination
    )
    $scale = [Math]::Max($Destination.Width / $Image.Width,
            $Destination.Height / $Image.Height)
    $sourceWidth = $Destination.Width / $scale
    $sourceHeight = $Destination.Height / $scale
    $sourceX = ($Image.Width - $sourceWidth) / 2.0
    $sourceY = ($Image.Height - $sourceHeight) / 2.0
    $Graphics.DrawImage($Image, $Destination,
            [single] $sourceX, [single] $sourceY,
            [single] $sourceWidth, [single] $sourceHeight,
            [Drawing.GraphicsUnit]::Pixel)
}

function Draw-Contained {
    param(
        [Drawing.Graphics] $Graphics,
        [Drawing.Image] $Image,
        [Drawing.RectangleF] $Bounds
    )
    $scale = [Math]::Min($Bounds.Width / $Image.Width,
            $Bounds.Height / $Image.Height)
    $width = $Image.Width * $scale
    $height = $Image.Height * $scale
    $x = $Bounds.X + ($Bounds.Width - $width) / 2.0
    $y = $Bounds.Y + ($Bounds.Height - $height) / 2.0
    $Graphics.DrawImage($Image, [Drawing.RectangleF]::new(
            $x, $y, $width, $height))
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$felt = [Drawing.Image]::FromFile($feltPath)
$logo = [Drawing.Image]::FromFile($logoPath)
$avatar = [Drawing.Image]::FromFile($avatarPath)
$chips = [Drawing.Image]::FromFile($chipsPath)

try {
    $wizard = New-Canvas 492 942
    try {
        $graphics = [Drawing.Graphics]::FromImage($wizard)
        try {
            Set-Quality $graphics
            Draw-Cover $graphics $felt ([Drawing.Rectangle]::new(
                    0, 0, $wizard.Width, $wizard.Height))
            $shade = [Drawing.Drawing2D.LinearGradientBrush]::new(
                    [Drawing.Point]::new(0, 0),
                    [Drawing.Point]::new(0, $wizard.Height),
                    [Drawing.Color]::FromArgb(205, 4, 19, 31),
                    [Drawing.Color]::FromArgb(150, 0, 55, 40))
            try {
                $graphics.FillRectangle($shade, 0, 0,
                        $wizard.Width, $wizard.Height)
            } finally {
                $shade.Dispose()
            }
            $gold = [Drawing.Pen]::new(
                    [Drawing.Color]::FromArgb(235, 244, 194, 62), 5)
            $cyan = [Drawing.Pen]::new(
                    [Drawing.Color]::FromArgb(190, 43, 196, 224), 2)
            try {
                $graphics.DrawLine($gold, 34, 40, 458, 40)
                $graphics.DrawLine($cyan, 58, 58, 434, 58)
                $graphics.DrawLine($cyan, 58, 884, 434, 884)
                $graphics.DrawLine($gold, 34, 902, 458, 902)
            } finally {
                $gold.Dispose()
                $cyan.Dispose()
            }
            Draw-Contained $graphics $logo ([Drawing.RectangleF]::new(
                    34, 120, 424, 300))
            Draw-Contained $graphics $chips ([Drawing.RectangleF]::new(
                    60, 555, 372, 260))
        } finally {
            $graphics.Dispose()
        }
        $wizard.Save($wizardPath, [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $wizard.Dispose()
    }

    $small = New-Canvas 294 294
    try {
        $graphics = [Drawing.Graphics]::FromImage($small)
        try {
            Set-Quality $graphics
            Draw-Cover $graphics $felt ([Drawing.Rectangle]::new(
                    0, 0, $small.Width, $small.Height))
            $shade = [Drawing.SolidBrush]::new(
                    [Drawing.Color]::FromArgb(170, 4, 19, 31))
            try {
                $graphics.FillRectangle($shade, 0, 0,
                        $small.Width, $small.Height)
            } finally {
                $shade.Dispose()
            }
            Draw-Contained $graphics $avatar ([Drawing.RectangleF]::new(
                    18, 18, 258, 258))
        } finally {
            $graphics.Dispose()
        }
        $small.Save($smallPath, [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $small.Dispose()
    }
} finally {
    $felt.Dispose()
    $logo.Dispose()
    $avatar.Dispose()
    $chips.Dispose()
}

[pscustomobject]@{
    WizardImage = $wizardPath
    WizardSize = "492x942"
    HeaderImage = $smallPath
    HeaderSize = "294x294"
}
