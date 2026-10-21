# JarvisWake — continuità di sviluppo

Aggiornato il 21 ottobre 2026 per la 0.5.37. È pronto un APK ARM64 firmato permanentemente per provare swipe verso Home, arresto di Voice, wake «Ehi Jarvis» e ripresa della stessa pagina. Test DOM/unitari, lint, build, allineamento e firma sono verificati; resta necessario il collaudo sul Galaxy S24+.

## Stato attuale

- Versione sorgenti: **0.5.37**, versionCode **59**. Pacchetto `it.jarviswake`, minSdk 31, compile/target SDK 36, Build Tools 36.0.0, Gradle Wrapper 8.13, JDK 17, ABI `arm64-v8a`.
- Repository di riferimento: `https://github.com/Andry-iu/Jarvis`, branch `main`. Usare sempre il `main` remoto più recente come baseline; non assumere che la 0.5.30 sia ancora HEAD.
- Il codice si collega al progetto ChatGPT JarvisRuntime all'URL `https://chatgpt.com/g/g-p-6ad5d9f2d9f48191a7c4860050aa43cc-jarvisruntime/project`.
- La 0.5.33 mantiene Vosk Model/Recognizer per tutta la vita del servizio, tiene attiva la cattura per 30 secondi dopo screen-off e riprende subito all'accensione riusando il modello. Sul Galaxy S24+ l'utente ha confermato wake entro la finestra, nessun avvio dopo la scadenza e ripresa immediata all'accensione.
- La 0.5.34 elimina il focus esplicito del compositore durante l'inserimento DOM del saluto e non usa più `execCommand`; verificare che ChatGPT accetti e invii ancora il saluto senza mostrare la tastiera.
- La 0.5.35 elimina l'abbandono automatico dopo 10 secondi: `NOT_READY` è ritentabile senza limite fino alla preparazione del compositore o alla cancellazione del ciclo. `SENT` termina come inviato; campo occupato o esito ambiguo terminano senza nuovo invio per proteggere le bozze e l'invio singolo. Build e test locali superati; il collaudo sul Galaxy resta da eseguire.
- La 0.5.36 aggiunge etichette localizzate e fallback al campo editabile attivo, visibile e vuoto; la protezione contro bozze e invii doppi resta invariata. Nuovi eventi sintetici distinguono stato Voice e risultati del saluto, con registrazione limitata ai cambi di risultato e senza testo. Test/build locali superati; APK unsigned, firma permanente non eseguita, collaudo sul telefono aperto.
- La 0.5.37 rimuove X, aggiunge tocco singolo sui cerchi per opaco/semitrasparente, doppio tocco centrale per terminare il ciclo, doppio tocco in alto a destra per esportare diagnostica e swipe verso il basso per terminare Voice, mostrare Home e lasciare la pagina pronta alla wake. APK firmato permanentemente; firma, test e build locali superati. Stop microfono, wake in background e ripresa stessa conversazione ancora da provare sul Galaxy.
- Dispositivo di riferimento: Samsung Galaxy S24+ (SM-S926B), Android 16 / One UI. La correzione .32 richiede ancora conferma sul dispositivo; non dedurre il successo sul telefono dai test DOM o dalla compilazione.
- Feedback storico: l'utente aveva confermato che la schermata di blocco funzionava dopo una correzione precedente; in altre prove aveva segnalato assenza della risposta vocale. Questi riscontri non certificano l'APK 0.5.30.

## Requisiti e decisioni consolidate

1. Riconoscere localmente «Hey Jarvis» con Vosk, senza inviare audio o trascrizioni al cloud per la wake word. Conservare l'avvio manuale del ciclo dalla schermata principale.
2. Usare la sessione ChatGPT Web autenticata dell'utente e il progetto JarvisRuntime; non sostituirli con API o account separati.
3. Mostrare la cover J.A.R.V.I.S. sopra il blocco senza forzare impronta/PIN. Lo sblocco rimane un comando facoltativo. La wake word non autentica chi parla.
4. Cover opaca come scelta iniziale e alternativa semitrasparente al 50%, persistente. Niente pulsante X: un tocco sui cerchi cambia opacità, doppio tocco al centro chiude il ciclo, doppio tocco in alto a destra esporta la diagnostica e swipe verso il basso termina Voice e nasconde la cover per mostrare la Home. La pagina WebView resta in background per il successivo wake. Indietro annulla il ciclo. L'avvio da telefono già bloccato non deve essere scambiato per un nuovo evento di blocco.
5. Un solo gesto touch reale DOWN/UP per Voice in ogni ciclo. Dopo il dispatch, considerare Voice attiva e non riprovare a premere, anche se la pagina si ricrea o l'accessibilità si ricollega. Dopo l'attivazione, nascondere le note della cover.
6. Individuare il controllo nel DOM e ricavarne le coordinate dal viewport corrente, senza coordinate assolute fisse. Confermare due osservazioni stabili; scalare le coordinate CSS nelle dimensioni del WebView. Quando il keyguard è attivo, dare priorità alla zona inferiore, poi a quella superiore e infine alle altre. A schermo sbloccato privilegiare il controllo semanticamente riconosciuto. Il fallback è confinato al compositore vuoto e non deve premere microfono, invio o controlli estranei.
7. Il polling Accessibilità è 100 ms. Mantenere pause basate sulla disponibilità osservata; non inserire ritardi arbitrari per mascherare race. Bilanciare sempre DOWN con UP.
8. Inviare una sola volta il saluto testuale esatto «ciao jarvis» quando campo e pulsante Invia sono pronti. Non sovrascrivere bozze dell'utente. Non ripristinare «eccomi signore» né l'istruzione «Dì soltanto». L'iniezione di un audio predefinito “mascherato” non è implementata; non confonderla col saluto testuale.
9. Il tono richiesto per le risposte vocali di Jarvis è calmo, scorrevole, abbastanza rapido, formale senza servilismo e con ironia asciutta. Questo è un requisito dell'esperienza ChatGPT, non una trasformazione audio implementata dall'app.
10. Le diagnostiche devono essere locali, esportabili manualmente e prive di audio, trascrizioni, cookie, credenziali o testo della conversazione.
11. Ogni APK installabile sopra l'app esistente deve usare il certificato permanente. Keystore e password non entrano in chiaro nel repository. Non usare GitHub Actions per compilare: build e firma sono locali.
12. Prima di proporre una nuova ipotesi tecnica, leggere codice e documentazione pertinenti, verificare più fonti primarie quando si dipende da API Android o dal comportamento web, e distinguere chiaramente fatti, stime e prove su dispositivo.
13. Prima discutere l'intero ambito delle modifiche proposte. Non iniziare lo sviluppo finché l'utente non autorizza esplicitamente l'implementazione; la regola vale anche per le attività su Git.

## Evoluzione ricostruita delle versioni

| Versione | Codice | Stato delle informazioni |
| --- | ---: | --- |
| 0.5.27 | 49 | Ultima versione con sorgenti nel branch `main` prima del consolidamento corrente. Include polling Accessibilità a 100 ms, eventi diagnostici di caricamento e controllo Voice, e correzione della doppia registrazione della transizione Voice. Commit di riferimento: `99b7d8f3d5d542a43d6c7e0581587d752391d015`. |
| 0.5.28 | 50 | APK conservato nella Library; sorgenti non trovati nei branch Git esaminati. Dal bytecode recuperato: miglioramento delle etichette semantiche Voice e fallback limitato al compositore. Ricostruzione binaria, non recupero dei sorgenti originali. |
| 0.5.29 | 51 | APK firmato conservato nella Library; sorgenti non trovati nei branch Git esaminati. Dal bytecode: con keyguard attivo ricerca prima in basso e poi in alto, con diagnostiche della strategia semantica/fallback. Anche questo è recupero dal bytecode, non una ricostruzione integrale della cronologia sorgente. |
| 0.5.30 | 52 | Porta nel sorgente locale le modifiche osservabili in 0.5.28/0.5.29 e aggiunge il preloader descritto sotto. Test DOM, unit test, lint e build locali sono registrati in `VERIFICATION.md`. |
| 0.5.31 | 53 | Riusa alla wake lo stesso WebView pre-caricato, richiede il preloader a schermo acceso se `MainActivity` esiste, sospende la cattura Vosk a display spento e la riprende anche con keyguard, aggiunge diagnostica correlata fino alla riproduzione osservata nei media HTML. Build e firma permanente verificate; test dispositivo ancora da eseguire. |
| 0.5.32 | 54 | Aggiunge il WebView del preloader dietro la schermata principale interattiva. Build e firma verificati; conferma touch sul Galaxy ancora richiesta. |
| 0.5.33 | 55 | Aggiunge 30 secondi di cattura dopo screen-off, mantiene il modello e recognizer Vosk caricati per la vita del servizio, riapre `AudioRecord` alla riaccensione, registra in diagnostica scadenza e disponibilità del modello. Build, test e firma locali verificati; utente conferma sul Galaxy wake entro 30 secondi, stop dopo la scadenza e ripresa immediata alla riaccensione. |
| 0.5.34 | 56 | Inserisce «ciao jarvis» senza focus o `execCommand` per evitare il lampeggio della tastiera. Test, build e firma locali verificati; prova sul Galaxy ancora da eseguire. |
| 0.5.35 | 57 | Candidato locale non rilasciato: rimuove il termine di 10 secondi per `NOT_READY`, mantenendo terminali campo occupato ed esito ambiguo per non sovrascrivere testo o duplicare l'invio. Test, build e collaudo sul Galaxy da completare. |
| 0.5.36 | 58 | Aggiunge riconoscimento del campo localizzato o già attivo e diagnostica degli stati Voice e delle fasi del saluto, senza registrare contenuti. Test, build e collaudo sul Galaxy da completare. |
| 0.5.37 | 59 | Gesti della cover e ritorno alla Home; chiusura di Voice per il wake locale e ripresa della stessa pagina. APK firmato col certificato permanente; test e build superati, collaudo Galaxy da completare. |

I dettagli non deducibili dai bytecode .28/.29 non vanno inventati. Se emergono APK o sorgenti più completi, confrontarli con il codice consolidato prima di sostituire implementazioni.

## Flusso Voice e responsabilità

- `WakeService` mantiene una sola istanza di `Model` e `Recognizer` per la vita del servizio. Cattura a display interattivo e per 30 secondi dopo `ACTION_SCREEN_OFF`; alla scadenza rilascia `AudioRecord` e il wakelock CPU, ma conserva il modello in memoria. `ACTION_SCREEN_ON` annulla la scadenza e riapre la cattura anche con keyguard visibile. Una wake rilevata nella finestra buia avvia il ciclo; Voice e l’uso della chat continuano a usare il microfono solo dopo il rilascio della cattura Vosk.
- `VoiceCycle` coordina il token del ciclo, apertura WebView, tempi massimi, cancellazione e passaggio al servizio di ascolto.
- `LaunchActivity` porta la schermata di copertura sopra il keyguard; lo sblocco è facoltativo.
- `WebChatActivity` apre l'URL JarvisRuntime, concede il microfono solo agli host ChatGPT ammessi, analizza lo stato DOM e invia al massimo un gesto Voice. Dopo il gesto osserva lo stato, ma non autorizza tocchi Voice aggiuntivi.
- `VoiceAccessibilityService` osserva Voice, il microfono della pagina e i controlli del saluto; invia «ciao jarvis» una sola volta quando il compositore è vuoto e il pulsante Invia è visibile e attivo.
- `DiagnosticLog` scrive eventi sintetici con timestamp, tempo monotono e ID ciclo; ruota fino a tre file da 256 KiB ciascuno. L'app può esportare i log in ZIP.
- `JarvisVoiceInteractionService` integra il ruolo assistente Android. Il riconoscimento wake Vosk e la dettatura di sistema sono percorsi distinti.

## Pre-caricamento: comportamento effettivo e limite

La 0.5.31 crea dalla `MainActivity` un WebView invisibile mentre il servizio di ascolto è attivo. La 0.5.32 lo aggiunge dietro la schermata principale per preservarne i tocchi. Carica il progetto JarvisRuntime e cerca il controllo Voice nel DOM senza toccarlo. Interroga il controllo per un massimo di circa 30 secondi (prima ogni 250 ms, poi ogni secondo). Alla wake `WebChatActivity` stacca e riutilizza lo stesso WebView; se il caricamento non è terminato, continua ad attenderlo senza aprire una seconda pagina. La diagnostica registra `WEB_PRELOAD_REUSED`, `WEB_PRELOAD_VOICE_SEEDED` e `WEB_PAGE_READY` con l'ID del ciclo.

Il punto DOM precaricato viene scalato alle dimensioni correnti del WebView e usato solo per inizializzare il controllo di stabilità. Una lettura DOM fresca deve ancora corrispondere entro 8 px e la successiva osservazione stabile autorizza l'unico DOWN/UP. Non sono usate coordinate assolute e il seed non può da solo provocare il tocco. Se il WebView non può essere staccato, viene distrutto in sicurezza e la schermata attiva ne crea uno nuovo.

Il beneficio di latenza e l'autenticazione conservata dal WebView trasferito devono essere verificati sul Galaxy. Il preloader parte quando `MainActivity` richiede l'ascolto e viene richiesto di nuovo immediatamente su `ACTION_SCREEN_ON` se la stessa Activity esiste già. La richiesta riusa un'attività esistente e non ne lancia una invisibile dal background: Android limita gli avvii di Activity in background. In un cold start/avvio assistente dopo riavvio, senza `MainActivity` esistente, Vosk riprende ma il preloader parte solo quando l'interfaccia viene aperta. La creazione o identificazione di una nuova chat in JarvisRuntime per ogni ciclo non è verificata.

Non viene ancora persistita una posizione per dispositivo: ogni sessione usa il DOM live e, se disponibile, il seed del preloader dello stesso WebView. Una cache per dispositivo resta da discutere; non introdurre coordinate fisse universali.

## Diagnostica e tempi

I log misurano eventi di processo, non il momento in cui l'utente sente la prima risposta. Per una wake Vosk, cattura microfono, wake e rilascio condividono un ID di sessione che passa a `CYCLE_STARTED`. Gli eventi comprendono `VOSK_MODEL_READY`, `VOSK_MODEL_RELEASED`, `SCREEN_OFF_MIC_GRACE_STARTED`, `SCREEN_OFF_MIC_GRACE_EXPIRED`, `MICROPHONE_CAPTURE_STARTED`, `WAKE_DETECTED`, `MICROPHONE_CAPTURE_RELEASED`, `WEB_OPENED`, pagina pronta (preloader o live), `VOICE_CONTROL_READY`, `VOICE_DISPATCHED_FIRST`, `VOICE_INPUT_TRACK_LIVE` e `VOICE_MEDIA_PLAYING`. Quest'ultimo indica che un elemento audio/video HTML della pagina risulta in riproduzione; non prova che il contenuto sia parlato, che l'uscita audio sia instradata correttamente o che l'utente lo abbia sentito. I campioni precedenti mostrano:

- un ciclo: 11,65 s da `CYCLE_STARTED` a `WEB_PAGE_READY`, 13,48 s a `VOICE_CONTROL_READY` (circa 1,83 s dalla pagina pronta al controllo);
- un altro ciclo: 3,82 s a `WEB_PAGE_READY`, 11,41 s a `VOICE_CONTROL_READY` (circa 7,59 s di attesa/ricerca successiva);
- in altri cicli osservati, il controllo Voice era pronto in circa 3,3–8,1 s. Il precedente tempo di circa 3,92 s era riferito al primo evento Voice di un campione precedente.

Questi numeri sono campioni, non prestazioni garantite. I log in esame non contengono un `WAKE_DETECTED` corrispondente per tutti i cicli e non misurano l'audio udibile. Il requisito di misurare dalla wake word fino alla **prima risposta vocale udibile** resta aperto. Non etichettare `VOICE_DISPATCHED_FIRST` come “Voice pronta a rispondere” o “prima risposta sentita”. Prima misurare separatamente: wake, rilascio microfono, pagina pronta, controllo trovato, gesto, acquisizione WebRTC, primo audio remoto rilevabile e conferma manuale dell'ascolto.

## Avvio in background, privacy e consumi

Jarvis usa un `VoiceInteractionService` selezionabile come assistente e un servizio foreground. A schermo acceso e per i 30 secondi di grazia dopo screen-off il microfono è in cattura; poi `AudioRecord` viene chiuso e l'indicatore privacy deve spegnersi, mentre modello e recognizer restano residenti senza wakelock CPU. Android/One UI può applicare limiti di avvio, Doze e permessi; il codice non dimostra l'ascolto dopo riavvio, prima del primo sblocco o quando il processo è terminato. Verificare separatamente sul Galaxy S24+ che il ruolo assistente e il servizio riprendano.

Non è stato implementato un trigger DSP/hardware a basso consumo né un filtro audio generico prima di Vosk. Non ci sono misure comparative di batteria che provino che un secondo gate riduca il consumo netto; Vosk riceve già il flusso audio necessario al riconoscimento. Misurare prima l'uso energetico e l'eventuale ridondanza, senza aggiungere un secondo ascoltatore microfono.

## Repository, firma e rilascio

- Il branch `main` è il ramo sorgente consolidato. Il branch `signing-handoff-2026-10-19` conserva `signing-transfer/jarvis-signing-backup.tar.age` (SHA-256 `155b8afc72265dae8bfe5e8d4a09a49929375d88e9972bd9b2777723d2502e58`) e le istruzioni `signing-transfer/RECOVERY.md`. L'archivio contiene il keystore permanente e i file password cifrati con age in modalità passphrase. Il certificato verificato per l'APK è SHA-256 `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`.
- La passphrase di age **non è conservata su Git**. È stata fornita dall'utente nel contesto di lavoro e l'archivio è stato decrittato localmente per firmare la 0.5.31. In una nuova sessione senza quel contesto, il repository da solo non permette di decrittare l'archivio: l'utente dovrà inserirla nel prompt locale age, oppure firmare sul proprio Galaxy usando il keystore già presente in Termux (`~/jarvis-signing/jarvis-signing.jks`). Non chiedere di inserire la passphrase o le password nel repository o nei log.
- Il certificato non è legato al modello di telefono, ma deve coincidere con quello dell'app installata sul Galaxy S24+ per installare un aggiornamento. La firma 0.5.31 con quel certificato è stata verificata. `scripts/firma-jarvis-0.5.30-termux.sh` resta vincolato all'hash della build 0.5.30: non usarlo per un APK successivo senza aggiornarne hash/percorso e verificarlo per quella release. Procedura dettagliata: `docs/RECUPERO_FIRMA.md`.
- Non conservare passphrase, password, keystore in chiaro, cookie o diagnostiche nel repository. Non esporre password negli argomenti di processo o nei log. Per ogni firma verificare certificato e schemi APK.
- Non committare APK, modelli Vosk, cache, output intermedi o chiavi. Registrare il checksum dell'APK nel file di rilascio; consegnare l'APK direttamente quando richiesto.
- Usare JDK 17, Gradle Wrapper 8.13, Android platform 36 e Build Tools 36.0.0. Compilare localmente, senza workflow GitHub Actions. Il workflow Android deve restare senza trigger `push`; un commit su `main` non deve nemmeno avviare il workflow. Test consigliati: `node scripts/test-web-voice.cjs` e `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`.
- Evitare clone shallow per il lavoro normale: recuperare tutta la cronologia e tutti i branch prima di diagnosticare un'assenza. Prima di un push confrontare l'hash remoto atteso e usare aggiornamenti fast-forward; mai force-push.
- **Procedura approvata dall'utente (19 ottobre 2026):** prima di ogni modifica aggiornare `main` e iniziare da una baseline pulita, allineata al remoto e con cronologia completa. Quando la modifica è pronta e viene rilasciato l'APK firmato da testare, committare e pubblicare direttamente su `main` sorgenti, test e documentazione pertinenti; non attendere un ulteriore «Consolida». Lasciare fuori dal repository APK, chiavi e credenziali. Continuano a valere build e firma locali, senza GitHub Actions, e push fast-forward senza force-push.

## Perché la continuità si è interrotta e prevenzione

Verifiche del 19 ottobre 2026:

1. La copia locale ricevuta per il lavoro aveva `.git/shallow` e un solo commit visibile, con `HEAD` e `origin/main` entrambi su `99b7d8f` (0.5.27). Il log dichiarava quel commit `grafted`: non era possibile ricostruire la storia precedente da quella copia.
2. Dopo aver recuperato la cronologia completa, GitHub `main` risultava alla 0.5.27 e conservava la serie di commit fino alla radice del progetto (`03c9d1f`), inclusa la storia della 0.5.26. Quindi la storia precedente non era stata persa dal repository: il clone shallow locale impediva di vederla. Gli altri branch visibili erano `feature/diagnostic-logging-0.5.27` e `signing-handoff-2026-10-19`; non contenevano i sorgenti .28/.29. La seconda branch conteneva il backup cifrato di firma, non una release applicativa.
3. Gli APK .28/.29 erano recuperabili dalla Library, ma non i loro sorgenti completi. Per questo si è potuto recuperare dal bytecode solo una parte delle differenze.
4. `CONTINUITY.md`, `PROMPT_CONTINUITA.txt` e il titolo README erano ancora alla 0.5.26 nonostante la main fosse alla .27; mancava un registro di release .28/.29 e lo stato dei nuovi requisiti non era separato dall'implementazione.

La causa verificabile della perdita di continuità è duplice: il checkout locale shallow nascondeva la storia che GitHub aveva conservato fino alla .27; i sorgenti .28/.29 e le relative decisioni di sviluppo non furono consolidati nei branch esaminati, mentre i documenti locali erano rimasti alla .26. Non c'è evidenza che commit .28/.29 siano stati cancellati da `main`: la lacuna è tra release APK e repository sorgente. In questa sessione la cronologia completa e tutti i branch remoti sono stati recuperati localmente prima di preparare il consolidamento.

Prevenzione: ad ogni rilascio aggiornare codice, test, versione, `CONTINUITY.md`, `PROMPT_CONTINUITA.txt`, `README.md`, `VERIFICATION.md`, roadmap e checksum nello stesso commit; conservare la cronologia Git completa; non consegnare soltanto APK quando si intende proseguire lo sviluppo; alla ripresa controllare branch, commit e stato della Library prima di formulare conclusioni.

## Ripresa del prossimo lavoro

1. Recuperare il `main` remoto completo e leggere `AGENTS.md`, questo file, `README.md`, `VERIFICATION.md`, `docs/ROADMAP.md` e `docs/RECUPERO_FIRMA.md`.
2. Verificare il nuovo commit remoto, il branch e il working tree; poi leggere i log diagnostici reali disponibili.
3. Collaudare sul Galaxy S24+: verificare cattura immediata a schermo acceso, wake pronunciata entro i 30 secondi dopo screen-off, arresto cattura alla scadenza, modello residente senza wakelock CPU e ripresa alla riaccensione; poi ciclo Voice, risposta audio, X/Indietro e avvio assistente dopo riavvio.
4. Strumentare wake-to-first-audio distinguendo “audio remoto rilevato” da “utente lo ha sentito”.
5. Misurare sul Galaxy la differenza di latenza con il preloader riusato e verificare il mantenimento dell'accesso ChatGPT. Verificare la creazione automatica di una nuova chat nel progetto e memorizzare solo identificativi affidabili.
6. Discutere l'eventuale memorizzazione per dispositivo dell'ultima posizione DOM semantica, con fallback dinamico e senza coordinate assolute.
7. Al rilascio di ogni APK firmato da provare, committare direttamente su `main` sorgenti, test e documentazione della release, secondo la procedura approvata dall'utente.
