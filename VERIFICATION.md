# Verifica — JarvisWake 0.5.26

## Obiettivo e modifica rispetto alla 0.5.25

Avviare ChatGPT Voice quando viene pronunciata la wake word a telefono già bloccato, senza aspettare impronta/PIN. L'autenticazione del dispositivo non viene rimossa: le attività del ciclo sono mostrate sopra il keyguard tramite le API Android previste.

Nella 0.5.25, VoiceCycleState.openWeb rifiutava l'apertura se isDeviceLocked o isKeyguardLocked erano veri, e LaunchActivity richiedeva automaticamente lo sblocco. Il messaggio di attesa visto dall'utente corrispondeva a questa condizione esplicita.

- Il coordinatore autorizza il passaggio al WebView sopra il blocco soltanto con ciclo valido, attività resumed e focus acquisito. Restano scadenze, cancellazione e protezione da callback obsolete.
- Anche WebChatActivity usa setShowWhenLocked e setTurnScreenOn, soltanto per il ciclo automatico; la schermata usata per configurare l'accesso non è abilitata sopra il blocco.
- Nessuna richiesta automatica di sblocco. Il pulsante Sblocca telefono è disponibile come comando facoltativo durante il ciclo bloccato, compresa la fase Voice.
- Finché il keyguard è presente si usa la cover interna, con X e note aggiornate. La cover intercetta i tocchi fisici diretti alla pagina sottostante. Il tocco automatico Voice continua a raggiungere direttamente il WebView una sola volta.
- L'avvio da stato già bloccato non equivale a un nuovo evento SCREEN_OFF: X, Indietro e spegnimento dello schermo continuano a invalidare e chiudere il ciclo prima di riprendere Vosk.
- I meccanismi DOM di Voice, il rilascio del microfono e il saluto «ciao jarvis» non sono stati modificati. Rimangono la preferenza persistente opaco/semitrasparente e le note nascoste dopo l'attivazione Voice.

## Documentazione incrociata

- Activity.setShowWhenLocked: permette all'attività di rimanere resumed e visibile sopra il blocco.
  https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)
- Activity.setTurnScreenOn: accende lo schermo se l'attività può diventare visibile e resumed.
  https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean)
- KeyguardManager.requestDismissKeyguard: richiesta separata di rimozione del keyguard, con autenticazione quando necessaria. Nel nuovo percorso viene invocata solo dal pulsante dell'utente.
  https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,%20android.app.KeyguardManager.KeyguardDismissCallback)
- Microfono e servizi in background: restrizioni dei permessi while-in-use ed eccezioni, compreso VoiceInteractionService. La sola presenza del servizio assistente non dimostra che WebRTC funzioni da una pagina nascosta; il ciclo mantiene invece visibile l'attività WebView.
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

Queste fonti documentano le possibilità del sistema Android. Non certificano il comportamento della pagina ChatGPT o della cattura WebRTC sul Galaxy S24+.

## Verifiche locali

Comandi: `node scripts/test-web-voice.cjs` e Gradle offline `testDebugUnitTest lintDebug assembleDebug`.

- Compilazione riuscita; 16 test JVM superati, zero errori/fallimenti.
- Test JavaScript degli script DOM/media superati: attese del saluto, controllo dei pulsanti, conservazione delle bozze e invio singolo.
- Lint Android: 0 errori, 13 avvisi non bloccanti.
- Quattro nuovi test dello stato: avvio da bloccato senza autenticazione, obbligo di resume/focus, cancellazione dopo spegnimento/X, rifiuto di un avvio scaduto.
- APK verificato con apksigner e aapt: versione 0.5.26, codice 48, ABI arm64-v8a, firma valida e stesso certificato della 0.5.25.
- Integrità dell'archivio sorgente verificata. SHA-256 di APK e archivio riportati nel file di accompagnamento.

## Limiti e prova richiesta

Riscontro successivo dell'utente, 19 ottobre 2026: «Ho testato, funziona». È una conferma generale sul dispositivo di riferimento; non risultano un verbale di ogni scenario sotto elencato o prove su altri telefoni.

Nessun dispositivo Android collegato: l'effettivo avvio audio prima dell'impronta deve essere provato sul Samsung Android 16 / One UI. Login ChatGPT e permessi devono essere già configurati. Questa modifica non aggiunge il funzionamento prima del primo sblocco dopo il riavvio.

La conversazione con l'account connesso diventa disponibile a chi pronuncia la wake word senza autenticazione. Vosk non verifica l'identità del parlante. La protezione dei tocchi non limita ciò che si può chiedere vocalmente a ChatGPT.

1. Bloccare il telefono e pronunciare Hey Jarvis; attendere senza usare l'impronta: cover, attivazione Voice e invio singolo di «ciao jarvis» devono precedere lo sblocco.
2. Verificare risposta audio, ascolto della propria voce e assenza di ulteriori tocchi Voice.
3. Provare X e tasto di blocco sia durante il caricamento sia durante la conversazione: il ciclo deve chiudersi e Vosk riprendere.
4. Avviare un altro ciclo da bloccato, usare Sblocca telefono e verificare che la conversazione non venga riavviata o duplicata. Ripetere annullando la richiesta biometrica.
5. Verificare il ciclo manuale da telefono sbloccato e le due opzioni di copertura.

## Preparazione del commit Git

Il successivo aggiornamento di documentazione/CI conserva invariati i sorgenti applicativi, i test e la versione rispetto allo ZIP 0.5.26 consegnato. Aggiunge continuità di sviluppo, roadmap e istruzioni per riprendere il lavoro. Il nome degli artefatti CI viene letto dai metadati di compilazione tramite `scripts/package-apk.py`, invece del valore obsoleto 0.3.1. La verifica locale del packaging usa l'APK 0.5.26 già compilato; non equivale a una nuova esecuzione remota di GitHub Actions.

Controlli del packaging superati: nome 0.5.26/codice 48 ricavati dai metadati, output del workflow coerenti, APK byte per byte con lo stesso SHA-256 del file consegnato, YAML leggibile e allineamento ZIP a 16 KB verificato. Il confronto con lo ZIP originale conferma che nessun sorgente dell'app o test è cambiato.
