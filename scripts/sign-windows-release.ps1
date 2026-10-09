param(
    [Parameter(Mandatory = $true)][string]$CertificatePath,
    [Parameter(Mandatory = $true)][SecureString]$CertificatePassword,
    [Parameter(Mandatory = $true)][string]$TimestampUrl
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$artifacts = @(
    (Join-Path $projectRoot "desktopApp\build\compose\binaries\main\exe\PATISSERIE_POS-1.2.5.exe"),
    (Join-Path $projectRoot "desktopApp\build\compose\binaries\main\msi\PATISSERIE_POS-1.2.5.msi")
)

if (-not (Test-Path -LiteralPath $CertificatePath -PathType Leaf)) {
    throw "Code-signing certificate not found: $CertificatePath"
}
$missing = $artifacts | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) }
if ($missing) { throw "Build Windows packages before signing: $($missing -join ', ')" }

$signtool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin" -Filter signtool.exe -Recurse |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $signtool) { throw "signtool.exe was not found. Install the Windows SDK signing tools." }

$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($CertificatePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    foreach ($artifact in $artifacts) {
        & $signtool.FullName sign /fd SHA256 /td SHA256 /tr $TimestampUrl /f $CertificatePath /p $plainPassword $artifact
        if ($LASTEXITCODE -ne 0) { throw "Signing failed: $artifact" }
        & $signtool.FullName verify /pa /v $artifact
        if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $artifact" }
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
}

$artifacts | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
    [PSCustomObject]@{ File = $_; SHA256 = $hash.Hash; Signature = (Get-AuthenticodeSignature -LiteralPath $_).Status }
}
