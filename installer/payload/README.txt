NIMAY OMP 2.6.5 - installert
===========================

Innhold i denne mappen
----------------------
apk\OmpChat.apk        Android-appen (Nimay OMP Mobile)
bridge\                Broen mellom OMP pa denne PC-en og telefonen (Node.js)
voice\                 Talesyntese og talegjenkjenning som kjorer lokalt
tools\                 Hjelpeverktoy: QR-koder, lokal APK-server, oppstart
commands\              Globale OMP-kommandoer (/ompserver, /ompqr)

1. Installer appen pa telefonen
-------------------------------
- Apne "Del APK med telefonen" fra Start-menyen. En side apnes i nettleseren
  med en QR-kode og adressen til APK-filen.
- Skann QR-koden med telefonens kamera, eller skriv adressen i nettleseren
  pa telefonen. Telefonen ma vaere pa samme nettverk (LAN eller Tailscale).
- Last ned og apne filen. Tillat installasjon fra denne kilden hvis Android
  spor om det.

2. Start broen
--------------
- Start-menyen -> Nimay OMP -> Start OMP Bridge, eller
- Start-OMP-Bridge.cmd i denne mappen.
Broen lytter pa port 7777 og deler ogsa APK-en pa /apk/OmpChat.apk.

3. Koble telefonen til OMP
--------------------------
- Apne appen, velg paring, og skann QR-koden som installasjonsprogrammet viste,
  eller kjor /ompqr i OMP for a fa en ny paringskode.
- /ompserver i OMP starter broen i bakgrunnen og viser status.
- /ompqr bruker tools\omp_qr.py (Python + qrcode/pillow). Mangler Python, lager
  tools\qrgen.mjs (Node) det samme bildet, sa kommandoen virker uansett.
  Bildet legges i %LOCALAPPDATA%\OmpChat\omp_chat_qr.png og apnes i bildeviser.

4. Tale og stemme
-----------------
I appen: apne en samtale og trykk telefonknappen overst i chatten. Da starter
en ekte talesamtale: mikrofonen spiller inn, lokal STT transkriberer, svaret
gaar til den aktive OMP-sesjonen, og svaret leses hoyt med lokal TTS.
"Handfri samtale" lytter automatisk igjen etter at OMP har svart.

Talemotorene som ble valgt under installasjonen ligger i voice\.
- Innstillinger for tale: voice\voice-config.json
- Status og selvtest:     voice\voice-status.json
- Logg fra installasjon:  voice\setup.log
Test selv:
  voice\venv\Scripts\python.exe voice\voice_runtime.py tts --text "Hei" --out test.wav
  voice\venv\Scripts\python.exe voice\voice_runtime.py stt --wav test.wav
Vil du legge til flere motorer senere, kjor installasjonsprogrammet pa nytt og
velg flere komponenter. Eksisterende modeller og innstillinger beholdes.

5. Oppgradering og avinstallering
---------------------------------
Kjoring av et nyere installasjonsprogram oppgraderer uten a slette
bridge\config.json, voice\voice-config.json eller nedlastede modeller.
Avinstallering fjerner programfiler, modeller og brannmurregelen, og stopper
broen. Egne samtaler og OMP-data pa PC-en berores ikke.

6. Feilsoking
-------------
- Broen svarer ikke: kontroller at Node.js er installert (node --version) og
  at port 7777 ikke er opptatt. Se konsollvinduet fra Start OMP Bridge.
- Telefonen far ikke kontakt: sjekk at PC og telefon er pa samme nett, og at
  brannmurregelen "Nimay OMP Bridge" er tillatt for private nett.
- Tale feiler: se voice\setup.log og voice\voice-status.json. Modeller som ikke
  ble verifisert markeres som ikke installert - de blir aldri rapportert som OK.

Logg for installasjonen ligger ogsa i mappen Inno Setup oppgir i vinduet.
