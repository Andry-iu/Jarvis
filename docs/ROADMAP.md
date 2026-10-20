# Roadmap — JarvisWake

Stato sorgenti 0.5.33, versionCode 55; build, test e firma locali verificati. Il collaudo sul Samsung Galaxy S24+ Android 16 / One UI resta da eseguire.

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
- Preservare HUD opaco/semitrasparente persistente, X disponibile, wake anche da schermata di blocco, cancellazione ciclo, un solo gesto Voice e nessuna ripetizione dopo l'attivazione.
- Usare sessione ChatGPT autenticata e progetto JarvisRuntime; non API/account alternativi né ultima chat generica.
- Aggiornare test e documenti con codice. Build/firma localmente; niente compilazione automatica GitHub al push. Al rilascio firmato per il test, commit e push fast-forward su `main` secondo `AGENTS.md`.
