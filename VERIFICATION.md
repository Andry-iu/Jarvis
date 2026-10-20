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
