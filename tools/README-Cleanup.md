# Nimay OMP Bridge Cleanup Tool

A standalone Windows tool that finds and removes all old Nimay OMP Bridge installations.

## Download

- **File:** `Nimay-OMP-Bridge-Cleanup.exe`
- **SHA-256:** `B219B7E76E5C5EA3C544CDF22E73BCACCD4BE6EB30427578791BDA14AD79F5B1`

## What it does

1. **Scans** your system for old Bridge installations, processes, scheduled tasks, services, firewall rules, and startup shortcuts.
2. **Shows** everything found in a list before taking any action.
3. **Requires explicit confirmation** before removing anything.
4. **Removes** only confirmed Bridge components.
5. **Verifies** the cleanup afterward.
6. **Logs** all actions to `%TEMP%\NimayBridgeCleanup.log`.

## What it protects

- Oh My Pi (OMP) itself and its configuration
- OMP sessions and conversation history
- Mnemopi memory database
- AI models and API configurations
- Node.js and Bun (system programs)
- Docker/WOL service on Debian server
- Other Windows services and scheduled tasks
- Other Node.js/Bun processes

## What it removes

- Old Bridge processes (`omp-bridge.mjs`)
- Old Bridge installation folders
- Old Task Scheduler tasks (`Nimay OMP Bridge`, etc.)
- Old Bridge Windows services
- Old Bridge firewall rules
- Old Bridge startup shortcuts
- Old Bridge registry entries

## Usage

1. Download `Nimay-OMP-Bridge-Cleanup.exe`
2. Run it (no admin rights required for scanning)
3. Click "Skann systemet" to scan
4. Review the findings
5. Click "Rydd opp (bekreft)" to clean up
6. Confirm when prompted

## After cleanup

Install the latest Nimay OMP Bridge from:
https://github.com/megaxenon83-server/OmpChat/releases

## Notes

- The tool can be run multiple times safely.
- Unknown or ambiguous findings are NOT removed automatically.
- A detailed log is saved to `%TEMP%\NimayBridgeCleanup.log`.