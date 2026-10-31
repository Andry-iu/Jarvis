# Continuita Windows MCP — 0.5.7, 31 ottobre 2026

Leggere [STATUS](../docs/mcp-agent/STATUS.md), [verbale](../docs/mcp-agent/VALIDATION-0.5.7-20261031.md), [contratto](../docs/mcp-agent/RUNTIME-0.5.7.md), [istruzioni client](../docs/mcp-agent/CLIENT-INSTRUCTIONS-0.5.7.md) e [regola release](../docs/mcp-agent/RELEASE_POLICY.md). Questo agente Windows e distinto dall'app Android.

Branch reconcile-windows-mcp-agent-20261031. Agente operativo 0.5.7, eseguibile D:\Jarvis\jarvis-mcp.exe, source commit a10c9bdb1a87362bf904fbc24b697489f9b7080f, SHA-256 4A5A56B735879B256EA15A4F3B5C99568BC996D67BC5D4663CCAFFA8488CDD5F. Fonte D:\Jarvis\MCP-Source\windows-mcp-agent; root autorizzata D:\Jarvis\MCP-Source; stato D:\Jarvis\State\runtime-v1. Configurazione e credenziali locali in D:\Jarvis\Config, mai in chat/Git.

Verificati installazione, amministratore, riconnessione, file/cartelle, ripristino, immagini MCP dirette mostrate in chat, test/build Go, build .NET 9, persistenzа e routing fra due istanze isolate. Backup 0.5.6 e rollback provati in D:\Jarvis\Backups\mcp-0.5.7-20261031. Preservare tutti i backup precedenti, update.go.bak e stash backup update.go locale 20261031. Non ripetere collaudi superati senza una ragione tecnica e non modificare l'agente funzionante senza backup e rollback verificati.

Per riprendere, leggere agent_status e machine_info; non fissare l'identita da questa pagina. Nel client con vecchio catalogo usare task_start con operation=runtime e request contenente operation e machine_id; project_catalog mostra i progetti, job_list/request_status lo storico. Non inviare comandi normali attraverso update_prepare/update_execute quando il runner controllato e sufficiente.

Punto aperto: Codex App Server funziona per account/quote e lista sessioni, ma balance=null blocca l'inferenza. UI osservata: 0 crediti e 88% quota inclusa residua; ricarica automatica non verificata. Il tentativo di accettare saldo nullo/testare un turno e stato respinto dalla revisione automatica; codice ripristinato rigorosamente chiuso su saldo sconosciuto. Nessuna inferenza avviata. Attendere verifica/autorizzazione appropriata, senza aggiramenti, acquisti o chiavi API.

Servono inoltre aggiornamento catalogo client (38 strumenti sul server, dieci precedenti esposti in questa chat), verifica nuova chat/Voice, e configurazione esplicita di altri PC reali. Non dichiarare questi punti completati. Avvio dopo reboot non verificato. MSBuild/CMake/cl non nel PATH; .NET SDK 9.0.317 disponibile e compilazione riuscita. Clone NestingOpt ancora sospeso.
