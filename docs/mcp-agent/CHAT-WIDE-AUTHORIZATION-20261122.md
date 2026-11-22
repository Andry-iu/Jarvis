# Jarvis Agent — autorizzazione unica per chat (requisito 22/11/2026)

## Richiesta dell'utente

La richiesta di "sessione" riguarda **tutto l'utilizzo dell'agente nella singola chat ChatGPT**, non una sessione della finestra Paint, un solo screenshot, un'azione UI o un singolo strumento.

Comportamento desiderato:

1. Alla prima operazione con l'agente in una chat, eseguire **un'unica richiesta di autorizzazione** per la sessione di quella chat (sul computer/agente indicato e nei limiti dei permessi applicabili).
2. Dopo l'accettazione, le successive richieste della **stessa chat** a qualsiasi strumento Jarvis autorizzato — lettura, file, mouse, tastiera, finestre, processi, Git, coda, ecc. — non devono ripetere l'intero rituale di conferma e i controlli onerosi.
3. Ogni comando autenticato della stessa chat aggiorna l'ultimo utilizzo; dopo **300 secondi consecutivi senza alcun comando** il consenso riutilizzabile scade. La verifica e la nuova richiesta avvengono *solo alla richiesta successiva*, non con notifiche automatiche.
4. Chat diverse non condividono autorizzazioni, anche se eseguite dallo stesso utente, dallo stesso plugin o attraverso lo stesso gateway.
5. L'interazione UI puo, indipendentemente, usare screenshot riutilizzati e `ui_sequence` con mouse/tastiera/testo. Non confondere questo con l'autorizzazione dell'intera chat.

## Chiarimento sui controlli interni, 22 novembre 2026

Il riuso del consenso deve valere anche **dentro ogni tool e nelle funzioni interne di Jarvis**: i controlli onerosi gia superati per questa chat, agente e insieme di capacita non vanno riavviati a ogni comando. Esempi di operazioni da evitare a ogni richiesta: ricreare un contesto di consenso, rieseguire una procedura di riconoscimento iniziale, riproporre lo stesso prompt o ristabilire sessioni UI non cambiate. Per ciascun comando usare un accesso al lease in memoria (costante/rapido), aggiornare l'ultimo utilizzo e mantenere soltanto i controlli locali economici e necessari (autenticazione della richiesta, permessi richiesti, macchina e parametri, replay, stato live di finestra/desktop quando applicabile).

Non introdurre un timer che interrompe un comando in corso: la durata e 300 secondi consecutivi **senza nuovi comandi** della stessa chat; una richiesta valida rinnova il lease. Non trasformare il risparmio di controlli in un bypass di autenticazione di trasporto, autorizzazioni imposte dal sistema ospitante, sicurezza Windows o conferme obbligatorie per azioni eccezionali.

**Stato ChatGPT verificato il 22/11/2026:** Jarvis-Desktop1 e Jarvis-Desktop2 ereditano entrambi il permesso globale «Consenti azioni a basso rischio». Le conferme della piattaforma ChatGPT non sono controllabili dal solo server Jarvis. Il permesso plugin «Consenti tutte le azioni» e una scelta persistente, non una concessione temporanea di cinque minuti: **non abilitarla automaticamente** per simulare questo requisito.

## Contratto di sicurezza non eliminabile

La sessione e una cache di consenso e di controlli onerosi **non** un permesso amministrativo illimitato:
- Ogni chiamata deve mantenere autenticazione di trasporto / Cloudflare Access o credenziali gateway, verifica della macchina bersaglio, controllo dei parametri, privilegi Windows, ACL, isolamento delle risorse, richieste univoche, ricevute e protezione da replay. Queste verifiche devono restare economiche, non sparire.
- Per azioni sensibili o con requisito di consenso puntuale (nuovo pairing, UAC, conferme Codex, credenziali, azioni distruttive, trasferimenti di segreti) mantenere la specifica autorizzazione: una sessione chat non puo sostituirla.
- Scadenza immediata per disconnessione/revoca/riavvio dell'agente, cambio di identita macchina, modifica del soggetto autenticato o del contesto di fiducia; scadenza per inattivita a 300 s.
- Non concedere consenso automaticamente alla semplice apertura di una connessione o di una chat.
- Una sessione non deve abilitare strumenti nuovi, escalation di privilegi o raggiungimento di nuovi PC che non erano inclusi nel consenso iniziale.

## Prerequisito di integrazione: identita verificabile della chat

**Il backend MCP attuale non riceve un identificatore verificabile della conversazione ChatGPT.** Non usare un `conversation_id` scelto liberamente dal modello, un `session_id` MCP opzionale o il token Cloudflare condiviso come prova che due richieste provengono dalla stessa chat. Non assumere che il gateway autentichi una chat: autentica il computer/peer.

Per realizzare *realmente* una sola autorizzazione per chat, occorre che il livello di integrazione ChatGPT/plugin fornisca un contesto chat affidabile/attestato e un evento di consenso valido, oppure un token di consenso non trasferibile emesso e confermato nel flusso di autorizzazione del client. L'agente puo allora associare una concessione a `(soggetto autenticato, chat verificata, macchina, insieme di capacita, ultima_attivita)`, rinnovandone solo la scadenza di inattivita.

Se tale segnale non e disponibile, **non implementare una pseudo-sessione globale condivisa tra chat**. Continuare con le autorizzazioni esistenti e ottimizzare le parti implementabili (UI sequence, riuso worker, cache di metadata/controlli non critici, screenshot throttle).

## Responsabilita dei livelli

- **ChatGPT/plugin host**: mostra/gestisce le proprie conferme e fornisce il vincolo per-chat; il server Jarvis non puo sopprimere popup o conferme che l'host decide di mostrare.
- **Jarvis runtime**: mantiene eventualmente il lease in memoria, lo invalida e verifica in modo economico su ogni richiesta i controlli di sicurezza necessari.
- **Gateway Jarvis**: inoltra la prova autorizzata senza sostituirsi alla chat; separazione per destinatario e divieto di riuso su altri agenti.
- **UI worker**: conserva una sessione distinta e piu restrittiva, per finestra/screenshot, come nella versione 0.9.0.

## Test di accettazione per la futura implementazione

1. Chat A / PC1: primo comando richiede una conferma; i comandi successivi della stessa chat e PC riutilizzano il consenso per tutti gli strumenti inclusi.
2. Dopo 299 s di inattivita l'accesso rimane valido; dopo 300 s una nuova richiesta richiede una nuova conferma.
3. Ogni nuovo comando rinnova il timeout di inattivita senza richiesta addizionale.
4. Chat B / PC1: non puo riutilizzare il consenso di A.
5. Chat A / PC2: non eredita privilegi non accordati esplicitamente per PC2.
6. Logout, riavvio, cambio del token/profilo o revoca invalidano la sessione.
7. L'identita di finestra cambiata, UAC, nuovo pairing, prompt di Codex e mutazioni rischiose continuano a essere verificate secondo le rispettive regole.
8. Nessuna possibilita di scavalcare l'host creando un identificatore di chat arbitrario.
9. Le autorizzazioni non sono confuse con `ui_session_open` e non richiedono screenshot.
10. Audit registra solo identificativi opachi, scopi, tempi ed esiti; nessun segreto o testo della conversazione.

**Stato:** specifica chiarita e registrata; non ancora implementata ne verificata una concessione per-chat. La versione 0.9.0 ha soltanto la sessione UI e la sequenza di input.
