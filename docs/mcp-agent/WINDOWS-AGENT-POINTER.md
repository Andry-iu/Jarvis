# Come trovare l'ultima versione di Jarvis Windows MCP Agent

**Punto di accesso per le nuove chat e per gli strumenti che aprono il ramo predefinito `main`.**

Questo ramo `main` conserva una versione **storica** dei sorgenti
`windows-mcp-agent` (README originale 0.4.1) insieme al progetto
**JarvisWake Android**. Il numero del README Windows presente qui NON e
l'ultima release Windows MCP. Non confondere versioni Android, installer
storici e versione attuale dell'agente.

**Ultima release Windows MCP verificata al 19 novembre 2026: 0.8.4.**
- Branch Windows di lavoro verificato: `feature/unified-agent-source-update-20261114`
- Commit dei sorgenti 0.8.4: `ff2038d4b250c9d3e7e6590c2409ad55e5081380`.
- ZIP verificato: `Jarvis-Agent-Setup-0.8.4.zip`, SHA-256
  `d10fa8e6d5e9d174660f0ca77e2d2ed1873ed9e8c4b305d66cb26c2494c2ccbe`.
- Desktop1 verificato **live** via MCP il 19/11/2026: `version=0.8.4`,
  `elevated=true`, `state=idle`.
- Desktop2 e target LAN: 0.8.4 verificata nei verbali del 14/11/2026;
  non trasformare tali verbali in una lettura live di oggi.

## Fonte canonica e procedura di ripresa

**Leggere prima di rispondere o consegnare un installer:**

[CURRENT-RELEASE.md nel branch Windows](https://github.com/Andry-iu/Jarvis/blob/feature/unified-agent-source-update-20261114/docs/mcp-agent/CURRENT-RELEASE.md)

Quindi controllare su GitHub i rami Windows attuali, eventuali commit e
manifest di release piu recenti. Per la versione **installata su un PC**
usare `machine_info` / `agent_status` direttamente o via gateway;
un file storico Git non sostituisce la lettura live. Se la connessione
non riesce, distinguere ultimo stato verificato e stato attuale sconosciuto.

Dopo ogni nuovo rilascio, aggiornare sia il documento canonico sul ramo
di sviluppo **sia questo rimando su main**, con versione, branch, commit,
manifest checksum e stato dei dispositivi. Non fare merge del codice Windows
su `main` implicitamente: questa pagina e una nota di orientamento,
non un cambio del ramo di sviluppo. Non pubblicare ZIP, segreti o credenziali.

Registrazione richiesta dall'utente il 19 novembre 2026 dopo l'errore di
aver identificato `0.8.2` consultando un ramo Windows precedente.
