# JarvisWake — installazione pulita e configurazione di un nuovo dispositivo

Stato: guida operativa basata sul codice 0.5.26 e sul collaudo del 19 ottobre 2026 su Samsung Galaxy S24+ / Android 16. Su altri produttori o versioni Android nomi delle voci e disponibilità del ruolo assistente possono cambiare. Non equivale a certificazione su tutti i dispositivi.

## Prima di iniziare

- Usare un APK **ARM64** proveniente dagli artifact ufficiali del repository; verificare l'esito del workflow Android APK. La build selettiva parte con commit contenente `[build-apk]` oppure manualmente con `workflow_dispatch`.
- Le build destinate agli aggiornamenti devono usare la **stessa chiave permanente** (`JARVIS_KEYSTORE_BASE64` e password `ANDRY` in GitHub Secrets); APK firmati con altre chiavi non aggiornano un'installazione esistente. Non condividere né versionare keystore, password o contenuti Base64.
- Connessione Internet per ChatGPT Web e account ChatGPT valido; riconoscimento «Hey Jarvis» tramite Vosk offline. Non è garantita l'autenticazione tramite WebView su tutti i dispositivi.
- Se il dispositivo ha già una versione installata con certificato diverso, l'aggiornamento viene rifiutato: non disinstallare senza aver valutato la perdita di sessione WebView e preferenze.

## Configurazione iniziale — ordine consigliato

1. Installare l'APK estratto dallo ZIP di GitHub Actions; aprire **JarvisWake**.
2. Concedere **Microfono** e **Notifiche** quando richiesti. Se negati: Impostazioni Android → Applicazioni → JarvisWake → Autorizzazioni. L'ascolto continuo richiede la notifica del servizio in primo piano.
3. Consentire **Comparsa in primo piano / Visualizza sopra altre app**: pulsante «Consenti HUD sopra le altre app» nell'app oppure impostazioni speciali di accesso. Verificare che sia attivo.
4. **Samsung / Android con restrizioni per APK esterni:** Impostazioni → Applicazioni → JarvisWake → menu ⋮ → **Consenti impostazioni con restrizioni** (nome variabile). Autorizzare soltanto se l'APK è attendibile. Se l'opzione non compare, non presumere che sia necessaria: controllare il passo seguente.
5. Aprire Impostazioni → Accessibilità → Applicazioni installate → **JarvisWake: avvio Voice** e **attivare il servizio**. La scritta «Gestita tramite impostazioni con restrizioni» indica che prima occorre il passo 4. La sola autorizzazione a sovrapporsi alle altre app non sostituisce l'Accessibilità.
6. In JarvisWake scegliere **Configura accesso a ChatGPT**. Accedere a `chatgpt.com` nel WebView dell'app, completare eventuali verifiche di account e assicurarsi che la sessione resti autenticata. Non è la stessa sessione del browser esterno o dell'app ChatGPT nativa.
7. Premere **Imposta Jarvis come assistente del telefono** e selezionare JarvisWake. Questo **sostituisce l'assistente digitale precedente** per i richiami di sistema; è reversibile dalle impostazioni Android. Alcuni dispositivi non offrono il ruolo richiesto.
8. Tornare nell'app e verificare che il servizio **Avvia ascolto Hey Jarvis** sia attivo. Se Android limita il servizio per batteria, esaminare le impostazioni batteria dell'app e le eventuali restrizioni di background del produttore; non disattivare indiscriminatamente tutte le protezioni. Il funzionamento dopo riavvio e prima del primo sblocco non è garantito.
9. Nell'app principale scegliere **Copertura: opaca / semitrasparente** secondo preferenza; l'impostazione è persistente. La schermata di copertura J.A.R.V.I.S. non richiede controlli aggiuntivi.

## Collaudo dopo installazione

1. A telefono **sbloccato**, provare «Avvia ciclo completo: ChatGPT Voice» e verificare caricamento WebView, accesso ChatGPT, autorizzazione del microfono Web, apertura Voice, invio singolo «ciao jarvis» e risposta vocale.
2. Bloccare il telefono, pronunciare **«Hey Jarvis»** senza toccare lo schermo: la copertura deve restare visibile sopra il blocco e la conversazione partire **senza richiesta automatica di impronta/PIN**. Il pulsante «Sblocca telefono» è facoltativo.
3. Provare X/Indietro e un nuovo spegnimento schermo: il ciclo deve chiudersi e l'ascolto riprendere; nessuna seconda pressione automatica su Voice.
4. Verificare il passaggio opaco/semitrasparente nell'**app principale**, non nella copertura.
5. Usare «Esporta diagnostica» nell'app principale per salvare un archivio ZIP di eventi locali. Verificare che il file venga creato e che non contenga audio, conversazioni, cookie o credenziali. Non considerare il logging collaudato su altri dispositivi senza una prova specifica.

## Diagnosi rapida

| Sintomo | Controllo iniziale |
| --- | --- |
| Jarvis compare ma sparisce / Android chiede impronta | Controllare soprattutto **impostazioni con restrizioni** e **servizio Accessibilità attivo** dopo reinstallazione; poi permesso overlay. Non modificare il codice della copertura senza evidenza. |
| «Gestita tramite impostazioni con restrizioni» | Abilitare l'accesso alle impostazioni con restrizioni dalla pagina informazioni app, quindi attivare il servizio Accessibilità. |
| Nessuna risposta a «Hey Jarvis» | Controllare microfono, notifiche, servizio in primo piano, ruolo assistente e restrizioni batteria; verificare lo stato nell'app. |
| WebView non accede a ChatGPT | Verificare login nel WebView e connettività; l'accesso nel browser esterno non è sufficiente. |
| Voice non si apre / microfono negato | Verificare permesso microfono Android, autorizzazione WebView, servizio Accessibilità e assenza di altre app che trattengono il microfono. |
| «App non installata» dopo «Aggiorna» | Confrontare certificati di firma APK installato e nuovo APK; controllare anche versionCode e compatibilità ABI/SDK. Una build GitHub riuscita non dimostra la compatibilità della firma. |

## Limiti di sicurezza e manutenzione

- L'avvio vocale sopra il blocco rende ChatGPT accessibile a chiunque pronunci la wake word: Vosk **non autentica il parlante**. Non equivale allo sblocco del dispositivo.
- Dopo reinstallazione possono azzerarsi autorizzazioni, ruolo assistente, sessione WebView e preferenze. Ripetere questa checklist.
- L'avvio dopo reboot, le differenze tra One UI e altri Android, il consumo batteria e la compatibilità ChatGPT Web richiedono prove sul dispositivo reale.
- Questa guida descrive il comportamento e i passaggi noti, non garantisce che tutte le varianti Android mostrino gli stessi menu.
