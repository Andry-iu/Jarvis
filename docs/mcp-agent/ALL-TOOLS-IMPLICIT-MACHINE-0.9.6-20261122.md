> **Verifica post-rilascio completata (22/11/2026):** 0.9.6 installata su **Desktop1 e Desktop2**, `agent_update_status=completed`, `cleanup_verified=true`, test runtime `project_catalog` senza `machine_id` riuscito su entrambi. Strumenti UI MCP diretti post-refresh verificati concretamente su **Desktop1**; per Desktop2 resta da attestare lo stesso controllo lato client ChatGPT. Tutte le autorizzazioni ChatGPT `Consenti` rimangono governate dalla piattaforma. Leggere [STATUS-CONSOLIDATO-0.9.6-20261122.md](STATUS-CONSOLIDATO-0.9.6-20261122.md) per le decisioni e i limiti piu recenti.

# Jarvis 0.9.6: meno metadati e controlli ridondanti, per tutte le operazioni
Decisioni: 22 novembre 2026.

## Ambito richiesto
L'utente ha chiarito che la semplificazione deve valere per **tutte** le funzioni di Jarvis, non solo UI. Le richieste di consenso osservate in ChatGPT menzionavano identificatori tecnici e, per una `ui_sequence`, anche l'azione di controllo del computer. Eliminare un identificatore dal payload puo ridurre condivisione di dati non necessari, **ma non rimuove i controlli di consenso della piattaforma**.

## Riduzione attuata nel codice 0.9.6
- Il collegamento MCP specifico `Jarvis-Desktop1` o `Jarvis-Desktop2` identifica gia inequivocabilmente il PC locale. **Tutti gli strumenti runtime dedicati** omettono ora `machine_id` dai propri `inputSchema` in `tools/list`; il server associa automaticamente ogni richiesta all'identita della macchina locale.
- Questo vale per lettura e scrittura file, directory, processi, Codex, job, coda, aggiornamenti, gateway/device_call, screenshot e operazioni UI. `device_call` deve ancora specificare il `device_id` remoto voluto; la macchina locale non viene ricavata dal prompt.
- Il decoder runtime accetta ancora il `machine_id` esplicito dei vecchi client (per compatibilita), ma **rifiuta** quelli diversi dall'identita reale. Un campo assente non richiede una chiamata preliminare a `machine_info`.
- Il catalogo `task_start` continua a dire **compatibility fallback only** e preferire le funzioni specifiche.
- La versione e aggiornata a 0.9.6. Nessun token conversazionale rintrodotto.

## Controlli intenzionalmente conservati
- Autenticazione Cloudflare Access o Bearer, verifica del target remoto, autorizzazioni effettive, limiti su file/comandi, identificazione della finestra attiva, rilascio atomico del mouse e ricevute anti-ripetizione.
- Una sessione UI usa ancora `session_id` e `request_id`: sono necessari per impedire che una sessione precedente o una chat concorrente agiscano per errore su una finestra diversa. Sono due identificatori distinti dal token conversazionale rimosso in 0.9.5.
- La cache JWT autenticati (30 minuti di inattivita, massimo exp JWT), la cache JWKS Cloudflare (30 minuti) e il riuso TLS restano invariati.
- I prompt `Consenti` sono decisi dal livello **ChatGPT/plugin host**. Il codice Jarvis non puo renderli automaticamente assenti, anche in assenza di `machine_id`; una richiesta con mouse/tastiera puo comunque richiedere autorizzazione.

## Collaudo
- CI Go e PowerShell: `tools/list` non deve includere `machine_id` come campo dei tool runtime; il decoder deve accettare il payload senza identificatore; il server deve associarlo alla propria macchina; un ID esplicito diverso deve essere rifiutato; `device_call` deve continuare a selezionare il target tramite `device_id`.
- Dopo CI/merge, aggiornare Desktop1 e Desktop2 dal medesimo commit, verificando eseguibile versione/hashes, `machine_info`, e chiamate senza `machine_id`.
- Il catalogo ChatGPT puo restare quello precedente finche il collegamento non viene aggiornato. Non dedurre la scomparsa dei prompt dalle sole prove di backend.

## Futuri interventi per la latenza
Misurare tempi prima/dopo in condizioni equivalenti, includendo overhead del tool host. Valutare aggregazione delle operazioni e compressione delle verifiche ridondanti; preservare sempre le garanzie di destinazione, identita e assenza di replay di comandi incerti.
