> **SUPERATO DALLA 0.9.5 (22/11/2026):** su richiesta dell'utente e stato rimosso il token di sessione conversazionale per evitare l'invio di un campo potenzialmente sensibile a ChatGPT. Questo documento descrive il precedente design 0.9.3–0.9.4 **solo per storico**, non e piu la specifica attiva. Le cache JWT/JWKS di 30 minuti e le ottimizzazioni TLS rimangono operative. Vedere [AUTH-OPTIMIZATIONS-NO-TOKEN-0.9.5-20261122.md](AUTH-OPTIMIZATIONS-NO-TOKEN-0.9.5-20261122.md).

> **Distribuzione e collaudo completati 22/11/2026:** Jarvis **0.9.4**, commit sorgente `fc4ecf67e7654ab2a1d0f85f0e037edb0ba5802f`, installata su Desktop1 e Desktop2. `agent_update_status` completed con cleanup verificato e `machine_info.version=0.9.4` live. Test su entrambi: `0` genera token, `project_catalog` lo riutilizza, token sconosciuto genera nuovo token, nuovo riuso riuscito. Nessuna modifica delle impostazioni Cloudflare Zero Trust effettuata: il token Jarvis non prolunga la sessione globale Access. Hash, PID e ricevute sono in [CURRENT-RELEASE.md](CURRENT-RELEASE.md).

> **Correzione 0.9.4:** gli strumenti ChatGPT gia collegati possono avere schemi MCP obsoleti e rifiutare il parametro `session_token` sulla chiamata diretta. Fino al refresh dello schema, usare il percorso compatibile `task_start(operation="runtime", request={operation:"machine_info", machine_id:"...", session_token:"0"})` (o un altro runtime tool autorizzato). Il gateway continua a fare tutte le verifiche precedenti. Stato installazione 0.9.4 registrato separatamente dopo collaudo.

# Jarvis Windows MCP 0.9.3: rolling session token and authentication cache
Decisioni aggiornate al 22 novembre 2026.

## Obiettivo
Ridurre la latenza per singolo comando e per workflow composti senza richiedere autorizzazioni ripetitive durante il lavoro. Conservare le ottimizzazioni 0.9.2: peer HTTPS/TLS keep-alive, catalogo strumenti precomputato, percorso telemetria semplificato, cache identita peer TLS-pinned. Separare sempre ottimizzazione dell'autenticazione dall'eliminazione dei controlli di sicurezza.

## Protocollo concordato
- Tutti gli strumenti Jarvis hanno parametro facoltativo `session_token` (stringa opaca; accettare anche il valore numerico `0`).
- Primo comando: `session_token=0`; si applicano autenticazione e autorizzazioni iniziali vigenti, il comando viene eseguito nella stessa chiamata e, in caso di successo, torna `session_token` nuovo. Nessuna chiamata apposita prima del comando.
- Chiamata successiva: riutilizzare lo stesso token. Ogni richiesta valida e autenticata ne rinnova l'ultimo utilizzo.
- Scadenza di inattivita: **1800 secondi (30 minuti)**, non 5. La scadenza non interrompe le operazioni gia avviate; al comando successivo si apre una nuova sessione.
- Token **sconosciuto, scaduto, perduto nella finestra di contesto o `0`**: comportamento di nuova sessione, senza errore di sessione pregiudiziale. Nuovo token restituito quando il comando ha avuto esito positivo. In caso di problemi di autorizzazione non si genera un token.
- Le chat molto lunghe possono reinviare `0` senza conoscere un ID di conversazione. Questo NON fornisce l'isolamento per-chat: il token e una capacita riutilizzabile da chi possiede il token e le credenziali di trasporto della stessa identita.
- La vecchia interfaccia senza `session_token` resta compatibile. I token sono casuali a 256 bit, memorizzati in RAM lato agente solo come hash, associati al principal autenticato. Invalidate dal restart, separate fra Desktop1 e Desktop2 e limitate nel numero.
- Per gli agenti remoti, il token non deve diventare automaticamente un'autorizzazione a raggiungere computer diversi. Il gateway continua a verificare identita target, trasporto, permessi, loop e ricevute.

## Cache e rinnovo durante l'attivita
- Sessione token: timeout **mobile** di 30 minuti dopo l'ultimo comando.
- JWT Access gia autenticati: cache di verifica rapida con timeout **mobile** di 30 minuti, associata al contenuto completo del JWT, ma **mai** oltre il suo campo `exp`; dopo la scadenza occorre un JWT valido nuovo. La sessione Access Cloudflare puo rinnovare un application token autonomamente se le sue policy lo consentono.
- Chiavi pubbliche JWKS Cloudflare: permanenza in memoria di **30 minuti** tra refresh. La successiva verifica aggiorna automaticamente le chiavi; non e un nuovo login utente. **Non estendere indefinitamente** la fiducia in vecchie chiavi con sliding TTL.
- Connessioni TCP/TLS inattive della 0.9.2: mantenere il keep-alive a 5 minuti se non emergono benchmark che ne giustifichino l'aumento; questo non equivale al timeout di sessione.
- Le durate Cloudflare **global, policy e application** non sono modificabili dal solo codice dell'agente. Per ridurre login interattivi ripetuti, verificare nel dashboard Zero Trust le durate **global, policy, application e client**. Cloudflare documenta 24 ore come valore predefinito delle sessioni global e application: non diminuirle senza motivo. Se una policy piu restrittiva forza login ricorrenti, adeguarne la durata solo dopo verifica dell'effettiva configurazione e dell'identita autorizzata; mantenere condizioni di accesso, revoche, MFA e scadenze JWT.
- Il consenso dei plugin/chat host e indipendente: il server Jarvis non puo sopprimerne i popup; il token non e una conferma originata dal host.

## Sicurezza e compatibilita
- Il token Jarvis non sostituisce le credenziali Cloudflare/JARVIS_MCP_TOKEN o le policy Access. Non rimuovere firma/exp, machine_id, ACL, Windows UAC, pairing, autenticazione per richiesta, conferme obbligatorie, ricevute, anti-replay.
- Il riuso JWT risparmia verifiche RSA ripetute solo per lo stesso JWT gia autenticato, con cache limitata, exp assoluto e idoneita di trasporto invariata.
- Nuovo token e risultato coesistono nel payload JSON del tool; non loggare mai il token, non persisterlo su disco, e non inserirlo nei file Git.
- Client senza nuovi schemi possono continuare a operare come prima; per ottenere i vantaggi della sessione il client MCP deve poter inoltrare `session_token`.
- Misurare end-to-end e le singole fasi prima di attribuire miglioramenti: chiamata Cloudflare/ChatGPT, gateway, JWKS/JWT, peer TLS, esecuzione, return.

## Collaudo / release
- Test unitari: zero, sconosciuto, validita, rinnovo prima di 1800s, scadenza a 1800s, differente principal, fallimento prima autorizzazione, JWKS, JWT exp assoluto.
- CI Go e PowerShell, build Windows, rilascio via commit Git definitivo, aggiornamento Desktop1 e Desktop2 con `agent_update`, controllo live `machine_info.version`, hash, backup/cleanup, smoke test; verifica in seguito prestazioni su sequenze reali.
- **Stato finale:** 0.9.3 integrata con PR #9, successivamente corretta e distribuita come **0.9.4** con PR #10, installazione e test live di Desktop1 e Desktop2 completati; vedere `CURRENT-RELEASE.md`.
- Dopo installazione collaudata, completare commit della documentazione finale con stato reale, versioni, hash, e limitazioni.
