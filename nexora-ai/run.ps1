# Starts NEXORA AI (Windows PowerShell). Run from the project folder.
if (-not (Test-Path .env)) { Copy-Item .env.example .env; Write-Host "Created .env - edit DB_PASSWORD, then run this script again."; notepad .env; exit }
mvn spring-boot:run
