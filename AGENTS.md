# AGENTS.md – Nimay OMP Chat

## Permanent utviklingsregel – APK-filnavn og versjonering

Fra og med versjon 3.0.5 skal alle nye Nimay OMP Chat APK-utgivelser ha versjonsnummeret i filnavnet.

Format:
- nimayomp-chatgpt-inspired-VERSJON.apk
- Eksempler: nimayomp-chatgpt-inspired-3.0.5.apk, nimayomp-chatgpt-inspired-3.0.6.apk

Krav:
1. Versjonsnummeret skal alltid stå rett før ".apk".
2. Hver ny utgivelse skal ha økt versionCode og korrekt versionName.
3. Tidligere APK-utgivelser skal bevares i release-arkivet.
4. Nedlastingssiden skal vise riktig versjonsnummer og lenke til den versjonerte APK-filen.
5. Oppdater update.json med korrekt versjon, SHA-256 og nedlastingsadresse.
6. En fast "latest"-lenke kan beholdes for automatisk oppdatering og bakoverkompatibilitet, men den versjonerte APK-en skal alltid eksistere separat.
7. Verifiser at den publiserte APK-en har samme SHA-256 som den signerte release-filen.
8. Ikke endre pakkenavn eller signeringssertifikat.

## Permanent utviklingsregel – OMP Bridge-versjonering

Fra og med versjon 2.9.16 skal hver ny utgave av OMP Bridge få et høyere versjonsnummer.

Regler:
1. Gjeldende versjon er "2.9.16". Neste utgave med kodeendringer skal være minst "2.9.17".
2. Oppdater versjonsnummeret konsekvent i kildekode (VERSION-konstanten), API-responser, logger og eventuelle installasjonsmetadata.
3. Android-app og Bridge skal versjoneres uavhengig av hverandre.
4. Ved oppdatering skal det verifiseres at den installerte og kjørende Bridge faktisk rapporterer den nye versjonen. Kontroller også at riktig kode er lastet.
5. Ikke betrakt en Bridge-oppdatering som ferdig før versjonsnummer, installerte filer og kjørende prosess samsvarer.
6. Dokumenter alltid gammel versjon, ny versjon og resultatet av verifikasjonen i sluttrapporten.
7. Bevar kompatibilitet med eksisterende Android-versjoner.
8. Ikke stopp den aktive Bridge-en uten en trygg oppdateringsmekanisme og nødvendig godkjenning.
9. Ikke endre versjonsnummeret uten å restarte Bridge med kontrollert oppdatering.

## Permanent utviklingsregel – Parallell OMP Bridge på port 7799

Ved oppdateringer av OMP Bridge skal det fra nå av kunne kjøres to Bridge-instanser parallelt på samme PC.

Regler:
1. Eksisterende Bridge beholdes uendret: samme port, token, prosess og oppstartsmekanisme.
2. Ny Bridge installeres i separat mappe: %LOCALAPPDATA%\Nimay\OMPBridge-7799\
3. Ny Bridge bruker port 7799 med nytt, sterkt, tilfeldig token.
4. Separat konfigurasjon, PID-fil, loggfil og oppstartsskript.
5. Ikke overskriv eksisterende token eller konfigurasjonsfiler.
6. Start ny Bridge uten å påvirke den gamle.
7. Kontroller helsesjekk og autentisering for ny Bridge.
8. Kontroller at prosjekt- og filsystemendepunkter fungerer.
9. Sørg for at ny Bridge er tilgjengelig via LAN og Tailscale, ikke offentlig internett.
10. Tokenet skal ikke committes til Git eller inkluderes i offentlig logging.
11. Ikke fjern den gamle Bridge-en eller gjennomfør permanent bytte uten eksplisitt godkjenning.
