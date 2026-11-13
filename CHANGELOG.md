# OmpChat CHANGELOG.md - Released changes


### 2.9.56 - Go to active PC chat in sidebar (2026-11-02)

**Android APK 2.9.56 (code 81)**
- **New button: Go to active PC chat** in sidebar drawer
- Same discreet style (32dp icon chip) as the other sidebar chips
- Tap to open the active chat on the PC
- Tap once to open the active conversation from the sidebar
- SHA-256: 94929B556D6261EDA57DC059AE4CEB61047AB57BD3EE92566927793FDECB44F7

### 2.9.55 - Pin any message + pinned messages list (2026-11-02)

### 2.9.54 - Pin messages in header (2026-11-02)

**Android APK 2.9.54 (code 79)**
- **Pin messages** - discreet pushpin icon button in chat header next to export chat chip
- Same discreet style (32dp icon chip without text) as the other chips
- Tap to pin/unpin the latest message in chat
- Pinned messages can be unpinning by tapping again
- Toggle with resource monitor setting (PC Resource Monitor in Settings)
- SHA-256: 34B071E0545254BE154F2DC2F39FC17753405B019D43342DE33381DF96C1E654

### 2.9.53 - Export chat chip in header (2026-11-02)

**Android APK 2.9.53 (code 78)**
- **Export chat chip** in chat header next to resource monitor chip
- Same discreet style (32dp icon chip without text) as resource monitor chip
- Dropdown menu with choices: "Last 3 chats", "Last 5 chats", "Last 10 chats"
- Copies selected chats to clipboard as formatted text (timestamp + role + message)
- Useful for pasting conversations into other apps
- Toggle with resource monitor setting (resourceMonitorEnabled)
- SHA-256: 05AE91FF5ABE333CB5411C1951568118CFEC2BE7E3E864AC5434AA2975069CF4

### 2.9.52 / Installer 3.2.1 - Resource monitor fix + bridge /api/resources (2026-11-02)

**Android APK 2.9.52 (code 77)**
- **Resource Monitor (PC Resource Monitor):** Popup rendered via Compose Popup (overlay) — chip does not move when tapped. Chip calls loadResources() on open, shows real CPU/RAM/GPU data instead of hanging on 'Loading resources...'. On-demand loading via tapping (beyond 30s polling). Debug log: DEBUG flag in ResourceMonitor.kt (tag 'OmpChat').
- **Bridge requirement:** Requires omp-bridge 2.9.9 with /api/resources endpoint. On PCs with older bridge, resource monitor will not show data.
- SHA-256: DE179083D744C054EF8A52ED00F89B9788E2C7FD51AF0752869DCDDE3274152F

**NimayOMP Installer 3.2.1 (Windows bridge)**
- **Bridge:** omp-bridge.mjs oppdatert med /api/resources-endepunkt (versjon 2.9.9). Kun bridge-koden er endret i installeren — ingen APK-endring i installer-pakken.
- SHA-256: 8AE882849AD98CBC6381D65D26C51B38C1A44A2D4912097A15C97B7209D93F6D

**GitHub release:** https://github.com/megaxenon83-server/OmpChat/releases/tag/v3.2.0

### 2.7.1 - Filbehandler stabilisering og redesign (30.10.2026)
- **PC-listing reparert:** PC-fanen starter på rot/drevvisning med hurtigplasser (Hjem, Skrivebord, Dokumenter, …), viser Windows-stasjoner og Linux-baner, ekte feil, lagringsinfo, «prøv igjen» og ingen falske filer. Nettverkskall kjøres på Dispatchers.IO.
- **Profesjonell kompakt UI:** Material Files/Solid Explorer-inspirert toppfelt (tilbake, tittel, søk, liste/rutenett, meny), Telefon/PC-velger med tilkoblingsstatus, brødsmuler + opp, hurtigtilgang, kompakte rader med ikoner/miniatyrer, diskret valgmarkering.
- **Handlinger:** åpne, langtrykk flervalg, kopier/klipp ut/lim inn, gi nytt navn, slett, ny mappe, del, sorter, søk, egenskaper.
- **Overføring begge veier:** flere filer og hele mapper/undermapper, målvalg, prosent/hastighet/størrelse, kø (maks 2 samtidige), pause/fortsett/avbryt, konflikthåndtering (overskriv/behold begge/hopp over), SHA-256, HTTP Range for nedlasting, offset-append+finalize for opplasting, .ompdownload-temp, store filer uten full RAM-lasting, LAN + Tailscale.
- **SAF/rettigheter:** lagrede telefonmapper, persistable URI, rekursiv kopi/flytt, gjenopptak etter nettverksbrudd, mappe bevares ved fanebytte/rotasjon, ingen autoåpne tastatur.
- **Backend (omp-bridge.mjs):** fsutil-drevlisting, /api/fs/list|storage|tree|search|walk, /api/fs/mkdir|rename|delete|copy|move, /api/fs/job (kopier/flytt med progress/avbryt), /api/files/download (Range+Content-Range), /api/files/hash, /api/files/upload (offset-append+finalize+SHA), /api/files/upload-status (tmp+final), beskyttede systemstier, fsError-oversettelser.
- **Tester:** 42 JVM-tester (FsPaths, FsFormat, FsMime, FsSaf.tempName) grønne; bygg OK. Emulatorverifisert: PC-list (rot + hjem + undermapper), telefonfiler via SAF, overføring begge veier med mappe/undermappe og 300 MB-fil, SHA-256 match, konfliktmodi, avbryt med opprydding. Ingen fysisk telefon tilgjengelig — SAF/langtidstester rapportert som emulatortest.
- **Release:** APK OmpChat-2.7.1-release.apk (8 662 506 byte, SHA-256 120E00EC464E7A69C4E3AA4A8620277FB741FAAE193E51818E025FCBF6CEE09D) på https://chat.nimay.net/downloads/nimayomp/; EXE NimayOMP-Setup-2.6.8.exe uendret (bridge oppdateres ved install/restart).

Dette dokumentet inneholder en oversikt over alle faktiske endringer som er utfört i OmpChat-prosjektet.

### 2.6.9 / hotfix (17:0x-17:4x) – æ/ø/å, STT-CUDA og godkjenningsknapper
- **Rare æ/ø/å-tegn i tale-samtalen (f.eks. «Jeg lurer p◆ n◆r»):** Python-vindumotor default
  cp1252 ga mekket utf-8 i STT-svaret som telefonen lagde. Retting: `sys.stdout.reconfigure
  (utf-8)` i `serve`-modus + `PYTHONIOENCODING=utf-8`/`PYTHONUTF8=1` for alle python-barn som
  broen scorer (studentm og one-shot). STT/TTS rundtur på norrmål verifisert tegn-riktig.
- **«Talegjenkjenning feilet: Library cublas64_12.dll is not found…»** (av vergene når en
  motor med GPU-oppsett valgtes, f.eks. NB-Whisper Medium med `device=auto`): ctranslate2-er
  krevde cublas-DLL fra torch/lib som ikke lå: PATH. Retting: `_ensure_openvino_cuda_paths()`
  i voice_runtime legger venv\\...\\torch\\lib på DLL-sesontinje; **gpu-fallback til CPU/int8
  hvis ellers en cuda/dll-feil inntrer** – talekall stopper aldri pga. GPU-chash.
- **Godkjenningsknappene gjør nå det de sier:** OMP tilbyr bare moduisene
  `always-ask|always-ask-cached|write|yolo`; appens gamle «default»/«ask» ble ignorert av
  OMP som spørrer om alt uansett. Nå:
  - **Alltid spør** → `always-ask`
  - **Spør ved sletting/store endringer** → `write` (kun redigering/sletting av filer)
  - **Tillat alt** → `yolo` (godkjenner alle verktøy automatisk)
  Broen mapper legacy-verdiene («default»→write, «ask»→yolo), validerer, og genstarter
  ledige OMP-runtimeer natte modusendringen (pågående oppgaver berøres ikke).
  Innstillinger-siden viser de beskrivende navnene i stedet for rå modusstreng
  («always-ask»-etikett fjernet, nå «Alltid spør» o.l.).
- **Bygg:** APK 2.6.9 (versionCode 18), 8 473 825 byte, SHA-256 `ED0BA08F…9E1D` (clean, 0
  testfeil), deployet til `C:\Program Files\Nimay OMP\apk\OmpChat.apk` + levert til
  megaxenon@192.168.50.222 (sha256sum ok). Installer rebygget idet «NimayOMP-Setup-2.6.8.exe»
  med oppdatert bridge + voice_runtime: SHA-256 `E6EE11C7…7AF64`, levert + verifisert.
  Broen kjører ny kode: godkjenningsmodus satt til `write` som forslag.

## [2.6.8] - 2026-10-29

### Fiks: bakgrunnsfrysing og tapte oppdateringer
- **Rotårsak 1 – ingen lifecycle-håndtering:** `connect()` kjørte kun ved prosessstart/pairing. Halvåpen SSE-sokkel etter Wi-Fi-/Doze-nettbrudd hang i `readUtf8Line()` evig (`streamClient` hadde readTimeout 0), så appen viste aldri gap eller reconnect. Nå: MainActivity-lifecycle (ON_START/ON_STOP) -> `ChatRepository.onAppForeground()/onAppBackground()`; `BridgeClient` bruker en watchdog-strøm med readTimeout 45 s (bridge-heartbeat hvert 15 s holder den varm)
- **Rotårsak 2 – tapte hendelser ved reconnect:** `handle("connected")` satte bare `connected=true`; `connection_error` nullstilte til og med `streaming`. Nå beholder den spinneren, og hvert reconnect (`connected`) triggar automatisk resync: transkript + raw `/api/state` + models + sessions (2,5 s debounce). Pågående OMP-oppgave berøres aldri (ingen reopen/restart)
- **Bakgrunn:** i bakgrunnen parkeres SSE-koblingen når ingen oppgave streamer (batteri/unngå døde sokler); under pågående streaming holdes den varm til oppgaven ender (bakgrunnsmonitor)
- **Rotårsak 3 – auto-scroll:** scroll triggerte bare på `messages.size`; delta-tekst og verktøyresultat vokser INNENfor siste melding. Nå trigges den også på innholdsvokst (tailFingerprint), og `autoFollow` resettes når brukeren er på bunnen (`canScrollForward` via snapshotFlow); manuell opp-skrolling tvinger ikke ned
- Duplikatsikring: transkript-reload erstatter hele listen (id-nøkkel); live delta-buffere beholdes så lenge oppgaven pågår

### Hotfix (samme kveld) – «Modellmappen mangler: …models\piper\vibevoice-1.5b»
- **Rotårsak:** en installasjons-/status-runde stempelte feil mappesøyle for begge
  VibeVoice-motorene (`voice\models\piper\vibevoice-<id>` i stedet for
  `voice\models\<id>`) i `voice-status.json`/`voice-config.json`. «Modellmappen
  mangler»-feilen stoppet da alle talekall fra telefonen («Prøv igjen»-visning i
  talesamtale-skjermen).
- **Retting 1:** feil dir-oppføringer reparert i `voice-status.json` og `voice-config.json`
  (peker nå på `voice\models\vibevoice-0.5b` / `-1.5b`).
- **Retting 2 – varig guard i `voice_runtime.py`:** `_resolve_vibe_dir()` prøver mappen
  under `voice\models\<id>` når konfigurert dir ikke finnes, og alle stemme- og
  konfigstier bruker samme reparerte dir; «Modellmappen mangler» kan ikke lenger
  blokkere et talekall så lenge modellen faktisk ligger der.
- **Verifisert over bro (ekte kall):** vibevoice-1.5B 5,2 s lyd / 70 s, vibevoice-0.5B
  6 s lyd / 36 s (18 s varm), piper 1,4 s lyd / 4 s. Telefontesten fungerer nå uten ny APK.
- Ny installer: NimayOMP-Setup-2.6.8.exe SHA-256 `CCE291AC577824B597F686D4AECC684D0E744FC3429DC43AA677336272EC78A8`
  (rebygg med oppdatert voice-runtime-payload), levert til megaxenon@192.168.50.222 (sha256sum ok).
- APK uendret: OmpChat-2.6.8-release.apk SHA-256 `16F766B9…412E` (feilen var server-side).


### Bygg
- APK 2.6.8 (versionCode 17), 8 473 818 byte, SHA-256 `16F766B92F1BD2BCD1B20B71575F8D3A951F979EC3EBB0D5D91E34FB2454F12E`
- Instrumentert med `Log.d("OmpChat", …)` (connect, resumeLast, openSession, resync, onAppForeground)
- Installer NimayOMP-Setup-2.6.8.exe, SHA-256 `39A9304248CA11145C92FC1BB867DD330D2C01108D6F69680328F45CAA605896`, levert til megaxenon@192.168.50.222 (sha256sum ok)
- Verifisert på emulator Pi_Test_Pixel (API 34): lange OMP-oppgaver med verktøykall, HOME/skjermlås/nettbrudd/force-stop, resync-burst per resume, ingen krasjer

## [2.6.7] - 2026-10-29

### Nytt i tale-motorer (ekte VibeVoice + flere stemmer)
- **VibeVoice er nå ekte, lokalt installerte motorer** (vibevoice-community, `vibevoice/VibeVoice-1.5B` + `microsoft/VibeVoice-Realtime-0.5B`):
  - **vibevoice-0.5b (Realtime)**: 1,9 GB, **7 stemmer (.pt-embeddings)** – Emma/Grace/Carter/Davis/Frank/Mike/Samuel. Målt i varm worker: ~20 s totalt for 3,5 s lyd
  - **vibevoice-1.5B**: 3,8 GB, **5 stemmer (.wav)** – Alice/Maya/Xinran/Frank/Carter. ~90 s per kort setning (14×) – kvalitetsmodus, ikke samtalelatens
  - `synthesize_vibevoice()` i voice_runtime.py: per-motor-sti (streaming-klasse for 0.5b, full inferens for 1.5B/7B), PCM16-utskrift, DDPM 4/5 steg
- **Piper NVCC med 10 stemmer** (K* kvinner, M* menn; `POST /api/voice/select {voice:"KSV"…}` → `speakerId`-felt)
- **`GET /api/voice/engines` viser `voices`-lister per motor**; installasjonsknapp i appen → `POST /api/voice/install`
- venv: transformers pin **4.51.3**, tokenizers 0.21.4, huggingface_hub 0.30.2, **accelerate 1.6.0**

### Resident voice-worker i broen (latens)
- `voice_runtime.py serve`: JSON-linjer på stdin (ping/status/tts/stt/quit); modeller holdes lastet i prosess
- Broen: `spawnVoiceWorker()/runVoiceWorker()` + **.resp-fil-respons** (python-launcher-kjeden re-lerer pipen sent; svaret skrives også til `*.wav.resp` og polles hvert 300 ms)
- Timeout 480 s TTS / 240 s STT; worker stoppes ved bro-shutdown

### Fikset
- **`setup-voice.ps1`**: `Convert-PsObjectToHashtable([object]$input)` → fikk PowerShell's reserverte `$input`-enumerator og ga `{"Current":null}`-entroier (config ødelagt i keep-runder) → parameter heter nå `$obj`
- **STT-regresjon**: install-løp nullstilte `stt` i voice-config.json pga. bugen over; rekonstruert fra disk og verifisert via runtime
- **Python-DNS døde globalt** (`getaddrinfo failed`): modell-nedlasting over til PowerShell/curl i `setup-voice.ps1`

### Bygg
- APK 2.6.7 (versionCode 16), 8 469 719 byte, SHA-256 `0048E77A65C087F73B9B36BB1CCE31B1EA5EF2FCE8D9A2D1A3A31690E734CA70` (clean build, 29 tester 0 feil; stemmevelger + installer-knapp)
- Installer **NimayOMP-Setup-2.6.7.exe** (9,93 MB), SHA-256 `0E4D45E76022017EE4FF72507A7DA0321D52A3B2CE7A0791AA25236C6D790BFC` – levert til megaxenon@192.168.50.222 (sha256sum ok)

## [2.6.6] - 2026-10-29

### Nytter for å begrunne speed endringer tale
- Nivåmåling ekte samtaler: STT ~2 s (når OK), TTS ~2 s, **OMP-svar 24 s selv på «Svar med ett ord» (Nemotron 120B, reasoning)** — flaskehalsen er modellen, ikke talekjeden. Foreslått virkemiddel: bytt modell i header (f.eks. gemma4:12b lokalt eller en flash-modell) til talesamtaler

### Nytt i samtalefall (ChatGPT-stil)
- **Interaktiv orb:** stor, samtalereagerende kule (pulser i lyttemodus, roterende sveip i «tenker», grønn glød i «snakker») — trykk for å mute/unmute, trykk i «tenker»/«snakker» = avbryt OMP
- **Mute-knapp:** mini/mic av-på midt i samtalen uten å bryte runden (`setMuted`, mikrofon slippes)
- **«Stopp OMP»-knapp (+ int-tegget i orb):** avbryter pågående svar (bridge `/api/abort`, løser også ventende godkjenninger) og returnerer til lyttemodus; barge-in stopper avspillingen umiddelbart
- **Godkenningsark vises nå På samtalefall** — verktøytillatelse midt i en samtale fryser ikke lenger serien i stillhet 300 s
- Call-silhenviste farvelarbeider: error card ø m.m. uendret

### Bygg
- APK 2.6.6 (versionCode 15), 8 465 610 byte, SHA-256 803BD500D86B38A1A9D5A94932B872DBA9333E6EFF52677177E0F05F3E84493D (clean build, 29-tester 0-feil-sertifikat inkl. 2 nye VAD-burst-tester)
- Installer 2.6.6 SHA-256 6ECFB2BF11B8F3D2F9CFD48FAD82F45C0EE6F16C6606755C745FB624434D479F; levert til megaxenon@192.168.50.222 (hash ok)

## [2.6.5] - 2026-10-29

### Fikset
- **Talesamtalen hang i «Lytter …» for alltid på ekte telefon:** VAD-tilpasningen i `VoiceCallEngine` satte terskelen til `clamp(romstøy * 3.2, 400..3500)` — med ~1200 RMS romstøy (synlig som level ≈ 0.4 i nivåmåleren) ble takken låst på makstakken 3500, og vanlig tale kom aldri over. Terskelen er nå `clamp(støy * 2.4, 320..1600)`, slik at normalt når tale ved mikrofonen utløser STARTED
- Mikrofonå fangst var OK hele tiden (grøn mic-indikator + bevegelige nivåstolper); bare startterskelen var feilkalibrert

### Bygg
- APK 2.6.5 (versionCode 14), 8 461 522 byte, SHA-256 BC4ADC35DE373D5350E3CA7ECA0AABB657CE873B207F50017776C8D0F82A4F72 (clean build, ingen stalte konstanter — 27 tester, 0 feil)
- Installer: NimayOMP-Setup-2.6.5.exe (10 400 596 byte-ish), SHA-256 68330F76013A4F502F5E377B0E1D9213B0A92BFD5402B21E0CD5861DFE03B911
- Servert på 7777/7787 og levert til `megaxenon@192.168.50.222:/home/megaxenon/` (hash-verifisert)

## [2.6.4] - 2026-10-29

### Fikset
- **Tale-motor-picker åpnet ikke:** «Velg STT-motor»/«Velg TTS-motor» gjorde ingenting. Årsak: `SettingsScreen` brukte en lokal `remember { mutableStateOf(null) }`-variabel for `voiceEngines` i stedet for `state.voiceEngines` som repositoryet oppdaterer fra bridge. Variabelen var alltid `null`, så AlertDialog-betingelsen var aldri sann. Nå leser skjermen `state.voiceEngines`, laster motorliste fra bridge ved trykk, og dialogen viser alle installerte/manglende motorer
- **Stale versjonskonstant:** appen viste «2.6.3 (12)» i Om-seksjonen selv om manifestet var 2.6.4/13. Årsak: Kotlin inkrementell kompilering kompilerte ikke `BuildConfig`-referanser på nytt ved versjonsbytte. Fiks: clean build; versjonen viser nå «2.6.4 (13)». Rutine fremover: clean build etter hvert versjonsbytte

### Verifisert på emulator (API 35) mot ekte bro (2.6.4: CCAF7122…)
- Innstillinger → Om viser **2.6.4 (13)**
- STT-picker: Whisper Tiny / NB-Whisper Small / NB-Whisper Medium (Installert) → valgte NB-Whisper Small
- TTS-picker: Norsk talesyntese (Piper medium) / Norsk NVCC / Chatterbox Multilingual (Installert), VibeVoice (Mangler) → valgte Piper medium
- Valgene vises med «(Installert)»-merking og synkroniseres til bridge (`POST /api/voice/select`); bro viser active STT `nb-whisper-small`, active TTS `piper-talesyntese`
- Waveform-knapp i composer åpner talesamtalen; telefonknapp fjernet fra headeren
- 27 JVM-tester, 0 feil

### Bygg
- APK 2.6.4 (versionCode 13), 8 461 518 byte, SHA-256 CCAF712278771652858FA7F87CC51D341BAF5C67379864EFDC0B064861D30BC2
- Installer 2.6.4, SHA-256 D427C15D42167BA8E42AB4453E33C06E08EADCB3A8E608F693DBEF89642AA213
- Levert til `megaxenon@192.168.50.222:/home/megaxenon/` og hash-verifisert

## [2.6.3] - 2026-10-28 / 2026-10-29

### Fikset
- Modellvalg ble nullstilt og vist som «ingen modell» rett etter valg. Årsak: `/api/state` svarte `model: null` (eller timet ut) og overskrev valget i appen. Appen beholder nå valgt modell når OMP ikke rapporterer noen, setter valget optimistisk før nettverkskallet og lagrer det, slik at header og innstillinger alltid viser modellen brukeren valgte
- `applyState` overskriver ikke lenger modellen med `null`; `setModel` ruller ikke tilbake ved feil, men varsler
- **Blokkert OMP-runtime etter ubesvart godkjenning:** OMP venter evig på svar på en `extension_ui_request`, og mens den venter svarer den ikke på `get_state`, `new_session`, `set_model` eller `abort`. Det ga tidsavbrudd (60 s) på ny samtale, modellbytte og status – og appen mistet modellen. Bridge-fikser:
  - Ventende godkjenninger/spørsmål huskes per runtime og sendes på nytt til appen når SSE-tilkoblingen gjenopprettes (appen kan svare selv etter at den har vært lukket/bakgrunn)
  - `GET /api/state` svarer fra cache med `request` (den ventende godkjenningen) i stedet for å time ut
  - `GET /api/models` bruker cache mens en godkjenning venter
  - `POST /api/abort` avbryter ventende godkjenninger først, slik at OMP slipper RPC-låsen; hvis den fortsatt ikke svarer, stoppes OMP-prosessen slik at neste forespørsel starter en frisk
  - Ventende godkjenninger ryddes når runden avsluttes eller OMP-prosessen dør

### Nye funksjoner (denne runden)
- **Godkjenningsinnstillinger (Innstillinger → Tilkobling):** Tre valg — «Godta alt» (default), «Spør ved behov» (default), «Spør alltid» (always-ask). Endring sendes til bridge via `POST /api/approval`, synkroniseres med serveren og huskes etter omstart.
- **Talesamtaleknapp i chatfeltet:** Den blå telefonknappen i toppmenyen er fjernet. Ny waveform-knapp ved siden av mikrofonen i chatfeltet nederst (bare når feltet er tomt). Mikrofon = diktering til tekst; waveform = direkte toveis talesamtale med AI (åpner VoiceScreen). Aktiv modell, prosjekt og samtale beholdes i talemodus.
- **Intelligent automatisk scrolling:** Chatten følger nyeste tekst under streaming. Når brukeren scroller opp manuelt, stoppes autofølging. «Hopp til siste»-knapp vises når man ikke er helt nede. Autofølging gjenopptas når bunnen nås.
- **Valg av talemotorer (Innstillinger → Tale og lyd):** Egen seksjon for STT (viser alle installerte STT-motorer fra `/api/voice/engines`), TTS (viser alle installerte TTS-motorer) og samtaleinnstillinger (automatisk lytting/VAD, avbryt AI-tale, talehastighet). Viser om motor er installert, mangler eller ikke er tilgjengelig. Valg synkroniseres til bridge via `POST /api/voice/select`. Testknapp for å høre valgt stemme.

### Verifisert mot ekte bro (2.6.3-bridge, 7777)
- `POST /api/prompt` som krever bash-godkjenning → `GET /api/state` svarte på **16 ms** med `request` = godkjenningen (før: 60 s tidsavbrudd)
- Ny SSE-tilkobling fikk godkjenningen tilsendt på nytt (replay) og kunne svare via `POST /api/ask`
- `POST /api/abort` returnerte `{"ok":true}` umiddelbart, `streaming=false`, `request=none`, og `POST /api/sessions/new` virket med en gang etterpå
- `sessions/new` for testprosjekt: 1 s; `state` etter restart: ~1 s
- `POST /api/approval`: endret mellom default og always-ask, svarer `{"ok":true}`
- `POST /api/voice/select`: STT whisper-tiny / nb-whisper-small; TTS piper-talesyntese / chatterbox
- `GET /api/voice/engines`: stt/tts lister, active, installed, python
- 27 JVM-tester, 0 feil
- Testsesjoner og testprosjekt (`%TEMP%\omp-263-test`) er slettet; brukerens sesjoner i `upgrade` er urørt
- Bridge: `GET /api/session/messages` hoppet tidligere over assistentmeldinger med tomt innhold, selv om de inneholdt en feil. Meldinger med `errorMessage` sendes nå med.

### Bygg
- APK 2.6.3 (versionCode 12), 8 461 525 byte, SHA-256 8E9176BE23C0772FD654AD2F9E912CC2F3C0C2AC1117D20C6E4D090EBE3E2846
- Bridge `omp-bridge.mjs` SHA-256 4CC863E109378621904B9A5F1807B8880F772D87650F134802B5268D0A819C79 (repo, installer-payload og installert kopi er identiske)
- Installer: NimayOMP-Setup-2.6.3.exe, SHA-256 E41B7FA9BE6D892BD520BB32628BB7098BB1676F4B6F74A067ABC96C1BD35377
- Levert til `megaxenon@192.168.50.222:/home/megaxenon/` og hash-verifisert

## [2.6.2] - 2026-10-28

### Fikset
- Appen kunne ikke skrive i chatten når ingen OMP-sesjon var aktiv: inndatafeltet var deaktivert og «Ny samtale» ga bare en advarsel. Feltet er nå aktivt så lenge bridgen er tilkoblet
- Sending oppretter nå en sesjon automatisk (siste prosjekt → mest brukte prosjekt) i stedet for å kreve at en sesjon finnes
- Talesamtale oppretter også sesjon automatisk; feilen «Åpne en samtale før du starter talesamtale» er borte i normale tilfeller
- Modellvalg ble nullstilt ved ny samtale og vist som «Bytt modell» i innstillinger. Valgt modell huskes nå (provider/id/navn) og brukes på nye sesjoner; innstillinger viser sist valgte modell
- `setModel` gir nå tydelig varsel når prosjektmappe mangler, i stedet for å returnere stille

### Årsak til feilene på telefonen
OMP-runtimeen for prosjektet appen brukte (`C:\Users\Megaxenon\documents\omp\upgrade`) sto fast i en runde som ventet på verktøygodkjenning (`always-ask`). Da ga `POST /api/sessions/new` tidsavbrudd (ingen sesjon → deaktivert felt), `/api/prompt` svarte «Agent is already processing», og `POST /api/model` fikk ikke svar (modellen ble ikke vist). Runtimeen ble drept og startet på nytt; deretter virket sesjon, modelliste og modellbytte mot ekte bro.

### Tester
- 27 JVM-tester, 0 feil
- Emulator (API 35, 2.6.2): ny samtale opprettet, tastatur åpnet ved trykk, melding sendt, OMP svarte med godkjenningsdialog for verktøy
- Broen: `sessions/new`, `models` (271) og `model` verifisert på ny runtime
- Testsesjoner laget under feilsøkingen i `upgrade` er slettet; brukerens opprinnelige sesjoner er beholdt

### Bygg
- APK 2.6.2 (versionCode 10), 8 436 947 byte, SHA-256 F4FC4A11BFB36FAC21465970BB5459B2BD9B10EFA2BE7CF0045A3F0833C18CFE
- Installer: NimayOMP-Setup-2.6.2.exe (10 375 765 byte), SHA-256 7BB9F8E46BC32AFF67E0B4BCE9797C70A2F3C72FC2CE946226D1A35D41ECEAEF
- Levert til `megaxenon@192.168.50.222:/home/megaxenon/` og hash-verifisert

## [2.6.1] - 2026-10-28

### Fikset
- `/ompqr` feilet med `Error: Mangler C:\Program Files\Nimay OMP\tools\omp_qr.py`: `tools\omp_qr.py` manglet i installer-payloaden og er nå inkludert
- QR-bildet ble skrevet til `C:\Program Files\Nimay OMP\omp_chat_qr.png`, som ikke er skrivbart for vanlige brukere. Bildet skrives nå til `%LOCALAPPDATA%\OmpChat\omp_chat_qr.png` når prosjektroten ikke er skrivbar
- `qrOutputPath()` bruker en reell skriveprobe i stedet for `fs.accessSync(W_OK)`, som feilaktig rapporterer Program Files som skrivbar på Windows
- Node-fallback (`tools\qrgen.mjs`) lager QR-bildet når Python eller `qrcode`/`pillow` mangler; skala økt 8 → 12 for bedre skanning

### Tester
- Node-harness med falsk OMP-kontekst: Python-stien skrev `omp_chat_qr.png` (961 byte) og viste terminal-QR; `--no-python` skrev `omp_chat_qr.bmp` (1 037 286 byte)
- QR-payload dekodet med jsQR (ZXing-basert): `omp2://100.96.46.66:7777?token=…` og `omp2://192.168.50.159:7777?token=…`
- Live-installasjon patchet: `tools\omp_qr.py` kopiert inn, kommandoene oppdatert, `ompchat-commands.json` peker på `C:\Program Files\Nimay OMP`
- Installer 2.6.1 ikke installasjonstestet (ville overskrevet brukerens aktive installasjon); payload og versjonsinfo verifisert
- Reproduksjonsbygg av EXE-en fra samme kilder ble byte-identisk med den leverte filen (samme SHA-256)

### Bygg
- Installer: NimayOMP-Setup-2.6.1.exe (10 373 260 byte), SHA-256 6BC77EB09E6C8249AC5C9112C9FCE2A733B593F2CDA9D32CB00596963DDD75B4
- Versjonsinfo: 2.6.1.0 / 2.6.1
- APK uendret fra 2.6 (samme SHA-256)
- Kopiert til `megaxenon@192.168.50.222:/home/megaxenon/` og hash-verifisert

## [2.6] - 2026-10-28

### Legg til
- Ekte talesamtale i appen: mikrofon → VAD-endepunkting → lokal STT → aktiv OMP-sesjon → lokal TTS → automatisk avspilling
- Egen talesamtale-skjerm med stor anropsknapp, nivåmåler, transkripsjon/svar og «Håndfri samtale»
- Telefonknapp i chat-headeren som åpner talesamtalen
- Runtime-tillatelse for RECORD_AUDIO
- `BridgeClient`-metoder for `/api/voice/engines|transcribe|speak|audio`

### Tester
- 27 JVM-tester (10 nye i `VoiceCallTest`)
- 2 instrumenterte tester mot ekte bro, ekte STT/TTS og ekte OMP-sesjon (`VoiceChainTest`)
- Release-APK 2.6 installert og startet på emulator (API 35)

### Bygg
- Versjon 2.6 (versionCode 9)
- SHA-256 APK: 7BAE9DE16CA575ED605ED208CC7B82B6066A3243F273F3BB21007E457D1F4ADA
- Installer: NimayOMP-Setup-2.6.exe, SHA-256 133EE659E1830E8F9ACDF8C3981134C7FE7888DF5E443FF85D26F71D2ACE4F86

## [2.3] - 2026-10-28

### Fikset
- APK-installasjonskrasj på Android 16 (fjernet adaptive-icon og splash-ikon referanser)
- /ompqr QR-kode ble avkuttet i terminalwidget (mindre QR-kode, PNG åpnes alltid i bildeviser)

### Bygg
- Oppdatert versjon til 2.3 (versionCode 5)
- Signert med samme nøkkel som 2.0/2.1/2.2

## [2.2] - 2026-10-27

### Fikset
- Release-APK installasjonsproblemer
- Signeringsnøkkel verifisert (samme som v2.0)

### Bygg
- Oppdatert versjon til 2.2 (versionCode 4)

## [2.1] - 2026-10-27

### Legg til
- Full UI/UX-overhaling inspirert av ChatGPT mobilapp
- Ny fargepalett (mørk modus)
- Ny typografi og former
- Ny chat-layout (brukeremeldinger høyre, AI-svar uten boble)
- Ny sidepanel med søk, prosjektvelger, gruppert samtaleliste
- Ny meldingsfelt med auto-høyde, plussikon, mikrofon, send/stopp

### Bygg
- Oppdatert versjon til 2.1 (versionCode 3)
- Signert med samme nøkkel som v2.0

## [2.0] - 2026-10-26

### Legg til
- Fungerende OMP-integrasjon via bridge på port 7777
- CrashReporter/CrashScreen for telefon-krasjrapportering
- Globale /ompserver og /ompqr OMP-kommandoer
- Crash-fiks (raster-ikoner + bitmap-splash)

### Bygg
- Versjon 2.0 (versionCode 2)
- SHA-256: FFDE3887DD400B00109C324D4A5810F7C57848A5CA1EB773DF60708A06361AEB## [2.7.0] - 2026-10-29

### Nytt: full tonet Filbehandler i OmpChat
- **Filbehandler-skjerm** i sidemenyen (telefon + PC): to faner «Telefon» og «PC»,
  brødsmuler, liste- og rutenettvisning, sortering etter navn/dato/størrelse/type,
  PC-side filsøk, langttrykk for flervalg, ny mappe, sletting (med bekreftelse),
  gi nytt navn, del (shared intent), og lagringsinfo (brukt/ledig + fremdrifts behalf).
- **Telefon-siden** bruker Androids Storage Access Framework (SAF): trær legges til
  via systemfilvelger (androidx.documentfile), tilganger husker taak Repository
  (persistable uri permissions), interne tmpfiler ender med `.ompdownload`
  omdosing fra `/api/files/upload-status&finalize=1` + overstyring/rename.
- **Overføring godkjent (to veier med RÆ-databasert streaming, ingjene base64):**
  - **PC → Telefon:** `GET /api/files/download` med HTTP `Range` for gjenopptak;
    appen borrower .ompdownload + rename := kontrollerer . size og SHA-256 mot bro.
  - **Telefon → PC:** `POST /api/files/upload` (octet-stream, offset + finalize),
    midlertidig fil `.ompdownload` som omdøpes ved evt. konflikt; SHA-256 kommer
    i bro-responset og sammenlignes mot mobil-siden som unnfaller samme hash.
- **Konfliktkanaler:** dialog per fil med Hopp over/Overskriv (samt Overskriv alle);
  PC-mapsystemmene/parserne trygt brut.
- **Stabilitet:** automatisk gjenoppta (logik + bridge restart🐇), retry pattern,
  bridgejeg tokling (PC-side fs jobber med SSE fs_job), gjenopptak fra offset,
  verfiedelse via /api/files/hash. Kø kontrollert fra filskjermen.
- **Bro-endepunkter:** /api/fs/list|storage|tree|search, /api/fs/mkdir|rename|
  delete|copy|move (PC-side: cp/mv, sikret av protectedFsPath for System mapper),
  /api/files/download (HTTP Range), /api/files/upload (octet-stream offset),
  /api/files/hash (stream), /api/files/upload-status (gjenopptak probing).
  Windows diskliste via A:\-fsutil, Linux via / + /home|/media|/mnt.
- **Ikke-nedskaling:** ingen SSH, ingen ekstern sky; brukte broen med samme token
  (sett = OMP-tilkobling). verker både via Wi-Fi, LAN, Tailscale.

### Bygg
- APK 2.7.0 (versionCode 19) 8 568 285 byte, SHA-256 749B19E3…7B5C, clean build,
  deployet til C:\Program Files\Nimay OMP\apk\OmpChat.apk, levert til megaxenon@192.168.50.222
  og på chat.nimay.net/downloads/nimayomp/OmpChat-2.7.0.apk.
- Installer NimayOMP-Setup-2.6.8.exe rebygget med filbehandler-bro: SHA-256
  D0E510B9…1EF; levert til /home/megaxenon/Docker/nimay-chat/updates/nimayomp/.
- Tests: backend-endropunktene verifisert med curl/PowerShell mot broen (roots/PC-
  list, upload+finalize, hash + HTTP-range download, mkdir/rename/delete med beskyttelse);
  filbehandler-skjermen på emulator viser lagring del + registrerte faner.setAttribute; SAF-trær
  og lang-tid transfer dialoger trenger fysisk telefontest (neste steg).



## 2.9.60
- Multi-PC reliability, sidebar redesign, active session indicators, cleanup/archive/trash, Bridge 2.9.12, installer 3.2.2.
