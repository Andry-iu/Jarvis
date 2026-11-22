> **VERIFICA SUCCESSIVA: 0.9.6 installata e catalogo ChatGPT aggiornato su Desktop1.** `ui_session_open`, `ui_sequence`, `ui_mouse_screen` e altri strumenti sono disponibili e invocati **direttamente** senza `machine_id` o `session_token`; il disegno completo in Paint e stato effettivamente realizzato con piu chiamate. Il blocco di esposizione degli strumenti diretti documentato sotto e **storico e risolto su Desktop1**. Per Desktop2 e verificato il backend senza ID, non un collaudo altrettanto dettagliato dello schema ChatGPT post-refresh. Restano prompt ChatGPT per operazioni sensibili e difetti di affidabilita UI. Vedere [STATUS-CONSOLIDATO-0.9.6-20261122.md](STATUS-CONSOLIDATO-0.9.6-20261122.md).

> **Aggiornamento 0.9.5:** il token conversazionale `session_token` e stato rimosso. Il problema di esposizione degli strumenti UI in ChatGPT e indipendente dal token: il catalogo MCP server continua a esporre le operazioni autonome e il client potrebbe richiedere un refresh. Nella diagnostica usare chiamate senza `session_token`.

# MCP direct UI tools — diagnosi catalogo ChatGPT (22 novembre 2026)

## Risultato osservato

Jarvis Agent Desktop1 **0.9.4**, online ed elevato, pubblica nel catalogo **`tools/list`** le operazioni indipendenti `ui_session_open`, `ui_sequence`, `ui_session_close`, `ui_mouse`, `ui_mouse_screen`, `window_list`, `window_inspect`, `screen_capture`, `ui_keys`, `ui_action`. Ogni operazione ha un proprio `inputSchema` e i controlli gia presenti di identita macchina, sessione, foreground, conferme e anti-replay. La sequenza ammette `settle_ms` 0..500 ms.

**Tuttavia**, il set di funzioni attualmente visibile da ChatGPT attraverso la connessione `Jarvis_Desktop1` contiene `task_start`, `screen_capture`, `window_list`, `window_inspect` ecc., ma **non** `ui_sequence`, `ui_session_open`, `ui_mouse`, `ui_mouse_screen`. Le chiamate sono state pertanto inoltrate tramite `task_start(operation=runtime,request={...})`; alcune sono riuscite e altre sono state respinte **prima che comparisse una ricevuta Jarvis**.

Un messaggio di blocco generico OpenAI **non rivela la specifica policy o il componente che l'ha generato**. L'assenza di una ricevuta di runtime e sufficiente a dire che l'azione non e stata registrata da Jarvis, ma **non e sufficiente** a identificare con certezza il livello esatto del rifiuto (host, gateway o filtro preventivo).

## Conseguenza architetturale

**Non aggiungere nuovi duplicati degli strumenti UI al codice server**: il catalogo MCP e gia disponibile. La correzione risiede nel **refresh della registrazione della connessione MCP nel client ChatGPT**, in modo da rendere visibili gli strumenti dichiarati. Non modificare policy Cloudflare o saltare controlli di sicurezza per questo problema.

Non e disponibile in questa sessione uno strumento di amministrazione che permetta di aggiornare forzatamente i manifest e le funzioni della connessione Jarvis installata su ChatGPT. Non dare per scontato che un riavvio di Jarvis aggiorni automaticamente uno schema gia memorizzato dal client.

## Verifica da eseguire dopo refresh autorizzato della connessione

1. Aggiornare/ricollegare la connessione Jarvis Desktop1 dalle impostazioni Plugin/MCP dell'app, preservando l'URL Cloudflare esistente e le condizioni Access. Non reinstallare o sostituire l'agente Windows solo per cambiare lo schema lato ChatGPT.
2. Rieseguire il confronto dei nomi disponibili nella connessione ChatGPT: deve comparire una funzione diretta distinta per ciascuna UI richiesta.
3. Eseguire `machine_info` e `screen_capture` in sola lettura, poi `ui_session_open` e una singola `ui_sequence` non distruttiva su Paint, dopo aver identificato finestra e coordinate in uno screenshot fresco. Verificare esito visivo con nuova cattura.
4. Quando i comandi funzionano, ripetere il benchmark completo: tela bianca, rettangolo, 4 cerchi rossi e testo centrale blu `Jarvis`. Misurare tempo `setup`, `ui_sequence`, `capture`, `totale`; usare inizialmente `settle_ms=300` e ridurre solo dopo un esito visivo corretto.

## Regressione automatica

`windows-mcp-agent/mcp_direct_ui_catalog_test.go` controlla che tutti gli strumenti UI diretti siano inclusi nel catalogo effettivamente pubblicato `tools/list` con i campi e i vincoli previsti. Superare il test conferma il **server**, non aggiorna ne convalida il set di strumenti esposto dal **client ChatGPT**.
