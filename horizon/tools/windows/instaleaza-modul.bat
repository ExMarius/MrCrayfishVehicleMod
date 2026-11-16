@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title Instalez modul de masini (Horizon)

set "URL=https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/horizon-mod/vehicle.hnmod"
set "TARGET=%~1"

echo.
echo  ============================================
echo   Instalez modul de masini pentru Horizon
echo  ============================================
echo.

if not "%TARGET%"=="" goto :have_target

echo  Caut folderul jocului (cel cu horizon-mods si options.txt)...
echo  Dureaza cateva secunde.
echo.

for %%R in ("%APPDATA%" "%LOCALAPPDATA%" "%USERPROFILE%\Documents" "%USERPROFILE%\Desktop" "%USERPROFILE%\Downloads") do (
    if exist "%%~R" (
        for /f "delims=" %%D in ('dir /s /b /ad "%%~R\horizon-mods" 2^>nul') do (
            set "TARGET=%%~dpD"
        )
    )
)

if "%TARGET%"=="" (
    echo  NU am gasit folderul jocului automat.
    echo.
    echo  Fa asa:
    echo    1. Deschide launcherul Horizon
    echo    2. Mods -^> butonul "Open folder"
    echo    3. Copiaza calea din bara de sus a ferestrei
    echo    4. Ruleaza din nou fisierul asa: tragand folderul peste el,
    echo       sau scrie in fereastra asta calea si apasa Enter.
    echo.
    pause
    exit /b 1
)

:have_target
if "%TARGET:~-1%"=="\" set "TARGET=%TARGET:~0,-1%"
echo  Folderul jocului: %TARGET%
echo.

if not exist "%TARGET%\horizon-mods" (
    echo  ATENTIE: aici nu e folderul jocului (nu vad horizon-mods).
    echo  Verifica si ruleaza din nou, dand calea ca argument.
    echo.
    pause
    exit /b 1
)

if not exist "%TARGET%\horizon-native-mods" (
    mkdir "%TARGET%\horizon-native-mods"
    echo  Am creat folderul horizon-native-mods.
)

echo  Descarc modul...
where curl >nul 2>nul
if %errorlevel%==0 (
    curl -L --fail --silent --show-error -o "%TARGET%\horizon-native-mods\vehicle.hnmod" "%URL%"
) else (
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%TARGET%\horizon-native-mods\vehicle.hnmod'"
)

if not exist "%TARGET%\horizon-native-mods\vehicle.hnmod" (
    echo.
    echo  Descarcarea a esuat. Verifica internetul si ruleaza din nou.
    echo.
    pause
    exit /b 1
)

for %%F in ("%TARGET%\horizon-native-mods\vehicle.hnmod") do set "SIZE=%%~zF"
echo.
echo  GATA. Modul e pus aici:
echo    %TARGET%\horizon-native-mods\vehicle.hnmod  (%SIZE% octeti)
echo.
echo  Ce mai ai de facut:
echo    1. Deschide launcherul Horizon
echo    2. Mods -^> randul "Native build of ..." -^> butonul Build
echo    3. Asteapta sa scrie "Up to date"
echo.
pause
