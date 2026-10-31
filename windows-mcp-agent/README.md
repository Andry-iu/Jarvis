# Jarvis Windows MCP Agent 0.5.5

Agente Windows Go, separato dall'app Android Jarvis. Installazione e collaudo MCP verificati il 31 ottobre 2026; dettagli in [STATUS](../docs/mcp-agent/STATUS.md) e [validazione](../docs/mcp-agent/VALIDATION-0.5.5-20261031.md).

## Test e compilazione controllati

Lo strumento esistente task_start accetta:

```json
{"operation":"go_test","project":"windows-mcp-agent"}
```

Sostituire operation con go_build per compilare in Staging/jarvis-mcp.exe.
agent_status restituisce avanzamento e risultato; task_stop richiede l'annullamento.
Non sono ammessi comandi o argomenti shell arbitrari. La directory deve essere relativa alla root autorizzata e contenere go.mod.
Timeout 120 s; su Windows lo stop termina anche i processi figli tramite taskkill.exe di sistema.
Lo stato cancelled copre sia stop sia timeout: il risultato contiene il motivo del contesto.
go_test usa go test ./... e può utilizzare la cache; per verifica indipendente usare localmente go test -count=1 ./....

Le operazioni Go eseguono codice del progetto con i permessi dell'agente: selezionare moduli fidati.
I risultati conservano gli ultimi 12000 byte dell'output; la raccolta iniziale in memoria non è limitata.

## Configurazione e installazione verificata

- Windows/amd64; build verificata con Go 1.27.0, modulo compatibile Go >=1.23.2.
- Eseguibile operativo: D:\Jarvis\jarvis-mcp.exe.
- Sorgenti: D:\Jarvis\MCP-Source\windows-mcp-agent, branch reconcile-windows-mcp-agent-20261031.
- JARVIS_PROJECTS_ROOT operativo: D:\Jarvis\MCP-Source.
- Configurazione locale sotto D:\Jarvis\Config, credenziali fuori da Git.
- Endpoint locale 127.0.0.1:8765/mcp; Cloudflare Tunnel e Access Managed OAuth per l'accesso remoto.
- Backup 0.5.4 e configurazione precedente: D:\Jarvis\Backups\mcp-0.5.5-20261031.

Il percorso operativo reale differisce da quello Agent\jarvis-mcp.exe degli script bootstrap di esempio: non sostituire l'avvio esistente senza verificarlo.

## Build locale sul notebook

```powershell
go test -count=1 ./...
go build -o Staging\jarvis-mcp.exe .

```

L'agente attivo deve trovarsi fuori da Staging, affinché la compilazione non sovrascriva un eseguibile in uso.
Prima di distribuire: backup con checksum, prova di ripristino isolata e verifica MCP dopo riconnessione.
Seguire [RELEASE_POLICY](../docs/mcp-agent/RELEASE_POLICY.md); non usare GitHub Actions per compilare o firmare.
Nessun controllo desktop completo o funzionamento della cattura schermo è attestato da questa release.
