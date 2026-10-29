0.5.68 / 90 — unico gruppo approvato il 29 ottobre 2026: animazione grigia completamente ferma soltanto dopo conferma microfono Voice muto; celeste e movimento ripresi senza salto di fase dopo conferma riattivazione. Rimossa ogni dicitura superiore della cover; J.A.R.V.I.S. e versione rimangono sotto l’animazione, messaggi di errore soltanto sotto. Gesti invariati. Nella modalità App ChatGPT, swipe sinistro/giù nascondono subito la cover e avviano subito il controllo stop, senza attendere il tick periodico né lo snapshot diagnostico. La wake resta sospesa fino alla conferma stabile dell’arresto; solo allora Home per swipe giù, stessa conversazione per sinistra. Stop consumato nelle preferenze prima del click, conservato alla riconnessione. Dopo 5 secondi senza conferma ricompare una sola volta la cover con errore/esportazione; nessun nuovo stop e nessun rilascio fittizio del microfono. Collaudo Galaxy ancora necessario.

Ambiente obbligatorio verificato il 29 ottobre 2026: cloud Jarvis esistente, checkout /workspace/Jarvis, toolchain/cache /workspace/.jarvis-cloud. Attività verificata della .67: 01a1787d-f6ef-7785-acea-bbf5e98410cc (ID attività, non ID ambiente). Configurazione cloud verificata dal servizio: f68203bf-ad95-493f-b5d1-b05e44686cd2~cecfg_6adcda8d13e081918b4bd1e231e145b8. Istanza runtime osservata: ccarenv_b64_Y2NhcmVudl8yMDI4MmMzMDA4NWM4MTkxOTVmYTNjMmVmZTJlZDRiYw. Riprendere questa attività/configurazione e verificare lo stato dell’istanza; non ricostruire altrove né equiparare ID attività e ambiente. Checkout e toolchain persistono; continuità dell’host fisico sottostante non attestabile. Segreto ambiente Passphrase presente/utilizzabile, mai nei log o in Git.

0.5.67 / 89 — swipe nativi distinti, via esplicito utente ricevuto. Swipe sinistro richiede fine Voice e lascia visibile la conversazione ChatGPT corrente; swipe in basso richiede fine Voice e poi invia GLOBAL_ACTION_HOME. Entrambi mantengono cover e wake sospesa fino alla conferma di arresto della 0.5.66. La prima destinazione richiesta resta salvata durante l’attesa e la riconnessione del servizio; gesti successivi non la sostituiscono né ripetono lo stop. Prima di un comando Voice, uscita immediata senza avviarlo. Comando Home inviato una sola volta dopo chiusura del ciclo; esito Android registrato come NATIVE_HOME_COMMAND/NATIVE_HOME_REJECTED, senza scambiare l’accettazione per prova della Home visibile. Doppio tocco centrale invariato. Riscontro utente 0.5.66: resto apparentemente funzionante, swipe giù termina Voice ma lascia ChatGPT, swipe sinistro senza effetto. Nuovi due percorsi ancora da collaudare sul Galaxy.

0.5.66 / 88 — chiusura Voice nativa basata sul log Galaxy. Lo snapshot manual_export di jarvis-diagnostics _4_.zip espone Termina su un figlio del pulsante X azionabile e Disattiva microfono sul relativo comando. Aggiunta la variante esatta Termina; è inviata solo dopo avvio Voice del ciclo e riconoscimento congiunto dei controlli vocali. Conferma di chiusura separata: ritorno del pulsante avvio unico/stabile per almeno250ms e assenza di tutti i candidati stop/mute/unmute. Non si richiede più il vecchio titolo progetto dopo la chiamata, perché la chat può avere un nuovo titolo; controllo progetto prima dell’avvio conservato. Una sola pressione stop; sparizione finestra, cambio candidato o ritorno di controlli vocali azzerano la conferma. Evento NATIVE_STOP_CONFIRMED prima di togliere cover e riprendere Vosk. Avvio/stop effettivo e rilascio audio da verificare sul Galaxy, non certificati dai soli test.

0.5.65 / 87 — diagnostica chiusura Voice nativa. Utente conferma avvio riuscito con 0.5.64 ma stop e doppio tocco centrale restano in attesa. Non sono note le etichette native di fine/mute: non aggiunte varianti ipotetiche. Snapshot ora acquisiti anche dopo il comando Voice e durante la richiesta di arresto (massimo tre diversi per fase); fase indicata nel file. Doppio tocco in alto a destra acquisisce subito lo stato corrente e apre l’esportazione ZIP anche a ciclo attivo. Solo durante il salvataggio manuale la cover viene sospesa per rendere accessibile il selettore Android e ripristinata a fine/annullamento; il ciclo rimane valido e il microfono wake sospeso. Nessun comando Voice/stop ripetuto o conferma fittizia di arresto. Se stop non riconosciuto, messaggio cover indica il gesto di esportazione. Questo rilascio diagnostico non dichiara risolta la chiusura nativa: serve il log nello stato Voice attivo per identificare il comando esatto.

0.5.64 / 86 — correzione basata sulla diagnostica Galaxy. Nei tre snapshot del ciclo 09f6344d, il controllo Voice espone esattamente Avvia una conversazione vocale su un figlio non cliccabile del pulsante. Mancava soltanto questa variante con una nel vocabolario; aggiunta e coperta da test di regressione tramite risoluzione al padre. Nessuna modifica a cover, shortcut, progetto, geometria o latch singolo. Nessun comando Voice risulta inviato nel ciclo diagnostico precedente; la causa della mancata selezione è ora identificata nel testo non corrispondente. Il click e la conferma dello stato Voice attivo richiedono ancora prova reale sul Galaxy.

0.5.63 / 85 — diagnostica delle etichette native richiesta dall’utente. Quando Voice non è riconosciuta, salva fino a tre rilevazioni diverse della finestra ChatGPT, con intervallo minimo 1,5 secondi, nel file privato native-controls.log. Per ogni nodo visibile non editabile/password: struttura (classe, parent index, ID risorsa, bounds, azioni, clickability, stato abilitato e antenato cliccabile). Descrizioni accessibili soltanto sui controlli/figli di controlli; testo soltanto su pulsanti/figli di pulsanti. Esclusi testo generico pagina, campi editabili/password e finestre diverse da ChatGPT. Il file viene incluso nello ZIP di Esporta diagnostica da entrambe le schermate di esportazione. La prima rilevazione del ciclo sostituisce quella precedente; dati grezzi rimangono sul telefono ed escono solo con esportazione manuale. Nessuna nuova pressione Voice o modifica ai selettori.

0.5.62 / 84 — ricerca pulsanti nativi: etichette Voice e comandi nativi risolti al più vicino antenato cliccabile, visibile e abilitato della sola app ChatGPT (massimo tre livelli). Nodi figlio/padre che indicano lo stesso controllo vengono deduplicati; due pulsanti distinti restano ambigui e non vengono premuti. Aggiunto candidato esatto Start voice. Separati gli stati nessun pulsante / più pulsanti e registrati solo conteggi diagnostici NATIVE_CONTROL_PROBE, senza testi dell’app. Il controllo del progetto è conservato: sul Galaxy il nome corretto è JarvisRuntime, senza spazio. Non cambia il singolo comando Voice per ciclo. Screenshot utente conferma progetto aperto e icona Voice visibile; non espone l’albero accessibilità, quindi il motivo preciso della mancata corrispondenza e il risultato della correzione restano da verificare sul telefono.

0.5.61 / 83 — destinazione selezionabile WebView / App ChatGPT (shortcut), persistente; default WebView. Richiede Jarvis come assistente Android e Accessibilità connessa. Selezione degli shortcut accessibili di com.openai.chatgpt, inclusi quelli appuntati da altri launcher tramite FLAG_MATCH_PINNED_BY_ANY_LAUNCHER. Salva ID reale e nome progetto da riconoscere; nessun deep link inventato o fallback alla chat generica. Pulsante di prova manuale senza Voice. In modalità app, configurazione accesso/modello aprono il progetto nativo. Alla wake: rilascio Vosk, eventuale sblocco richiesto dal sistema, cover accessibility sopra l’app prima dell’avvio shortcut, controllo del nome progetto visibile e candidato Voice unico/stabile. Un solo ACTION_CLICK Voice per ciclo; conferma separata tramite controllo fine Voice più mute/unmute. Etichette semantiche candidate, da verificare sul Galaxy. Mute e arresto solo tramite controlli nativi riconosciuti. Se arresto/esito restano incerti, cover e sospensione wake restano attive, senza ripetere il comando di chiusura. WebView mantiene il comportamento 0.5.60.

0.5.60 / 82 — confronto mirato dell’identità browser. Ripristinato lo User-Agent originale del runtime Android WebView: rimosse soltanto la trasformazione Linux desktop e le relative prove unitarie obsolete. Conservati wide viewport, overview e viewport di 980 CSS px: layout desktop richiesto. Nessuna modifica a sessione/cookie, cover, wake, Voice o gestione Cloudflare della 0.5.59. Con UA Android il server potrebbe scegliere contenuti mobile: questa build isola il fattore identità, non garantisce la stessa variante server di Chrome desktop. Riscontro utente precedente: loop persistente anche con 0.5.59, Chrome desktop entra sulla stessa rete. Causa remota ancora non dimostrata. Dopo installazione chiudere e riaprire Jarvis per avviare un nuovo runtime; non cancellare i dati locali per questa prova. Verificare accesso e layout prima della wake. Se il loop scompare, il cambio UA è un fattore probabile, non una prova causale assoluta (rete e servizio possono variare).

Aggiornamento 0.5.59 / 81 — compatibilità verifica manuale Cloudflare, desktop attivo.

La 0.5.59 mantiene la modalità desktop della 0.5.58 e corregge la navigazione dei soli frame interni about:blank/about:srcdoc, richiesti dalla documentazione Cloudflare. Le navigazioni principali non HTTPS restano bloccate. Una risposta con header cf-mitigated: challenge viene riconosciuta e conservata per la verifica manuale; sulla pagina challenge non vengono applicati viewport o sonde Voice. Restano le scadenze del ciclo automatico: configurare l’accesso manualmente prima della wake. I nuovi eventi diagnostici distinguono HTTP della pagina principale, challenge e schemi bloccati senza salvare URL completi, cookie, header grezzi o testo. Nessun cambio a UA, Client Hints, cookie o permessi. Il collaudo Galaxy deve stabilire se queste correzioni risolvono il loop; causa esatta del rifiuto remoto non ancora dimostrata.

Stato 0.5.58: build/test locali superati; APK firmato con il certificato permanente e verificato, consegnato su Google Drive il 28 ottobre 2026. Collaudo Galaxy aperto; vedere VERIFICATION.md.

# JarvisWake 0.5.68 — assistente Android e ChatGPT Voice

La 0.5.58 / versionCode 80 richiede ChatGPT desktop sia nel preloader sia nella schermata Voice/login: user agent Linux desktop derivato dalla versione Chromium installata, wide viewport e overview, meta viewport di 980 CSS px a pagina caricata. La policy condivisa evita di riscrivere un UA identico durante handoff. URL progetto, cookie, wake, cover, saluto e singolo comando Voice restano invariati. Nessun percorso dot aggiunto. User-Agent Client Hints restano gestiti dal runtime: non si dichiara equivalenza con Chrome desktop. Layout, login e Voice reali richiedono collaudo sul Galaxy; nessuna correzione garantita per il precedente HTTP 404 remoto.

App Kotlin per Android 12–16 (minSdk 31, targetSdk 36), APK ARM64 destinato alla prova su Samsung Galaxy S24+ Android 16.

La 0.5.47 rende verificabile anche il funzionamento del rilevatore: registra invii periodici della sonda, esito del callback, conteggi dei nodi di ruolo/assistente visibili e stabili, oltre agli errori di parsing. Mantiene la diagnostica delle risposte e delle transizioni media della 0.5.46. Il log contiene solo stati, conteggi, identificativi numerici e lunghezze; il testo viene confrontato nel WebView e non viene esportato.

Il file `JarvisWake-0.5.47-test-bundle.zip` contiene l'APK ARM64 firmato col certificato permanente e le istruzioni per riprodurre la diagnostica. L'APK non firmato è disponibile separatamente; `scripts/firma-jarvis-0.5.47-termux.sh` verifica il suo checksum e il certificato permanente prima di firmarlo in Termux. Non installare un APK non firmato sopra una versione già presente. I checksum dei tre artefatti sono in `releases/0.5.47/SHA256SUMS`.

La 0.5.48 è stata ricostruita dal bytecode dell'APK storico fornito dall'utente: apre la stessa WebView autenticata per impostare modello e livello; durante Voice lo swipe sinistro ferma Voice e lascia la chat utilizzabile, mentre la wake successiva riporta alla stessa conversazione. Lo swipe verso il basso continua a tornare alla Home. Build e firma permanente sono verificate; il collaudo Galaxy resta aperto.

La frase inviata dopo l'attivazione di Voice resta configurabile dalla schermata principale e salvata nelle preferenze locali; «ciao jarvis» è il valore predefinito. Mantiene inoltre lo stop Voice della 0.5.44.

La 0.5.49 aggiunge la configurazione persistente dell'indirizzo del progetto ChatGPT. Il valore iniziale è `https://chatgpt.com/g/g-p-6adc87c2850c8191b52e9df5d94bfba6/project`; dalla schermata principale si può modificarlo e salvarlo. Il preloader e il ciclo wake leggono la stessa preferenza. Sono accettati soltanto URL HTTPS di progetti su `chatgpt.com`. Build pulita, 50 test JVM, test DOM, lint e firma permanente verificati; resta la prova sul Galaxy S24+.

La 0.5.50 aggiunge **Svuota cache WebView**. Poiché l'utente ha segnalato che l'errore Voice persiste, la 0.5.51 aggiunge anche **Elimina dati locali ChatGPT**: dopo conferma cancella cache, cookie e storage locale della WebView e richiede un nuovo accesso. Non elimina conversazioni salvate nell'account. Build, 50 test JVM, test DOM, lint e firma permanente sono verificati; resta il collaudo sul Galaxy S24+.

L'utente ha provato anche la 0.5.51: dopo il reset completo e un nuovo login, l'errore rosso Voice è rimasto. La diagnostica 0.5.52 ha registrato un HTTP 404 da `persistent.oaistatic.com` durante il ciclo. Aprendo in Chrome l'URL completo del modulo, l'utente ha ricevuto `BlobNotFound`: l'asset remoto non esiste all'indirizzo richiesto e la correzione deve avvenire lato servizio ChatGPT.

Nella diagnostica successiva, esportata il 24 ottobre 2026 alle 17:46, tre cicli hanno ciascuno un solo `GREETING_SENT` e un HTTP 404 da `persistent.oaistatic.com`. Il monitor ha riportato `assistantNodes=0` dopo l'invio: il log non mostra il testo della risposta e non permette di verificare se ChatGPT abbia seguito il prompt. L'utente ha osservato premesse e saluti ridondanti; l'invio singolo da Jarvis non garantisce una risposta conforme. Non è stata implementata una correzione.

## Anteprima del volume 0.5.57

Ogni variazione manuale del cursore volume salva il nuovo livello, interrompe l’anteprima precedente e riproduce il file selezionato dall’inizio. La durata è il tempo minimo impostato; se vale zero, l’anteprima dura 2 secondi. I brani più brevi si ripetono in loop fino alla scadenza, misurata dall’avvio del player dopo la preparazione asincrona. Zero volume produce un’anteprima silenziosa.

L’anteprima funziona anche con l’opzione audio di attesa OFF e non la modifica. Richiede un file già selezionato e non avvia Voice. Si ferma lasciando la schermata principale (anche per aprire le impostazioni ChatGPT), perdendo il focus audio o avviando un ciclo. Durante un ciclo Voice visibile il volume resta regolabile, ma l’anteprima non sostituisce il player del ciclo né ne riavvia il conteggio minimo. Se il ciclo è nascosto in attesa della wake, l’anteprima è disponibile; la wake la interrompe prima della ripresa. Aprire/ricreare l’app non genera anteprime automatiche. Collaudo Galaxy ancora aperto.

## Tempo minimo prima di Voice 0.5.56

Cursore «Tempo minimo audio prima di Voice» da 0 a 30 secondi, salvato automaticamente e applicato dal ciclo successivo (wake, avvio manuale e ripresa). Default 0: nessuna attesa aggiuntiva. Il conteggio inizia dopo `MediaPlayer.start()`, non durante la preparazione del file. Se il pulsante Voice è pronto prima della scadenza, Jarvis attende; se è pronto dopo, lo attiva alla prima verifica utile senza aggiungere un nuovo ritardo. I controlli DOM e il limite di un solo gesto restano attivi: le coordinate vengono rilette durante l’attesa.

L’attesa minima vale quando l’audio è abilitato e disponibile, anche con volume a zero. Audio assente, disattivato, errore o perdita del focus liberano l’attesa. Restano lo stop su cancellazione/screen-off e i timeout di sicurezza del ciclo/audio. Se la pagina mostra già Voice attiva non viene inviato un nuovo comando. La latenza acustica del dispositivo non è misurata; collaudo Galaxy aperto.

## Volume audio di attesa 0.5.55

Cursore «Volume audio di attesa» da 0 a 100%, con percentuale visibile e salvataggio automatico. Il livello viene mantenuto riaprendo l’app e nei cicli successivi; il valore iniziale è 100% per conservare il comportamento precedente. Zero silenzia il loop. La regolazione agisce sui due canali del solo MediaPlayer di attesa, anche quando già in riproduzione; resta soggetta al volume multimediale e al dispositivo di uscita del telefono.

## Audio di attesa 0.5.54

La schermata principale offre «Scegli audio di attesa» e l’interruttore persistente «Audio di attesa attivo», inizialmente disattivato. Selezionare un file audio riproducibile (massimo 64 MiB) e attivare l’interruttore. Il file viene copiato nella memoria privata esclusa dal backup; non serve mantenere il file originale né concedere accesso generale alla memoria. Annullare la selezione o importare un file non valido conserva la selezione precedente. Disattivare l’opzione conserva il file.

Il loop parte all’apertura della cover dopo wake word, avvio manuale o ripresa di un ciclo nascosto. Continua attraverso il passaggio dalla schermata iniziale alla WebView senza sovrapporre riproduttori. Si ferma quando la pagina conferma Voice attiva, alla chiusura/cancellazione del ciclo, allo spegnimento dello schermo, alla perdita del focus audio o dopo 40 secondi di sicurezza. Il solo dispatch del tocco Voice non interrompe l’attesa. Un errore di riproduzione non blocca l’avvio Voice. Si usa il volume multimediale del telefono; l’avvio asincrono evita di bloccare la UI, ma non garantisce latenza zero.

Il saluto testuale esistente resta configurato separatamente. Il collaudo reale sul Galaxy S24+ è ancora da eseguire; non è una correzione dell’HTTP 404 remoto di Voice.

## Cover 0.5.53

Tocco singolo centrale: mute/riattivazione delle tracce microfono Voice; animazione fucsia scuro tendente al rosso durante il mute confermato. Tocco sotto i cerchi: alterna opaco/semitrasparente. Doppio tocco centrale: chiude il ciclo; doppio tocco in alto a destra: esporta diagnostica. Swipe verso il basso: Home; swipe sinistro: chat interattiva.
Il tocco centrale è confermato dopo la finestra del doppio tocco, così la chiusura non provoca un mute intermedio. Il mute riguarda le tracce del microfono nella sessione WebView, non la riproduzione di ChatGPT. Prima dell’avvio di Voice non modifica il microfono. Il collaudo sul Galaxy resta necessario.


La zona della semitrasparenza è limitata alla fascia sotto i cerchi e, quando «Sblocca telefono» è visibile, termina 16 dp prima del pulsante. Il pulsante conserva la precedenza ai tocchi; la parte bassa dello schermo non è un comando di opacità.
## Documentazione e continuità

- [docs/INSTALLAZIONE_NUOVO_DISPOSITIVO.md](docs/INSTALLAZIONE_NUOVO_DISPOSITIVO.md): checklist completa per installazione pulita, permessi Android, Accessibilità, assistente, firma APK e collaudo su telefono bloccato.
- [CONTINUITY.md](CONTINUITY.md): stato del progetto, requisiti consolidati e indicazioni per riprendere il lavoro.
- [PROMPT_CONTINUITA.txt](PROMPT_CONTINUITA.txt): istruzione pronta per una nuova conversazione di sviluppo.
- [docs/ROADMAP.md](docs/ROADMAP.md): comportamento implementato e verifiche ancora aperte.
- [VERIFICATION.md](VERIFICATION.md): verifiche eseguite e limiti delle prove.
- [AGENTS.md](AGENTS.md): istruzioni operative per chi modifica i sorgenti.

## Obiettivo

JarvisWake riconosce localmente “Hey Jarvis”, vibra, libera il microfono e apre ChatGPT Voice usando una sessione **ChatGPT Web autenticata con l'account dell'utente**. Questo percorso conserva l'ambiente ChatGPT dell'account, invece di ricostruire memoria e istruzioni tramite API separate.

La destinazione predefinita è il progetto ChatGPT indicato dall'utente; l'URL è modificabile dalla schermata principale e salvato localmente. La creazione automatica verificata di una nuova conversazione per ogni ciclo non è ancora dimostrata: non presumere che la pagina di progetto apra sempre una chat nuova.

## Configurazione iniziale

1. Installare l'APK e concedere microfono e notifiche.
2. Abilitare **JarvisWake: avvio Voice** nelle impostazioni Accessibilità e consentire l'HUD sopra altre app.
3. Aprire JarvisWake, scegliere **Configura accesso a ChatGPT** e accedere al proprio account.
4. Tornare a JarvisWake. La sessione e i cookie restano nel profilo WebView privato dell'app.
5. Controllare il campo **Indirizzo del progetto ChatGPT** nella schermata principale. Modificarlo se serve e premere **Salva indirizzo ChatGPT**; il nuovo indirizzo verrà usato sia dalle impostazioni interattive sia dai cicli wake.
6. Premere **Imposta Jarvis come assistente del telefono** e selezionare JarvisWake nella richiesta Android. Il sistema userà Jarvis al posto dell'assistente precedente per i richiami di sistema.

La frase inviata quando si attiva Voice si modifica dalla schermata principale, nel campo **Frase da inviare dopo l'attivazione di Voice**. Premi **Salva frase**: la scelta resta memorizzata dopo la chiusura dell'app. **Ripristina «ciao jarvis»** ripristina il testo iniziale.

Se l'autenticazione viene rifiutata dal browser incorporato, il percorso web non è utilizzabile su quel dispositivo.

La dichiarazione Android del servizio assistente include la sessione, il servizio di riconoscimento e `supportsAssist=true`: Android richiede questi metadati per elencare Jarvis tra gli assistenti. Prima di chiedere il ruolo, l'app memorizza il riconoscitore vocale predefinito e inoltra a esso le richieste di dettatura del sistema. Il riconoscimento “Hey Jarvis” resta separato e locale tramite Vosk. Se il telefono non ha un riconoscitore predefinito disponibile, la dettatura non è fornita da Jarvis.

## Flusso automatico

1. La wake word o **Avvia ciclo completo: ChatGPT Voice** creano un solo ciclo identificato. Un secondo avvio durante il ciclo viene ignorato.
2. A schermo acceso la cattura Vosk resta attiva anche con il keyguard visibile. Quando il display si spegne, la cattura prosegue per 30 secondi così è ancora possibile pronunciare la wake word; se il display si riaccende prima, la finestra termina e l’ascolto continua. Dopo 30 secondi `AudioRecord` viene rilasciato, mentre modello e recognizer Vosk restano caricati finché il servizio di ascolto resta attivo. Alla wake il microfono viene rilasciato prima di avviare Voice.
3. Se il telefono è bloccato, Jarvis accende lo schermo e mostra la cover J.A.R.V.I.S. sopra la schermata di blocco, senza richiedere automaticamente impronta/PIN.
4. Quando l'attività è ripresa e ha il focus, apre la propria attività WebView, anch'essa abilitata a restare visibile sopra il blocco. Il telefono resta bloccato: lo sblocco non è più un prerequisito per il ciclo Voice. Il comando **Sblocca telefono** rimane disponibile come azione facoltativa.
5. Il WebView carica l'URL del progetto JarvisRuntime. Dopo una ricreazione prima del tocco ripristina la navigazione salvata, oppure carica l'URL del progetto se il ripristino non è disponibile. La nuova chat per ogni sessione resta da verificare.
6. Il DOM individua il controllo Voice; due letture consecutive ne verificano la posizione. Una sola sequenza touch nativa DOWN/UP attiva il blocco contro ulteriori tocchi, condiviso dall'intero ciclo.
7. Dopo il tocco, Voice viene considerata attiva e le note della cover scompaiono, come richiesto. Le osservazioni successive servono per il saluto e per riconoscere la chiusura; non autorizzano altre pressioni Voice.
8. X, Indietro o schermo spento invalidano il ciclo prima di rimuovere finestre e WebView. Le callback precedenti non possono riaprire ChatGPT. Il servizio di ascolto già avviato riprende dopo il rilascio del WebView.

Il coordinatore applica scadenze indipendenti dal WebView e dall'accessibilità: 45 secondi perché la schermata Jarvis sia pronta e 40 secondi per caricamento/ricerca del controllo Voice. Un caricamento che non termina non può più saltare il timeout. Errori della pagina principale e arresto del processo WebView chiudono il ciclo e lasciano una diagnosi nella schermata principale.

Mentre la schermata principale ha avviato l'ascolto, Jarvis carica il progetto in un WebView invisibile e osserva il DOM senza premere Voice. Il preloader resta dietro la schermata interattiva, così non intercetta i tocchi dei pulsanti. Alla wake trasferisce il medesimo WebView alla schermata Voice, evitando una seconda creazione e un nuovo caricamento della pagina. L'eventuale posizione DOM osservata prima della wake viene usata solo come indizio: le coordinate vengono scalate al WebView corrente e confrontate con una nuova osservazione DOM prima del gesto unico. Il vantaggio reale di latenza e la disponibilità della sessione ChatGPT vanno verificati sul Galaxy.

Se il display si spegne durante l'ascolto Vosk, la cattura continua per 30 secondi; alla scadenza `AudioRecord` viene rilasciato e il modello resta in memoria senza mantenere un wakelock CPU. Un risveglio dello schermo riattiva la cattura usando il modello già residente e richiede subito il preloader alla `MainActivity` già esistente, senza attendere lo sblocco e senza avviare schermate nascoste dal background. Se l'attività non esiste (per esempio dopo un cold start), l'ascolto riprende comunque ma il preloader resta in attesa che Jarvis venga aperto. Se lo schermo si spegne durante un ciclo ChatGPT, il ciclo viene annullato; finché resta la finestra residua dei 30 secondi il servizio può poi ascoltare di nuovo la wake word.

Android invia `ACTION_SCREEN_ON` quando cambia lo stato interattivo del dispositivo ([riferimento API](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_ON)); gli avvii di Activity dal background sono soggetti a restrizioni ([documentazione Android](https://developer.android.com/guide/components/activities/secure-bal)).

La diagnostica associa cattura, wake, rilascio del microfono, ciclo, riuso del preloader, controllo Voice, gesto, traccia microfono della pagina e riproduzione rilevata nei tag media HTML. La riproduzione media rilevata non prova da sola che il primo parlato sia stato effettivamente udibile.

Una ricreazione Android **dopo** il tocco Voice chiude il ciclo: lo stato audio vivo di una pagina non si ripristina tramite la cronologia WebView. Non viene mai premuto Voice una seconda volta per tentare di ricostruirlo. Dopo la morte del processo, intent appartenenti al ciclo precedente vengono rifiutati.

La sessione assistente Android disabilita la propria finestra predefinita e la nasconde dopo aver richiesto l'attività principale. Il percorso Vosk resta distinto dall'invocazione dell'assistente tramite il sistema.

## Messaggio iniziale

Il testo inviato è **«ciao jarvis»**, senza l'istruzione precedente «Dì soltanto». Il saluto non viene consumato quando il campo manca: dopo aver osservato Voice, l'app attende un compositore identificabile, vuoto, abilitato e stabile in due letture. Se l'osservatore ha rilevato una richiesta microfono, attende anche una traccia audio live e nessuna acquisizione pendente o fallita. La 0.5.34 inserisce il saluto via DOM senza assegnare il focus al campo, per evitare l'apertura momentanea della tastiera; serve conferma sul dispositivo che ChatGPT accetti ancora l'inserimento e l'invio.

Dopo la compilazione del campo attende un unico pulsante **Invia** visibile e abilitato. Il solo tipo HTML `submit` non basta e non vengono usati Invio sintetico o invio indiscriminato del form. Gli esiti che confermano che nessun click è avvenuto restano ritentabili finché il compositore e Invia sono pronti o il ciclo viene annullato; non c'è una scadenza di 10 secondi che consumi il saluto. Il click viene bloccato prima dell'esecuzione nel DOM e un esito ambiguo termina il tentativo senza un secondo invio. Se il campo contiene testo dell'utente non viene sovrascritto.

Il DOM e la traccia microfono non certificano che il collegamento remoto Voice sia pronto a rispondere: l'accettazione effettiva del testo e il parlato devono essere verificati sul telefono. Le pagine ChatGPT e il comportamento dei loro editor possono cambiare.

## Schermata JarvisWake

L'HUD ha il **fondo opaco come impostazione iniziale**, il nome `J.A.R.V.I.S.`, la versione sotto il nome e le note più in basso. Quando Voice è attiva, le note vengono nascoste.

Il pulsante **Copertura** nella schermata principale indica l'impostazione corrente e permette di alternare fondo opaco e semitrasparente al 50%. La scelta è salvata e resta valida dopo la chiusura dell'app e il riavvio del telefono. Si applica a tutte le cover (sblocco, caricamento e Voice), anche se già visibili. La stessa opzione si alterna toccando sotto l’animazione; la cover non presenta il pulsante X.

Finché il telefono è bloccato viene usata la cover interna al WebView, senza X e con pulsante di sblocco. Il doppio tocco centrale chiude il ciclo. La cover intercetta i tocchi fisici per evitare che raggiungano le pagine dell'account sottostanti; l'avvio Voice continua a inviare una sola sequenza touch direttamente al WebView. Le note di preparazione sono aggiornate anche sulla cover interna.

Dopo lo sblocco, la cover di accessibilità sostituisce quella interna quando disponibile. Se l'HUD esterno non è disponibile, rimane la cover interna con gli stessi gesti. I controlli Jarvis non possono sostituire o sovrapporsi alle richieste di autenticazione protette di Android.

## Privacy e limiti

- **Voice è accessibile senza autenticazione del telefono**: chi pronuncia la wake word può avviare una conversazione con l'account ChatGPT già connesso, inclusa la memoria che ChatGPT rende disponibile alla conversazione. Vosk riconosce parole, non l'identità del parlante.
- L'avvio da telefono già bloccato è distinto da un nuovo spegnimento dello schermo durante la conversazione: quest'ultimo continua a chiudere Voice e il ciclo. Questa versione non aggiunge funzionamento prima del primo sblocco dopo il riavvio.
- Il supporto Android a un'attività sopra il blocco non garantisce da solo il funzionamento di WebRTC/ChatGPT Voice su One UI: serve la prova sul Samsung. Login ChatGPT e permessi devono essere già configurati.
- Il riconoscimento della wake word usa Vosk 0.3.75 e il modello `small-en-us 0.15`; modello e recognizer restano in memoria finché il servizio di ascolto è attivo. La cattura e l’analisi audio si fermano dopo 30 secondi a display spento; non salva audio o trascrizioni. L’indicatore privacy del microfono resta attivo durante la finestra di cattura.
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

## Prova della modalità App ChatGPT 0.5.61

1. Impostare Jarvis come assistente del telefono e abilitare Accessibilità.
2. Usare Seleziona collegamento ChatGPT, scegliere Jarvis Runtime e confermare il nome esatto mostrato nella pagina. Se l’elenco è vuoto o negato, il percorso shortcut non è disponibile in quel contesto: non viene aperta una chat sostitutiva.
3. Provare il collegamento senza Voice e preparare manualmente login, account/workspace e permesso microfono dell’app ChatGPT. Provare manualmente Voice prima dell’automazione per non trovarsi un consenso Android sotto la cover.
4. Selezionare App ChatGPT e provare una wake a telefono sbloccato. Verificare nome progetto, un solo comando, conferma Voice e audio bidirezionale; poi mute e arresto.

La prima versione nativa richiede collaudo dei controlli del Galaxy: non garantisce funzionamento bloccato senza sblocco, né consente di dichiarare Voice attiva dal solo click. Il saluto testuale via DOM è specifico del WebView e non viene inoltrato all’app nativa; la conversazione nativa parte dall’input vocale dell’utente. Dalla 0.5.67, swipe sinistro termina Voice e lascia visibile ChatGPT; swipe giù termina Voice e invia Home, entrambi dopo conferma di arresto. Doppio tocco in alto a destra durante la sessione nativa esporta la diagnostica, sospendendo la cover soltanto per il salvataggio manuale e ripristinandola al termine. Se il controllo di arresto non viene riconosciuto, serve terminare Voice nell’app ChatGPT tramite i comandi di sistema e riaprire Jarvis: non si può certificare arresto/microfono dal solo stato locale. La riconnessione del servizio non riapre lo shortcut e non ripete il comando Voice; la morte dell’intero processo non garantisce la chiusura della sessione di un’altra app.
