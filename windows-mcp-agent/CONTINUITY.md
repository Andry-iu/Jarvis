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

# Continuita Windows MCP — 0.6.0 installata e collaudata, 31 ottobre 2026

Leggere AGENTS.md di questa cartella e docs/mcp-agent/{STATUS,QUEUE-0.6.0,VALIDATION-0.6.0-20261031,RELEASE_POLICY}.md. Agente Windows distinto da Android. Branch reconcile-windows-mcp-agent-20261031. Ripresa verificata in una nuova chat con Jarvis-Desktop1.

Coda persistente per progetto, riordino/cancellazione in attesa e letture concorrenti operativi. Percorsi sovrapposti esclusi, progetti indipendenti paralleli (massimo quattro). command_start/codex_turn accodano: leggere queue_list per job_id/turn_id. Cancellazione in attesa distinta da interruzione attiva; nessun replay automatico. Dipendenze esplicite e pausa dopo errore/riavvio. Per catalogo precedente usare task_start operation=runtime e request completa.

Verificati test Windows/vet/build, collaudo isolato con riavvio senza replay, backup/rollback e installazione. Poi dalla chat: riordino C prima di B, B cancellata senza avvio, letture durante A, progetto indipendente concluso durante A, turno Codex accodato completato e final_usage presente. Sorgente binario a63fb7b36dce3e543c28194ae80f8519524949fc; installazione da c01400edcd185b7d6b6bde114b8064931f742195 dopo correzione del solo script di collaudo. SHA-256 68D7D4DB1B63576B81EC67E7DCAB137763F7BE38A6B841BA5A8B46F4B5A1DEFE. Backup D:\Jarvis\Backups\mcp-0.6.0-20261031.

Quota finale storica 17:56:53 UTC: 87%, saldo servizio "0" noto, reset 4 novembre 13:39:22 UTC. Le letture precedenti con saldo null restano sconosciute; non sostituirle con zero. Sempre codex_status per il dato attuale, senza inferenza. Guardia iniziale e lettura finale conservate; nessun acquisto/ricarica/API. Prova costi associata account/PC per saldo null non prorogata, scadenza 7 novembre 15:55 UTC. Automazione quota giornaliera esistente verificata abilitata, ID 6ae61868fe2081919636cd881282ad27; nessun duplicato, esecuzione futura non verificata.

Restano da verificare Voice, altri PC fisici e reboot del PC; device_list mostra solo locale. Il percorso di compatibilita funziona in questa chat, ma non dimostra il refresh dei cinque strumenti diretti queue_* nel client. MSBuild/CMake/cl non nel PATH. NestingOpt sospeso. Checkout sparse: includere docs/mcp-agent oltre a windows-mcp-agent. Conservare Staging, update.go.bak, stash e backup precedenti.

## Storico dei rilasci precedenti

# Continuita Windows MCP — 0.5.9 installata e collaudata, 31 ottobre 2026

Autorizzato dall'utente: lettura iniziale per ammissibilita (gia esistente), lettura finale automatica senza confronti, consultazione diretta su richiesta e breve dato attuale nel riepilogo di ogni elaborazione Codex. Il comando diretto e codex_status; non crearne duplicati. La risposta precedente che lo dichiarava assente era errata.

Codice candidato: riepilogo usage in codex_status; lettura asincrona unica dopo turn/completed, evento persistente jarvis/usage, final_usage e usage_pending in codex_events. Non attendere RPC nel reader. Non nascondere dati disponibili quando inference_allowed=false. Saldo null rimane sconosciuto. Quote riferite all'account, non al singolo progetto. Nessun acquisto, fallback API o modifica delle prove costi. Guardie 0.5.8 conservate.

Stato corrente: test Windows/vet/build, backup/rollback e installazione superati; turno reale con lettura finale jarvis/usage verificato. Sorgente binario 924f23aa6409f77342d964cc225991ecb4296002; SHA-256 CB7B9B3E0C3A9CA9133E738E0412CD518AF6BEA7E79FEE2C65830776635540C5. Ultima lettura 87% disponibile, saldo crediti API sconosciuto, reset 4 novembre 2026 13:39 UTC. Non trattare questi numeri come stato corrente senza nuova lettura. Seguire docs/mcp-agent/USAGE-0.5.9.md e RELEASE_POLICY.md, preservare backup/stato/identita. Branch reconcile-windows-mcp-agent-20261031.

Modifica successiva approvata in Voice: coda per progetto, consultazione/cancellazione/riordino e letture in parallelo, specifica in docs/mcp-agent/QUEUE-SPEC.md. Non inclusa nella 0.5.9; implementazione successiva in corso. Rischio attuale: sessioni Codex diverse possono toccare lo stesso progetto; il runner comandi limita un job attivo per PC e ogni sessione Codex limita un turno attivo.

# Continuita Windows MCP — 0.5.8, 31 ottobre 2026

Stato corrente: [STATUS](../docs/mcp-agent/STATUS.md), [collaudo Codex 0.5.8](../docs/mcp-agent/CODEX-0.5.8.md). Branch reconcile-windows-mcp-agent-20261031. Binario operativo D:\Jarvis\jarvis-mcp.exe, versione 0.5.8, elevazione verificata. Source commit 6b5530e8347502f35511aabdf696c58877836cee; SHA-256 04F369F365816BC076CBCE3F89589BAC63FE628385FFF8B96316E2E79115C1BE. Modifiche documentali successive non cambiano il codice compilato.

L'utente ha aggiornato il plugin: 38 strumenti diretti verificati. Ha fornito la schermata con 88% quota residua, 0 crediti e ricarica automatica disattivata. Config/codex-cost-proof.json conserva solo il riscontro locale e un hash dell'account, mai token/email in Git. Per il saldo API nullo la 0.5.8 richiede riscontro valido per account/PC, zero crediti e ricarica disattivata; API hasCredits/unlimited, piano, limiti e quota vengono ricontrollati prima dei turni. Riscontro corrente valido fino al 7 novembre 2026 15:55 UTC; scaduto/ignoto/cambio account la guardia si chiude. Non acquistare crediti, usare API key o aggirare la guardia.

Collaudi reali Codex: risposta e storico, steer, interruzione confermata, ripresa dopo riavvio, modifica file verificata tramite MCP. Dopo installazione Codex ha risposto JARVIS_INSTALL_OK dal plugin. Backup 0.5.7 e 16 file di stato verificati in D:\Jarvis\Backups\mcp-0.5.8-20261031; rollback isolato superato. Identita stabile e job .NET precedente conservati. Conservare tutti i backup, update.go.bak e stash backup update.go locale 20261031.

Riprendere leggendo machine_info e project_catalog, poi job_list e codex_threads del progetto esatto. Non duplicare operazioni con esito incerto. Altri PC fisici, nuova chat/Voice e avvio dopo reboot non collaudati. Clone NestingOpt ancora sospeso. Lo storico 0.5.7 seguente descrive il precedente blocco, risolto nella 0.5.8 dopo la nuova prova fornita dall'utente.

# Continuita Windows MCP — 0.5.7, 31 ottobre 2026

Leggere [STATUS](../docs/mcp-agent/STATUS.md), [verbale](../docs/mcp-agent/VALIDATION-0.5.7-20261031.md), [contratto](../docs/mcp-agent/RUNTIME-0.5.7.md), [istruzioni client](../docs/mcp-agent/CLIENT-INSTRUCTIONS-0.5.7.md) e [regola release](../docs/mcp-agent/RELEASE_POLICY.md). Questo agente Windows e distinto dall'app Android.

Branch reconcile-windows-mcp-agent-20261031. Agente operativo 0.5.7, eseguibile D:\Jarvis\jarvis-mcp.exe, source commit a10c9bdb1a87362bf904fbc24b697489f9b7080f, SHA-256 4A5A56B735879B256EA15A4F3B5C99568BC996D67BC5D4663CCAFFA8488CDD5F. Fonte D:\Jarvis\MCP-Source\windows-mcp-agent; root autorizzata D:\Jarvis\MCP-Source; stato D:\Jarvis\State\runtime-v1. Configurazione e credenziali locali in D:\Jarvis\Config, mai in chat/Git.

Verificati installazione, amministratore, riconnessione, file/cartelle, ripristino, immagini MCP dirette mostrate in chat, test/build Go, build .NET 9, persistenzа e routing fra due istanze isolate. Backup 0.5.6 e rollback provati in D:\Jarvis\Backups\mcp-0.5.7-20261031. Preservare tutti i backup precedenti, update.go.bak e stash backup update.go locale 20261031. Non ripetere collaudi superati senza una ragione tecnica e non modificare l'agente funzionante senza backup e rollback verificati.

Per riprendere, leggere agent_status e machine_info; non fissare l'identita da questa pagina. Nel client con vecchio catalogo usare task_start con operation=runtime e request contenente operation e machine_id; project_catalog mostra i progetti, job_list/request_status lo storico. Non inviare comandi normali attraverso update_prepare/update_execute quando il runner controllato e sufficiente.

Punto aperto: Codex App Server funziona per account/quote e lista sessioni, ma balance=null blocca l'inferenza. UI osservata: 0 crediti e 88% quota inclusa residua; ricarica automatica non verificata. Il tentativo di accettare saldo nullo/testare un turno e stato respinto dalla revisione automatica; codice ripristinato rigorosamente chiuso su saldo sconosciuto. Nessuna inferenza avviata. Attendere verifica/autorizzazione appropriata, senza aggiramenti, acquisti o chiavi API.

Servono inoltre aggiornamento catalogo client (38 strumenti sul server, dieci precedenti esposti in questa chat), verifica nuova chat/Voice, e configurazione esplicita di altri PC reali. Non dichiarare questi punti completati. Avvio dopo reboot non verificato. MSBuild/CMake/cl non nel PATH; .NET SDK 9.0.317 disponibile e compilazione riuscita. Clone NestingOpt ancora sospeso.



