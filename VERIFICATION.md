# Verifica — JarvisWake 0.5.30

## Build e APK

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

## Modifiche 0.5.28–0.5.30 portate nel sorgente

- Confrontati gli APK .27/.28/.29 e recuperate dal bytecode soltanto le differenze osservabili: semantic labeling del controllo Voice, fallback limitato al compositore e preferenza basso-prima con keyguard.
- La 0.5.30 aggiunge un WebView invisibile che carica il progetto mentre `MainActivity` avvia l'ascolto e osserva il DOM senza toccare Voice.
- Alla wake il preloader è distrutto; il WebView effettivo ricarica l'URL progetto. Non sono trasferiti la pagina DOM pronta o il controllo individuato. L'eventuale beneficio da cache non è misurato.

## Analisi dei tempi diagnostici

I log disponibili registrano tempo monotono per `CYCLE_STARTED`, `WEB_OPENED`, `WEB_PAGE_READY`, `VOICE_CONTROL_READY` e `VOICE_DISPATCHED_FIRST`.

- In un ciclo, `WEB_PAGE_READY` arriva 11,65 s dopo `CYCLE_STARTED` e `VOICE_CONTROL_READY` a 13,48 s: circa 1,83 s dalla pagina pronta al controllo.
- In un altro ciclo, pagina pronta a 3,82 s e controllo Voice a 11,41 s: circa 7,59 s tra pagina pronta e controllo.
- Altri campioni di controllo pronto sono tra circa 3,3 e 8,1 s. Un precedente campione era circa 3,92 s.

Questi sono esempi osservati, non statistiche né una garanzia. Nei log esaminati non c'è sempre un evento `WAKE_DETECTED` associabile; `CYCLE_STARTED` può riferirsi a un ciclo manuale. `VOICE_DISPATCHED_FIRST` indica il gesto inviato, non che il server sia pronto, che abbia risposto o che l'utente abbia sentito l'audio. Non è ancora misurato il tempo wake-to-first-audible-response.

## Prove ancora da fare sul telefono

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
9. Misura separata dal riconoscimento wake fino al primo audio remoto e alla conferma manuale che sia udibile.

## Riferimenti Android per il percorso bloccato

La 0.5.26 introdusse l'attività visibile sopra il keyguard senza richiedere automaticamente lo sblocco. I riferimenti Android spiegano le API usate ma non certificano WebRTC o ChatGPT Voice su One UI:

- [Activity.setShowWhenLocked](https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean))
- [Activity.setTurnScreenOn](https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean))
- [KeyguardManager.requestDismissKeyguard](https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,%20android.app.KeyguardManager.KeyguardDismissCallback))
- [Limitazioni dei servizi foreground e permesso microfono](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

La base storica di verifica 0.5.26 e la ricostruzione della causa del vuoto di continuità sono riassunte in `CONTINUITY.md`.
