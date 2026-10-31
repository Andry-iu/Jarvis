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
