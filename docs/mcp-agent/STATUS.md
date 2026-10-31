# Jarvis Windows MCP Agent — stato verificato (2026-10-31)

## Ambito
Sottoprogetto distinto dall'app Android Jarvis. Agente Windows (Go) con endpoint MCP Streamable HTTP locale `127.0.0.1:8765/mcp`. Il collegamento ChatGPT usa Cloudflare Tunnel HTTPS e Cloudflare Access Managed OAuth. Non inserire credenziali, token, JWT, AUD o segreti nel repository.

## Versioni
- **0.3.1**: JWT Cloudflare Access e log delle richieste; collegamento MCP ChatGPT effettivamente riuscito e `agent_status` verificato da ChatGPT.
- **0.4.0**: `project_list` e `project_status`, limitati a `JARVIS_PROJECTS_ROOT`.
- **0.4.1**: `project_clone` asincrono/interrompibile, limitato a repository GitHub dell'owner `Andry-iu`; prevenzione sovrascritture. Ultima versione eseguita sul notebook; comando `project_clone` realmente invocato da ChatGPT.

## Stato corrente (non confondere con completato)
- Notebook: `Jarvis-Desktop1`; root autorizzata `D:\Jarvis\Projects`.
- Plugin privato `Jarvis-Desktop1`: OAuth Cloudflare stabilito, comunicazione diretta funzionante, strumenti aggiornati fino a `project_clone`.
- Repository da clonare: `Andry-iu/NestingOpt` (privato), branch principale `main`.
- **Clone non riuscito**: l'agente ha avviato l'operazione `task-1`, conclusa `failed` con `fatal: unable to get password from user`. Non dichiarare NestingOpt presente nella cartella.
- Nessuna modifica allo sviluppo NestingOpt è stata effettuata.
- Codex, PowerShell remoto, mouse/tastiera, gestione modifiche in corsa e motore autonomo **NON ancora implementati**.

## Punto esatto di ripresa
La prossima operazione concordata era, sul notebook, in una seconda PowerShell:
```powershell
git config --global credential.helper manager
```
Poi completare l'autenticazione GitHub in Git Credential Manager **senza condividere credenziali in chat** e ritentare via MCP:
`project_clone({ "repository_url": "https://github.com/Andry-iu/NestingOpt.git", "project": "NestingOpt" })`.
Monitorare con `agent_status` e verificare con `project_status` o `project_list`.

**L'utente ha richiesto esplicitamente di sospendere qui l'operativita sul clone e prioritizzare la conservazione del progetto su GitHub.** Non riprendere il clone senza una nuova richiesta.

## Verifiche effettive
- 0.3.1: `agent_status` ha restituito `idle`, versione `0.3.1`.
- 0.4.1: `agent_status` ha restituito `idle`, versione `0.4.1`.
- 0.4.1: `project_clone` ha restituito `running` (`task-1`), quindi `agent_status` ha restituito `failed` per mancata autenticazione Git.
- Build/test locali delle release riportati come superati durante la produzione dei pacchetti, ma l'ambiente attuale non sostituisce un collaudo completo Windows/ChatGPT per ogni funzione.

## Ripartenza fra computer/account
1. Recuperare repository Jarvis, sottoprogetto Windows MCP, documentazione di continuita e artefatti di release.
2. Installare Go >=1.23.2 e Git, configurare credenziali GitHub tramite Git Credential Manager.
3. Compilare: `go test -race ./...` e `GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build ...`.
4. Configurare localmente, mai in Git: `JARVIS_MCP_TOKEN`, `JARVIS_CF_TEAM_DOMAIN` (URL https completo), `JARVIS_CF_APP_AUD`, `JARVIS_PROJECTS_ROOT`.
5. Verificare Cloudflare Access e Managed OAuth, poi aggiornare gli strumenti del plugin ChatGPT a ogni modifica dello schema MCP.
6. Confermare `/health`, `agent_status` e un comando reale dal plugin prima di dichiarare operativo il rilascio.

## Sicurezza
Il token MCP precedentemente esposto in una chat e stato sostituito; non replicarlo. La cartella autorizzata limita i repository; il prototipo non esegue shell arbitraria. Il notebook deve mantenere una sessione desktop attiva; la sospensione interrompe il tunnel.

## Allineamento GitHub (2026-10-31)
I sorgenti integrali della versione **0.4.1** (13 file: Go, test, `go.mod`, README e guide di upgrade) sono stati pubblicati come **file singoli in chiaro** sotto `windows-mcp-agent/`, senza ZIP. Al momento del commit, tutti i 13 contenuti hanno superato il controllo di lunghezza byte rispetto ai file originali. Il test `go test ./...` e passato su Linux/amd64 con Go 1.23.2. Il collaudo completo su Windows della copia Git e la compilazione da checkout remoto restano da eseguire. Nessuna credenziale reale e stata inserita nei sorgenti.
