# Jarvis Windows MCP — stato consolidato, decisioni, prove e limiti
**Ricognizione documentale: 22 novembre 2026, dopo il collaudo Paint e la chiusura dell'app.**

Questo documento e il **punto di ingresso per la ripresa dei lavori su Jarvis Windows MCP**, non sostituisce il registro storico. Fonte: ramo `main` di `Andry-iu/Jarvis`, codice 0.9.6 da commit `a6588c3590ce338aa127b3cfd1615a4481e592f1` (PR #13), integrazioni documentali fino a `972f60750eb216fc05ded80ccac8b3803db73555`. Gli elementi sotto distinguono **test confermati**, **decisioni** e **attivita ancora aperte**. Non attribuire alla 0.9.6 la versione Android o l'installer.

## Stato distribuito e verificato

| Elemento | Desktop1 | Desktop2 |
| --- | --- | --- |
| Agente Windows live | **0.9.6** | **0.9.6** |
| `agent_update_status` | `completed`, `cleanup_verified=true` | `completed`, `cleanup_verified=true` |
| Privilegi | amministratore verificato | amministratore verificato |
| SHA-256 dell'eseguibile | `fb48aa3c96c5fbc56a6d9681ed2624de804bffe92cca1801919ea2f951666aa6` | `1267019010f717fa4aef5d91596deb635f4f19a3f7a1ae8cea9628bb14202c71` |
| Chiamata runtime senza `machine_id` | `project_catalog` riuscito, 7 progetti | `project_catalog` riuscito, 5 progetti |

CI del candidato 0.9.6, workflow `45072776302`: **Go e PowerShell superati**. Il precedente candidato della stessa PR aveva un **test storico fallito**, `TestRuntimeMachineMismatchAndCatalog`, perche imponeva ancora `machine_id` obbligatorio: test aggiornato e CI finale verde. Il risultato finale non cancella il fallimento storico.

## Decisioni implementate (ordine cronologico)

1. **0.9.2: prestazioni e connessione**. Riutilizzo connessioni HTTPS/TLS ai peer autenticati, cache positiva dell'identita di peer pinned, catalogo strumenti precomputato, riduzione conversioni/telemetria ridondanti. Nessuna percentuale di accelerazione certificata con test controllati.
2. **0.9.3/0.9.4: token conversazionale**. A suo tempo progettato e collaudato, ma **superato** dalle decisioni successive. Documenti originali conservati solo come storico.
3. **0.9.5: rimozione del `session_token`**. Non deve comparire negli input/risultati del protocollo corrente. Restano cache di JWT Cloudflare **gia verificati** per 30 minuti di inattivita, senza superare `exp` del JWT, e refresh delle chiavi JWKS ogni 30 minuti. Rimangono TLS persistente, anti-replay, permessi e limiti operativi.
4. **0.9.5: `task_start` come fallback**. Il catalogo MCP lo segnala esplicitamente: preferire sempre strumenti specifici, incluse `ui_session_open`, `ui_sequence`, `ui_mouse_screen`, `ui_keys`, file, processi, gateway, coda e Codex. `task_start(operation=runtime)` resta compatibile solo dove manca un equivalente diretto.
5. **0.9.6: identita locale implicita per tutte le funzioni**. Il server deduce il computer dalla connessione autenticata, quindi gli strumenti runtime dedicati non chiedono `machine_id`; un vecchio client puo inviare un ID corrispondente, ma un valore diverso e rifiutato. `device_call` usa ancora `device_id` per selezionare esplicitamente il destinatario remoto.
6. **Sicurezza**: semplificare i controlli ridondanti e i metadati esposti a ChatGPT, **non disattivare** autenticazione Cloudflare/Bearer, TLS/pinning, destinatario remoto, permessi, ricevute anti-replay, protezioni su finestra o rilascio del mouse. Non e stata applicata una modifica alle policy Cloudflare Zero Trust.
7. **Nessun consenso globale per chat implementato**: la richiesta originale riguardava ogni strumento dell'agente, non soltanto la UI, e ipotizzava **cinque minuti di inattivita**. Il successivo token da 30 minuti e stato rimosso. Senza un identificatore chat affidabile e un consenso host attestato, il requisito non puo essere dato per realizzato. **Non confondere** i cinque minuti delle sessioni `ui_session_open` con l'autorizzazione generale della chat.

## Prove applicative e loro significato

### Pubblicazione strumenti MCP
- Sul client ChatGPT collegato a **Desktop1**, dopo l'aggiornamento dei plugin, sono stati realmente richiamati gli strumenti **diretti** `ui_session_open`, `ui_sequence`, `ui_mouse_screen`, `window_inspect` e `window_close` senza inviare `machine_id` o `session_token`. L'esposizione diretta completa dello **schema ChatGPT di Desktop2 dopo refresh** non e stata collaudata in modo altrettanto esplicito; il backend 0.9.6 di Desktop2 e stato comunque verificato con chiamata runtime senza ID.
- Una risposta `ui_sequence_dispatched` con `verified=false` **non attesta** il risultato grafico: il commit di una forma o del testo deve essere controllato con una nuova immagine.

### Paint Desktop1
- Scenario verificato: **rettangolo nero, quattro piccoli cerchi rossi, scritta blu `Jarvis` vicino al centro della tela**. Screenshot finale conferma il risultato; testo Calibri 12 piccolo, non dimensionato come un titolo.
- La sequenza **non e affidabile al primo tentativo**: il primo trascinamento dopo cambio strumento talvolta non produce una forma; `ui_mouse_screen` con cattura fresca ha completato il rettangolo. La prima creazione della casella di testo puo non acquisire il focus; clic e conferma separati hanno permesso il testo.
- Misure **a segmenti** del collaudo 0.9.6: tela bianca→rettangolo **13,298 s** (con secondo trascinamento); quattro cerchi **14,065 s**; preparazione e testo con altre chiamate e verifiche. **Non sommare e non chiamare questi tempi baseline completa**, perche includono correzioni e tentativi.
- Baseline tecnica piu vecchia, **fallita**: 7 step 300 ms, `ui_sequence` **5,229 s**, intero tentativo con screenshot **9,143 s** ma canvas rimasto bianco.
- Paint e stato infine **chiuso senza salvare**: `window_close` aveva restituito `pending` a causa della finestra `Salvare il lavoro?`; selezionato `Non salvare`, quindi `window_list` non mostrava piu Paint. Nessun file del test da recuperare.

## Consensi ChatGPT: diagnosi reale e limite della piattaforma

Sono state osservate **piu categorie di richiesta**, che non vanno confuse con i permessi interni dell'agente:
- Prima della 0.9.5: `task_start` con `machine_id` e `session_token` segnalato da ChatGPT come **Very high-risk PII**.
- Anche dopo la rimozione del token: `ui_sequence` con `machine_id` e `session_id` segnalato come **PII ad alto rischio**. Da qui la riduzione dell'ID macchina nel catalogo 0.9.6.
- Dopo il refresh 0.9.6: una sequenza Paint comprendente `CTRL+A` e `DELETE` ha provocato un prompt **per cancellazione di contenuto potenzialmente persistente**, pur senza il token conversazionale. Questa conferma non e imputabile a una richiesta di `session_token`.

I due plugin **Jarvis-Desktop1** e **Jarvis-Desktop2** risultavano configurati come `Use my default` rispetto all'impostazione globale **Allow low-risk actions / Consenti azioni a basso rischio**. L'utente ha segnalato di vedere solo opzioni di lettura e azioni a basso rischio, **non** `Non chiedere mai` / `Consenti tutte le azioni`. Un tentativo di aggiornare i permessi mediante il gestore plugin ha restituito **404 Action not found** per entrambi: **nessuna modifica del permesso e stata applicata**.

La schermata `Consenti a ChatGPT di usare Jarvis-Desktop1?` e generata dal livello ChatGPT/plugin host: **nessuna modifica al server Jarvis garantisce l'eliminazione di questi prompt**. Preferire richieste specifiche e non distruttive quando possibili, senza mascherare azioni distruttive o abbassare la sicurezza della connessione.

## Limiti aperti e criteri di chiusura

| Priorita | Punto aperto | Verifica necessaria |
| --- | --- | --- |
| Alta | **Riproducibilita UI**: primo trascinamento, transizioni forma/colore e focus del testo non affidabili | 3 o piu esecuzioni consecutive, da canvas bianco al risultato verificato, **senza ritentativi** e senza azioni invisibili |
| Alta | **Baseline end-to-end** non misurata su un run riuscito al primo tentativo | Tempo unico da cattura iniziale a screenshot finale con suddivisione setup/azioni/catture/attese e verifica visiva; poi confronto pause 300/200/150/100 ms |
| Alta | **Popup `Consenti` ancora possibili** con operazioni sensibili/distruttive | Prove comparative a parita di azione e account; registrare quali strumenti e contenuti producono conferme. Non dichiarare un meccanismo per disabilitarli se non esposto nell'interfaccia ChatGPT |
| Media | **Schema diretto Desktop2 post-refresh** non collaudato esplicitamente | Verificare i nomi/parametri effettivamente esposti e una chiamata diretta senza ID; non confondere con il test backend |
| Media | **Sessione di consenso per chat** (5 minuti globali su ogni strumento) non implementata | Richiede identita chat e concessione verificabili dal lato host; attualmente solo cache auth e sessioni UI separate |
| Media | **Dimensione/centratura testo** e acquisizione focus Paint | Stabilire dimensione del testo e creazione casella affidabile; confermare sempre il commit del testo dopo una chiamata separata |
| Media | **Misura di miglioramento delle prestazioni** 0.9.2–0.9.6 | Benchmark ripetuto e comparabile con mediana, distinguendo overhead di ChatGPT, MCP, PowerShell, rete, UI |
| Media | **Installer ZIP attuale e altri artefatti coordinati** | Non e attestata da questo ciclo la creazione/verifica di un installer ZIP Windows 0.9.6 o APK Android aggiornati. Non equiparare una release sorgenti e l'auto-update live a un pacchetto di installazione verificato |
| Bassa | **Verifica reale della policy Cloudflare Access** | Leggere configurazione Zero Trust e durata sessione se necessario: cache JWT/JWKS interne non sostituiscono la durata policy esterna |

## Collegamenti di continuita
- [CURRENT-RELEASE.md](CURRENT-RELEASE.md): verbali storici installazione/versioni, hash, ricevute e CI
- [PAINT-UI-SEQUENCE-BENCHMARK-20261122.md](PAINT-UI-SEQUENCE-BENCHMARK-20261122.md): misure e osservazioni visive con esiti distinti
- [ALL-TOOLS-IMPLICIT-MACHINE-0.9.6-20261122.md](ALL-TOOLS-IMPLICIT-MACHINE-0.9.6-20261122.md): schemi MCP 0.9.6
- [AUTH-OPTIMIZATIONS-NO-TOKEN-0.9.5-20261122.md](AUTH-OPTIMIZATIONS-NO-TOKEN-0.9.5-20261122.md): rimozione token e cache mantenute
- [DIRECT-UI-MCP-TOOL-CATALOG-20261122.md](DIRECT-UI-MCP-TOOL-CATALOG-20261122.md): storico del disallineamento tool ChatGPT
- [CHAT-WIDE-AUTHORIZATION-20261122.md](CHAT-WIDE-AUTHORIZATION-20261122.md) e [SESSION-TOKEN-0.9.3-20261122.md](SESSION-TOKEN-0.9.3-20261122.md): requisiti originali e protocolli **superati/storici**
- [../../windows-mcp-agent/CHANGELOG.md](../../windows-mcp-agent/CHANGELOG.md): modifiche alle versioni del codice Windows

**Principio di lettura:** un risultato storico riportato come "da eseguire" rimane tale **al momento di quella voce**, non deve prevalere su questo stato consolidato quando esistono verifiche successive. Evitare di segnare come risolto cio che e stato soltanto disposto, inviato o compilato senza collaudo funzionale.
