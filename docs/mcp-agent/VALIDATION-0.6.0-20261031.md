# Windows MCP 0.6.0 — installata e collaudata, 31 ottobre 2026

## Identita e provenienza

- Computer verificato dal plugin: DESKTOP-1ESSB0K; machine_id 1b927a030fa3b7dfa15c40cfccb4ff60.
- Repository Andry-iu/Jarvis, branch reconcile-windows-mcp-agent-20261031; nessun merge a main e nessuna modifica Android.
- Codice incorporato nel binario: a63fb7b36dce3e543c28194ae80f8519524949fc.
- Checkout al momento dell'installazione: c01400edcd185b7d6b6bde114b8064931f742195; differenza solo nello script di collaudo.
- Binario D:\Jarvis\jarvis-mcp.exe, 12.042.752 byte, SHA-256 68D7D4DB1B63576B81EC67E7DCAB137763F7BE38A6B841BA5A8B46F4B5A1DEFE.
- Build/test eseguiti su Desktop1, Windows AMD64, Go 1.27.0. Nessuna build GitHub Actions.

## Evidenze

| Verifica | Esito |
|---|---|
| Stato iniziale reale | 0.5.9 operativa, candidata 0.6.0 gia su Git, tracciati puliti, nessun job attivo. Staging e update.go.bak non tracciati conservati. |
| Test Windows | Job 97bb6103c804b2e832aca4388576eedf completed, exit 0, 12.469s. Coperti ordine, dipendenze, idempotenza, riavvio, progetti sovrapposti/indipendenti e stato finale Codex. |
| Vet/build | Job 0c3da6f716a112b603a759372717f78d e a7cb3a52916a2116bd0c52010a061712 completed, exit 0. |
| Prova MCP isolata | Riordino, cancellazione in attesa, letture file/quota durante lavoro, serializzazione stesso progetto, riavvio senza replay, ripresa esplicita: PASS in diagnostics/v60-queue-test.log. |
| Correzione del collaudo | Primo test fermato da NativeCommandError PowerShell durante taskkill di un figlio gia terminato. Script corretto con cattura esplicita stdout/stderr e attesa del processo; ogni esecuzione usa home e fixture nuove. Nessuna modifica al controllo costi o al codice runtime. Seconda prova completa PASS. |
| Backup | D:\Jarvis\Backups\mcp-0.6.0-20261031: 0.5.9 precedente, configurazioni, startup, prova costi e snapshot JSON dello stato; copie verificate per hash. |
| Rollback | Eseguiti candidato 0.6.0 e backup 0.5.9 sullo stesso percorso isolato, health e MCP verificati: ROLLBACK_REHEARSAL_PASS. |
| Installazione | diagnostics/v60-deploy.log: INSTALLED_PID 504848, hash atteso e PASS installed_health_and_mcp alle 17:54 UTC. machine_info dal plugin conferma 0.6.0 e identita invariata. |
| Riordino dalla chat | Progetto windows-mcp-agent/Staging/continuity-060-20261031: A in esecuzione, C spostata prima di B, B cancellata senza job_id; A e C poi completed. |
| Letture durante lavoro | file_read e codex_status riusciti mentre A era running, senza aspettare i 60 secondi della fixture. |
| Progetto indipendente | git_status su Staging/runtime-fixture: job 834b49c644da0610635904914070d99b, 17:56:36.674–36.774 UTC, mentre A durava 17:55:47.174–17:56:48.205 UTC. |
| Turno Codex accodato | queue_id 9888c9b068dd5f51b3855e5b7e76e389; avviato dopo A e C. Thread 01a1931a-108e-71e1-bbe8-fb6f44f680d6, turn 01a1931b-69de-7c12-8743-ce5e40a8a455; risposta JARVIS_QUEUE_060_OK, completed, nessuna approvazione pendente. |
| Lettura finale | jarvis/usage sequence 21, final_usage presente, usage_pending=false. Lettura 17:56:53 UTC: 87% residuo, saldo servizio "0" known, reset 2026-11-04T13:39:22Z. Il saldo prima del turno era null/unknown: entrambi sono dati osservati, mai dedotti dalla prova costi. |
| Coda finale | Tutte le cinque voci del collaudo concluse o cancellate, nessun lavoro attivo/in attesa e coda sana. |
| Automazione | Verificata esistente e abilitata "Consumo quota Codex", ID 6ae61868fe2081919636cd881282ad27, giornaliera Europe/Rome. Nessun duplicato o modifica; next_run_time non valorizzato, quindi non e dimostrata l'esecuzione futura. |

## Costi e limiti

codex_status legge account/quota senza inferenza. Prima dell'avvio effettivo Codex vengono ricontrollati account ChatGPT, piano, credito, quota e blocchi; lettura finale preservata anche per errori/interruzioni nei test RPC. La guardia e separata dai dati osservati, quindi quota bassa resta leggibile anche quando l'avvio e negato.

Riscontro locale indipendente per il solo saldo null: associazione account/PC e validita massima sette giorni; scadenza invariata 7 novembre 2026 15:55 UTC (16:55 Europe/Rome). Nessuna proroga, acquisto, ricarica, API key o fallback a consumo. Una lettura numerica zero non deve trasformare le successive letture null in zero.

La coda protegge percorsi di progetto sovrapposti e consente fino a quattro progetti indipendenti. Le risorse globali e i processi esterni richiedono coordinamento esplicito. Elenchi directory non sono snapshot atomici. Cancellazione in attesa distinta da job_cancel/codex_interrupt.

Nuovi strumenti queue_* verificati in questa nuova conversazione tramite task_start operation=runtime, compatibilita del catalogo client gia esistente. Questo non prova il refresh dei cinque strumenti diretti nel client. Voice, altri PC fisici e avvio dopo reboot del PC restano da collaudare. device_list mostra soltanto il dispositivo locale. MSBuild/CMake/cl non disponibili nel PATH; non installati. Clone NestingOpt resta sospeso.

## Ripresa

Leggere windows-mcp-agent/AGENTS.md, CONTINUITY.md, STATUS.md e QUEUE-0.6.0.md. Prima machine_info, project_catalog, queue_list/job_list e codex_threads del progetto esatto. Con catalogo precedente usare task_start operation=runtime; non ripetere operazioni incerte. Mantenere configurazioni e stato fuori da Git, preservare backup e ricevute. Il checkout puo essere sparse: includere docs/mcp-agent oltre a windows-mcp-agent per avere la documentazione locale.

## Controllo finale aggiuntivo

diagnostics/v60-final-check.log (31 ottobre 18:01:31 UTC): hash operativo invariato, listener PID 504848 con TokenElevation verificato true tramite token del processo, 43 strumenti server inclusi cinque queue_*, 67 file JSON nel backup, backup eseguibile 0.5.9 corretto, configurazioni/prova costi presenti, nessun listener sulle porte 8766/8767 e update.go.bak conservato. Documentazione docs/mcp-agent aggiunta al checkout sparse.

HEAD e origin coincidevano con 6ffed432782f1dda152dccbd6c859fe115f84df6, tracciati puliti; le successive modifiche riguardano soltanto questo verbale e lo script riproducibile scripts/verify-release-060.ps1. Dopo il checkout della documentazione un riepilogo project_status ha temporaneamente riportato 20 modifiche; verifiche complete nelle due modalita Git e successivo project_status alle 18:03:34 UTC hanno confermato zero modifiche tracciate, senza ripristinare o riscrivere sorgenti. Non e necessario cambiare configurazione Git sulla base di quella lettura transitoria.

Il vecchio job .NET 8bbda9f9e74a9864c141b979aec7840a e ancora leggibile dopo installazione, completed/exit 0 (nessun nuovo build .NET eseguito). Le ricevute e il collaudo coda corrente sono persistiti nello stato privato. La successiva sincronizzazione finale deve ancora verificare HEAD/origin dopo gli aggiornamenti documentali.
