/**
 * /ompserver — start the OmpChat bridge as a detached background process.
 *
 * Installed to ~/.omp/agent/commands/ompserver/index.ts so it is available from
 * every OMP project. Uses the existing bridge/config.json untouched.
 */
import fs from "node:fs";
import {
  bridgeDir,
  bridgeStatus,
  checkStartPrerequisites,
  findPidOnPort,
  logPaths,
  pingBridge,
  projectRoot,
  readBridgeConfig,
  show,
  spawnBridge,
  statusLines,
  tailFile,
  waitForBridge,
} from "../ompchat-shared/ompchat.ts";

interface Ctx {
  hasUI?: boolean;
  ui: {
    notify(message: string, type?: "info" | "warning" | "error"): void;
    setWidget(key: string, content: string[] | undefined, options?: { placement?: "aboveEditor" | "belowEditor" }): void;
  };
}

export default function ompServerCommand() {
  return {
    name: "ompserver",
    description: "Start OmpChat-broen i bakgrunnen og vis status (port, IP, Tailscale)",
    async execute(args: string[], ctx: Ctx): Promise<undefined> {
      const mode = (args[0] ?? "").toLowerCase();
      const root = projectRoot();
      const config = readBridgeConfig(root);
      const port = config.port;
      const alreadyUp = await pingBridge(port);

      if (alreadyUp) {
        const status = bridgeStatus(port, true);
        show(
          ctx,
          "ompserver",
          statusLines(status, ["Status:     kjører allerede — startet ikke en ny prosess"]),
          `OmpChat-broen kjører allerede på port ${port}`,
        );
        return;
      }

      if (mode === "status") {
        const status = bridgeStatus(port, false);
        show(
          ctx,
          "ompserver",
          statusLines(status, ["Status:     ikke startet"]),
          `OmpChat-broen kjører ikke på port ${port}`,
          "warning",
        );
        return;
      }

      const problems = checkStartPrerequisites(root);
      const hard = problems.filter((problem) => !problem.includes("config.json"));
      if (hard.length > 0) {
        const status = bridgeStatus(port, false);
        show(
          ctx,
          "ompserver",
          statusLines(status, ["", "Kan ikke starte:", ...hard.map((problem) => `  · ${problem}`)]),
          "Kan ikke starte OmpChat-broen — se /ompserver",
          "error",
        );
        return;
      }

      const started = spawnBridge(root);
      if (started.error) {
        show(
          ctx,
          "ompserver",
          statusLines(bridgeStatus(port, false), ["", `Start feilet: ${started.error}`]),
          `Kunne ikke starte broen: ${started.error}`,
          "error",
        );
        return;
      }

      const up = await waitForBridge(port);
      if (!up) {
        const logs = logPaths();
        const tail = [...tailFile(logs.err, 6), ...tailFile(logs.out, 6)].slice(-8);
        show(
          ctx,
          "ompserver",
          statusLines(bridgeStatus(port, false), [
            "",
            "Broen svarte ikke innen 12 sekunder.",
            ...(tail.length ? ["Siste logg:", ...tail.map((line) => `  ${line}`)] : []),
          ]),
          "Broen startet ikke — sjekk loggen",
          "error",
        );
        return;
      }

      const pid = findPidOnPort(port) ?? started.pid ?? null;
      const status = bridgeStatus(port, true);
      if (pid && !status.pid) status.pid = pid;
      const configFile = `${bridgeDir(root)}\\config.json`;
      const configExists = fs.existsSync(configFile);
      show(
        ctx,
        "ompserver",
        statusLines(status, [
          `Startet:   bakgrunnsprosess som overlever at OMP lukkes`,
          configExists ? "Config:    bruker eksisterende config.json (token urørt)" : "Config:    ny config.json ble opprettet",
          `Pare mobil: /ompqr`,
        ]),
        `OmpChat-broen kjører på port ${port}`,
      );
      return;
    },
  };
}
