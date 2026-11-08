# Android Auto — cubo 3D e richiesta di apertura dalla cover
Aggiornato l’8 novembre 2026, Europe/Rome.

## Stato corrente
APK sperimentale **0.5.122 / 144 compilato, firmato e verificato** nel cloud Jarvis sul sorgente d70a27714fa216ca62dbf9f8eff71ea13424e816. Job 6c800b3f-05c5-49a4-ae4e-7b8e39d9cd9f succeeded; link privato ottenuto tramite cloud_artifact. Installazione/download sul telefono e collaudo Audi non ancora confermati.
Il requisito finale resta: aprire automaticamente il contenuto sull’Audi all’apertura della cover sul telefono, senza installare nulla sull’MMI. Questo prototipo tenta un’apertura su un display scelto durante una prova temporanea; non certifica quel risultato né lo abilita stabilmente a ogni wake.

## Perimetro e limite decisivo
- Cubo 3D locale in HTML/Canvas dentro una Activity del telefono; nessuna pagina remota, libreria di rete, acquisizione audio o installazione sull’Audi.
- Entry sperimentale CAR_LAUNCHER per auto ferma; nessuna categoria NAVIGATION, gioco fittizio, metadata distractionOptimized=true, API nascoste, root, override o cambi automatici alle impostazioni Android Auto.
- **CAR_LAUNCHER non qualifica Jarvis per una categoria ammessa.** La documentazione attuale limita le app parcheggiate di Android Auto ai giochi: il launcher può quindi non mostrare questa Activity o rifiutarla. Non vendere il prototipo come integrazione ufficialmente supportata.
- Percorso distinto: con proiezione Android Auto osservata, Android15+, telefono sbloccato e scelta esplicita di un display secondario per il quale isActivityStartAllowedOnDisplay restituisce true, richiedere l’Activity con ActivityOptions.setLaunchDisplayId. Un display secondario, anche con Android Auto collegato, non identifica da solo l’Audi.
- Se il display non è esposto o l’accesso è negato, fermarsi. Nessuna ricerca per nomi interni, scelta implicita tra schermi, uso di display privati negati, fallback sullo schermo del telefono o tentativi di aggirare il rifiuto.
- La prova richiede auto parcheggiata e dura60secondi. Non introduce funzionamento durante la guida o una dichiarazione di ammissibilità basata su grafica apparentemente funzionale.

## Implementazione preparata
- CarCubeProbeState: armamento soltanto RAM, scadenza con clock monotono, consumo prima del comando, una richiesta per VoiceCycle, rifiuti consumati, chiusura terminale e invalidazione di Activity/frame tardivi.
- CarCubeProbe: API pubbliche, scelta del target sul telefono, nessuna enumerazione salvata o inoltrata. Hook asincroni sulla visibilità della HolographicOverlayView, condivisi da cover iniziale/nativa/WebView/Google. Non inserire attese nel percorso che rimuove l’HUD.
- CarCubeProbeActivity: finestra separata sul target, nessuna azione Voice; rifiuta chiamanti esterni, generazioni/cicli/display non validi. WebView locale senza rete/content access/bridge JavaScript; console ridotta al solo ACK fisso del primo frame. Pause ferma il rendering; uscita/stop chiude senza riapertura nello stesso ciclo.
- Mute effettivo letto dalla cover visibile (anche Google), grigio/rotazione ferma; chiusura/nascondimento della cover chiude il cubo. Lock, disconnessione e scadenza disabilitano la prova. Il ciclo Voice e la wake conservano i propri controlli e gesti.
- Menu telefono Configurazione → Cover → Android Auto · prova cubo 3D. La prima prova richiede armamento temporaneo sul telefono; non è ancora un automatismo permanente senza preparazione.
- Guida unica .122/144 aggiornata a35argomenti. Sorgente backend modificato soltanto per la guida generata; backend live .121 non ridistribuito prima della verifica dell’APK.

## Verifiche effettivamente eseguite
- node scripts/test-car-cube.cjs: PASS; geometria finita/rotazione, primo frame, mute, pausa/visibilità, ripresa senza salto temporale, formati800×480/1920×484/480×800/1×1 e assenza di rete/audio.
- node --test --test-isolation=none phone-bridge/test/bridge.test.mjs:75passati/0falliti.
- node --test --test-isolation=none phone-bridge/test/cloud.test.mjs:14passati/0falliti.
- python3 scripts/sync-guide.py --check:35argomenti sincronizzati.
- Build Worker ESM e relativo validate-artifact:PASS. Non sono compilazione Android.
- Nel cloud Jarvis compilati ed eseguiti i nuovi CarCubeProbeStateTest e CarCubeProbeActivityTest insieme alle regressioni esistenti:710test Android,0failure,0skipped; lint0errori/56avvisi. Report del job conservato su main.
- Tentativo Playwright reale non avviabile: binario chromium_headless_shell-1234 assente. Nessuna screenshot/browser QA dichiarata e nessun download di browser eseguito.
- Gradle/JVM, lint Android, assemble e firma completati nel cloud Jarvis. Non eseguiti test del launcher Android Auto/DHU o prova Galaxy/Audi.
- Prima della modifica13blob della baseline confrontati con gli SHA Git remoti. Pubblicare soltanto le differenze pertinenti e controllare la lease sul main aggiornato.

## Compilazione e ripresa
Runner riattivato tramite la sessione cloud Jarvis esistente: processo assente, strumenti e configurazione integri. Bootstrap eseguito nel cloud originale, configurazione e journal preservati; heartbeat recente e claim dello stesso job provati. Percorso completo e URL della sessione: docs/PROCEDURA_BUILD_RILASCIO.md, scheda G. Non dedurre always-on o salvataggio dello snapshot dall’avvio del runner.

Job **6c800b3f-05c5-49a4-ae4e-7b8e39d9cd9f succeeded/complete**, operation=build_signed, risoluzione esatta d70a27714fa216ca62dbf9f8eff71ea13424e816. Conservata la richiesta originaria; nessuna duplicazione. Verbale su main nel commit8cce21cbb389308f278796b2015a555b9917fc28, file releases/cloud-jobs/6c800b3f-05c5-49a4-ae4e-7b8e39d9cd9f.json.

APK .122/144:90072897byte; SHA256 f4cf5503b46fd3662bb317dfb99de4af04fc214decbe93c53f3e3020a0f85ecd; certificato permanente a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a.710test passati,0failure/skipped; lint0errori/56avvisi. Nessuna API Google a pagamento e nessun collaudo fisico dedotti dalla build.

Per consegnare usare jarviswake_cloud_artifact con questo job_id, autenticazione Site dello stesso account; artefatto privato con scadenza15novembre2026 (epoch1794762870642). Non conservare qui il link di download temporaneo e non ricompilare per riottenerlo. Backend live .121 conservato: il backend sorgente .122 cambia la guida, ma non è stato pubblicato in questa richiesta.

## Prima prova, soltanto dopo APK verificato
1. Installare .122/144 firmata con lo stesso certificato della .121; nessuna installazione sull’MMI.
2. Parcheggiare, collegare Android Auto e sbloccare il telefono. Le opzioni sviluppatore/Origini sconosciute di Android Auto, se necessarie al sideload del prototipo, sono scelte esplicite sul telefono; non garantiscono il supporto della categoria.
3. Aprire il menu di prova sul telefono. Abilitare60s e scegliere il display. Se l’elenco è vuoto, registrare questo esito: niente APK “già compatibile” e niente cambio categoria per mascherare il limite.
4. Attivare Jarvis entro60s. Verificare separatamente che la cover del telefono resti usabile, Voice si avvii una sola volta e il cubo compaia effettivamente sull’Audi senza toccare il display dell’auto.
5. Prove mute/unmute, doppio tap/uscita, swipe sinistro (senza stop Voice), stop pendente, lock e scollegamento. Il cubo non deve riapparire alla ricreazione o riconnessione dello stesso ciclo.
6. Se non compare: esportare la diagnostica, leggere i soli codici CAR_CUBE_PROBE. probe_dispatch=richiesta, probe_resumed=Activity ripresa sul target verificato, probe_frame=primo frame HTML con focus. **Nessuno di questi codici identifica da solo lo schermo come Audi né prova che l’utente abbia visto l’immagine.**
7. La prova positiva deve includere il riscontro fisico. Apertura permanente a ogni attivazione, avvio da telefono bloccato, tutti i casi di ripresa e compatibilità del launcher restano da valutare; non dichiararli completati con questo prototipo.

## Fonti primarie consultate l’8 novembre2026
- https://developer.android.com/training/cars/parked/auto
- https://developer.android.com/training/cars/platforms/android-auto
- https://developer.android.com/training/cars/testing
- https://developer.android.com/reference/android/app/ActivityManager#isActivityStartAllowedOnDisplay(android.content.Context,%20int,%20android.content.Intent)
- https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)
- https://developer.android.com/reference/android/app/Activity#getLaunchedFromUid()

Conservare tutti i residui precedenti della roadmap, la firma permanente, pairing/segreti e i requisiti di uscita cover .70/.79/.96. Nessun nuovo risultato fisico, deploy, pubblicazione su Play o funzionamento in marcia dedotto da queste modifiche.
