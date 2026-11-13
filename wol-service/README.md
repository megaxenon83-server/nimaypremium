# Nimay Wake-on-LAN-tjeneste

Sentral Docker-tjeneste for å registrere, administrere og vekke PC-er som kjører OMP.

## Installasjon

1. Kopier mappen `wol-service` til serveren, f.eks. `/home/megaxenon/Docker/nimay-wol`.
2. Kopier `.env.example` til `.env` og sett `WOL_API_TOKEN` til en lang tilfeldig hemmelighet.
3. Start:
   ```bash
   docker compose up -d --build
   ```
4. Verifiser:
   ```bash
   curl http://127.0.0.1:8790/health
   ```

## API

Alle endepunkter (unntatt `/health`) krever `Authorization: Bearer <WOL_API_TOKEN>`.

- `GET /pcs` — liste PC-er med online-status (TCP-sjekk mot bridge-port).
- `POST /pcs` — registrer PC: `{name, mac, ip/host, port, broadcast, wolPort}`.
- `GET /pcs/:id` — hent én PC.
- `PUT /pcs/:id` — oppdater.
- `DELETE /pcs/:id` — slett.
- `POST /pcs/:id/wake` — send WOL magic packet.

Konfigurasjon lagres i `/data/pcs.json` (volume), så den overlever container-restart.

## Tailscale

Tjenesten binder seg til `0.0.0.0` og er dermed tilgjengelig via Tailscale-IP-en til serveren.
Sett `WOL_BIND_IP` hvis du vil begrense til et interface.

## Automatisk restart

`restart: unless-stopped` i compose-filen. Containeren starter automatisk ved server-boot
hvis Docker er satt til å starte ved boot (`systemctl enable docker`).

## Android-integrasjon

I appens PC-oversikt: slå på "Bruk sentral WOL-tjeneste" for en PC og fyll inn
tjeneste-URL + token. "Vekk"-knappen sender da forespørsel til tjenesten i stedet for
direkte WOL-pakke fra telefonen.

## Begrensninger

- WOL krever at PC-ens BIOS/UEFI og nettverkskort har WOL aktivert.
- WOL-pakker når bare enheter på samme LAN (eller via Tailscale subnet router).
- Online-status er en enkel TCP-sjekk mot bridge-porten (7777).
