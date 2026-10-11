param(
    [Parameter(Mandatory = $true)][string]$MsiPath,
    [string]$PreviousMsiPath,
    [switch]$ExpectUnsafe
)

$ErrorActionPreference = 'Stop'

function Read-MsiRows($database, [string]$table, [int]$columns) {
    $view = $database.OpenView("SELECT * FROM $table")
    try {
        [void]$view.Execute()
        $rows = @()
        while ($record = $view.Fetch()) {
            $row = @()
            for ($i = 1; $i -le $columns; $i++) { $row += [string]$record.StringData($i) }
            $rows += [PSCustomObject]@{ Values = $row }
        }
        return $rows
    } finally {
        [void]$view.Close()
    }
}

function Get-InstallerLayout([string]$path) {
    $resolved = (Resolve-Path -LiteralPath $path).Path
    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $installer.OpenDatabase($resolved, 0)
    $properties = @{}
    foreach ($entry in (Read-MsiRows $database 'Property' 2)) {
        $row = $entry.Values
        $properties[$row[0]] = $row[1]
    }
    $directories = @{}
    foreach ($entry in (Read-MsiRows $database 'Directory' 3)) {
        $row = $entry.Values
        $directories[$row[0]] = $row
    }
    if (-not $directories.ContainsKey('INSTALLDIR')) { throw "MSI has no INSTALLDIR: $resolved" }

    function Is-UnderDirectory([string]$directoryId, [string]$ancestorId) {
        $cursor = $directoryId
        while ($cursor -and $directories.ContainsKey($cursor)) {
            if ($cursor -eq $ancestorId) { return $true }
            $cursor = $directories[$cursor][1]
        }
        return $false
    }

    $parts = @()
    $id = 'INSTALLDIR'
    while ($id -and $id -ne 'LocalAppDataFolder' -and $id -ne 'TARGETDIR') {
        if (-not $directories.ContainsKey($id)) { throw "Unknown directory $id in $resolved" }
        $row = $directories[$id]
        $name = ($row[2] -split '\|')[-1]
        if ($name -ne '.' -and $name -ne 'SourceDir') { $parts = @($name) + $parts }
        $id = $row[1]
    }
    $baseDirectory = $id
    $installPath = $parts -join '\'
    $dataRoot = 'PATISSERIE_POS\data'
    $overlapsData = $installPath -eq 'PATISSERIE_POS' -or
        $installPath.StartsWith("$dataRoot\", [StringComparison]::OrdinalIgnoreCase) -or
        $dataRoot.StartsWith("$installPath\", [StringComparison]::OrdinalIgnoreCase)

    $recursiveInstallRemoval = $false
    $unsafeRemoveTargets = @()
    $mappedRemoveProperties = @()
    try {
        $removeProperties = @(Read-MsiRows $database 'WixRemoveFolderEx' 4 | ForEach-Object { $_.Values[2] })
        foreach ($entry in (Read-MsiRows $database 'Registry' 6)) {
            $row = $entry.Values
            if ($removeProperties -contains $row[3]) {
                $mappedRemoveProperties += $row[3]
                if ($row[4] -eq '[INSTALLDIR]') { $recursiveInstallRemoval = $true }
                else { $unsafeRemoveTargets += $row[4] }
            }
        }
    } catch {
        if ($_.Exception.Message -notmatch 'OpenView') { throw }
    }
    $unmappedRemoveProperties = @($removeProperties | Where-Object { $mappedRemoveProperties -notcontains $_ })
    $unsafeRemoveFiles = @(Read-MsiRows $database 'RemoveFile' 5 | Where-Object {
        $dir = $_.Values[3]
        -not (Is-UnderDirectory $dir 'INSTALLDIR') -and
        -not (Is-UnderDirectory $dir 'ProgramMenuFolder')
    } | ForEach-Object { $_.Values[3] })
    $allowedActions = @('JpFindRelatedProducts', 'JpSetARPINSTALLLOCATION',
        'JpSetARPCOMMENTS', 'JpSetARPCONTACT', 'JpSetARPSIZE', 'WixRemoveFoldersEx')
    $unexpectedActions = @(Read-MsiRows $database 'CustomAction' 4 | Where-Object {
        $allowedActions -notcontains $_.Values[0]
    } | ForEach-Object { $_.Values[0] })

    $customerFiles = @(Read-MsiRows $database 'File' 8 | ForEach-Object {
        ($_.Values[2] -split '\|')[-1]
    } | Where-Object { $_ -match '^(?i:pos\.db|license\.dat|.*\.db-wal|.*\.db-shm)$' })

    [PSCustomObject]@{
        File = $resolved
        UpgradeCode = $properties['UpgradeCode']
        ProductVersion = $properties['ProductVersion']
        BaseDirectory = $baseDirectory
        InstallPathUnderLocalAppData = $installPath
        OverlapsCustomerData = $overlapsData
        RecursivelyRemovesInstallDirectory = $recursiveInstallRemoval
        OtherRecursiveRemovalTargets = $unsafeRemoveTargets
        UnmappedRecursiveRemovalProperties = $unmappedRemoveProperties
        RemoveFileOutsideApplication = $unsafeRemoveFiles
        UnexpectedCustomActions = $unexpectedActions
        PackagedCustomerFiles = $customerFiles
    }
}

$current = Get-InstallerLayout $MsiPath
$current | Format-List
$problems = @()
if ($current.OverlapsCustomerData) {
    $problems += 'Installer binaries share the customer data directory.'
}
if ($current.BaseDirectory -ne 'LocalAppDataFolder' -or
    $current.InstallPathUnderLocalAppData -ne 'PATISSERIE_POS_APP') {
    $problems += 'INSTALLDIR must be %LOCALAPPDATA%\PATISSERIE_POS_APP.'
}
if ($current.UpgradeCode -ne '{46788696-3912-4D20-9F7D-220B62935D31}') {
    $problems += 'Unexpected UpgradeCode; future updates may create a second installation.'
}
if ($current.OtherRecursiveRemovalTargets.Count -gt 0) {
    $problems += 'MSI recursively removes a directory other than INSTALLDIR.'
}
if ($current.UnmappedRecursiveRemovalProperties.Count -gt 0) {
    $problems += 'MSI has a recursive removal property with no verified directory mapping.'
}
if ($current.RemoveFileOutsideApplication.Count -gt 0) {
    $problems += 'MSI RemoveFile targets a directory outside the application or Start Menu.'
}
if ($current.UnexpectedCustomActions.Count -gt 0) {
    $problems += 'MSI contains custom actions that require manual data-safety review.'
}
if ($current.PackagedCustomerFiles.Count -gt 0) {
    $problems += 'MSI contains a customer database or license file.'
}
if ($PreviousMsiPath) {
    $previous = Get-InstallerLayout $PreviousMsiPath
    $previous | Format-List
    if ($current.UpgradeCode -eq $previous.UpgradeCode -and
        $previous.OverlapsCustomerData -and $previous.RecursivelyRemovesInstallDirectory) {
        $problems += 'The previous unsafe MSI can recursively delete customer data during an in-place upgrade. Use the documented manual transition.'
    }
    if ($current.UpgradeCode -ne $previous.UpgradeCode) {
        $problems += 'UpgradeCode changed between packages.'
    }
}
if ($ExpectUnsafe) {
    if ($problems.Count -eq 0) { throw 'Expected an unsafe legacy installer, but no data-loss risk was detected.' }
    $problems | ForEach-Object { Write-Output "Detected expected risk: $_" }
    return
}
if ($problems.Count -gt 0) { throw ("UNSAFE: " + ($problems -join ' ')) }
Write-Output 'Installer layout safety checks passed.'
