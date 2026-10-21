# Roadmap — JarvisWake

Stato release 0.5.42, versionCode 64. Dopo che lo screenshot della 0.5.41 ha mostrato l'interfaccia Voice ancora aperta con il controllo `X` visibile, la 0.5.42 cerca un controllo di uscita accessibile nella UI attuale e usa le sue coordinate aggiornate. Target mancante o ambiguo: nessun tocco. La diagnostica distingue target trovato, dispatch e conferma. Build: 38 test JVM e test DOM superati, lint 0 errori/18 avvisi. APK firmato permanentemente, SHA-256 `86d09959e19f71679390f539da5f6fd18f8c30d42a924c7e8ea137eea6054b64`; v2/v3 e allineamento 16 KB verificati. Collaudo Galaxy S24+ necessario.

Stato precedente: 0.5.39, versionCode 61. La prova della 0.5.38 sul Galaxy S24+ ha mostrato che lo swipe non produce effetti. La cover interna è posizionata sotto il padding della barra di stato ma riutilizzava l'inset assoluto del display nelle coordinate locali; la 0.5.39 sottrae la posizione sullo schermo della vista e amplia la zona superiore interna. Build e 37 test locali superati; APK firmato col certificato permanente, SHA-256 `4f5e310c45a0da250507f385a883b7910d872068acc87f7df81ad5f752c49396`. Firme v2/v3 e allineamento 16 KB verificati; collaudo sul dispositivo da completare.

## In verifica nella 0.5.42

- Prima dello stop, il DOM cerca nella schermata Voice corrente un solo controllo di uscita visibile, abilitato e raggiungibile, con descrizione semantica Voice/stop. Il punto d'avvio non è riutilizzato.
- Viene inviato un solo gesto DOWN/UP al target attuale. La conferma attende fino a due secondi che torni disponibile un controllo iniziale Voice. Swipe ripetuti durante l'arresto sono ignorati.
- Se non c'è un target unico, Jarvis non tenta un tocco geometrico o un pulsante generico. La diagnostica registra target trovato/mancante, tocco inviato e arresto confermato/fallito, senza contenuti della conversazione.
- Test e build locali superati; screenshot e prova sul Galaxy confermano che la 0.5.41 non chiudeva la UI Voice. La 0.5.42 resta da collaudare sul Galaxy S24+.

## Storico della 0.5.41

- La 0.5.41 riutilizzava il punto normalizzato WebView selezionato all'avvio per tentare lo stop. Lo screenshot successivo mostra Voice ancora aperta con il controllo `X` visibile; il metodo non ha fermato la sessione. Build e firma della 0.5.41 sono documentate in `VERIFICATION.md`.

## Storico della 0.5.40

- La zona iniziale dello swipe usa coordinate assolute sullo schermo, altezza della cover e inset di sistema. Test specifici coprono cover interna sotto il padding, overlay esterno, barra di stato, direzioni non valide, zona bassa e reset del gesto.
- Se viene riconosciuto lo swipe ma il controllo Voice non conferma l'arresto, un messaggio rosso resta visibile e l'evento compare nella diagnostica. Build completata col kit locale: 38 test JVM, test DOM superati, lint 0 errori/18 avvisi. APK firmato permanentemente: `9225cd5cfe87cc1a7867b4dd2a273c84baa6de652ac4c7a6ce8f954a45b6f438`; firme v2/v3, ZIP CRC e allineamento 16 KB superati. Resta la prova sul Galaxy S24+.

## In prova nella 0.5.38

- Tocco singolo sui cerchi: alterna cover opaca e semitrasparente. Doppio tocco al centro: chiude il ciclo. Doppio tocco in alto a destra: esporta la diagnostica. Il pulsante X è stato rimosso.
- Swipe iniziato nella fascia alta interna, appena sotto la barra di stato: il rilevatore si attiva durante il movimento e termina la modalità Voice, nasconde la cover e torna alla Home. La pagina e la conversazione WebView restano aperte in background; la wake word «Ehi Jarvis» dovrebbe riportare alla stessa sessione e riattivare Voice.
- Indicazione visiva nella parte alta della cover per mostrare la direzione del gesto e la zona di avvio, tenuta fuori dal bordo che Android usa per la tendina di sistema.
- APK firmato permanentemente pronto. Test sul Galaxy S24+ necessario per confermare il gesto, il rilascio reale del microfono, l'ascolto wake in background e la ripresa della stessa conversazione.

## Correzione in lavorazione per la 0.5.36

- Il campo vuoto viene selezionato anche se la pagina ne cambia l'etichetta, purché sia il campo attivo o abbia un'etichetta localizzata riconosciuta; resta richiesta la stabilità su due letture.
- La policy ritenta solo esiti che garantiscono che non è avvenuto il click d'invio. Testo già presente e risultato ambiguo restano terminali per proteggere bozze e invio singolo.
- Eventi diagnostici sintetici registrano cambi di stato Voice e risultato della preparazione/invio. Non contengono testo, audio, URL o credenziali e i risultati ripetuti uguali non vengono registrati di nuovo.
- Test DOM/unitari e build superati, APK unsigned prodotto; firma permanente e comportamento reale da verificare sul Galaxy S24+.

## Correzione preparata per la 0.5.35

- La sola condizione di invio ritentabile è `NOT_READY`, che garantisce che il click non è stato eseguito. Rimane ritentabile fino a quando il saluto è pronto o il ciclo termina; la scadenza di 10 secondi non lo marca più come concluso.
- `SENT` chiude il tentativo come inviato. Campo occupato o esito ambiguo chiudono il tentativo senza sovrascrivere testo e senza rischiare un secondo click. La protezione DOM `submitted` resta impostata prima del click.
- Test unitario della classificazione degli esiti aggiunto. Build e prova sul Galaxy S24+ ancora da completare.

## Implementato nella 0.5.34

- Il saluto «ciao jarvis» aggiorna il compositore DOM senza chiamare `focus()` o `execCommand`, per evitare l'apertura automatica della tastiera. Il test DOM controlla che il focus non sia richiesto e che il testo resti inviabile una volta sola. L'accettazione da parte della pagina ChatGPT e la scomparsa del lampeggio vanno verificate sul Galaxy.

## Implementato nella 0.5.33

- A schermo acceso, Vosk resta in cattura. A `ACTION_SCREEN_OFF` apre una finestra di 30 secondi durante la quale la wake word può ancora avviare Voice. Allo scadere rilascia `AudioRecord` e il wakelock parziale; il `Model` e il `Recognizer` restano residenti per tutta la vita del servizio. A `ACTION_SCREEN_ON` la cattura riparte con il modello già pronto. Eventi diagnostici misurano modello pronto, inizio e fine della finestra e avvio/arresto cattura. Build, test e firma locale verificati; il comportamento sul Galaxy resta da confermare.

## Implementato nella 0.5.32

- Corretto l’ordine dei figli nel `FrameLayout` della schermata principale: il WebView a schermo intero del preloader viene inserito dietro lo `ScrollView`, evitando che un livello invisibile intercetti i tocchi dei pulsanti. Il WebView resta attaccato e pronto al successivo handoff. Test touch sul dispositivo fisico ancora da eseguire.

## Implementato nella 0.5.31

- Riconoscimento locale «Hey Jarvis» tramite Vosk e avvio manuale del ciclo.
- Integrazione Android `VoiceInteractionService` e servizio microfono continuo quando lo schermo è interattivo.
- Spegnere lo schermo durante una sessione Voice chiude il ciclo.
- Preloader: la pagina JarvisRuntime aperta in WebView invisibile dalla schermata principale viene trasferita e riutilizzata dalla sessione Voice, senza ricreare il WebView né ricaricare intenzionalmente il progetto.
- All'evento di accensione schermo, il servizio chiede alla `MainActivity` già esistente di avviare subito il preloader; se non esiste, non tenta un avvio Activity dal background.
- Seed DOM del controllo Voice raccolto durante il pre-caricamento. È solo un aiuto per la stabilità: il punto viene scalato al viewport corrente e validato con nuove osservazioni DOM prima dell'unico tocco nativo.
- Una sola sequenza DOWN/UP per ciclo, protezione contro gesti duplicati e saluto «ciao jarvis» una sola submission quando campo e pulsante sono pronti.
- HUD con X, opaco predefinito, scelta semitrasparente persistente; note nascoste dopo Voice. X/Indietro/spegnimento schermo annullano il ciclo.
- Diagnostica locale sintetica ed esportabile, con ID correlato per cattura microfono, wake, rilascio, ciclo, preloader, Voice e media playback. Nessun audio, trascrizione o testo conversazione.

## Misure aperte

1. **Collaudo su Galaxy S24+**: verificare i 30 secondi di cattura dopo lo spegnimento, lo stop effettivo alla scadenza e la ripresa immediata a schermo acceso, con schermata bloccata e senza sblocco; display spento, pressione tasto laterale, wake prima dello sblocco, ripresa Vosk, avvio manuale, risposta vocale, X/Indietro/spegnimento durante Voice e ciclo dopo riavvio.
2. **Preloader e latenza**: confrontare dati diagnostici prima/dopo 0.5.31 a parità di rete e login. Verificare che la sessione autenticata resti disponibile dopo il trasferimento del WebView.
3. **Prima risposta udibile**: `VOICE_MEDIA_PLAYING` segnala solo la riproduzione di un tag HTML `audio` o `video`. Va confrontato con l'ascolto reale sul telefono; non è prova che l'utente abbia udito la risposta.
4. **Nuova chat JarvisRuntime**: verificare che ogni ciclo crei una nuova conversazione nel progetto. La pagina viene aperta dal progetto, ma la creazione automatica non è dimostrata.
5. **Avvio assistente dopo boot/process death**: il preloader viene avviato allo schermo acceso se `MainActivity` esiste. Dopo cold start o riavvio, Android può non avere l'Activity; Vosk riprende senza preloader finché l'utente non apre Jarvis.
6. **Posizione per dispositivo**: non è persistita. Il codice usa una posizione dal WebView stesso solo se ne trova una durante il pre-caricamento; decide il DOM live, non una coordinata universale.
7. **Consumi**: nessun trigger DSP/hardware aggiuntivo o gate di ampiezza è stato introdotto. Misurare Vosk separatamente prima di progettare un secondo filtro.

## Interpretazione della diagnostica

Gli eventi riportano UTC, tempo monotono, nome e token. Per gli ascolti Vosk il token nasce all'avvio della cattura; se viene rilevata la wake, lo stesso ID passa a `CYCLE_STARTED` e agli eventi successivi. Si possono così calcolare tempi wake→rilascio, wake→pagina pronta, wake→controllo Voice, wake→gesto e wake→media playback rilevato. Media playback non equivale a parlato intelligibile o udibile.

Le misure dipendono da rete, account ChatGPT, DOM del servizio, WebView e dispositivo. Non aggiungere pause arbitrarie o tocchi ripetuti per nascondere condizioni non osservate.

## Regole di lavoro

- Discutere l'intero ambito di una release prima di iniziare lo sviluppo. Quando l'utente sta ancora valutando le opzioni, non cambiare codice; implementare soltanto dopo autorizzazione esplicita. La regola vale anche sul branch Git.
- Preservare HUD opaco/semitrasparente persistente senza X, gesti della cover, wake anche da schermata di blocco, cancellazione ciclo, un solo gesto Voice e nessuna ripetizione dopo l'attivazione.
- Usare sessione ChatGPT autenticata e progetto JarvisRuntime; non API/account alternativi né ultima chat generica.
- Aggiornare test e documenti con codice. Build/firma localmente; niente compilazione automatica GitHub al push. Al rilascio firmato per il test, commit e push fast-forward su `main` secondo `AGENTS.md`.
