# Installeur TerraCraft (launcher Minecraft officiel, Windows).
# Clic droit > « Exécuter avec PowerShell ». Installe Fabric 26.3 puis les mods de la dernière version.
$ErrorActionPreference = "Stop"
$Repo = "propann/TerraCraft"
$Mc = Join-Path $env:APPDATA ".minecraft"
$Work = Join-Path $env:TEMP ("terracraft-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $Work | Out-Null

Write-Host "== Dernière version de TerraCraft"
$release = Invoke-RestMethod "https://api.github.com/repos/$Repo/releases/latest"
$asset = $release.assets | Where-Object { $_.name -like "*.mrpack" } | Select-Object -First 1
Invoke-WebRequest $asset.browser_download_url -OutFile "$Work\pack.zip"
Expand-Archive "$Work\pack.zip" -DestinationPath "$Work\pack"

Write-Host "== Fabric 26.3 (loader 0.19.5)"
if (-not (Test-Path "$Mc\versions\fabric-loader-0.19.5-26.3")) {
    Invoke-WebRequest "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.exe" -OutFile "$Work\fabric.exe"
    $fabricArgs = @("client", "-mcversion", "26.3", "-loader", "0.19.5", "-dir", $Mc)
    if (-not (Test-Path "$Mc\launcher_profiles.json")) { $fabricArgs += "-noprofile" }
    & "$Work\fabric.exe" @fabricArgs | Out-Host
}

Write-Host "== Mods"
$mods = Join-Path $Mc "mods"
if ((Test-Path $mods) -and (Get-ChildItem $mods).Count -gt 0) {
    $backup = "$Mc\mods-avant-terracraft-" + (Get-Date -Format "yyyyMMdd-HHmmss")
    Move-Item $mods $backup
    Write-Host "Anciens mods déplacés dans $backup"
}
New-Item -ItemType Directory -Force -Path $mods | Out-Null
$index = Get-Content "$Work\pack\modrinth.index.json" -Raw | ConvertFrom-Json
foreach ($file in $index.files) {
    if ($file.env.client -ne "required") { continue }
    $target = Join-Path $Mc $file.path
    Invoke-WebRequest $file.downloads[0] -OutFile $target -UserAgent "TerraCraft-installer"
    $hash = (Get-FileHash $target -Algorithm SHA512).Hash.ToLower()
    if ($hash -ne $file.hashes.sha512) { throw "Somme de contrôle invalide : $($file.path)" }
    Write-Host "  installé $($file.path)"
}
# Liste Multijoueur : posée seulement si le joueur n'en a pas encore (on n'écrase jamais la sienne).
$Servers = "$Work\pack\overrides\servers.dat"
if (Test-Path $Servers) {
    if (-not (Test-Path "$Mc\servers.dat")) {
        Copy-Item $Servers "$Mc\servers.dat"
        Write-Host "Serveur TerraCraft ajouté à la liste Multijoueur."
    }
    Remove-Item $Servers
}
Copy-Item "$Work\pack\overrides\*" $Mc -Recurse -Force
Remove-Item $Work -Recurse -Force
Write-Host ""
Write-Host "TerraCraft est installé. Dans le launcher Minecraft, choisis le profil « fabric-loader-26.3 » puis connecte-toi au serveur."
Read-Host "Appuie sur Entrée pour fermer"
