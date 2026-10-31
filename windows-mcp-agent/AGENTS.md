# Jarvis Windows MCP Agent — istruzioni di progetto

Questo sottoprogetto e l'agente Windows Jarvis MCP / Jarvis-Desktop1, distinto dall'app Android nello stesso repository. Le regole Android su APK, firma, cover, Gradle e cloud Android non definiscono l'ambiente di questo agente: l'utente ha autorizzato sviluppo, test e installazione su Desktop1.

Prima di intervenire leggere README.md, CONTINUITY.md, CHANGELOG.md e ../docs/mcp-agent/{STATUS,RELEASE_POLICY,QUEUE-0.6.0,VALIDATION-0.6.0-20261031}.md. Verificare identita e versione correnti con machine_info; non assumere che gli ID dei verbali siano ancora il bersaglio corretto. Consultare coda, job, ricevute e sessioni del progetto esatto prima di nuove mutazioni.

- Branch di lavoro autorizzato: reconcile-windows-mcp-agent-20261031. Verificare il remoto attuale e le modifiche locali; nessun merge implicito su main, force push o modifica Android.
- Sorgenti operativi D:\Jarvis\MCP-Source\windows-mcp-agent; root Git D:\Jarvis\MCP-Source. Checkout sparse: includere windows-mcp-agent e docs/mcp-agent. I documenti remoti sono la fonte se non presenti localmente.
- Ogni rilascio richiede sorgenti/test/documenti/versione/checksum su Git remoto, test/vet/build Windows, backup verificato, rollback isolato, installazione e collaudo MCP. Distinguere ciascun esito. Non sostituire l'agente funzionante prima di queste protezioni.
- Conservare backup, stato/identita/ricevute, update.go.bak e stash backup update.go locale 20261031. Non usare git clean o cancellazioni ricorsive indiscriminate. Staging e binari non vanno aggiunti a Git.
- Zero costi aggiuntivi: niente API key, crediti/acquisti/ricariche, upgrade, tier a pagamento o fallback. Non modificare/estendere prove o guardie per far passare un turno. Saldo null significa sconosciuto. Prova costi privata con account/PC e scadenza massima sette giorni; mai segreti o dati account in Git/chat/log.
- Consultazione quota: codex_status, senza inferenza. Prima del turno controllare disponibilita all'avvio effettivo. Dopo ogni turno attendere jarvis/usage/final_usage e riferire il dato corrente o l'errore, senza confronti o riuso di letture precedenti.
- command_start/codex_turn restituiscono queue_id: consultare queue_list per job_id/turn_id e gli eventi esatti. queue_cancel riguarda solo attesa; lavori attivi richiedono job_cancel/codex_interrupt e verifica finale. Riordino non interrompe il lavoro attivo; rispettare depends_on.
- Un lavoro per progetto/percorso sovrapposto, fino a quattro progetti indipendenti. Letture disponibili durante elaborazioni; file in scrittura possono essere indisponibili. Coordinare risorse globali e processi esterni. Nessun replay automatico dopo disconnessione/esito incerto.
- Usare runner fisso e API file quando sufficienti. update_prepare/update_execute servono per distribuzione e script di collaudo/diagnosi non eseguibili con i profili, con byte/hash esatti e verifica successiva. Non usarli per aggirare protezioni o decisioni sulle approvazioni.
- Catalogo precedente: task_start con operation=runtime e request completa (operation e machine_id). Non dichiarare aggiornato il catalogo client senza verificarlo. Non promettere tutte le chat/Voice, altri PC o reboot sulla sola base delle prove locali.
- Non riprendere NestingOpt, Android o configurare altri computer senza la relativa richiesta.


## Ambito approvato 0.6.1
Leggere docs/mcp-agent/IMPROVEMENTS-0.6.1.md e PROJECT-RECORD.md (percorsi relativi alla root Git). Nessuno stop temporale o avviso programmato per i turni Codex. queue_list e operativo; activity_history recupera terminali. Ogni progetto mantiene su Git un registro curato delle attivita significative aggiornato insieme a commit/rilasci, mai esportazione automatica dello stato privato. Dichiarare risorse condivise note, preservando parallelismo dei lavori indipendenti.
