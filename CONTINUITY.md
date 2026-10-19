# Continuità di sviluppo — JarvisWake

Aggiornato il 19 ottobre 2026. Base applicativa: **0.5.26**, versionCode **48**. Questa revisione aggiunge documentazione di continuità e corregge il nome degli artefatti CI; non cambia il comportamento dell'APK 0.5.26 già consegnato.

## Stato verificato

- Kotlin Android, minSdk 31, compile/targetSdk 36, build-tools 36.0.0, JDK 17, Gradle 8.13; pacchetto `it.jarviswake`, ABI `arm64-v8a`.
- Dispositivo di riferimento: Samsung Galaxy S24+ con Android 16. La 0.5.24 era stata dichiarata funzionante dall'utente; il 19 ottobre nella conversazione successiva l'utente ha confermato «Ho testato, funziona». Tale conferma generale non sostituisce un verbale di tutte le prove o una verifica su altri modelli.
- 0.5.25: fondo opaco predefinito, pulsante opaco/semitrasparente al 50%, preferenza persistente.
- 0.5.26: avvio del ciclo Voice sopra il blocco senza attendere impronta/PIN; sblocco facoltativo. X, Indietro o un nuovo spegnimento dello schermo chiudono il ciclo.
- Ultima verifica locale della 0.5.26: build riuscita, 16 test JVM e test DOM JavaScript superati; lint 0 errori, 13 avvisi. Non è stato collegato un dispositivo al sistema di compilazione.
- La 0.5.26 apre ancora una nuova chat normale a ogni ciclo. Logging strutturato, riuso della conversazione dedicata e integrazione con JarvisRuntime **non sono implementati**.

## Requisiti da preservare

1. Riconoscimento locale/offline «Hey Jarvis» tramite Vosk, senza chiavi cloud. Alla wake: vibrazione, rilascio del microfono e avvio ChatGPT Voice senza tocchi manuali.
2. Ascolto in background e integrazione come assistente tramite VoiceInteractionService; non promettere consumi da DSP o funzionamento prima del primo sblocco al boot, non implementati.
3. Sessione ChatGPT autenticata dell'utente, con le caratteristiche effettivamente disponibili nell'account. Nessuna sostituzione silenziosa con un assistente API separato.
4. HUD J.A.R.V.I.S., versione sotto il titolo e note più in basso; note rimosse dopo il gesto Voice. Opaco iniziale, alternativa semitrasparente al 50%, scelta memorizzata.
5. X sempre disponibile nella superficie Jarvis; una sola attivazione Voice per ciclo, senza ritoccare Voice dopo l'apertura. Le richieste protette del sistema restano sotto il controllo di Android.
6. Pause minime basate sulla disponibilità dei controlli. Conservare il controllo di rilascio del microfono e la stabilità del pulsante; non aggiungere ritardi arbitrari per nascondere una race.
7. X/Indietro/schermo spento: invalidare il ciclo prima di chiudere HUD e WebView, quindi riprendere l'ascolto. Una callback obsoleta non deve aprire chat o inviare testo.
8. Saluto iniziale esatto: **«ciao jarvis»**. Attendere compositore e pulsante Invia utilizzabili; invio massimo una volta; non sovrascrivere testo dell'utente. Non ripristinare «eccomi signore» o l'istruzione «Dì soltanto».
9. Conservare il pulsante di avvio manuale del ciclo nella schermata principale.
10. Nuovo requisito della 0.5.26: un telefono già bloccato può iniziare la conversazione. Questo non autorizza a ignorare un successivo evento di spegnimento schermo. La wake word non autentica l'identità di chi parla.

## Componenti e responsabilità

| Componente | Responsabilità |
| --- | --- |
| WakeService / WakePhrase | Acquisizione audio Vosk, wake, rilascio e ripresa del microfono |
| VoiceCycle / VoiceCycleState | Identificativo, transizioni, scadenze e cancellazione del ciclo |
| LaunchActivity | Superficie iniziale visibile sopra il blocco, avvio solo con resume/focus |
| ChatGpt | Avvio attività web e attivazione dell'automazione per il ciclo valido |
| WebChatActivity | Sessione web, unico gesto Voice, acquisizione microfono della pagina, saluto, cover interna e X |
| VoiceAccessibilityService | Coordinamento periodico e osservazione, HUD esterno quando sbloccato |
| WebVoiceTouchGate | Blocco condiviso contro ulteriori gesti Voice |
| HolographicOverlayView / CoverSettings / CycleUi | Animazione, opacità persistente, controlli X |
| JarvisVoiceInteractionService e sessione | Ruolo assistente e richiami Android |
| State | Stato/diagnostica sintetica attuale; non è ancora il registro strutturato pianificato |

Le etichette interne di fase sono `UNLOCK`, `WEB`, `VOICE`: nella 0.5.26 `UNLOCK` identifica ancora la preparazione della superficie, ma non obbliga più all'autenticazione se l'attività è abilitata sopra il blocco.

## Decisioni successive già accettate

Vedere `docs/ROADMAP.md`. Priorità concordate: logging diagnostico locale senza audio o trascrizioni, con rotazione ed esportazione manuale; continuità tramite conversazione dedicata identificata esplicitamente; progetto ChatGPT **JarvisRuntime**, creato dall'utente con memoria «Solo progetto» e istruzioni inserite.

L'ID/URL del progetto e l'ID della conversazione dedicata non sono presenti in questi sorgenti. Non inventarli e non dedurli da «ultima chat». La navigazione automatica nel progetto e l'apertura diretta della conversazione vanno verificate prima di implementarle. La politica di rotazione delle conversazioni non è ancora definita in termini numerici.

## Ripresa del lavoro

1. Leggere questi documenti, verificare versione e stato Git; confrontare eventuali cambiamenti remoti prima di intervenire.
2. Usare la 0.5.26 come base e mantenere separati: codice implementato, decisioni approvate, ipotesi e prove ancora necessarie.
3. Per la prossima versione, partire dal logging concordato; coordinare la successiva integrazione JarvisRuntime sulla base di controlli reali e identificativi verificati.
4. Aggiornare versione e documenti quando cambia l'app. Per un mero aggiornamento documentale/CI, non dichiarare un nuovo APK funzionale.
5. Verificare i rischi modificati: `node scripts/test-web-voice.cjs` e Gradle `testDebugUnitTest lintDebug assembleDebug` per variazioni del ciclo; controlli mirati per soli documenti/CI.

## Build, firma e artefatti

`bash scripts/prepare-model.sh` scarica il modello Vosk 0.15 e ne verifica lo SHA-256. Il modello è escluso da Git perché riproducibile tramite lo script; gli ZIP sorgente già consegnati lo includono. Gradle wrapper, script, licenze e avvisi restano versionati.

L'APK 0.5.26 consegnato ha lo stesso certificato della 0.5.25. La chiave è esterna al repository e non deve essere committata. La CI attuale usa la firma debug del runner: senza provisioning della chiave precedente non si deve promettere che il suo APK aggiorni l'installazione esistente.

`releases/0.5.26/SHA256SUMS` identifica l'APK e lo ZIP già consegnati, non un eventuale nuovo ZIP con questi documenti. Il workflow ricava ora il nome dell'artefatto dalla versione realmente compilata; era rimasto erroneamente fissato a 0.3.1.

## Stato della sincronizzazione Git

Repository di riferimento: **https://github.com/Andry-iu/Jarvis**, pubblico, ramo predefinito `main`. Accesso in lettura e scrittura verificato il 19 ottobre 2026. Il vecchio nome `Andry-iu/JarvisWake` viene risolto da GitHub verso questo repository; usare il nome attuale.

Il blocco iniziale `403: Resource not accessible by integration` è stato superato dopo l'aggiornamento delle autorizzazioni da parte dell'utente. La scrittura è stata verificata creando il README nel commit remoto `03c9d1fa1469d5ecfd55c92c6cce010a601b1850`. Prima di questa inizializzazione il repository risultava vuoto, senza rami o file.

La base locale è stata ricostruita dai sorgenti 0.5.26 consegnati; il commit snapshot iniziale è `764f555d2f4497973eda627597b16dbe031c8e62`. Il bundle Git conserva la preparazione locale precedente alla pubblicazione. La cronologia remota parte dal commit di inizializzazione e integra il progetto completo: sorgenti, test, script, workflow, documentazione e istruzioni di continuità. Gli artefatti e le chiavi restano esclusi come descritto sopra. I commit dello snapshot locale non sono antenati della cronologia remota.

Per le modifiche successive recuperare prima `main`, confrontare i contenuti e conservare la cronologia remota. Non eseguire force-push. Verificare il riferimento del ramo e il contenuto effettivamente pubblicato prima di dichiarare riuscita una sincronizzazione.
