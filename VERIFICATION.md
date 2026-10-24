# 0.5.51 — eliminazione dati locali ChatGPT, APK firmato

- Aggiunto il pulsante **Elimina dati locali ChatGPT** separato dalla sola pulizia cache. Una finestra di conferma avvisa che la sessione sarà cancellata e richiederà un nuovo accesso; le conversazioni conservate nell'account non vengono eliminate.
- La pulizia elimina la cache risorse WebView, cookie con callback asincrona, storage JavaScript/Web SQL e dati HTTP/form salvati dal componente WebView; quindi ricrea il preloader sul progetto configurato. L'azione non è consentita durante un ciclo Voice attivo.
- Build locale JDK 17 / Gradle 8.13 / Android SDK 36 / Build Tools 36.0.0: `clean testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true` — `BUILD SUCCESSFUL`.
- Test JVM: 50 superati, 0 falliti, 0 errori, 0 saltati. `node scripts/test-web-voice.cjs`: superato. Lint: 0 errori, 20 avvisi.
- APK `it.jarviswake`, versionName `0.5.51`, versionCode `73`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. Certificato permanente SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; schema v3 verificato.
- `zipalign -c -P 16 -v 4` e `unzip -t`: superati. SHA-256 APK `89b1957424206b56e148bd05ffb8480eb49db4f76ee896fb959a65de0bd38ecd`, registrato in `releases/0.5.51/SHA256SUMS`. Resta la prova sul Galaxy S24+; non è confermato che questo reset risolva l'errore Voice.

# 0.5.50 — pulizia mirata della cache WebView, APK firmato

- Aggiunto alla schermata principale il pulsante **Svuota cache WebView**. Usa `WebView.clearCache(true)` per eliminare la cache delle risorse dell'app e ricarica il WebView precaricato; non rimuove cookie né DOM storage, quindi non cancella il login ChatGPT.
- Se non esiste un preloader, una WebView temporanea pulisce la cache condivisa nell'app e viene poi ricreato il preloader. L'azione viene rifiutata durante un ciclo Voice attivo.
- Build locale con JDK 17, Gradle 8.13, Android SDK 36 e Build Tools 36.0.0: `clean testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true` — `BUILD SUCCESSFUL`.
- Test JVM: 50 superati, 0 falliti, 0 errori, 0 saltati. `node scripts/test-web-voice.cjs`: superato. Lint: 0 errori, 20 avvisi.
- APK firmato: `it.jarviswake`, versionName `0.5.50`, versionCode `72`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. Certificato permanente SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; firma v3 verificata (v2 non abilitata nell'APK, compatibile con minSdk 31).
- `zipalign -c -P 16 -v 4` e `unzip -t`: superati. SHA-256 APK firmato `4754116efc4ede6deba51d3c8c6e90e9acb8257be4211a41794521f4c93cf2f5`, registrato in `releases/0.5.50/SHA256SUMS`. Collaudo sul Galaxy S24+ ancora da fare.

# 0.5.49 — configurazione URL progetto e APK firmato

- Aggiunto un campo persistente nella schermata principale con l'indirizzo fornito dall'utente come valore iniziale. Il preloader e `WebChatActivity` leggono la stessa preferenza, perciò anche il ciclo wake usa la destinazione salvata. Quando il valore cambia, il WebView precaricato viene scartato e ricreato.
- La convalida limita l'URL a HTTPS su `chatgpt.com` con percorso `/g/g-p-…/project`, senza porta, credenziali, query o frammento. Le nuove stringhe sono in `res/values/strings.xml` con commento XML sullo scopo della preferenza.
- Build pulita con JDK 17.0.21, Gradle 8.13, Android SDK 36 e Build Tools 36.0.0: `clean testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true` — `BUILD SUCCESSFUL`.
- Test JVM: 50 superati, 0 falliti, 0 errori, 0 saltati. `node scripts/test-web-voice.cjs`: superato. Lint: 0 errori, 20 avvisi.
- APK firmato: `it.jarviswake`, versionName `0.5.49`, versionCode `71`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. Firma v2 verificata per API 24 e v3 per API 31+. Fingerprint SHA-256 certificato permanente `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`.
- Allineamento a 16 KB e integrità ZIP verificati. SHA-256 APK firmato `c8458def818f02489e1ee651c81a020041ee99084fe6d840d9179f8e92348e04`; registrato in `releases/0.5.49/SHA256SUMS`. Prova sul Galaxy S24+ ancora da fare.

# 0.5.48 — ricostruzione dal bytecode e build

- APK storico fornito dall'utente: `it.jarviswake`, versionCode 70, ARM64, firmato col certificato permanente SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; SHA-256 `1cd72be0dc60c3eabd2801f856b90f37b4aa0e426ba23dd27eaed9239e8a9213`.
- Differenze osservabili riportate nei sorgenti Kotlin: trasferimento della stessa WebView ChatGPT fra preloader e pagina interattiva; configurazione dal pulsante «Imposta modello e livello ChatGPT»; swipe a sinistra per fermare Voice e mostrare la chat; wake successiva sulla conversazione mantenuta; swipe verso il basso ancora diretto alla Home. Aggiunti gli eventi `CHAT_REVEAL_GESTURE_RECOGNIZED`, `CHAT_REVEALED_FOR_INTERACTION`, `CHAT_REVEAL_FAILED` e test del gesto.
- La decompilazione ricostruisce il comportamento, non gli originali Kotlin o i test storici. Build locale pulita: `./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true` — `BUILD SUCCESSFUL`; 45 test JVM superati, lint senza errori (20 avvisi), `node scripts/test-web-voice.cjs` superato.
- APK finale firmato: `it.jarviswake`, versionCode 70, versionName `0.5.48`, minSdk 31, target/compile SDK 36, ABI `arm64-v8a`; SHA-256 `2b8a26af1ca747c7d80a2bd2f8883cd2da47ea6739cedf58aca648d7b2480590`. Allineamento 16 KB verificato; `apksigner verify --verbose --print-certs --min-sdk-version 31` conferma schema v3 e certificato permanente SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`, uguale a quello dell'APK storico fornito. Il certificato è stato esportato anche direttamente dal keystore e il digest coincide. Collaudo sul Galaxy S24+ ancora da fare.
- La build unsigned intermedia ha SHA-256 `369f19c334b68a395bbc7debca97a0f3b2ed7184cc75bfb85a831ae1cb013355`; non installarla. Il checksum della versione firmata è in `releases/0.5.48/SHA256SUMS`.

# 0.5.47 — verifica della sonda diagnostica

- La sonda ora registra `ASSISTANT_MONITOR_QUERY` ogni 5 secondi durante il polling, `ASSISTANT_MONITOR_RESULT` quando i conteggi cambiano e con heartbeat di 5 secondi, e `ASSISTANT_MONITOR_INVALID_RESULT` per callback non-stringa, JSON/schema non validi o errore di valutazione. I conteggi distinguono nodi con attributo ruolo, selettore assistant, nodi visibili, candidati leggibili e candidati stabili; ogni evento è correlato alla generazione documento.
- L'esportazione attende che la coda di scrittura del log completi gli eventi precedenti prima di copiare i file nell'archivio.
- Il log resta privo di testo, hash, audio, URL e credenziali. L'assenza di risposta ora è classificabile: query inviata senza callback, errore callback, zero nodi selezionati, nodi non visibili/non leggibili o risposta stabile senza evento duplicato.
- `node scripts/test-web-voice.cjs`: superato, compresi esiti di monitor con selettore corrispondente e non corrispondente, stabilità, aggiornamenti, duplicati esatti e ripetizioni interne senza esportare testo. Test JVM: 43 superati, 0 falliti, 0 errori, 0 saltati. `lintDebug`: 0 errori/18 avvisi.
- `clean testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true`: `BUILD SUCCESSFUL` con JDK 17, Gradle 8.13, SDK 36 e Build Tools 36.0.0. Nessun dispositivo collegato.
- APK non firmato: pacchetto `it.jarviswake`, versionCode 69, versionName `0.5.47`, minSdk 31, target/compile SDK 36, ABI arm64-v8a; SHA-256 `cfdce6f36140dcfe2d6719bc049e1a942e065747db69e3bc19cfb23caf1f8c91`. CRC ZIP e allineamento 16 KB superati; `apksigner` conferma che non è firmato. Lo script Termux dedicato controlla hash e certificato permanente prima di firmare.
- APK firmato incluso nel pacchetto di prova: SHA-256 `dae84a27f83675a2916bececb98ae123c706b7d188f2cbbc9fa52caf7e14fe93`; certificato permanente `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`, schemi v2/v3 e allineamento 16 KB verificati. Pacchetto `JarvisWake-0.5.47-test-bundle.zip`, CRC verificato; SHA-256 `7d10f774891577b51dc0b8b2938869cee4b5cba161b2d6c07d65a6521bad81ff`.
- Resta il test sul Galaxy S24+: firmare dal Termux, installare l'APK firmato, riprodurre il duplicato e poi esportare il log.

# 0.5.46 — diagnostica delle risposte ripetute

- Il WebView osserva i soli container visibili dell'assistente `[data-message-author-role="assistant"]`. Una risposta viene riportata dopo 1,2 secondi senza modifiche, con ID, revisione e lunghezza. Il testo viene confrontato solo in memoria nel WebView per riconoscere risposte identiche tra messaggi e frasi ripetute nello stesso messaggio. Il log esportato non contiene testo, hash, audio, URL o credenziali.
- `VOICE_MEDIA_PLAYBACK_STARTED` e `VOICE_MEDIA_PLAYBACK_STOPPED` registrano ogni transizione osservata della riproduzione media; `sequence` conta gli avvii. `ASSISTANT_RESPONSE_*` include `response`, `revision`, `chars`, `duplicateOf` e `repeats` quando pertinenti.
- Test: 43 test JVM superati; `node scripts/test-web-voice.cjs` superato; lint 0 errori/18 avvisi. `clean testDebugUnitTest lintDebug` e `assembleDebug -PjarvisUnsigned=true` superati con JDK 17, Gradle 8.13, SDK 36 e Build Tools 36.0.0. Test DOM: stabilità, aggiornamento, duplicazione tra due messaggi, ripetizione di frasi e assenza del testo nei dati restituiti.
- APK firmato con il certificato permanente. Certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; schema v2 verificato per API 24–27 e v3 per API 31+; allineamento 16 KB e CRC ZIP superati. Pacchetto `it.jarviswake`, versionCode 68, versionName `0.5.46`, minSdk 31, target/compile SDK 36, ABI arm64-v8a. SHA-256 APK `68db2837289d9c218f6679a0a7f410f2c722c3c91e24396f28c95fd8289bb023`.
- Sul Galaxy S24+ riprodurre una risposta che in precedenza si è ripetuta, attendere qualche secondo e poi esportare il log. `ASSISTANT_RESPONSE_DUPLICATE` indica due container distinti con testo esattamente uguale; `ASSISTANT_RESPONSE_INTERNAL_REPEAT` segnala frasi duplicate nel medesimo container; più sequenze `VOICE_MEDIA_PLAYBACK_STARTED` segnalano ripartenze osservate del media. Le frasi minime corte (<10 caratteri) sono ignorate dal rilevatore di ripetizioni interne.
- Limite: il rilevatore dipende dall'attributo DOM corrente di ChatGPT e dal polling dello stato Voice. Se la pagina cambia markup o se la ripetizione avviene solo nell'audio senza transizione media osservabile, il log potrebbe non catturarla; il test fisico resta necessario.

# 0.5.45 — frase configurabile dopo l'attivazione Voice

- Aggiunto nella schermata principale un campo con i comandi Salva e Ripristina «ciao jarvis». Le preferenze sono locali all'app e persistono dopo la chiusura; una frase vuota non sovrascrive quella salvata.
- La frase configurata viene letta all'inizio del ciclo di invio e codificata con `JSONObject.quote` prima di inserirla nello script della pagina. Le protezioni esistenti contro bozze sovrascritte e invii duplicati restano attive.
- Test aggiunto per normalizzazione e rifiuto del campo vuoto; test DOM esteso con apostrofo e virgolette nel testo configurato. `node scripts/test-web-voice.cjs`: superato.
- `./gradlew --no-daemon --no-watch-fs clean testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true`: superato con JDK 17, Gradle 8.13 e Android SDK 36. Test unitari e lint superati.
- APK firmato con la chiave permanente. Certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; firma v2 verificata per API 24–27 e v3 per API 31+. `zipalign -c -P 16 -v 4` e CRC ZIP: superati. Pacchetto `it.jarviswake`, versionCode 67, versionName 0.5.45, minSdk 31, targetSdk 36, ABI arm64-v8a. SHA-256 `24cc53e03d46dda28f1ca1b9b829f8d1a42a4fb6229ae8dec60591c800994299`.
- Prova ChatGPT reale sul Galaxy S24+ da fare.

# 0.5.44 — X Voice corrente nell'angolo inferiore destro

- La schermata attiva viene interrogata al momento dello swipe. Sono candidati i controlli visibili, abilitati e raggiungibili, compatti e nella fascia inferiore destra; viene selezionato il più a destra senza richiedere un'etichetta Voice/X. Il punto iniziale non viene conservato né riusato.
- Test JVM: 41 superati, 0 falliti, 0 errori, 0 saltati. `node scripts/test-web-voice.cjs`: superato, inclusi X senza etichetta in basso a destra, controllo superiore ignorato, mute a sinistra, disabilitato e nessun target. `lintDebug`: 0 errori e 18 avvisi.
- Build locale JDK 17, Gradle 8.13, SDK 36 e Build Tools 36.0.0: `testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true` superata. Pacchetto `it.jarviswake`, versionName `0.5.44`, versionCode `66`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. ZIP CRC e allineamento 16 KB verificati.
- APK firmato con il certificato permanente: SHA-256 certificato `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; schema v3 verificato per API 31+ e schema v2 verificato separatamente per API 24–27. SHA-256 APK firmato `9ce6d51bb2b00e6645f0388acb319211a59f98c53be23341b453b3199cace18f`. L'APK firmato mantiene versione `0.5.44` / versionCode `66` ed è allineato a 16 KB.
- Resta la prova del gesto e del controllo X sulla UI reale del Galaxy S24+; i test DOM usano una pagina simulata.

# 0.5.43 — ripetizione del tocco Voice alle coordinate salvate

- I log della prova 0.5.42 mostrano `HIDE_GESTURE_RECOGNIZED` seguito da `VOICE_STOP_TARGET_NOT_FOUND`: lo swipe funziona, ma la ricerca semantica della X non la riconosceva.
- All'apertura Voice vengono salvate le coordinate normalizzate del tocco nativo. Quando lo swipe viene riconosciuto, Jarvis invia un singolo DOWN/UP nativo allo stesso punto della WebView, senza consultare il DOM, cercare la X o aspettare di trovarla. Poi controlla per 2 secondi se Voice è tornata allo stato iniziale.
- Test JVM: 42 superati, 0 falliti, 0 errori. `node scripts/test-web-voice.cjs`: superato; verifica anche che lo stop non interroghi più il DOM. `lintDebug`: 0 errori e 18 avvisi.
- Build locale con JDK 17, Gradle 8.13, SDK 36 e Build Tools 36.0.0: `testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true` superata.
- APK: `it.jarviswake`, versionName `0.5.43`, versionCode `65`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. ZIP CRC e allineamento 16 KB verificati.
- APK firmato con il certificato permanente, SHA-256 certificato `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; schema v3 verificato per API 31+ e v2 per API 24–27. SHA-256 APK `dc3a86698072d8465c0bbb022136df8a207b66ff937b160b87244c7dc9016711`.
- Resta la prova sul Galaxy S24+: verificare che il tocco alle coordinate salvate fermi Voice, nasconda la cover e mostri Home. La build e i test locali non provano il comportamento del controllo nella UI reale di ChatGPT.

# 0.5.42 — target di uscita Voice rilevato nella schermata attiva

- Il feedback con screenshot della 0.5.41 mostra la UI Voice ancora aperta e la `X` visibile dopo lo swipe. Il punto riutilizzato dall'avvio non ha chiuso la sessione.
- Prima dell'unico tocco di stop, il DOM individua nella UI corrente un controllo di uscita Voice visibile, abilitato e raggiungibile. Il target deve essere unico e semanticamente legato a Voice e alla chiusura; se manca o è ambiguo, non parte alcun tocco.
- Dopo il DOWN/UP nativo, lo stato viene verificato ripetutamente per un massimo di 2 secondi. Gli swipe duplicati restano bloccati durante il tentativo. La diagnostica registra target trovato/mancante, tocco inviato e conferma o fallimento, senza testo, audio o URL.
- Test JVM: 38 superati, 0 falliti, 0 errori, 0 saltati. `node scripts/test-web-voice.cjs`: superato, inclusi controllo Voice corrente unico, target ambiguo e chiusura generica rifiutata. `lintDebug`: 0 errori e 18 avvisi.
- Build locale JDK 17, Gradle 8.13, SDK 36 e Build Tools 36.0.0: `testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true` superata.
- APK: `it.jarviswake`, versionName `0.5.42`, versionCode `64`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. ZIP CRC e allineamento 16 KB verificati.
- Firma permanente: certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; schema v3 verificato per API 31+ e schema v2 verificato separatamente per API 24–27. SHA-256 APK firmato `86d09959e19f71679390f539da5f6fd18f8c30d42a924c7e8ea137eea6054b64`.
- Resta la prova sul Galaxy S24+: verificare che il controllo `X` venga premuto, Voice si chiuda, la cover sparisca e si apra Home. I test DOM non riproducono il DOM effettivo della UI ChatGPT sul telefono.

## 0.5.41 — riuso del punto del controllo Voice per avvio e arresto

- La posizione del controllo trovata e premuta per avviare Voice viene conservata per il ciclo e riutilizzata per lo stop, scalata alle dimensioni correnti della WebView. Non viene cercato un secondo comando di uscita.
- Una richiesta di arresto già in corso ignora ulteriori swipe finché non torna il risultato, evitando un secondo tocco sul toggle e una possibile riapertura.
- La conferma dopo lo stop considera valido il candidato nel compositore compatto oltre al controllo semantico; entrambi provengono dalla stessa ricerca prudente già usata per l'avvio.
- Il blob `WebChatActivity.kt` del commit baseline 0.5.40 era binario non compilabile come sorgente. Il file Kotlin è stato ripristinato dalla revisione leggibile precedente e integrato con i metodi e la logica della 0.5.41; il build corrente è stato eseguito davvero.
- Test JVM: 40 superati, 0 falliti. `node scripts/test-web-voice.cjs`: superato. `lintDebug`: 0 errori e 18 avvisi.
- Build locale con JDK 17.0.20.1, Gradle 8.13, SDK 36 e Build Tools 36.0.0: `testDebugUnitTest lintDebug assembleDebug -PjarvisUnsigned=true` superata usando la cache locale dei repository; `settings.gradle.kts` è stato ripristinato dopo la build.
- APK: `it.jarviswake`, versionName `0.5.41`, versionCode `63`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. ZIP CRC e allineamento 16 KB verificati.
- Firma permanente verificata: certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; schema v2 verificato con API 24–27 e v3 con API 31+. SHA-256 APK firmato `3e85f77d335f928c5897fdea6fbc31469a363262f6ffcc24059b74cefbbbe92a`.
- Resta la prova sul Galaxy S24+: lo swipe deve usare lo stesso punto, chiudere Voice, nascondere la cover e lasciare la conversazione riattivabile con la wake word.

# 0.5.40 — swipe cover in coordinate schermo e stato di arresto Voice

- Dopo il mancato effetto osservato con la 0.5.38 e la correzione insufficiente della 0.5.39, la zona iniziale ora viene valutata usando posizione assoluta della vista, inset di sistema e altezza reale della cover. Sono coperte sia la cover interna già spostata dal padding sia l'overlay flottante.
- La fascia valida è ampia nella parte superiore della cover e inizia sotto la barra di stato; restano esclusi tocchi dalla barra di sistema, swipe orizzontali o verso l'alto e inizi troppo in basso. Se il gesto è riconosciuto ma l'arresto di ChatGPT Voice non viene confermato, la cover mostra un errore e il log diagnostico registra l'esito.
- Test JVM: 38 superati, 0 falliti, 0 errori, 0 saltati. Test DOM superati. `lintDebug`: 0 errori e 18 avvisi.
- Build locale con JDK 17, Gradle 8.13, SDK 36 e Build Tools 36.0.0 completata. APK ARM64, versionName `0.5.40`, versionCode `62`, minSdk `31`, target/compile SDK `36`. ZIP CRC e allineamento 16 KB verificati.
- Firma permanente: certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; v2 verificata per API 24–27, v3 per API 31+. SHA-256 APK firmato `9225cd5cfe87cc1a7867b4dd2a273c84baa6de652ac4c7a6ce8f954a45b6f438`.
- Resta la prova sul Galaxy S24+: verificare che lo swipe nasconda la cover, termini davvero Voice e permetta la riattivazione con wake word.

# 0.5.39 — coordinate locali dello swipe sulla cover

- La cover interna è contenuta in una root che applica il padding della barra di stato. Prima la logica riutilizzava l'inset assoluto senza sottrarre la posizione della vista, spostando troppo in basso la zona touch valida. Ora l'inset viene convertito rispetto all'origine della cover; l'overlay flottante continua a riferirsi al bordo del display.
- Aggiunto test JVM che verifica una partenza vicino al bordo superiore locale quando il padding è già applicato.
- Compilazione con `local-build-kit/build-local.sh`, toolchain verificata JDK 17.0.20.1+1 / Gradle 8.13 / SDK 36 / Build Tools 36.0.0. Test DOM superati; test JVM: 37 superati, 0 falliti, 0 errori, 0 saltati. Lint: 0 errori e 18 avvisi.
- APK firmato: `it.jarviswake`, versionName `0.5.39`, versionCode `61`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. SHA-256 `4f5e310c45a0da250507f385a883b7910d872068acc87f7df81ad5f752c49396`. Certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`, corrispondente al certificato permanente; schemi v2 e v3 verificati. ZIP CRC e allineamento 16 KB verificati.
- Resta il collaudo sul Galaxy S24+: lo swipe deve nascondere la cover, fermare Voice e mostrare Home.

# 0.5.38 — riconoscimento swipe dalla cover (APK firmato per prova)

- Corretto il riconoscimento del drag: la cover valuta il movimento del dito durante `ACTION_MOVE`, parte in una fascia alta interna sotto la barra di stato, usa soglia di 72 dp e tollera una diagonale discendente. Lo swipe orizzontale, quello verso l'alto e le partenze nella barra di sistema non attivano l'azione.
- Aggiunta un'indicazione visiva in alto: «SCORRI VERSO IL BASSO PER HOME». Al riconoscimento, la logica esistente termina Voice, nasconde la cover e mostra la Home; la pagina resta disponibile per la riattivazione con «Ehi Jarvis».
- Test JVM: 36 superati, 0 falliti, 0 errori, 0 saltati. Nuovi casi coprono soglia, direzione, zona iniziale, trascinamento diagonale e reset. `node scripts/test-web-voice.cjs`: superato. `lintDebug`: 0 errori e 18 warning.
- Build pulita locale con JDK 17, Gradle 8.13, SDK 36: `clean testDebugUnitTest lintDebug assembleDebug` superata. APK arm64 allineato a 16 KB.
- APK: `it.jarviswake`, versionName `0.5.38`, versionCode `60`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`.
- Firma permanente verificata con alias `jarvis`; certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; firme v2 e v3 verificate separatamente. `zipalign -c -P 16 -v 4`: superato.
- SHA-256 APK firmato: `306d298e7b448c07529da1e60036c33cc08717ea3808362cfba8605fa21ed4a3`.
- Resta la prova sul Galaxy S24+: verificare che lo swipe iniziato nella fascia indicata nasconda davvero la cover, fermi l'audio Voice e poi permetta wake e ripresa della stessa conversazione.

# 0.5.37 — cover nascosta e riattivazione con wake word (APK firmato per prova)

- Implementato lo swipe verso il basso per nascondere la cover e tornare alla Home; prima di nasconderla l'app termina la modalità Voice per rilasciare il microfono, mantiene la pagina/conversazione WebView e riprende Voice sulla stessa pagina al trigger «Ehi Jarvis».
- Un tocco sui cerchi alterna opaco/semitrasparente; doppio tocco al centro chiude il ciclo; doppio tocco in alto a destra esporta il log diagnostico locale. Rimosso il pulsante X.
- Diagnostica esportata in archivio locale limitato, senza testo conversazione o audio.
- Test JVM: 31 superati, 0 falliti, 0 errori, 0 saltati. `node scripts/test-web-voice.cjs`: superato. `lintDebug`: 0 errori e 17 warning.
- Build locale con JDK 17, Gradle 8.13 e SDK 36: `clean testDebugUnitTest lintDebug assembleDebug` superata. APK arm64 allineato a 16 KB.
- APK: `it.jarviswake`, versionName `0.5.37`, versionCode `59`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`.
- Firma permanente verificata: alias `jarvis`; certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`; firme v3 e v2 verificate separatamente. `zipalign -c -P 16 -v 4`: superato.
- SHA-256 APK firmato: `ac44b814881fa7512bdf95acd1f71f1b249ec1f6062cd9cb274aae33f52cb7b4`.
- Limite della prova: nessun telefono collegato all'ambiente. Va collaudato sul Galaxy S24+ che lo swipe mostri la Home, il microfono di ChatGPT si fermi, Jarvis rilevi «Ehi Jarvis» e Voice riprenda nella stessa conversazione.

## 0.5.36 — riconoscimento campo attivo e diagnostica del saluto (candidato locale)

- Dopo la prova dell'utente con la 0.5.35, la schermata mostrava una chat nuova e il cursore nel campo vuoto, ma non il saluto. Questo conferma il sintomo sul Galaxy S24+; non identifica da solo il ramo DOM responsabile.
- `FILL_GREETING_SCRIPT` riconosce «Digita» e altre etichette localizzate; come fallback considera il campo editabile già attivo, se visibile e vuoto. Mantiene le due letture stabili e non chiama `focus()`.
- La diagnostica registra solo transizioni di stato Voice e categorie di risultato del saluto, senza contenuto, audio, URL o credenziali. Risultati ripetuti uguali non vengono riscritti a ogni ciclo di polling.
- `node scripts/test-web-voice.cjs`: superato, inclusi campo italiano/non etichettato, protezione delle bozze e invio singolo. Test JVM: 29 superati, 0 falliti, 0 errori, 0 saltati. `lintDebug`: 0 errori e 16 warning.
- Build locale con JDK 17, Gradle 8.13 e SDK 36: `clean testDebugUnitTest lintDebug assembleDebug` superata. Il repository Maven locale è stato usato; non sono stati necessari download durante la build.
- APK: `it.jarviswake`, versionName `0.5.36`, versionCode `58`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`. SHA-256 dell'APK unsigned: `76c9a1666538deef12fc0be0339e98f05965bfa9918f5d0a9102a0630aa57b62`.
- `zipalign -c -P 16 -v 4`, CRC ZIP e metadati APK: superati. La build è unsigned; `apksigner` conferma che non ha firma. Non è installabile come aggiornamento finché non viene firmata con il certificato permanente.
- Nessun dispositivo è collegato all'ambiente: resta da installare e provare sul Galaxy S24+ che il saluto sia compilato e inviato una sola volta senza sovrascrivere testo o aprire la tastiera.

## 0.5.35 — retry sicuro del saluto (candidato locale)

- Rimossa la scadenza di 10 secondi che trasformava `NOT_READY` in un esito terminale. `NOT_READY` è ripetibile perché lo script garantisce che nessun click di invio è avvenuto.
- Aggiunta una policy testata: solo `NOT_READY` è ritentabile; `SENT` è terminale come inviato; campo occupato e risultato ambiguo sono terminali per evitare sovrascritture o duplicati.
- `node scripts/test-web-voice.cjs`: superato. Test JVM: 29 superati, 0 falliti, 0 errori, 0 saltati. `lintDebug`: 0 errori e 16 avvisi.
- Build locale con JDK 17, Gradle 8.13, SDK 36 e Build Tools 36.0.0: `BUILD SUCCESSFUL`; `testDebugUnitTest lintDebug assembleDebug` superati.
- APK: `it.jarviswake`, versionName `0.5.35`, versionCode `57`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`.
- APK firmato permanente: `JarvisWake-0.5.35-debug-arm64-permanent.apk`; SHA-256 `5e816e5c6162c1def057c3b44c4a5fc4a8573c538b45bece549a3d88c8eb2de8`.
- Certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`, uguale a quello permanente. Verifica v3 superata; v2 verificata separatamente nell'intervallo API 24–27, metodo necessario perché il controllo limitato a minSdk 31 usa v3. `zipalign -c -P 16 -v 4` e `unzip -t`: superati. APK escluso da Git.
- Prova dell'utente sul Galaxy S24+: la chat si apre e il cursore resta attivo, ma il saluto non viene inserito né inviato. La causa non è distinguibile dai log della 0.5.35; la 0.5.36 aggiunge il fallback sul campo attivo e diagnostica per individuare la fase.

## 0.5.34 — inserimento del saluto senza focus

- Modificato `FILL_GREETING_SCRIPT`: il saluto «ciao jarvis» viene inserito nel DOM senza `target.focus()` e senza `document.execCommand`, che senza focus potrebbe agire sulla selezione errata. Il test protegge dall'uso del focus, verifica il testo in `contenteditable` e conserva la protezione contro invii duplicati.
- `node scripts/test-web-voice.cjs`: superato. Test JVM: 28 superati, 0 falliti, 0 errori, 0 saltati. `lintDebug`: 0 errori, 17 avvisi preesistenti.
- Build locale: `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` — superata.
- APK: `it.jarviswake`, versionName `0.5.34`, versionCode `56`, minSdk `31`, target/compile SDK `36`, ABI ARM64 (`arm64-v8a`).
- APK firmato: `JarvisWake-0.5.34-debug-arm64-permanent.apk`; SHA-256 `9e799398e10818d4369a704daef55200101d2b79f9c29cbe6d86ceaeab19e136`.
- `apksigner verify`: schema v3 valido; certificato SHA-256 permanente `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`. `zipalign -c -P 16 -v 4`, `unzip -t` e metadati `aapt`: superati. Checksum in `releases/0.5.34/SHA256SUMS`; APK escluso da Git.
- Il Galaxy S24+ non è collegato a questo ambiente: va verificato se la tastiera smette di lampeggiare e se ChatGPT riceve e invia ancora il saluto una sola volta. La prova fisica della 0.5.33 ha confermato wake entro i 30 secondi a schermo spento, nessun avvio dopo la scadenza e ripresa immediata all'accensione.

## 0.5.33 — finestra microfono screen-off e modello residente

Build locale eseguita il 20 ottobre 2026 con Gradle Wrapper 8.13, Eclipse Temurin JDK 17.0.20.1, Android SDK platform 36 e Build Tools 36.0.0. Nessuna GitHub Actions è stata usata.

- `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`: `BUILD SUCCESSFUL`; 28 test JVM superati, 0 falliti, 0 errori.
- `node scripts/test-web-voice.cjs`: superato.
- Lint: 0 errori e 17 warning già pertinenti a risorse/compatibilità, splash screen, strings, WebView, dipendenze e ABI; nessun blocco alla build.
- APK locale: `it.jarviswake`, versionName `0.5.33`, versionCode `55`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`; 55,614,488 byte.
- APK firmato: `JarvisWake-0.5.33-debug-arm64-permanent.apk`; SHA-256 `83760c8d9127d8d06bf884a06e2a96c3d43e8310af569e85d673b85d61fcb21d`.
- Certificato permanente SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`, verificato. `apksigner verify` conferma schema v3; il manifest ha minSdk 31. Schema v1/v2 non abilitati per questo APK.
- `zipalign -c -P 16 -v 4` e `unzip -t`: superati. `aapt dump badging`: pacchetto, versione, SDK e metadati verificati. Checksum anche in `releases/0.5.33/SHA256SUMS`; l'APK non è committato.
- Le verifiche di build non provano il comportamento effettivo del microfono dopo screen-off né la ripresa alla pressione del tasto laterale sul Galaxy S24+; questo richiede collaudo fisico.

Modifiche: il `WakeService` mantiene un unico `Model` e `Recognizer` nel worker finché resta attivo il servizio. A `ACTION_SCREEN_OFF` mantiene la cattura per 30 secondi; alla scadenza chiude `AudioRecord` e rilascia il wakelock parziale, continuando ad attendere con modello/recognizer residenti. `ACTION_SCREEN_ON` annulla la scadenza e riapre immediatamente `AudioRecord`. Wake rilevate nella finestra screen-off seguono il normale flusso di ciclo; Voice continua a sospendere l'ascolto Vosk. La diagnostica aggiunge `SCREEN_OFF_MIC_GRACE_STARTED`, `SCREEN_OFF_MIC_GRACE_EXPIRED`, `VOSK_MODEL_READY` e `VOSK_MODEL_RELEASED`. Test unitari aggiunti per la soglia di 30 secondi e per la precedenza del ciclo Voice.

# Registro verifiche — JarvisWake

## 0.5.32 — preloader dietro l’interfaccia e APK firmato

Build locale eseguita il 19 ottobre 2026 con JDK 17, Gradle 8.13 e Android SDK/Build Tools 36.0.0. Build/test senza GitHub Actions.

- Correzione: `ChatGptPreloader` aggiunge la WebView a schermo intero come figlio 0 del `FrameLayout`; lo `ScrollView` principale resta sopra e riceve i tocchi. L’opacità zero da sola non impediva alla WebView di interferire con il touch dispatch.
- `testDebugUnitTest lintDebug assembleDebug`: BUILD SUCCESSFUL; 25 test JVM superati. `node scripts/test-web-voice.cjs`: superato.
- APK: `it.jarviswake`, versionName `0.5.32`, versionCode `54`, minSdk `31`, target/compile SDK `36`, ABI `arm64-v8a`.
- APK firmato: `JarvisWake-0.5.32-debug-arm64-permanent.apk`; SHA-256 `d381bcc76be2e10a5d2731395908cc7a4a0b123417191f888ba2e2fd90ec914b`.
- Certificato permanente SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`, verificato. Schema v3 verificato per API 31+. `zipalign -c -P 16 -v 4` e `unzip -t` superati; APK ARM64 e versione verificati con `aapt dump badging`.
- Il test sul telefono deve confermare che i pulsanti restino interattivi mentre il preloader carica; la build non certifica touch, Voice, rete o risposta audio su One UI.

## 0.5.31 — build locale e firma

Build eseguita il 19 ottobre 2026 con JDK 17, Gradle 8.13, Android SDK 36 e Build Tools 36.0.0. Non è stata usata GitHub Actions.

```text
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
BUILD SUCCESSFUL — 53 task
```

- Test JVM: 25 superati, 0 falliti, 0 saltati. `node scripts/test-web-voice.cjs`: superato, inclusi casi di stato audio playback e invio saluto.
- APK locale: `it.jarviswake`, versionName `0.5.31`, versionCode `53`, minSdk `31`, target/compile SDK `36`, ABI ARM64 (`lib/arm64-v8a`). Dimensione circa 54 MiB.
- APK consegnato: `JarvisWake-0.5.31-debug-arm64-permanent.apk`; SHA-256 `dd253417641d8b52715139bce5202ee8a033289b76e0a3c72f65839369215e83`.
- Firma verificata con il certificato permanente: `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`. Schema v3 verificato per API 31+; schema v2 verificato separatamente per API 24–27. Il manifest dell'app ha minSdk 31, perciò il percorso supportato in questo rilascio è v3.
- `zipalign -c -P 16 -v 4` e `unzip -t`: superati. `aapt dump badging`: pacchetto, versione e SDK verificati. Checksum anche in `releases/0.5.31/SHA256SUMS`; l'APK non viene committato.
- La build e il test DOM non verificano il comportamento di Android/One UI, la riduzione effettiva della latenza, il mantenimento della sessione Web ChatGPT dopo handoff o l'audio realmente udibile. Prova sul Galaxy S24+ ancora richiesta.

## 0.5.30 — storico

### Build e APK

Build locale eseguita il 19 ottobre 2026 con JDK 17, Gradle 8.13, Android SDK 36 e Build Tools 36.0.0:

```text
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
BUILD SUCCESSFUL — 53 task
```

- Test JVM: 16 superati. Lint: task completata senza errori bloccanti.
- `node scripts/test-web-voice.cjs`: superato; include riconoscimento semantico, strategia spaziale, fallback nel compositore, stabilità del controllo, protezione bozze e invio singolo.
- APK: `it.jarviswake`, versionName `0.5.30`, versionCode `52`, minSdk `31`, target/compile SDK `36`, ABI ARM64.
- SHA-256 della build debug prima della firma permanente: `30f888c22d589484c4a950d6b65ab612e0858526d6c1a40963ea9c6d6d3a23ff`.
- APK consegnato: `JarvisWake-0.5.30-debug-arm64-permanent.apk`, SHA-256 `02a9ccbd5d477acc7408a2337158eda7c54208d859641655b362f69bbb9c202f`.
- Certificato SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`, uguale al certificato permanente consolidato. `apksigner verify` conferma schema v3 per API 31+ e schema v2 con verifica separata per API 24–27. `zipalign -c -P 16 -v 4` superato.
- Il file cifrato è stato letto dal branch GitHub `signing-handoff-2026-10-19`; l'archivio age a passphrase è stato autenticato, il keystore verificato e i temporanei in chiaro rimossi dopo la firma. Nessuna credenziale è stata inclusa in questo repository.
- Nessuna GitHub Action è stata usata per compilare o firmare.

I checksum sono registrati anche in `releases/0.5.30/SHA256SUMS`. Il contenuto APK non è committato.

### Modifiche 0.5.28–0.5.30 portate nel sorgente (storico)

- Confrontati gli APK .27/.28/.29 e recuperate dal bytecode soltanto le differenze osservabili: semantic labeling del controllo Voice, fallback limitato al compositore e preferenza basso-prima con keyguard.
- La 0.5.30 aggiunge un WebView invisibile che carica il progetto mentre `MainActivity` avvia l'ascolto e osserva il DOM senza toccare Voice.
- Alla wake il preloader era distrutto e il WebView effettivo ricaricava il progetto; la 0.5.31 corregge questo comportamento riutilizzando la stessa istanza.

### Analisi dei tempi diagnostici

I log disponibili registrano tempo monotono per `CYCLE_STARTED`, `WEB_OPENED`, `WEB_PAGE_READY`, `VOICE_CONTROL_READY` e `VOICE_DISPATCHED_FIRST`.

- In un ciclo, `WEB_PAGE_READY` arriva 11,65 s dopo `CYCLE_STARTED` e `VOICE_CONTROL_READY` a 13,48 s: circa 1,83 s dalla pagina pronta al controllo.
- In un altro ciclo, pagina pronta a 3,82 s e controllo Voice a 11,41 s: circa 7,59 s tra pagina pronta e controllo.
- Altri campioni di controllo pronto sono tra circa 3,3 e 8,1 s. Un precedente campione era circa 3,92 s.

Questi sono esempi osservati, non statistiche né una garanzia. Nei log esaminati non c'è sempre un evento `WAKE_DETECTED` associabile; `CYCLE_STARTED` può riferirsi a un ciclo manuale. `VOICE_DISPATCHED_FIRST` indica il gesto inviato, non che il server sia pronto, che abbia risposto o che l'utente abbia sentito l'audio. Non è ancora misurato il tempo wake-to-first-audible-response.

### Prove ancora da fare sul telefono per la 0.5.31

Nessun dispositivo Android era collegato alla build. La prova fisica va eseguita sul Samsung Galaxy S24+ Android 16 / One UI. Non è dichiarato verificato in questa build:

In un feedback precedente l'utente aveva detto che una correzione della schermata di blocco funzionava; in un'altra prova aveva segnalato Voice senza risposta vocale dopo l'avvio manuale. Non è noto se quei risultati corrispondano al codice 0.5.30.

1. Wake con telefono sbloccato e bloccato, senza impronta obbligatoria; cover visibile, Voice attiva e risposta audio.
2. Una sola attivazione Voice e nessun ulteriore tocco durante caricamento, ricreazione o riconnessione Accessibilità.
3. Invio di «ciao jarvis», ricezione da ChatGPT e parlato di risposta; nessuna bozza personale sovrascritta.
4. X, Indietro e nuovo spegnimento schermo chiudono l'intera sessione e riattivano Vosk.
5. Avvio da assistente dopo riavvio, dopo processo terminato e prima/dopo primo sblocco.
6. Copertura opaca/semitrasparente persistente e leggibilità della X.
7. Nuova chat generata automaticamente nel progetto JarvisRuntime per ogni ciclo.
8. Differenza di latenza e batteria con preloader attivo/disattivo.
9. Misura dal `WAKE_DETECTED` al primo `VOICE_MEDIA_PLAYING`, quindi confermare manualmente che l'audio sia una risposta vocale udibile. `VOICE_MEDIA_PLAYING` da solo non certifica l'uscita audio né l'ascolto.
10. Schermo spento: verificare stop di Vosk, accensione display senza sblocco, ripresa del microfono e richiesta immediata di preloader quando `MainActivity` è ancora viva. Verificare anche il comportamento senza Activity dopo avvio a freddo.

### Riferimenti Android

La 0.5.26 introdusse l'attività visibile sopra il keyguard senza richiedere automaticamente lo sblocco. I riferimenti Android spiegano le API usate ma non certificano WebRTC o ChatGPT Voice su One UI:

- [Activity.setShowWhenLocked](https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean))
- [Activity.setTurnScreenOn](https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean))
- [KeyguardManager.requestDismissKeyguard](https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,%20android.app.KeyguardManager.KeyguardDismissCallback))
- [Limitazioni dei servizi foreground e permesso microfono](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

La base storica di verifica 0.5.26 e la ricostruzione della causa del vuoto di continuità sono riassunte in `CONTINUITY.md`.
