# JarvisWake 0.5.30 — assistente Android e ChatGPT Voice

App Kotlin per Android 12–16 (minSdk 31, targetSdk 36), APK ARM64 destinato alla prova su Samsung Galaxy S24+ Android 16.

## Documentazione e continuità

- [docs/INSTALLAZIONE_NUOVO_DISPOSITIVO.md](docs/INSTALLAZIONE_NUOVO_DISPOSITIVO.md): checklist completa per installazione pulita, permessi Android, Accessibilità, assistente, firma APK e collaudo su telefono bloccato.
- [CONTINUITY.md](CONTINUITY.md): stato del progetto, requisiti consolidati e indicazioni per riprendere il lavoro.
- [PROMPT_CONTINUITA.txt](PROMPT_CONTINUITA.txt): istruzione pronta per una nuova conversazione di sviluppo.
- [docs/ROADMAP.md](docs/ROADMAP.md): logging e integrazione JarvisRuntime approvati, ancora da implementare.
- [VERIFICATION.md](VERIFICATION.md): verifiche eseguite e limiti delle prove.
- [AGENTS.md](AGENTS.md): istruzioni operative per chi modifica i sorgenti.

## Obiettivo

JarvisWake riconosce localmente “Hey Jarvis”, vibra, libera il microfono e apre ChatGPT Voice usando una sessione **ChatGPT Web autenticata con l'account dell'utente**. Questo percorso conserva l'ambiente ChatGPT dell'account, invece di ricostruire memoria e istruzioni tramite API separate.

La destinazione è il progetto ChatGPT **JarvisRuntime**, configurato dall'utente con memoria «Solo progetto». L'app apre l'URL del progetto. La creazione automatica verificata di una nuova conversazione per ogni ciclo non è ancora dimostrata: non presumere che la pagina di progetto apra sempre una chat nuova.

## Configurazione iniziale

1. Installare l'APK e concedere microfono e notifiche.
2. Abilitare **JarvisWake: avvio Voice** nelle impostazioni Accessibilità e consentire l'HUD sopra altre app.
3. Aprire JarvisWake, scegliere **Configura accesso a ChatGPT** e accedere al proprio account.
4. Tornare a JarvisWake. La sessione e i cookie restano nel profilo WebView privato dell'app.
5. Premere **Imposta Jarvis come assistente del telefono** e selezionare JarvisWake nella richiesta Android. Il sistema userà Jarvis al posto dell'assistente precedente per i richiami di sistema.

Se l'autenticazione viene rifiutata dal browser incorporato, il percorso web non è utilizzabile su quel dispositivo.

La dichiarazione Android del servizio assistente include la sessione, il servizio di riconoscimento e `supportsAssist=true`: Android richiede questi metadati per elencare Jarvis tra gli assistenti. Prima di chiedere il ruolo, l'app memorizza il riconoscitore vocale predefinito e inoltra a esso le richieste di dettatura del sistema. Il riconoscimento “Hey Jarvis” resta separato e locale tramite Vosk. Se il telefono non ha un riconoscitore predefinito disponibile, la dettatura non è fornita da Jarvis.

## Flusso automatico

1. La wake word o **Avvia ciclo completo: ChatGPT Voice** creano un solo ciclo identificato. Un secondo avvio durante il ciclo viene ignorato.
2. Vosk interrompe la cattura. Il controllo Voice non viene premuto finché il worker audio non ha rilasciato il microfono.
3. Se il telefono è bloccato, Jarvis accende lo schermo e mostra la cover J.A.R.V.I.S. sopra la schermata di blocco, senza richiedere automaticamente impronta/PIN.
4. Quando l'attività è ripresa e ha il focus, apre la propria attività WebView, anch'essa abilitata a restare visibile sopra il blocco. Il telefono resta bloccato: lo sblocco non è più un prerequisito per il ciclo Voice. Il comando **Sblocca telefono** rimane disponibile come azione facoltativa.
5. Il WebView carica l'URL del progetto JarvisRuntime. Dopo una ricreazione prima del tocco ripristina la navigazione salvata, oppure carica l'URL del progetto se il ripristino non è disponibile. La nuova chat per ogni sessione resta da verificare.
6. Il DOM individua il controllo Voice; due letture consecutive ne verificano la posizione. Una sola sequenza touch nativa DOWN/UP attiva il blocco contro ulteriori tocchi, condiviso dall'intero ciclo.
7. Dopo il tocco, Voice viene considerata attiva e le note della cover scompaiono, come richiesto. Le osservazioni successive servono per il saluto e per riconoscere la chiusura; non autorizzano altre pressioni Voice.
8. X, Indietro o schermo spento invalidano il ciclo prima di rimuovere finestre e WebView. Le callback precedenti non possono riaprire ChatGPT. Il servizio di ascolto già avviato riprende dopo il rilascio del WebView.

Il coordinatore applica scadenze indipendenti dal WebView e dall'accessibilità: 45 secondi perché la schermata Jarvis sia pronta e 40 secondi per caricamento/ricerca del controllo Voice. Un caricamento che non termina non può più saltare il timeout. Errori della pagina principale e arresto del processo WebView chiudono il ciclo e lasciano una diagnosi nella schermata principale.

Mentre la schermata principale ha avviato l'ascolto, la 0.5.30 carica lo stesso progetto in un WebView invisibile e osserva il DOM senza premere Voice. Alla wake il preloader viene distrutto e la sessione effettiva crea un altro WebView. Perciò la pagina pronta e la posizione trovata non sono riutilizzate; non è stato misurato un vantaggio di latenza sul dispositivo.

Una ricreazione Android **dopo** il tocco Voice chiude il ciclo: lo stato audio vivo di una pagina non si ripristina tramite la cronologia WebView. Non viene mai premuto Voice una seconda volta per tentare di ricostruirlo. Dopo la morte del processo, intent appartenenti al ciclo precedente vengono rifiutati.

La sessione assistente Android disabilita la propria finestra predefinita e la nasconde dopo aver richiesto l'attività principale. Il percorso Vosk resta distinto dall'invocazione dell'assistente tramite il sistema.

## Messaggio iniziale

Il testo inviato è **«ciao jarvis»**, senza l'istruzione precedente «Dì soltanto». Il saluto non viene consumato quando il campo manca: dopo aver osservato Voice, l'app attende un compositore identificabile, vuoto, abilitato e stabile in due letture. Se l'osservatore ha rilevato una richiesta microfono, attende anche una traccia audio live e nessuna acquisizione pendente o fallita.

Dopo la compilazione del campo attende un unico pulsante **Invia** visibile e abilitato. Il solo tipo HTML `submit` non basta e non vengono usati Invio sintetico o invio indiscriminato del form. L'attesa viene ripetuta senza riscrivere il messaggio; il click di invio viene bloccato prima dell'esecuzione, così callback tardive non possono duplicarlo. La finestra di attesa è di 10 secondi di tempo trascorso dal primo tentativo, verificata quando Voice è osservabile; se il campo contiene testo dell'utente non viene sovrascritto.

Il DOM e la traccia microfono non certificano che il collegamento remoto Voice sia pronto a rispondere: l'accettazione effettiva del testo e il parlato devono essere verificati sul telefono. Le pagine ChatGPT e il comportamento dei loro editor possono cambiare.

## Schermata JarvisWake

L'HUD ha il **fondo opaco come impostazione iniziale**, il nome `J.A.R.V.I.S.`, la versione sotto il nome e le note più in basso. Quando Voice è attiva, le note vengono nascoste.

Il pulsante **Copertura** nella schermata principale indica l'impostazione corrente e permette di alternare fondo opaco e semitrasparente al 50%. La scelta è salvata e resta valida dopo la chiusura dell'app e il riavvio del telefono. Si applica a tutte le cover (sblocco, caricamento e Voice), anche se già visibili; non modifica l'aspetto o il funzionamento della X.

La schermata iniziale e quella di caricamento hanno una propria **X**. Finché il telefono è bloccato viene usata la cover interna al WebView, con X e pulsante di sblocco. La cover intercetta i tocchi fisici per evitare che raggiungano le pagine dell'account sottostanti; l'avvio Voice continua a inviare una sola sequenza touch direttamente al WebView. Le note di preparazione sono aggiornate anche sulla cover interna.

Dopo lo sblocco, la cover di accessibilità con X separata sostituisce quella interna soltanto se entrambe le finestre sono state aggiunte correttamente. Se l'HUD esterno non è disponibile, rimangono cover e X interne. I controlli Jarvis non possono sostituire o sovrapporsi alle richieste di autenticazione protette di Android.

## Privacy e limiti

- **Voice è accessibile senza autenticazione del telefono**: chi pronuncia la wake word può avviare una conversazione con l'account ChatGPT già connesso, inclusa la memoria che ChatGPT rende disponibile alla conversazione. Vosk riconosce parole, non l'identità del parlante.
- L'avvio da telefono già bloccato è distinto da un nuovo spegnimento dello schermo durante la conversazione: quest'ultimo continua a chiudere Voice e il ciclo. Questa versione non aggiunge funzionamento prima del primo sblocco dopo il riavvio.
- Il supporto Android a un'attività sopra il blocco non garantisce da solo il funzionamento di WebRTC/ChatGPT Voice su One UI: serve la prova sul Samsung. Login ChatGPT e permessi devono essere già configurati.
- Il riconoscimento della wake word usa Vosk 0.3.75 e il modello `small-en-us 0.15`; non salva audio o trascrizioni.
- ChatGPT Web richiede rete e gestisce direttamente audio, conversazione e dati dell'account.
- Il permesso microfono della pagina viene concesso soltanto alle origini ChatGPT esplicitamente riconosciute.
- Sono autorizzati `chatgpt.com`, i suoi sottodomini e l'host storico `chat.openai.com`; ogni altra origine viene rifiutata e mostrata nella diagnostica.
- Non viene esposto alla pagina alcun bridge JavaScript verso codice Android.
- Login, struttura della pagina e comandi accessibili di ChatGPT Web non costituiscono un contratto pubblico stabile e possono cambiare.
- Il prototipo avvia il servizio microfono da `VoiceInteractionService.onReady()` invece che dal ricevitore di boot. Android documenta il servizio assistente selezionato come mantenuto attivo per le hotword e come eccezione alle limitazioni del microfono in background. Il comportamento al boot va provato su Android 16 / One UI: se Jarvis non è assistente selezionato o i permessi non sono concessi, resta la notifica di avvio manuale.
- La selezione di Jarvis sostituisce Google o l'assistente corrente per i richiami di sistema. È reversibile dalle impostazioni Assistente digitale.
- One UI, Doze, chiamate, Bluetooth o il microfono già occupato possono interrompere il flusso.

## Compilazione

Richiede JDK 17, Android SDK platform 36 e build-tools 36.0.0.

```bash
bash scripts/prepare-model.sh
bash gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Vedere `VERIFICATION.md` per ciò che è stato verificato staticamente e ciò che richiede la prova sul Galaxy S24+.

Gli artefatti APK sono output di build e non vengono committati. Sorgenti, wrapper Gradle, script, test, licenze e documentazione sono versionati. Il modello Vosk viene preparato dallo script con checksum fissato. Per questo progetto la build e la firma richieste dall'utente si eseguono localmente, senza GitHub Actions.

Per aggiornare l'app installata serve il certificato permanente. Il backup del keystore è conservato cifrato nel branch `signing-handoff-2026-10-19`; non inserire mai passphrase o keystore in chiaro in Git. La procedura di ripristino e firma, compreso il limite dello script Termux vincolato alla 0.5.30, è in [docs/RECUPERO_FIRMA.md](docs/RECUPERO_FIRMA.md).
