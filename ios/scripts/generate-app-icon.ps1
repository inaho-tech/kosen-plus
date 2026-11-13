param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\PlusKosen\Assets.xcassets\AppIcon.appiconset\AppIcon-1024.png')
)

Add-Type -AssemblyName System.Drawing
$size = 1024
$bitmap = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::Black)

function Convert-Point([double]$x, [double]$y) {
    $scaledX = 12 + 0.55 * ($x - 12)
    $scaledY = 12 + 0.55 * ($y - 12)
    return [System.Drawing.PointF]::new([single]($scaledX / 24 * $size), [single]($scaledY / 24 * $size))
}

$kCoordinates = @(
    @(3,3), @(3,21), @(7.5,21), @(7.5,13.5), @(14.5,21), @(20.5,21),
    @(11.5,11.5), @(20.5,3), @(14.5,3), @(7.5,10), @(7.5,3)
)
$kPoints = [System.Drawing.PointF[]]($kCoordinates | ForEach-Object { Convert-Point $_[0] $_[1] })
$graphics.FillPolygon([System.Drawing.Brushes]::White, $kPoints)

$plusCoordinates = @(
    @(17,5), @(17,9), @(13,9), @(13,12), @(17,12), @(17,16),
    @(20,16), @(20,12), @(24,12), @(24,9), @(20,9), @(20,5)
)
$plusPoints = [System.Drawing.PointF[]]($plusCoordinates | ForEach-Object { Convert-Point $_[0] $_[1] })
$red = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 255, 23, 68))
$graphics.FillPolygon($red, $plusPoints)

$directory = Split-Path -Parent $OutputPath
[System.IO.Directory]::CreateDirectory($directory) | Out-Null
$bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
$red.Dispose()
$graphics.Dispose()
$bitmap.Dispose()
