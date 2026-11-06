# 0.5.115 / 137 — sorgenti preparati, verifica e rilascio cloud da completare

## Autorizzazioni e regole aggiornate — 6 novembre 2026

L’utente ha dato il via all’intero gruppo: correzione del collegamento fuori dalla pagina e pressione prolungata sul mute grigio. Ha poi autorizzato esplicitamente modifiche e commit da qualsiasi chat, riservando **compilazione, firma e rilascio al cloud Jarvis**. Ogni commit deve includere documentazione, stato effettivo e sviluppi concordati ancora da completare. Queste regole sono riportate in `AGENTS.md` e superano il precedente vincolo del cloud anche per lo sviluppo.

Preparazione effettuata in una chat con checkout Git in `/workspace/scratch/ce44b2fec689/Jarvis`, partendo da `main` remoto `d51abe4b78a7cef8e8b8e55fb9b77dcff683ef06`. **Questo ambiente non è il cloud Jarvis.** Nessuna toolchain Android ricostruita, compilazione Gradle/JVM, firma o release eseguita qui. `.115/137` è la versione dei sorgenti preparati, non un APK disponibile né una nuova installazione verificata. Ultima baseline sorgente `.114/136`.

Il tentativo di salvare le regole negli appunti JarvisWake sul telefono è scaduto senza risposta. Non dichiarare l’appunto salvato e non duplicare automaticamente la scrittura: prima rileggere l’elenco quando il telefono torna raggiungibile. Il testo delle regole è conservato in Git in `AGENTS.md` e in questo handoff. Nessun dato privato, ID dispositivo o credenziale è incluso qui.

## Collegamento: evidenze, cambiamento e limiti

Osservazione utente: entrando nella pagina Collegamento il trasporto riprende; uscendo si interrompe. Richieste del plugin scadute; errori generici rete/servizio. La baseline registra `setup` a `onResume`, lo rimuove a `onPause` e interrompe il worker se non ci sono altri owner. `WakeService` e sessione Google potevano mantenerlo, ma non esisteva un servizio dedicato al collegamento. Questo dimostra una dipendenza di ciclo di vita nel codice, **non prova lo stato preciso del servizio o della rete sul Galaxy durante il difetto**.

Implementazione preparata:

- `BridgeLinkService`: servizio foreground dedicato opt-in, tipo `specialUse` con permesso e spiegazione nel manifest. Non acquisisce microfono, non avvia wake o Voice. Notifica permanente di collegamento abilitato, ingresso alla pagina e azione esplicita per disabilitarlo conservando il pairing.
- Avvio/ripresa da Home o pagina Collegamento se già configurato e abilitato; tentativo dopo boot/aggiornamento. Disattivazione e cancellazione del pairing fermano il servizio. Arrestare la sola wake non disattiva il collegamento.
- Owner `link_service` indipendente dalla pagina; controllo ogni 10 secondi che recupera un worker terminato, senza rimpiazzare un worker vivo. Retry rete esistenti conservati; nessuna modifica al protocollo operativo, UUID/claim/journal/outbox o deduplicazione delle azioni.
- Diagnostica bounded in `native-runtime.log`: cambi owner per conteggi/flag, avvio/arresto servizio/worker, errori distinti timeout/DNS/TLS/HTTP/rete/protocollo-archivio. Nessun messaggio di eccezione, URL, token o contenuto privato. Stato UI distingue servizio locale non operativo, rifiuto di autorizzazione remota ed errori rete/protocollo.

Il servizio non è una prova di raggiungibilità e non aggira force-stop, revoca, assenza rete o limitazioni Android/Doze. Rifiuto dell’avvio viene segnalato; nessun bypass né nuovo pairing automatico. Verificare sul dispositivo anche i tempi di ripresa e il caso servizio wake spento.

Fonti primarie consultate per la scelta delle API:

- https://developer.android.com/develop/background-work/services/fgs/service-types — `specialUse`, permesso e proprietà subtype.
- https://developer.android.com/about/versions/14/changes/fgs-types-required — dichiarazioni dei tipi foreground.
- https://developer.android.com/reference/android/view/ViewConfiguration — soglia long press di sistema.
- https://developer.android.com/reference/android/view/MotionEvent — cancellazione della sequenza touch.

## Cover: pressione temporanea del microfono

- Tap breve mantiene l’inversione persistente esistente; doppio tap centrale mantiene la chiusura. Una pressione centrale lunga usa la soglia Android e non produce anche un tap al rilascio.
- Solo una pressione iniziata con mute confermato grigio richiede il microfono attivo fino al rilascio. Pressione lunga da attivo non introduce un’inversione.
- Rilascio, movimento oltre touch slop, multitouch, CANCEL, scomparsa o distacco della cover concludono una sola volta l’attivazione temporanea. Nessun nuovo HUD, Voice o saluto. I colori/animazione seguono lo stato confermato.
- Nativo: target mute esplicito serializzato rispetto alle osservazioni dei controlli semantici. Il rilascio in attesa di unmute resta memorizzato fino alla sua osservazione, poi richiede mute una volta. Stato conservato nel ciclo anche ricreando il controller; nessun secondo clic mentre la prima azione è incerta.
- WebView: SET idempotenti dei track, richiesta finale mute accodata dietro l’unmute e callback correlate per documento/seriale. Timeout di callback 2 secondi: unmute incerto richiede ripristino mute, senza ripetere l’unmute. Non confermare mute se il comando non è verificato. Playback indipendente conservato.
- Google: usa la cattura propria esistente. Rilascio arresta la cattura e attende il suo rilascio prima di grigio/congelamento e finalizzazione del parlato già trasmesso. La finalizzazione dello stream è singola e non viene sostituita dalla cancellazione distruttiva prevista dal tap mute. Le callback di avvio tardive rispettano il flag mute. Nuove dettature restano impedite mentre muto; il completamento della trascrizione/invio già iniziati è distinto dall’ascolto.

## Verifiche realmente eseguite qui

- `git diff --check` e controllo manifest XML: passati.
- `python3 scripts/sync-guide.py --check`: passata coerenza dei 33 argomenti tra sorgente, asset app, guida leggibile e sorgente backend.
- 5 script Node passati: `test-cover-microphone.cjs`, `test-google-text.cjs`, `test-prepared-chat.cjs`, `test-web-voice.cjs`, `test-desktop-web.cjs`. Microfono esteso con ripristino ripetuto idempotente e playback conservato.
- Worker simulato: **48 test passati**, nessuna rete/provider reali. Il primo run aveva soltanto l’asserzione della versione guida precedente: aggiornata alla versione preparata e rieseguita la suite con esito positivo.
- Nuovi test JVM/Robolectric **scritti ma non eseguiti**: `ConfirmedMicrophoneTargetTest`, `CoverMicrophoneHoldTest`, `BridgeLinkServiceTest`; estesa `GoogleVoiceRuntimeTest` con finalizzazione singola al rilascio e mancata conferma mute prima del rilascio del recorder. Richiedono compilazione nel cloud.

Questi controlli non provano compilabilità Kotlin, manifest/FGS reali, esecuzione Android, consumo batteria, avvio dopo boot o successo del gesto sul Galaxy.

## Lavoro concordato ancora da completare nel cloud Jarvis

1. Verificare identità dell’ambiente, checkout/toolchain/firma preesistenti. Fare fetch e integrare `origin/main` fast-forward senza cancellare lavoro locale. Leggere questo handoff e le regole aggiornate: il gruppo è già autorizzato.
2. Eseguire **l’intera suite JVM/Robolectric SDK 34 e 36**, inclusi i nuovi test, `NativeExitStateTest`, `NativeAudioRecoveryTest`, regressioni Google, menu e gesture. Usare JDK17 per Gradle e Java21 per i test secondo la procedura esistente (`jarvisTestJava`/`jarvisTestSdks`). Eseguire lint. Correggere eventuali errori nello stesso gruppo, aggiornando documentazione e risultati in ogni commit.
3. Aggiungere/estendere dove necessario le verifiche d’integrazione del worker reale con trasporto simulato (handoff pagina/servizio e worker unico) e delle callback WebView ritardate/timeout/rilascio/cambio documento. I test presenti su servizio e target non sostituiscono questi casi end-to-end.
4. Ripetere gli script Node e la suite Worker se una correzione li riguarda. Nessuna API Google reale né invio/chiamata automatici per il collaudo.
5. Compilare un solo APK `.115/137`, firmarlo con il certificato permanente e verificare versione, ABI, ZIP, certificato, zipalign e librerie 16KB secondo la procedura consolidata. Non usare GitHub Actions né creare chiavi sostitutive.
6. Registrare risultati effettivi, checksum e ambiente usato in `VERIFICATION.md`, continuità, changelog/roadmap. Committare e fare push fast-forward; verificare `HEAD == origin/main` prima della consegna APK. Nessun APK/keystore/output build in Git.
7. La guida app/backend è aggiornata nei sorgenti. **Nessun backend o plugin è stato pubblicato da questa chat**: la guida online può ancora mostrare `.114`. Un eventuale deploy del backend segue le autorizzazioni specifiche esistenti e non è implicito nel commit.

## Collaudo fisico rimasto all’utente

- Con pairing attuale e collegamento abilitato: chiudere la pagina e richiedere lo stato dal plugin; ripetere con wake spenta, altra app in primo piano, lock/unlock e dopo rete assente/ripristinata. Verificare avvio dopo reboot/aggiornamento, notifica e disattivazione esplicita, assenza di cattura audio dal solo collegamento.
- Nativo, WebView e Google: tap breve, doppio tap, pressione da grigio, rilascio prima della conferma unmute, trascinamento/multitouch/CANCEL, chiusura durante pressione e cambio schermata; microfono torna muto e nessuna inversione extra. Da attivo, pressione lunga senza nuova funzione.
- Google: il parlato durante pressione deve produrre una sola trascrizione/invio dopo rilascio; nessun riavvio cattura mentre grigio. Nativo/WebView: playback continua e colori non anticipano conferme non ricevute.
- Conservare uscita immediata della cover, swipe sinistro hide senza stop, basso/Home e doppio tap con stop singolo, nessun saluto/Voice duplicati, wake sospesa fino al rilascio audio.

Gli altri sviluppi e collaudi già concordati restano nella roadmap precedente: questo handoff non li considera completati e non ne amplia l’autorizzazione.
