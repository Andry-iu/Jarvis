# Quota e crediti MCP — 0.5.9 installata e collaudata

31 ottobre 2026. Sorgente binario: 924f23aa6409f77342d964cc225991ecb4296002.
Binario operativo D:\Jarvis\jarvis-mcp.exe, 11.985.408 byte, SHA-256 CB7B9B3E0C3A9CA9133E738E0412CD518AF6BEA7E79FEE2C65830776635540C5.
Branch reconcile-windows-mcp-agent-20261031.

## Verifiche effettive
- Test Windows via command_start/go_test: completed, exit 0, 7.411s. Processo RPC simulato per completed/interrupted/failed, notifica duplicata, errore servizio, quota esaurita e saldo null; nessuna inferenza per questi test.
- go_vet e go_build: completed, exit 0. Compilazione sul PC remoto DESKTOP-1ESSB0K con Go 1.27.0.
- Parser PowerShell del deploy: PASS. Errore di escaping nel primo script candidato rilevato prima dell'esecuzione, corretto e verificato. Batch preparato precedente mai eseguito.
- Backup 0.5.8, configurazioni, prova costi e snapshot JSON dello stato in D:\Jarvis\Backups\mcp-0.5.9-20261031; verifica hash. Rollback isolato 0.5.9/0.5.8: PASS.
- Installazione: health e MCP PASS, machine_info restituisce 0.5.9 e identita precedente invariata.
- Comando diretto codex_status dal plugin: riepilogo usage con 87% disponibile e reset 2026-11-04T13:39:22Z. Saldo API null mostrato come unknown, senza trasformazione in zero.
- Turno reale dal plugin: risposta JARVIS_USAGE_OK e turn/completed. Un solo evento jarvis/usage successivo, available=true, phase=after, final_usage presente e usage_pending=false. Lettura finale 2026-10-31T16:59:20Z: 87% disponibile, crediti numerici sconosciuti, nessun fallback/acquisto.
- Sorgenti pubblicati su Git; verbale aggiornato dopo il collaudo. La coda e il riordino sono la modifica successiva approvata, ancora da implementare; vedere QUEUE-SPEC.md.

## Uso
codex_status e il comando diretto gia disponibile per richieste su quota e crediti. Non avvia sessioni o inferenze. usage.windows espone percentuali residue e reset, usage.credits mantiene saldo e stato noto/sconosciuto.
Il controllo prima dei turni resta quello rigoroso della 0.5.8. Non garantisce il consumo futuro di tutto il lavoro.
A fine elaborazione Codex attendere jarvis/usage del turno esatto in codex_events (anche final_usage/usage_pending) e riferire brevemente quota residua, crediti se noti e prossimo reset in ora locale. Nessun confronto storico.
La lettura finale e asincrona per non bloccare il reader delle risposte RPC; funziona anche per interrupted/failed. Se fallisce, available=false e quota_read_failed. Non usare vecchi valori come attuali e non nascondere la quota letta solo perche inference_allowed=false.
I dati sono relativi all'intero account, non al singolo progetto. Lo storico eventi ha la retention gia documentata.
Le descrizioni MCP sono aggiornate, nomi/parametri esistenti invariati. Il file delle istruzioni client non prova un aggiornamento del plugin installato. Altre chat/Voice richiedono strumenti disponibili e PC raggiungibile; non sono state collaudate in questo rilascio.
