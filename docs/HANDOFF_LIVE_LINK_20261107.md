# Canale pronto durante Voice — .120 / 142

Via esplicito dell’utente il 7 novembre 2026: implementare e committare il gruppo discusso. Base iniziale `origin/main` 0a778e9 (.119 rilasciata), aggiornata a d02f7cd preservando il ponte cloud concorrente; lavoro isolato nel branch `voice-live-link` prima del push fast-forward. Nessun altro via richiesto per completare lo stesso gruppo.

## Risultato implementato

- `VoiceCycle.start` apre in anticipo il canale; la normale fine ciclo, lo stop per hidden wake e la ripresa gestiscono apertura/chiusura. Il solo hide della cover conserva la sessione. Un arresto nativo ancora da confermare non equivale a Voice terminata. Il servizio ordinario fuori sessione resta operativo.
- `BridgeLiveChannel` apre POST HTTPS `/device/live`, senza redirect, sullo stesso origin e con le stesse due credenziali. Legge NDJSON a frame limitati (24KiB più256byte d’involucro), ready/heartbeat/request; nessun comando prima di ready né operazione arbitraria. Solo thread di rete, nessuna cattura audio nuova. Il budget di lease usa tempi relativi server/monotoni, senza fidarsi dell’orologio del telefono.
- Il Worker riusa lo stream per più comandi e osserva la coda condivisa R2. Identità verificata prima dell’apertura e prima di ciascuna consegna/heartbeat; revoca, abort e backpressure sono rispettati. Query ai record indipendenti in parallelo. Nessuna mappa in RAM viene usata come coda tra isolate.
- Ogni trasporto termina entro20s o40 scansioni della coda (nominalmente circa10s se la coda è vuota e veloce) e viene rinnovato dall’app. L’attesa fra scansioni server è250ms, heartbeat3s. È un canale HTTP streaming di sessione con rinnovi, **non** un WebSocket, un socket eterno o push senza polling sul server. Nessun Durable Object, binding aggiunto o servizio a pagamento introdotto. Il carico e i limiti di subrequest R2 del vero hosting restano da verificare.
- Durante una capability live recente, le operazioni MCP attendono fino a4s l’esito nella prima chiamata. Il costo precedente dell’autenticazione/accodamento si aggiunge a questa finestra. `request_status` resta asincrono; APK141 conserva l’attesa diretta1200ms e gli altri strumenti precedenti. Se non pronto: stesso pending/UUID, nessun annullamento o riavvio. Il fallback normale del telefono riporta la capability ordinaria.
- Il risultato dell’azione continua su `/device/respond` e il claim su `/device/claim`: il canale persistente sostituisce la consegna periodica telefono-server, **non** tutte le transazioni del protocollo. UUID/fingerprint, creazione/claim condizionali, journal cifrato prima dell’azione, outbox e consensi sono conservati. Chiudere il canale non incrementa la generazione e non interrompe un’azione già in carico.
- Formato/endpoint/rete non disponibili: fallback al percorso ordinario, pausa60s prima di un nuovo tentativo live. Una riapertura di trasporto non genera nuovi request_id. Dopo revoca non consegnare nuove operazioni.
- `phonebook.py lookup --device-id UUID --query "Nome"` consulta l’indice e prepara il piano diretto in un unico passaggio locale. Un solo candidato esatto è usato; candidati solo simili, omonimi o troncamento richiedono scelta. Nessuna corrispondenza: mantenere il nome letterale dell’utente per la ricerca live. `accept --show-number` verifica fonte/UUID/hash/schema, salva incrementalmente e restituisce solo nome/numero/etichetta scelti; non richiede una ricerca locale successiva. Altri dati restano cifrati e fuori Git/diagnostica.

## Verifiche di questa chat

- 87 test backend Node (73telefono e14ponte cloud): compatibilità precedente, stream unico con più comandi, numero completo nella chiamata iniziale, isolamento dispositivi, revoca, abort, timeout d’attesa senza nuovo UUID o annullamento e recupero sul percorso ordinario.
- 16 test Python del runner cloud conservato e rieseguiti dopo integrazione; nessuna build reale del runner invocata.
- 29 test Python rubrica: scelta esatta, candidati simili/omonimi, nome letterale senza indice, risposta verificata senza ricerca aggiuntiva, hash/fonte/UUID errati, copie preesistenti conservate.
- Cinque suite Node DOM, generazione/verifica della guida34argomenti e build/verifica dell’artefatto Worker ESM.
- Sei nuovi metodi Android previsti su SDK34/36: parsing/riuso/credenziali/lease/chiusura/fallback e worker reale con trasporto simulato durante chiusura Voice e invio di una risposta. **Non compilati né eseguiti in questa chat.** Non c’è accesso operativo verificato al cloud Jarvis; le verifiche Gradle/build/firma non sono state spostate altrove.
- Nessun collegamento, chiamata, Maps, WhatsApp o importazione reale del telefono eseguiti per sviluppare/testare questa patch. Nessun APK .120, deploy o nuovo risultato reale di latenza dichiarato.

## Ripresa nel cloud Jarvis

1. Verificare ambiente Jarvis, main/working tree e segreti esistenti senza stamparli; recuperare questo commit. Leggere AGENTS/CONTINUITY/VERIFICATION, mantenendo gli altri residui della roadmap.
2. Eseguire `testDebugUnitTest` su SDK34/36, includendo `BridgeLiveChannelTest`, `BridgeLinkWorkerIntegrationTest`, `BridgeLinkServiceTest` e `NativeExitStateTest`, più lint/build previsti. Verificare compilazione, listener/cancellazione e assenza di regressioni sulle uscite native.
3. Nel medesimo cloud preparare APK con firma permanente, controlli versione/ABI/ZIP/16KB/certificato e checksum. Non usare GitHub Actions né toolchain alternative. Aggiornare i risultati reali e committare/pushare lo stato completo.
4. Pubblicare nello stesso Site/plugin privato il backend aggiornato, conservando audience, binding, segreti e abbinamenti. Verificare supporto dello streaming tramite il vero gateway: flush del frame ready, assenza di buffering, rinnovo e limiti storage/subrequest. Non dire pronto per il telefono se il gateway accumula l’intera risposta. Nessun nuovo servizio/infrastruttura richiesto implicitamente.
5. Collaudo Galaxy a cura dell’utente: tre richieste di numeri univoci durante lo stesso ciclo, poi Maps/appunti; omonimi senza scelta automatica, fine Voice durante risposta, rete persa/ripresa, blocco e chiusura cover. Nessuna azione irreversibile automatica per fare benchmark. Confrontare consumo dati/batteria con la .119 su sessioni comparabili, Wi-Fi e rete mobile.

## Criterio di latenza

Obiettivo1–3s, massimo5s **dalla fine della richiesta vocale all’inizio del parlato contenente il risultato**. Il superamento rende il test fallito ma lascia proseguire la richiesta con lo stesso UUID. Non trasformare la soglia in timeout operativo. Né i timer server né l’app osservano da soli tutti i confini della Voice nativa di ChatGPT: serve una misura esterna (registrazione cronologica concordata o cronometro), senza salvare automaticamente audio privato. Separare preparazione locale, invio MCP, accodamento, claim→risposta, recupero del risultato e avvio parlato. `queue_ms` non è il costo della sola apertura di connessione; `claimed_to_response_ms` include rete/storage e non solo CPU telefono. `age_ms` è l’età dell’esito.

La misura precedente sul contatto Andrea riportava44,4s dall’inizio dell’elaborazione backend alla verifica locale, di cui13,16s nel relay (10,23s coda e2,93s claim→risposta). Non era una misura completa fine-parlato→inizio-parlato. Nessun miglioramento numerico viene attribuito alla .120 prima del collaudo reale. Le latenze del modello e del frontend Voice non sono controllate da questa patch.

## Residui

Restano tutte le attività precedenti della roadmap, i collaudi Voice/cover/Watch/Google/importazione già aperti e il ponte MCP cloud generico implementato/pubblicato in altra chat nei commit df19454/d02f7cd. Quel lavoro è conservato, inclusa la rete del runner ancora bloccata dal proxy e il collaudo tool→runner→build→download ancora pendente. Questa modifica non cambia quel ponte e non cambia i progetti Windows, Watch o NestingOpt.

Fonti tecniche: [Cloudflare Streams](https://developers.cloudflare.com/workers/runtime-apis/streams/) e [ReadableStream](https://developers.cloudflare.com/workers/runtime-apis/streams/readablestream/). Le API supportano il flusso; non provano il comportamento del gateway Sites o del Galaxy.

Autorizzazione permanente riconfermata durante la consegna: l’utente ha esplicitamente approvato commit e push fast-forward su main senza riconferme per gli sviluppi già autorizzati, e ha richiesto la compilazione cloud successiva. Regola generale aggiornata alla versione39 e appunto sul telefono verificato note_saved/revisione2. Il terminale non dispone di credenziali per il push HTTPS; usare il collegamento GitHub autenticato senza introdurre credenziali in file/chat. Nuovi strumenti cloud disponibili nel catalogo; ultimo cloud_status: runner_online=false, last_seen=null, active_job=null. Nessuna compilazione ancora avviata.
