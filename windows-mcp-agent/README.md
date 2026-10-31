# Installer 1.1.0 — un solo avvio, prerequisiti e ripresa automatici

Richiesta utente: evitare comandi PowerShell manuali per preparare Codex e recuperare le installazioni. INSTALLA.cmd ora rileva/installa Git, Go e CLI Codex compatibile da fonti ufficiali con hash, gestisce login browser e verifica account/quota senza inferenza. Registro persistente per PC/utente/cartella, cache verificata, ricevute dei worker e nessun replay di esiti incerti. Ripresa della 1.0.1 fallita e dei tentativi 1.1.0 mai operativi; backup/rollback anche dei task, aggiornamento protetto degli script delle copie complete. Nome Desktop2 configurabile; binario agente 0.6.1 e servizio operativo Desktop1 invariati.

[Specifica, procedura e limiti](../docs/mcp-agent/INSTALLER-1.1.0.md). Cloudflare remoto (DNS/tunnel/Access/Managed OAuth), plugin e accessi account restano passaggi distinti: nessuna falsa dichiarazione di collegamento completo, nessun costo extra o modifica alle guardie. Collaudo secondo PC fisico e installazione reale dei runtime da assenti restano da eseguire; i test Windows isolati distinguono fixture da letture reali.

# Installer 1.0.3 — collegamento obbligatorio alla CLI Codex

Sostituisce la scelta opzionale della 1.0.2: l'obiettivo e usare Codex. Rilevamento CLI nativa prima di Windows App, versione esatta 0.165.0-alpha.1.2; nessuna modifica dei permessi WindowsApps. COLLEGA-CODEX.cmd recupera la prima copia 1.0.1 fallita oppure configura una copia completa, verifica account ChatGPT e quota senza inferenza. Se la configurazione di un agente attivo e diversa, rifiuta senza fermarlo. Dati osservati separati da inference_allowed; guardie economiche immutate e nessuna prova costi copiata.

[Procedura, test e limiti](../docs/mcp-agent/INSTALLER-DESKTOP2-1.0.3.md). Agente resta 0.6.1; il collegamento sul secondo PC fisico richiede esecuzione locale e riscontro dell'utente. Nome Desktop2 personalizzabile, plugin suggerito Jarvis-Desktop2. Lo storico seguente conserva le decisioni precedenti; la modalita Codex opzionale della 1.0.2 e superata.

# Installer 1.0.2 — correzione Accesso negato e ripresa protetta

Segnalazione utente dalla 1.0.1: codex.exe --version non accessibile interrompeva la prima installazione. Rilevamento opzionale ora intercettato: Jarvis prosegue con ponte Codex disabilitato. RIPRENDI.cmd recupera soltanto la copia 1.0.1 mai avviata, con backup integrale e ripristino su errore. Test Windows con vero Win32 5, fallimento originale e ripresa/rollback superati; servizio Desktop1 invariato. [Verbale, istruzioni e checksum](../docs/mcp-agent/INSTALLER-DESKTOP2-1.0.2.md). Il secondo PC richiede esecuzione e verifica dell'utente. Default Desktop2 personalizzabile; agente sempre 0.6.1.

# Installer 1.0.1 — Desktop2 predefinito, nome configurabile

La procedura chiede il nome del dispositivo; Invio mantiene Desktop2. Nome scelto usato per cartella proposta, task/collegamento, messaggi e nome suggerito del plugin. Verificati su Windows default e nome con spazi (Officina CNC), installazioni isolate e controlli precedenti. Pacchetto Jarvis-MCP-Setup-1.0.1.zip; agente resta 0.6.1. Nessuna rinomina Windows o cambio machine_id. [Verbale e checksum](../docs/mcp-agent/INSTALLER-DESKTOP2-1.0.1.md). Sostituisce il precedente pacchetto installer 1.0.0; i limiti di collaudo su secondo PC/account restano invariati.

# Installer Desktop2 1.0.0 — disponibile per la 0.6.1

Pacchetto di prima installazione per un secondo PC indipendente: copia binario verificato, credenziali/identita nuove, root progetti, avvio al login, configurazione Cloudflare locale e comandi di verifica/arresto. Suite Windows isolata superata; Cloudflare remoto, plugin ChatGPT, secondo PC fisico e reboot ancora da collaudare. Nessuna installazione o modifica del servizio Desktop1. [Procedura e verbale](../docs/mcp-agent/INSTALLER-DESKTOP2-1.0.0.md). Agente operativo resta 0.6.1; versione installer separata 1.0.0.

# Windows MCP 0.6.1 — installata e collaudata, 31 ottobre 2026

RPC Codex reattive e scheduler indipendente; nessun limite temporale dei turni e nessun avviso programmato. Coda solo operativa, terminali nell'archivio privato locale via activity_history; risorse condivise read/write e repository Git comuni riconosciuti. Registro significativo su Git distinto dai log tecnici. [Specifica](../docs/mcp-agent/IMPROVEMENTS-0.6.1.md), [verbale](../docs/mcp-agent/VALIDATION-0.6.1-20261031.md), [registro](../docs/mcp-agent/ACTIVITY.md), [regola di progetto](../docs/mcp-agent/PROJECT-RECORD.md).

Test Windows/vet/build, prova simulata di 310 secondi, MCP isolato, backup/rollback, installazione e turno Codex reale da questa conversazione superati. Codice binario 164cb980bd1a936749d41b8692979e98b929cb04, SHA-256 9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1. Backup D:\Jarvis\Backups\mcp-0.6.1-20261031. Prova costi invariata; letture storiche non sono valori correnti. Altri PC, reboot fisico e refresh catalogo diretto client restano da verificare.

## Storico precedente

# Jarvis Windows MCP Agent 0.6.0

Installata su Desktop1 e collaudata da una nuova chat ChatGPT il 31 ottobre 2026. Coda persistente, elenco/cancellazione/riordino e letture concorrenti; progetti indipendenti in parallelo, percorsi sovrapposti protetti. Test Windows/vet/build, backup/rollback, installazione e turno reale Codex accodato con quota finale verificati.

[Stato](../docs/mcp-agent/STATUS.md), [verbale 0.6.0](../docs/mcp-agent/VALIDATION-0.6.0-20261031.md), [contratto coda](../docs/mcp-agent/QUEUE-0.6.0.md), [istruzioni Windows](AGENTS.md), [continuita](CONTINUITY.md). La versione 0.5.9 ha introdotto il riepilogo diretto codex_status e la lettura finale automatica, qui preservati.

## Runtime

machine_info e project_catalog identificano computer e progetti. File/cartelle: file_list/read/search/write, directory_create, path_move e file_restore. Comandi: command_start restituisce queue_id; queue_list espone poi job_id per job_status/list/cancel. codex_turn segue lo stesso percorso con turn_id. queue_cancel rimuove solo attivita in attesa, queue_move ne cambia l'ordine, queue_resume richiede revisione dopo pausa. Ogni mutazione richiede request_id e, per i file, hash atteso. Nessuna shell arbitraria, sovrascrittura implicita o bypass dei percorsi protetti. Job, ricevute e identita persistono oltre una chat o un riavvio. screen_capture restituisce immagini MCP dirette, con dimensioni, monitor e origine.

Ponte Codex locale verificato per avvio, storico, risposta reale, modifica file, steer, interrupt e ripresa dopo riavvio. Account ChatGPT esistente, quota inclusa, niente chiavi API o acquisti. Riscontro locale dei costi associato ad account/PC, con validita massima sette giorni; dati API e quota ricontrollati a ogni turno. Routing fra due istanze collaudato; altri PC fisici devono essere configurati e autenticati.

Il catalogo server aggiunge cinque queue_* ai precedenti strumenti; in questa nuova chat i comandi coda sono stati verificati tramite compatibilita. Il refresh dei cinque strumenti diretti nel client non e dimostrato. Resta disponibile la compatibilita task_start con operation=runtime e request={operation:...,machine_id:...}. [Istruzioni client](../docs/mcp-agent/CLIENT-INSTRUCTIONS-0.5.7.md). Non promettere disponibilita in qualunque chat o in Voice senza il relativo collaudo.

## Controllo desktop senza coordinate

```json
{"operation":"desktop_inspect"}
{"operation":"desktop_action","desktop":{"snapshot_id":"<nuovo snapshot>","element_id":"<ID esatto>","action":"invoke"}}
{"operation":"desktop_action","desktop":{"snapshot_id":"<nuovo snapshot>","element_id":"<campo esatto>","action":"set_text","text":"Testo richiesto"}}
```

Richiamare tramite task_start e monitorare agent_status. Azioni: invoke, set_text, select, toggle secondo i pattern disponibili.
set_text sostituisce l'intero campo, non simula digitazione: richiede un'intenzione esplicita di sostituzione. Il contenuto originale non viene restituito.
Ogni snapshot dura 90 secondi ed è monouso. Ripetere l'ispezione dopo ogni azione o cambiamento.
I controlli sono individuati tramite UI Automation e, per Edit/Button nativi noti, HWND/classe/PID verificati. Si usano API dirette al controllo, senza muovere il cursore, SendInput/SendKeys o coordinate.
Se finestra, controllo, posizione, contenuto Edit o primo piano cambiano, l'azione viene rifiutata.
Non scegliere fra pulsanti omonimi senza contesto sufficiente; il solo nome non identifica il bersaglio.
Nessun retry automatico dopo esiti incerti; il dispatch non prova da solo l'effetto applicativo.

Il desktop deve essere sbloccato. Password, controlli non supportati, tasti/scorciatoie globali, trascinamenti e mouse libero sono esclusi; le pagine web non sono garantite.
UIA dipende dall'applicazione: non è una garanzia di infallibilità.
Configurazione locale JARVIS_DESKTOP_ENABLED=1; per disabilitare impostare 0 e riavviare in modo controllato.

## Compatibilita con i task Go 0.5.5/0.5.6

```json
{"operation":"go_test","project":"windows-mcp-agent"}
{"operation":"go_build","project":"windows-mcp-agent"}
```

go_test usa go test ./...; go_build compila in Staging/jarvis-mcp.exe. Timeout 120 s e annullamento dell'albero dei processi Windows, ereditati dalla 0.5.5.
Per test senza cache: go test -count=1 ./....
Il codice dei moduli gira con i permessi dell'agente: la root autorizzata non è una sandbox del codice.
L'output dei task Go conserva gli ultimi 12000 byte, dopo acquisizione in memoria.

## Installazione verificata

- Binario operativo D:\Jarvis\jarvis-mcp.exe, fuori da Staging.
- Sorgenti D:\Jarvis\MCP-Source\windows-mcp-agent; root autorizzata D:\Jarvis\MCP-Source.
- Configurazione D:\Jarvis\Config; credenziali fuori da Git.
- Endpoint locale 127.0.0.1:8765/mcp; Cloudflare Tunnel e Access esistenti.
- Backup precedente 0.5.9 in D:\Jarvis\Backups\mcp-0.6.0-20261031, con configurazione e snapshot dello stato; aggiornamento e rollback isolato collaudati.
- Go 1.27.0 Windows/amd64. Nessuna compilazione tramite GitHub Actions.

Gli script bootstrap in Agent\ sono esempi: il percorso operativo verificato è quello sopra.
La cattura MCP diretta e stata acquisita e mostrata in chat; l'avvio automatico dopo reboot resta da verificare/implementare.
Seguire [RELEASE_POLICY](../docs/mcp-agent/RELEASE_POLICY.md).




