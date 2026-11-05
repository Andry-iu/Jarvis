0.5.107 / 129 — via esplicito 5 novembre2026 all’intero percorso Google, con aggiunta concordata dei testi su tutta la cover. Google STT/TTS opzionale OFF, modalità congelata nel ciclo e cambio solo dopo rilascio; wake offline/manuale conservate. VAD RMS locale/preroll400ms e Google STT gRPC streaming dall’inizio del parlato, con un solo testo definitivo; WebView autenticata legata a conversazione esistente nel progetto, bozze protette/invio singolo/dispatch distinto da riscontro DOM. Stesso plugin/backend con speak correlato sessione/turno/UUID, progressi/claim/journal/outbox/scadenze. OAuth utente breve durata, cifrato Keystore, nessuna API key presunta sufficiente/chiave privilegiata nell’APK; token importati senza refresh automatico. TTS configurabile Enceladus da verificare via catalogo, nessuna API Google reale durante sviluppo. Microfono sospeso in sintesi, barge-in assente; sinistro solo hide, altre chiusure fermano audio senza cancellare lavori desktop. Testi piccoli semitrasparenti su tutta cover, toggle basso-destra, due dita scorrono; solo RAM cancellata alla chiusura. Consumi Google separati, token modello non esposti. Default Voice .106, appunti/preferiti/WhatsApp/Maps/chiamate conservati; Watch/Windows invariati. Dettagli docs/VOCE_GOOGLE.md e VERIFICATION.md; prove Galaxy ancora necessarie.

# Aggiornamento .89 — Preferiti separati

Preferiti e cache contatti apre le due sezioni. Nei Preferiti si aggiunge qualsiasi numero dalla rubrica oppure nome/numero manuali, senza creare contatti Android; importazione facoltativa. Cache rimovibile senza perdere Preferiti. Stesso plugin/pairing, risoluzione tramite riferimenti locali/revisioni e nessun numero arbitrario da MCP. Vedi [PREFERITI_CONTATTI.md](PREFERITI_CONTATTI.md).

# Aggiornamento .88 — telefonate e cache

Nuovo jarviswake_call_by_name per nome esplicito: risoluzione locale e chiamata in un comando, nessuna selezione automatica fra omonimi. Cache cifrata20 voci in Configurazione → Collegamento e azioni → Maps, chiamate e WhatsApp → Contatti frequenti e preferiti. Controlli rubrica/audio restano obbligatori. Vedi [CHIAMATE_RAPIDE.md](CHIAMATE_RAPIDE.md) per PiP Maps, polling adattivo200ms e tempi. WhatsApp .87 confermato funzionante dall’utente, sessione conservata.

# Aggiornamento 0.5.87 — WhatsApp QR e gestione cover

Le istruzioni UI WhatsApp .81 più sotto sono storiche: nella .87 sono sostituite dal client collegato sul telefono. Configurazione → Collegamento e azioni → Maps, chiamate e WhatsApp → Collega WhatsApp con QR. Genera il QR reale e scansionalo da WhatsApp → Dispositivi collegati (anche fotografandolo con un secondo telefono mentre valido). Attendi Collegato. Invio diretto, nessun editor/bozza/clic WhatsApp e nessun PC sempre acceso; ack server distinto da consegna. Vedi [WHATSAPP_QR.md](WHATSAPP_QR.md).

Maps conserva la cover solo con proiezione Android Auto accertata. Prima di telefonare mostra una cover di preparazione, richiama il launcher ChatGPT senza shortcut progetto e richiede un solo stop; chiamata solo dopo conferma ciclo chiuso/audio libero stabile. Doppio tocco/swipe annullano subito la preparazione, senza riaperture da polling. Vedi [BRIDGE_COVER_CHIAMATE.md](BRIDGE_COVER_CHIAMATE.md). I risultati reali sul Galaxy restano da collaudare.

# Menu .83 e guida interrogabile

Da Configurazione → Collegamento e azioni apri Abbinamento e stato, Maps/chiamate/WhatsApp oppure Accesso agli appunti dal plugin. I consensi e i dati salvati restano gli stessi. Nuovo jarviswake_guide nel plugin permette di consultare istruzioni/argomenti e rispondere a domande, senza telefono online e senza eseguire azioni. Vedi ORGANIZZAZIONE_MENU.md e GUIDA_COMPLETA.md.

# Aggiornamento0.5.82 — appunti locali

Stesso abbinamento e stesse azioni .81. Nuovo consenso separato **Consenti lettura e aggiornamento appunti da ChatGPT** e pulsante **Apri appunti locali**. Elenco/lettura/salvataggio con revisioni e archivio cifrato distinto dal pairing; nessun nuovo permesso Android. Google Calendar resta al plugin dedicato. Vedi [APPUNTI_LOCALI.md](APPUNTI_LOCALI.md) per comandi, istruzione facoltativa di rilettura, trasmissione dei contenuti richiesti e limiti.

# Stato e azioni — aggiornamento0.5.81

Il primo scambio bidirezionale .80 è riuscito realmente nella conversazione: risposta del dispositivo alla stessa richiesta, app.80/code102, servizio wake e input propri verificati, ciclo IDLE/cover assente. Questo verifica il collegamento e non dimostra ancora le nuove azioni né Live in background sul Galaxy.

## Aggiornare senza rifare l’abbinamento

Installare il nuovo APK firmato sopra l’app esistente, senza disinstallare/cancellare dati. Il codice, il flag collegamento e la chiave Android Keystore sono conservati anche dopo normale riavvio. La rete riparte quando JarvisWake/servizio torna operativo; questo non certifica avvio automatico dopo ogni reboot o funzionamento sotto force-stop/restrizioni Android. Reinstallazione senza dati, cancellazione, revoca o cambio di servizio richiedono nuovo abbinamento.

1. In Jarvis → **Collegamento ChatGPT — stato e azioni**, lasciare attivo il collegamento e attivare **Consenti Maps, contatti, chiamate e WhatsApp**.
2. Premere **Autorizza rubrica e chiamate** e concedere i permessi Android richiesti (rubrica, chiamate, stato telefonico). Quest’ultimo serve al passaggio/ripresa wake dopo telefonata. Nessun permesso viene ottenuto dal plugin o da un consenso vocale al posto dei controlli Android.
3. Lasciare attivo il servizio wake già previsto. Accessibilità Jarvis deve essere connessa per controllo Voice/WhatsApp e avvio delle app da background.
4. Usare lo stesso plugin **JarvisWake Link**. La pubblicazione ne aggiorna i tool; se la chat vede soltanto stato, riaprire la chat con il plugin per caricare i nuovi comandi. Non creare un nuovo Site/plugin e non rifare l’abbinamento per questo motivo. Eventuali approvazioni richieste da ChatGPT restano gestite dai suoi controlli supportati.

## Cosa provare

- **Maps:** «Usa JarvisWake Link e avvia Maps in auto verso [indirizzo preciso]». Poi «Cambia destinazione: [altro indirizzo]». Un Intent ufficiale google.navigation viene indirizzato a Google Maps; il secondo comando propone una nuova destinazione. Per continuare a parlare con Maps in primo piano abilitare le conversazioni in background di ChatGPT Live. La cover viene rimossa per rendere visibile l’app, senza cambiare conversazione/progetto o spegnere Live; non vengono installati SDK di navigazione o API separate. Provare prima sbloccato, poi con Android Auto collegato ad auto ferma: `intent_dispatched` prova soltanto il dispatch, non rotta visibile o guida partita.
- **Telefonata:** «Chiama [nome completo del contatto]». Il modello prima cerca il nome richiesto e usa un riferimento temporaneo del risultato; se più persone/numeri restano possibili, deve chiedere quale. Il telefono ricontrolla la riga rubrica/numero; nessun numero può essere inventato nel comando. Stop della propria Voice, audio libero stabile1s, un solo ACTION_CALL. `call_intent_dispatched` non significa destinatario raggiunto/risposta. Se Live resta attiva in background sotto un’altra app e il controllo Termina non è osservabile, può risultare `voice_stop_unconfirmed`: non chiama condividendo il microfono, serve chiudere Voice e richiedere esplicitamente una nuova chiamata. Nessun ritorno a chat generica per aggirare questo limite.
- **WhatsApp:** «Invia su WhatsApp a [contatto] questo testo: [messaggio]». Risolvere il contatto e dettare il contenuto prima del comando. Solo WhatsApp personale com.whatsapp, telefono con numero internazionale determinabile e sbloccato. Apre la chat indirizzata tramite wa.me senza testo, riconosce intestazione/campo vuoto, inserisce una volta e cerca un unico Invia semantico stabile. Non usa coordinate o ID privati inventati, non sovrascrive una bozza. Intestazione non univoca, permessi mancanti o controlli non riconosciuti fermano l’invio. `composer_cleared_after_send` indica campo svuotato dopo il clic; `send_dispatched_unconfirmed` indica solo clic accettato. Nessuno dei due certifica consegna o lettura: il primo invio reale va verificato nella chat WhatsApp del destinatario scelto dall’utente.

Le chiamate sospendono la wake anche dopo la chiusura di Live; lo stato telefonico viene osservato senza numeri. Alla fine IDLE e audio libero consentono ripresa, rispettando il limite30s a schermo spento e ripresa richiesta a schermo acceso. Un avvio telefonico non osservato entro30s con stato IDLE/audio libero libera l’attesa per non lasciare wake disattivata a seguito di un lancio fallito; SIM/permessi/OS possono richiedere interazione e il dispatch non ne certifica l’esito. Non sono modificati audio routing o microfono di sistema.

## Esiti e tutela dei tentativi

Ogni nuova richiesta autorizzata usa un UUIDv4; per leggere l’esito o ritentare il trasporto si conserva lo stesso ID. Non generare un altro ID automaticamente dopo `outcome_uncertain`, `send_dispatched_unconfirmed` o chiamata incerta. Sul server, claim condizionale e risultato di sola prima risposta; sul telefono, consumo cifrato prima del claim/azione e outbox per ritrasmettere soltanto il risultato. Riavvio/rete assente non diventano nuove chiamate/invii. Un metadato di deduplicazione senza contenuto del comando rimane anche dopo la pulizia dei vecchi risultati; un vecchio ID non torna eseguibile.

La ricerca contatti restituisce massimo8 nomi/etichette/ultime4cifre e riferimenti scadenti dopo10min o ricerca successiva; i numeri completi restano sul telefono. Il testo WhatsApp e l’indirizzo necessari passano nel comando privato, vengono rimossi dal record richiesta dopo la risposta e non entrano in diagnostica o Git. La pulizia di ulteriori record vecchi è opportunistica, non una cancellazione programmata garantita.

Gli stati/risultati `permission_required`, `actions_disabled`, `locked`, `contact_expired`, `contact_changed`, `voice_stop_unconfirmed`, `recipient_unverified`, `draft_exists`, `send_unavailable` e `outcome_uncertain` richiedono interpretazione: non sono successo e non autorizzano fallback a destinatari, numeri, bozze o clic diversi.

Le istruzioni seguenti descrivono l’abbinamento iniziale .80 e rimangono valide; i vincoli storico stato-only sono superati soltanto dal gruppo .81 esplicitamente autorizzato.

# Prova bidirezionale JarvisWake Link — 0.5.80

Obiettivo: durante la conversazione ChatGPT Live richiedere uno stato e ricevere una risposta dalla stessa app JarvisWake sul Galaxy. Questo rilascio legge soltanto stati; Google Maps, comandi UI, audio, trascrizioni ed eventi che risvegliano autonomamente una conversazione inattiva sono fuori ambito.

## Un solo abbinamento iniziale

1. Installare l’APK 0.5.80 firmato permanente sopra l’app esistente.
2. Nei plugin creati dal proprio account ChatGPT installare/collegare **JarvisWake Link**, se non già connesso. Usare lo stesso account/spazio proprietario del servizio.
3. Nella Home Jarvis aprire **Collegamento ChatGPT — prova di stato**, poi **1. Apri pagina privata di collegamento**. Pagina: https://jarviswake-link-andrea.autech-sm267-2695.chatgpt.site . L’accesso è privato e può richiedere l’accesso ChatGPT nel browser.
4. Nella pagina premere **Crea codice di collegamento** e **Copia codice**. Tornare in Jarvis, **2. Incolla codice di collegamento**, incollare e salvare; attivare **Collegamento di stato attivo**. Il codice contiene credenziali: copiarlo soltanto nell’app, mai in chat o nei log.
5. Tornare alla Home Jarvis e avviare/mantenere l’ascolto già previsto. Il collegamento di stato funziona mentre il servizio Jarvis è attivo, anche quando il suo input wake è sospeso per Voice, oppure mentre una schermata Jarvis è visibile. Non avvia da solo il servizio microfono. Android può limitare la rete a schermo spento; la prima prova va fatta sbloccato, in rete.

## La prova minima

Durante ChatGPT **Live** dire: «Usa JarvisWake Link: richiedi e leggi lo stato del mio telefono».

Il plugin elenca i dispositivi abbinati; richiede lo stato del device_id scelto e riceve request_id. Legge poi il risultato di quello stesso device_id/request_id. Una risposta `responded` con `source=paired_device` prova l’andata/ritorno autenticato della richiesta; riferire anche l’età della risposta. Un risultato `pending`, `expired` o dispositivo assente NON è successo e non autorizza a inventare stati o usare un vecchio snapshot come corrente. Le richieste sono valide25s; risposta successiva rifiutata. Se il risultato è inizialmente pending, poche letture entro la scadenza possono raccogliere la risposta: non avviare monitoraggi infiniti o nuove richieste in loop.

Il primo risultato atteso riporta versione0.5.80/code102, servizio wake, cattura, fase del ciclo, cover e schermo. Durante Voice è normale che il servizio resti attivo ma la cattura wake sia `not_recording`. `active_verified` richiede configurazione propria non silenziata e una lettura recente della sessione corrente; `unverified` non prova ascolto. Il flag del ciclo e fase VOICE sono stati di Jarvis, non certificano audio o stato del server ChatGPT. Cover indica soltanto View proprie attached/shown (accessibilità, web, avvio), non un’immagine del telefono né la visibilità completa sopra ogni finestra.

Disattivando il collegamento nell’app si ferma la rete della prova. **Cancella collegamento dal telefono** rimuove il materiale locale. Per revocare le credenziali server, nella pagina privata usare **Revoca collegamenti precedenti**: agisce soltanto sui dispositivi abbinati al proprio utente, richiede conferma nella pagina e impone un nuovo abbinamento. Sono consentiti al massimo4 abbinamenti e4 richieste pendenti per telefono.

## Stato verificato nello sviluppo

- Stesso cloud Jarvis e toolchain esistente; nessun telefono collegato né chiamata reale avviata qui.
- Backend pubblicato owner-private dal servizio Sites, MCP pronto e plugin creato. Questa conferma non attesta che il plugin sia installato/collegato nel modello/superficie usato: gli strumenti non erano comparsi in questa chat a fine preparazione. Se dopo installazione non compaiono, aprire una nuova chat selezionando quel plugin; non ricreare Site/plugin.
- Il giro completo nel test Node usa R2/dispositivo simulati. Non costituisce una misura o un riscontro del Galaxy.
- Resta necessaria una risposta reale dello stesso request_id durante Live, dopo installazione/abbinamento. Gli eventuali permessi richiesti da ChatGPT vanno gestiti dai controlli supportati; non presumere che un consenso vocale sostituisca un’approvazione sullo schermo.

## Ripresa tecnica

Server canonico versionato: `phone-bridge/`. Checkout di pubblicazione nello stesso cloud: `/workspace/sites/jarviswake-link`. Sincronizzare il server canonico verso quel checkout prima di pubblicare; non duplicare identità o configurare un altro host automaticamente.

- Site: `appgprj_6ae75ae6cdd08191ab126c1d392a603a`.
- Plugin: `plugin_asdk_app_sites_20b3e95cbcfc8191b4b5c0f04aee3679`.
- Endpoint MCP: `/mcp`, tools `jarviswake_devices`, `jarviswake_request_status`, `jarviswake_status_result`.
- Protocollo device: POST HTTPS `/device/poll` e `/device/respond`; soltanto richiesta `status`, schema dello snapshot chiuso. Nessuna API di comando generico.
- Credenziale privata Sites in `SITE_DEVICE_ACCESS` come segreto runtime, mai in manifest o codice. Il codice d’abbinamento include accesso al solo Site privato e credenziale del dispositivo; nell’app cifratura AES-GCM con chiave Android Keystore e backup già disabilitato.
- R2 conserva richieste/risposte correlate; validità25s, pulizia opportunistica dei record oltre10min alle richieste successive, non un’automazione di cancellazione garantita. Revoca rimuove l’accesso ma non promette la cancellazione immediata di ogni vecchio oggetto.
- Gli errori rete restano nel collegamento e non fermano/riprendono Voice/wake. Nessuna scrittura dei token nella diagnostica esportabile.
- Check locali server: `node --test test/*.test.mjs`, `bash scripts/build.sh`, `node scripts/validate-artifact.mjs`. Il pacchetto Sites deve includere `.openai/hosting.json` e `dist/server/index.js` mantenendo il prefisso dist. La pubblicazione usa Git del Site e le operazioni native Sites; nessuna esposizione pubblica del cloud Jarvis.

Fonti primarie: [plugin in Live](https://help.openai.com/en/articles/20001274-chatgpt-voice), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [API R2](https://developers.cloudflare.com/r2/api/workers/workers-api-reference/). Disponibilità effettiva del plugin e approvazioni dipendono da account, superficie e restrizioni.
