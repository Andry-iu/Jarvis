# Jarvis Cube — vero gioco 3D per Android Auto
Aggiornato l’8 novembre 2026, Europe/Rome.

## Stato corrente
Via utente esplicito «Procedi, proviamo» dopo la proposta di gioco parcheggiato. Due APK **0.5.123 / 145 compilati, firmati e verificati** nel cloud Jarvis sul sorgente5dbd435d1982282e83d79f3f9d2b3a7654952a9e. Job telefono c89551d1-8436-4b1b-9b02-219502625adb succeeded; firma separata del gioco sullo stesso worktree/SHA, certificato permanente v2/v3, CRC e zipalign16KiB verificati. App718test, car-game12test,0failure/error/skipped; lint0errori con58/8avvisi. Metadati/hash in releases/0.5.123/CUBE_VERIFICATION.json. Consegna telefono ottenuta tramite cloud_artifact; il gioco firmato è stato caricato su Google Drive Primary con accesso privato verificato dopo l’autorizzazione esplicita dell’utente. Installazione .123 e Audi ancora da collaudare. Necessari due APK sul telefono: JarvisWake e Jarvis Cube. Nessuna installazione sull’Audi. La .122/144 è stata installata sul Galaxy; con Maps visibile nell’auto il menu prova ha mostrato nessun display autorizzato e nessun avvio. Questo non distingue il mancato rilevamento dalla mancanza di accesso al display.

## Comportamento implementato
- Modulo car-game, applicationId it.jarviswake.cube, categoria game e entry CAR_LAUNCHER. Activity normale avviabile dal vero host; nessun requisito di UID uguale al proprio all’avvio. Si gioca davvero: trascinamento sui due assi, faccia numerata bersaglio, conferma solo se dritta davanti, punto per risposta corretta, nuovo bersaglio e Mescola.
- Canvas nativo, nessuna WebView/rete/audio nel gioco. Stato del gioco salvato nella ricreazione dell’Activity; rendering e letture cessano in pausa, risposta IPC tardiva invalidata per generazione. Insets applicati al layout, spazio adattivo al cubo.
- JarvisWake rimane assistente, senza appCategory=game. Rimossa la vecchia entry CAR_LAUNCHER dell’assistente; nuovo menu Configurazione → Cover → Android Auto · Jarvis Cube con istruzioni e anteprima esplicita soltanto sul telefono.
- CarCubeSyncProvider espone soltanto content://it.jarviswake.cube.status/state: protocol1, cover_visible0/1, muted0/1, sampled_elapsed_ms. Permesso signature + UID/pacchetto/certificato; query senza parametri liberi, scritture/call rifiutate. Nessun testo, audio, numero, contatto, URL o ID di chat. Il gioco verifica anche identità e firma del provider.
- Campionamento RAM sul main thread,200ms solo con cover visibile; hook di uscita accodano soltanto lavoro senza attendere IPC. Stato vecchio oltre1500ms non resta attivo. Nel gioco letture fuori UI e rendering fermo durante mute; perdita del collegamento durante sincronizzazione ferma la rotazione. La cover chiusa lascia il gioco autonomo.
- Prima apertura dal launcher dell’auto. Nessun avvio automatico sopra Maps, nessun controllo di altre app, niente root, API private, permessi speciali di proiezione o override al movimento. Android Auto controlla le app parcheggiate. Il supporto di categoria non prova compatibilità del particolare Galaxy/MMI o presenza dell’icona.

## Build e firma
Sviluppo fuori cloud ammesso; compilazione/JVM/firma soltanto nel cloud Jarvis originale. I task :app:testDebugUnitTest, :app:lintDebug e :app:assembleDebug dipendono dai corrispondenti task :car-game. Il runner continua a pubblicare il solo APK JarvisWake tramite cloud_artifact; il conteggio Android del suo report è quello del modulo app, non la somma inventata dei due moduli.

Adattatore cloud-runner/signing.py: default kind='phone' conserva package/ABI/3ELF/16KiB; kind='cube' accetta soltanto car-game/it.jarviswake.cube senza librerie native. Stesso backup cifrato, certificato fisso, v2/v3, zipalign/CRC, password soltanto in file temporanei privati. Nessun nuovo keystore o segreto stampato.

Dopo il job telefono riuscito, firmare l’output car-game del MEDESIMO worktree/SHA con signing.sign(work, repo, out, kind='cube'), verificare i report test/lint del gioco e registrare hash/dimensione/certificato/package/versione. Consegnare il vero APK del gioco attraverso un canale privato già autorizzato; non inventare un secondo link cloud_artifact, non caricare binari su Git. Firma del gioco non dedotta dalla firma del telefono.

## Verifiche preparate e limiti
- CubeGameTest: tutte6facce raggiungibili, punteggio monouso, errore/angolo insufficiente, spin fermato dal giocatore, delta limitato, valori non finiti, invarianti geometriche.
- CubeLifecycleTest SDK34/36: apertura senza Jarvis, pausa, mute/perdita link/idle e rendering su800×480/1920×484/480×800/1×1.
- CarCubeSyncTest SDK34/36: schema fisso, chiamante sconosciuto rifiutato, parametri/call rifiutati e rimozione immediata cover. Conservare NativeExitStateTest e NativeAudioRecoveryTest.
- XML e sintassi Python verificati fuori cloud;89test backend passati, guida unica35argomenti sincronizzata. Test Android, lint, assemble e firme completati nel cloud sullo SHA corretto, come riportato nello stato corrente.
- Nessun DHU o riscontro fisico effettuato. Non affermare che la prima apertura sia già riuscita né che il cubo si apra automaticamente alla wake.

## Prima verifica cloud e correzione
Job b815df22-4310-4e3f-9f19-50c49d1721cf, sorgente b6e28dbd0ac09cc469ade1563f9d0c2b03b68cab: failed/android_failed,12test gioco/10passati/2falliti/0skipped. Entrambi i fallimenti in CubeLifecycleTest.gameOpensWithoutJarvisAndStopsWhenPaused su SDK34/36, UnsupportedOperationException in CubeGameActivity.kt67: Activity.display chiamato su contesto senza display associato. I task test del modulo app non sono stati raggiunti e i report lint non erano finali; nessun APK o firma dedotti.
Correzione minima: cube.display anziché Activity.display per identificare il display effettivamente associato alla View, fallback telefono quando non è ancora attached. La stessa suite che ha rilevato il difetto deve passare prima della firma; non trattare il job fallito come ripetibile. L’avviso del display esterno non certifica Android Auto o Audi. Correzione verificata: i12test car-game passano sullo SHA5dbd435d1982282e83d79f3f9d2b3a7654952a9e; nuova verifica cloud riuscita,718test app passati e lint di entrambi i moduli senza errori. Il vecchio fallimento resta immutabile.

## Consegna completata
- JarvisWake .123/145: job c89551d1-8436-4b1b-9b02-219502625adb succeeded; cloud_artifact restituisce il link APK privato dello stesso account. Nessuna ricompilazione per scaricarlo.
- Jarvis Cube .123/145: **2491555byte**, SHA256 locale **544bbc4286a1f3814196adc7423f02fc7388f8cda6c47dddd6dcc8650f953393**. Caricato una sola volta sul Google Drive Primary dell’utente dopo la sua risposta esplicita «Autorizzo» alla consegna privata del solo APK Cube. Nessuna nuova condivisione pubblica.
- File Drive osservato e riletto dalla chat chiamante: ID **1TCl1MopqN3dLhxNv8xfqyr_x5abo7zIe**, filename **JarvisCube-0.5.123-debug-permanent.apk**, MIME application/vnd.android.package-archive, dimensione2491555byte, shared=false e unico permesso restituito di tipo user/owner. URL restituito dal servizio: https://drive.google.com/file/d/1TCl1MopqN3dLhxNv8xfqyr_x5abo7zIe/view?usp=drivesdk .
- Il connettore non ha restituito un checksum remoto: non dichiarare confronto SHA remoto eseguito. Il file locale è stato verificato prima dell’upload; nome/dimensione/privacy Drive sono stati riletti dopo. Nessuna nuova build o firma durante la consegna.
- Copia originale nel cloud: /workspace/.jarvis-cloud/cloud-runner/artifacts/c89551d1-8436-4b1b-9b02-219502625adb/cube-local/JarvisCube-0.5.123-debug-permanent.apk, con verification.json adiacente. Per riconsegna usare il file Drive esistente o quel file verificato; non duplicare upload dopo esito incerto.
- Storico del blocco: la richiesta iniziale ampia (commit/push, firma e possibile Drive) fu respinta dalla revisione automatica; diagnosi e firma locale circoscritte furono poi accettate. La sessione cloud non esponeva Library/create_library_file; nessun library_file_id fu creato. La successiva autorizzazione esplicita dell’utente al solo Drive ha consentito la consegna. Nessun rifiuto aggirato.
- La pagina di controllo cloud è rimasta temporaneamente indietro e non mostrava i nuovi messaggi/esiti; un ricaricamento ordinario ha reso visibile la consegna già completata. I seguiti successivi hanno riusato lo stesso file senza duplicarlo. Una ricerca testuale vuota per un nome con punteggiatura non prova l’assenza del file: preferire l’ID restituito e metadata readback.

## Prima prova reale
1. Aggiornare JarvisWake .123 e installare Jarvis Cube .123 sullo stesso telefono senza disinstallare JarvisWake.
2. Ad auto parcheggiata collegare Android Auto. Per APK di sviluppo usare, se necessario, l’opzione Origini sconosciute nelle opzioni sviluppatore Android Auto sul telefono, secondo la documentazione Google. Nessun cambiamento automatico alle impostazioni.
3. Aprire il launcher Android Auto e cercare Jarvis Cube. Se manca, riferire questo fatto/versione Android Auto; non passare alla vecchia scansione dei display né dichiarare installazione sull’MMI necessaria.
4. Aprire il gioco, ruotare/allineare/confermare una faccia per provare interazione e punteggio sullo schermo Audi.
5. Lasciare il gioco aperto, attivare la cover Jarvis dal telefono: controllare stato sincronizzato, mute grigio/rotazione ferma, unmute e chiusura cover. Controllare separatamente uscite cover/Voice e assenza di replay.
6. Chiusura del gioco tramite Chiudi/controlli del sistema; test di limitazione durante il movimento soltanto col DHU o in condizioni sicure, senza interagire alla guida. Nessun override.
7. Per successo occorre il riscontro dell’utente su ciò che vede nell’Audi: build, presenza Activity e frame non bastano.

## Fonti primarie verificate
- https://developer.android.com/training/cars/parked/auto
- https://developer.android.com/training/cars/parked/games
- https://developer.android.com/training/cars/testing (Origini sconosciute per media, notifiche messaggi e parked; non per Car App Library)
- https://developer.android.com/docs/quality-guidelines/car-app-quality

Tutti i residui della roadmap e i requisiti precedenti restano conservati. Backend live .121 non modificato durante la preparazione; nessuna nuova API Google reale usata nei test.
