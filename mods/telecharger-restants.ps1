# Télécharge dans ce dossier les mods que leur licence ne permet pas de republier (Xaero), depuis Modrinth,
# avec vérification de l'empreinte SHA-512. Usage : clic droit > Exécuter avec PowerShell.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
foreach ($mod in (Get-Content "a-telecharger.json" -Raw | ConvertFrom-Json)) {
    Invoke-WebRequest -Uri $mod.url -OutFile $mod.file -UserAgent "propann/TerraCraft"
    $hash = (Get-FileHash $mod.file -Algorithm SHA512).Hash.ToLower()
    if ($hash -ne $mod.sha512) { Remove-Item $mod.file; throw "Empreinte incorrecte : $($mod.file)" }
    Write-Host "ok $($mod.file)"
}
Write-Host "Dossier complet : copie tous les .jar dans le dossier mods de ton Minecraft (Fabric 26.3, loader 0.19.5)."
