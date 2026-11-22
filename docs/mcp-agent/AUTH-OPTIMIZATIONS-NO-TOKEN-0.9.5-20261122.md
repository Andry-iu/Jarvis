# Jarvis Windows MCP 0.9.5 — remove conversational token, retain speed optimizations
Decisione del 22 novembre 2026.

## Richiesta e motivazione
L'utente ha richiesto di **mantenere tutte le ottimizzazioni ma togliere il token** passato tra ChatGPT e l'agente Jarvis. Una schermata di consenso ChatGPT `Jarvis-Desktop1` ha classificato la richiesta `task_start(operation=runtime,request={operation:"machine_info",machine_id:"...",session_token:"0"})` come condivisione di `Very high-risk PII`, citando espressamente identificatore persistente del dispositivo e token di sessione. Il dato `0` non era segreto; la categoria e il canale degli argomenti erano comunque sensibili per la piattaforma. Il token di Jarvis non puo eliminare i consensi applicati a monte da ChatGPT.

## Modifiche 0.9.5
- Eliminare **completamente** `session_token` dagli input MCP pubblicati, da `runtimeRequest`, dai controlli runtime, dal passaggio tramite `task_start(operation="runtime")` e dagli output; non generare o esporre token opachi.
- Eliminare la tabella delle lease conversazionali, i relativi hash, `sessionState`, `invokeScoped`, `verifiedAccessPrincipal`, i metadati `session_reused` e `session_idle_timeout_seconds`. La compatibilita `task_start(operation="runtime",request={...})` rimane disponibile **senza** token.
- Ogni chiamata resta vincolata alla normale autenticazione di trasporto (Bearer locale o Cloudflare Access), controlli di permesso, identita dispositivo, ricevute anti-replay, sicurezza UI/foreground, ecc. **Non si introducono scorciatoie di autorizzazione.**
- Le vecchie versioni degli schemi ChatGPT potrebbero continuare a mostrare il campo `session_token` facoltativo finche il collegamento non aggiorna il catalogo, ma **le chiamate della 0.9.5 devono ometterlo**. Non dipendere da esso.

## Ottimizzazioni conservate
- **Connessioni HTTPS/TLS peer persistenti** e riuso trasporto della 0.9.2, con pin TLS e identita machine.
- **Cache identita peer TLS pinned**, catalogo runtime/tool precomputato e riduzione conversioni JSON della 0.9.2.
- **Verifica JWT Cloudflare gia riuscita** conservata in una cache positiva in memoria (SHA-256 del JWT completo, massimo 256 voci). Il TTL e **30 minuti di inattivita**, rinnovato su uso, **mai oltre `exp` assoluto del JWT**.
- **Cache JWKS pubbliche Cloudflare** aggiornata ogni **30 minuti**: refresh periodico, non esteso indefinitamente sulla sola attivita. Non richiede un nuovo login per il semplice refresh delle chiavi.
- **`ui_sequence`**, screenshot riutilizzabili e pause `settle_ms` individuali 0–500ms restano invariati. Le sessioni interne `ui_session_open` sono **separate** e non equivalgono al token conversazionale eliminato.

## Limiti e collaudo
- Le **approvazioni di ChatGPT/plugin host** non sono controllabili dal codice dell'agente; la rimozione di `session_token` riduce un campo sensibile ma non garantisce la soppressione dei popup. `machine_id` e altre operazioni sensibili possono ancora richiedere conferma.
- La **durata globale/applicazione/policy di Cloudflare Access** rimane configurata indipendentemente dal runtime. Non e stata modificata da questa release.
- Test CI Go + PowerShell: il catalogo `tools/list` non pubblica `session_token`; `decodeRuntime` rifiuta vecchi argomenti `session_token`, ma accetta richieste normali; cache JWT mantiene sliding 30 min ed exp assoluta; test UI e build restano validi.
- Dopo merge, aggiornare Desktop1 e Desktop2 da commit esatto; convalidare `agent_update_status=completed`, `machine_info.version=0.9.5`, elevazione e checksum installato, `machine_info`/altri strumenti senza token e assenza di metadati di sessione. Registrare i risultati in `CURRENT-RELEASE.md`.
- Eseguire benchmark comparativi di latenza prima di affermare miglioramenti percentuali. Una schermata di consenso comparsa o meno durante una prova non basta a dimostrare l'origine interna della classificazione.

## Priorita degli strumenti MCP (richiesta utente)
- Nell'informazione dell'operazione pubblica `task_start` indicare con chiarezza che si tratta di uno strumento di **compatibilita da usare il meno possibile**: scegliere sempre la funzione dedicata (per UI: `ui_session_open`, `ui_sequence`, `ui_mouse`, `ui_mouse_screen`, `ui_keys`) quando esposta dal client. Usare `task_start` solo in mancanza di un'alternativa specifica o per diagnostiche legacy.
- La descrizione server sara visibile al client **dopo l'aggiornamento del catalogo MCP**. Questo commit da solo non puo cambiare istantaneamente uno schema gia memorizzato in ChatGPT e non permette di sopprimere richieste di consenso obbligatorie.
