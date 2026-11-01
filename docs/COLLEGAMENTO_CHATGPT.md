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
