# JarvisWake 0.5.30 — installazione e collaudo

Guida basata sul codice 0.5.30 e sul dispositivo di riferimento Samsung Galaxy S24+ / Android 16. Non certifica tutti i modelli Android. Build e firma sono locali; questa procedura non usa GitHub Actions.

## Prima di iniziare

- Scaricare l'APK ARM64 firmato con il certificato permanente e verificarlo tramite checksum indicato in `releases/0.5.30/SHA256SUMS`.
- Per aggiornare un'installazione esistente, il certificato deve essere identico. Non disinstallare per aggirare un errore di firma senza valutare la perdita della sessione WebView e delle preferenze.
- Serve Internet per ChatGPT Web e un account ChatGPT autenticato nel WebView dell'app. L'accesso nell'app ChatGPT nativa o nel browser esterno non condivide necessariamente la stessa sessione.
- Il riconoscimento «Hey Jarvis» è locale tramite Vosk. Non è un'autenticazione del parlante.

## Configurazione iniziale

1. Installare l'APK e aprire **JarvisWake**.
2. Concedere **Microfono** e **Notifiche**. L'ascolto continuo richiede la notifica del servizio in primo piano.
3. Consentire la sovrapposizione HUD tramite «Consenti HUD sopra le altre app».
4. Su Samsung, se Accessibilità è bloccata dalle restrizioni APK esterno, aprire Impostazioni → Applicazioni → JarvisWake → menu ⋮ → **Consenti impostazioni con restrizioni**. Il nome può variare; autorizzare solo se si considera attendibile l'APK.
5. Aprire Impostazioni → Accessibilità → Applicazioni installate → **JarvisWake: avvio Voice** e attivare il servizio.
6. In JarvisWake scegliere **Configura accesso a ChatGPT** e autenticarsi nel WebView.
7. Premere **Imposta Jarvis come assistente del telefono** e selezionare JarvisWake. Questo sostituisce l'assistente corrente per i richiami di sistema ed è reversibile dalle impostazioni Assistente digitale.
8. Tornare all'app e verificare che «Avvia ascolto Hey Jarvis» sia attivo. Esaminare le impostazioni batteria Android/One UI solo se il sistema arresta il servizio. Non è garantito l'avvio dopo riavvio o prima del primo sblocco.
9. Scegliere la copertura opaca o semitrasparente dall'app. La preferenza resta memorizzata.

## Prove consigliate

1. Da telefono sbloccato, premere **Avvia ciclo completo: ChatGPT Voice**. Verificare apertura del progetto JarvisRuntime, un solo gesto Voice, saluto «ciao jarvis», ascolto e risposta vocale.
2. Bloccare il telefono e pronunciare «Hey Jarvis» senza usare subito impronta/PIN. Verificare cover, Voice e risposta. Lo sblocco è facoltativo.
3. Provare X e Indietro durante caricamento e conversazione; verificare che la sessione Voice/WebView si chiuda e Vosk riprenda.
4. Durante una conversazione spegnere nuovamente lo schermo; verificare la chiusura del ciclo.
5. Verificare che Voice non riceva tocchi ripetuti, che le note spariscano dopo l'attivazione e che la X resti raggiungibile.
6. Esportare la diagnostica e controllare gli eventi di wake, pagina e controllo Voice. Il log non misura ancora la prima risposta audio udibile.
7. Riavviare il telefono e verificare separatamente il ruolo assistente, l'ascolto wake e il pre-caricamento. Il preloader 0.5.30 parte quando la MainActivity avvia l'ascolto; non è dimostrato in ogni percorso di boot.

La creazione automatica di una nuova conversazione per ogni sessione dentro JarvisRuntime e il beneficio del preloader devono ancora essere verificati. Il preloader osserva il controllo in un WebView invisibile, poi lo distrugge alla wake; il ciclo effettivo ne apre un altro.

## Diagnosi rapida

| Sintomo | Controllo iniziale |
| --- | --- |
| Jarvis compare ma sparisce o Android mostra solo la schermata di blocco | Verificare Accessibilità, permesso overlay, ruolo assistente e stato attività. Distinguere avvio da telefono già bloccato da un nuovo spegnimento schermo. |
| Nessuna risposta a «Hey Jarvis» | Controllare Microfono, Notifiche, servizio in primo piano, ruolo assistente e restrizioni batteria; esportare i log. |
| ChatGPT Web non è autenticato | Effettuare il login dal pulsante di configurazione dentro JarvisWake. |
| Voice non si apre o il microfono è negato | Controllare permesso microfono Android, permesso WebView per host ChatGPT, Accessibilità e app che potrebbero occupare il microfono. |
| «App non installata» durante l'aggiornamento | Verificare versione, ABI e certificato APK; non rimuovere l'app senza valutare la perdita di dati locali. |
| La risposta non è udibile | Distinguere Voice attiva, traccia microfono live, saluto inviato e risposta audio; i log attuali non rilevano la prima voce udibile. |

## Privacy e limiti

- Chiunque pronunci la wake word può avviare Voice con l'account ChatGPT autenticato; Vosk non riconosce l'identità della persona.
- Il puntino verde Android segnala l'accesso al microfono da parte del servizio di ascolto continuo. Non è implementato un trigger DSP a basso consumo.
- Doze, permessi, chiamate, Bluetooth, process kill e One UI possono interrompere l'ascolto o Voice.
- Pagina, login e controlli DOM di ChatGPT Web possono cambiare senza preavviso. Le prove DOM automatiche non sostituiscono il collaudo sul telefono.
