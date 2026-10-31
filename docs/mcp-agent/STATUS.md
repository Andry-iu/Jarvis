# 0.5.8 — installata e verificata, Codex operativo

31 ottobre 2026: plugin aggiornato dall'utente, 38 strumenti diretti disponibili. Schermata fornita dall'utente: quota residua 88%, zero crediti, ricarica automatica disattivata. La nuova guardia usa un riscontro privato associato ad account/PC soltanto per il saldo API nullo, con controlli API invariati e scadenza 7 novembre 2026 ore 15:55 UTC. Nessuna chiave API, acquisto o ricarica.

Codex verificato realmente: risposta, storico, steer, interrupt confermato, ripresa dopo riavvio e modifica di un file di prova con digest controllato. Dopo installazione, turno completato dal plugin con risposta JARVIS_INSTALL_OK. Agente 0.5.8 elevato, identita e job 0.5.7 conservati; backup 0.5.7 e stato verificati, rollback isolato superato. Sorgente binario 6b5530e8347502f35511aabdf696c58877836cee; SHA-256 04F369F365816BC076CBCE3F89589BAC63FE628385FFF8B96316E2E79115C1BE.

[Verbale 0.5.8](CODEX-0.5.8.md). Altri PC fisici, nuova chat/Voice e avvio dopo reboot restano da verificare; clone NestingOpt sospeso. Le sezioni seguenti sono lo storico dei rilasci precedenti.

# 0.5.7 — installata e verificata, Codex in attesa del controllo costi

31 ottobre 2026: test Go, go vet, compilazione Windows, backup e rollback isolato superati; agente operativo 0.5.7 con token elevato verificato. File/cartelle, immagini MCP dirette, test/build Go e build .NET 9 verificati anche tramite plugin da questa chat. Stato, job e ricevute persistenti; routing autenticato verificato fra due istanze sullo stesso PC. Nessun altro PC fisico configurato o verificato in questo rilascio.

Codex locale: account/quote e lista sessioni verificati; avvio/ripresa/turni reali bloccati per balance=null e revisione automatica. Zero inferenze Jarvis avviate. UI Impostazioni/Utilizzo osservata: 88% quota residua e 0 crediti; ricarica automatica non verificata. Restano necessari verifica/autorizzazione appropriata per questo punto, aggiornamento catalogo client e collaudo in nuova chat/Voice. [Verbale completo](VALIDATION-0.5.7-20261031.md), [contratto](RUNTIME-0.5.7.md), [continuita Windows](../../windows-mcp-agent/CONTINUITY.md).

# Jarvis Windows MCP Agent — stato corrente 0.5.6

**31 ottobre 2026: 0.5.6 installata sul PC Jarvis-Desktop1, elevata e collaudata.**
Questa sezione sostituisce tutte le sezioni storiche seguenti.

- Sorgenti compilati: eb5370396736283c1aed111e30001b95bd72c1b4.
- SHA-256 operativo: C230A1FF4EE852744318931FB1BF620022985B01606DFED5D1B1061182F410A5.
- Binario D:\Jarvis\jarvis-mcp.exe; sorgenti D:\Jarvis\MCP-Source\windows-mcp-agent.
- Branch reconcile-windows-mcp-agent-20261031; nessun merge su main.
- Nuove operazioni del task_start esistente: desktop_inspect e desktop_action.
- Azioni su controlli identificati: invoke, set_text, select, toggle, secondo il supporto reale.
- UIA oppure messaggi mirati a controlli Edit/Button nativi verificati; nessun input globale o clic su coordinate.
- Snapshot monouso 90 s, verifiche finestra/PID/avvio/runtime ID/geometria/primo piano/stato; rifiuto su incertezza.
- Desktop abilitato nella configurazione locale; password escluse, contenuti Edit non restituiti, controllo hash contro modifiche successive all'ispezione.
- set_text sostituisce l'intero campo: non usarlo per sovrascrivere bozze senza autorizzazione.
- WinForms e WPF: clic/testo/checkbox e casi avversi superati, pulsante esca mai invocato, due monitor X=0/-1920.
- Collaudo WinForms superato anche sul processo installato, porta 8765.
- go_test diretto dal plugin dopo installazione: completed, 100%, 3.313s.
- desktop_inspect diretto dal plugin sul desktop reale: completed, 11 controlli, senza troncamento. Una precedente osservazione non disponibile è stata rifiutata senza azioni; causa non identificata.
- TokenElevation=true verificato sul processo 476784; porte di prova libere; sorgenti tracciati puliti; backup originali preservati.
- Backup 0.5.5/configurazione in D:\Jarvis\Backups\mcp-0.5.6-20261031; ripristino isolato 0.5.5→0.5.6→0.5.5 verificato.
- Riferimento completo: [Desktop 0.5.6, collaudi, limiti e ripristino](DESKTOP-0.5.6.md).

## Limiti da mantenere espliciti

Nessuna promessa di controllo universale o assenza assoluta di errori dei provider. Se un controllo non è identificabile/supportato, fermarsi; nessun fallback a pixel o retry di azioni incerte.
Mouse libero, trascinamenti e tasti/scorciatoie globali non sono implementati. Le pagine web non sono garantite.
Il desktop deve essere sbloccato e il bersaglio in primo piano. Non aggirare UAC o desktop protetti.
Cattura diagnostica acquisita e mostrata all'utente; manca un comando MCP dedicato.
Avvio automatico dopo riavvio ancora da verificare. Android e NestingOpt restano fuori da questo intervento.

---

# Storico fino alla 0.5.5 — non usare come stato corrente

# Jarvis Windows MCP Agent — stato corrente 31 ottobre 2026

Questa sezione sostituisce le indicazioni operative storiche riportate sotto.

## Esito verificato

**0.5.5 installata sul notebook Jarvis-Desktop1 e collaudata direttamente da ChatGPT tramite MCP.**
Sottoprogetto Windows distinto dall'app Android; nessun intervento su Android o sul clone NestingOpt.

- Eseguibile operativo: `D:\Jarvis\jarvis-mcp.exe`.
- Checkout: `D:\Jarvis\MCP-Source\windows-mcp-agent`; root Git `D:\Jarvis\MCP-Source`.
- Branch: `reconcile-windows-mcp-agent-20261031`.
- Commit sorgenti del binario: `b982ed77bdbf4921eabf16e04461bfa89b944675`.
- SHA-256 operativo e staging: `0769E1597B37628B3197A545F10FB42491D85913B8491B3E722C0D398B47E47E`.
- Go 1.27.0, Windows/amd64. Nessuna GitHub Actions.
- Root operativa autorizzata: `D:\Jarvis\MCP-Source`; precedente configurazione `D:\Jarvis\Projects` salvata nel backup.
- Endpoint: 127.0.0.1:8765/mcp, accesso remoto tramite il tunnel e Cloudflare Access esistenti.

## Implementato e verificato

task_start accetta operation=go_test oppure go_build e project relativo alla root.
go_test esegue go test ./...; go_build compila in Staging/jarvis-mcp.exe.
Annullamento Windows corretto: termina l'albero dei processi tramite taskkill.exe di sistema.
Timeout 120 s, terminazione con limite 5 s e WaitDelay 2 s; contesto riportato nel risultato.
I test Go eseguono codice dei moduli autorizzati con i permessi dell'agente: il controllo dei percorsi non costituisce una sandbox del codice.

- Test locali sul notebook, incluso nuovo test regressione cancellazione: ok jarvis-mcp-prototype 3.009s.
- Collaudo MCP isolato dopo la correzione: figlio terminato dopo task_stop.
- Timeout reale: 120.4126 s, cancelled, context deadline exceeded, nessun figlio rimasto attivo.
- Rifiutati: operazione sconosciuta, percorso assoluto, traversal fuori root e directory senza go.mod.
- Backup e ripristino provati su porta 8767: 0.5.4 → 0.5.5 → 0.5.4.
- Dopo installazione, agent_status remoto: versione 0.5.5.
- go_test remoto reale: task-1 completed, 100%, ok jarvis-mcp-prototype 2.925s, 13:21:45–13:21:49 Europe/Rome.
- go_build remoto reale: task-2 completed, 100%, go_build succeeded, 13:22:11 Europe/Rome.
- Hash staging/operativo uguali; nessun listener temporaneo 8766/8767; sorgenti tracciati puliti.
- vcs.modified=true deriva da file non tracciati; nessuna modifica ai sorgenti tracciati al controllo finale.

## Backup e ripresa

Backup operativo: `D:\Jarvis\Backups\mcp-0.5.5-20261031`.
Conservare jarvis-mcp-0.5.4.exe, replaced-0.5.4.exe, agent.env, avvio originale e checksum.
SHA-256 0.5.4: `7A29039C153409C35135D11C9CD891BC7366A3C9713AA77C3CFF947780B0288E`.
Conservati stash `backup update.go locale 20261031` e update.go.bak.
La procedura di installazione include ripristino automatico su errore di avvio; non è stato necessario attivarlo in produzione.
Dettagli, log e ripristino manuale: [validazione 0.5.5](VALIDATION-0.5.5-20261031.md).

## Limiti e attività aperte

- Cattura schermo ancora non verificata; nessuna dichiarazione di funzionamento.
- Il catalogo plugin non ha restituito Jarvis-Desktop1: nessun aggiornamento del pacchetto plugin eseguito. I parametri operation/project sono comunque stati accettati nelle chiamate dirette verificate in questa sessione.
- Il timeout usa lo stato cancelled; distinguere timeout/stop dal testo context deadline exceeded/context canceled.
- go_test può utilizzare la cache Go. La regressione è stata anche eseguita con go test -count=1 ./... nella verifica del candidato.
- L'output salvato nel risultato è limitato agli ultimi 12000 byte; CombinedOutput acquisisce prima l'output in memoria.
- Annullamento con taskkill non è isolamento dei processi ostili: usare moduli fidati.
- La validazione non ha eseguito un riavvio del notebook né provato l'avvio automatico dopo reboot.
- Modifiche pubblicate sul branch di lavoro, senza merge implicito su main.

---

## Registro storico — non usare come stato corrente

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


## MCP 0.5.0 source release (2026-10-31)

Published update_prepare/update_execute, Cloudflare Access JWT required for both, start-agent PowerShell/CMD examples and local environment templates. No actual 0.5.0 install performed on Windows; desktop still runs 0.4.1. Windows integration of detached restart/rollback is pending. Existing failed NestingOpt clone remains paused.

