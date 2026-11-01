# WhatsApp collegato tramite QR — .87 / 109

Il nuovo percorso sostituisce WhatsAppFlow: nessuna apertura dell’app WhatsApp, ricerca del compositore, azione SET_TEXT/CLICK, coordinate o modifica delle bozze. Usa il proprio numero personale come dispositivo aggiuntivo, con motore whatsmeow integrato nell’APK Android. Non è la Cloud API Business ufficiale Meta.

## Uso

Configurazione → Collegamento e azioni → Maps, chiamate e WhatsApp → **Collega WhatsApp con QR**. Conservare il consenso azioni e il permesso rubrica già previsti dal plugin. Premere **Genera QR WhatsApp**, attendere il QR reale e scansionarlo da WhatsApp → Dispositivi collegati → Collega un dispositivo. L’utente ha scelto di fotografare il QR con un altro telefono e scansionare quella foto dallo stesso Galaxy: deve essere ancora valido. Il QR si aggiorna automaticamente alla scadenza; non esportarlo in chat o diagnostica. Attendere **WhatsApp collegato e connesso**.

Il codice è visibile solo nella schermata Android privata FLAG_SECURE. Riconnetti riprende una sessione salvata; Scollega richiede conferma e l’ack di logout prima di riportare successo. In caso di errore, controllare Dispositivi collegati in WhatsApp. La sessione eliminata non viene riutilizzata per un nuovo abbinamento: viene creato un nuovo client/store dispositivo. Nessun messaggio è inviato dalla sola apertura o scansione del QR.

Il PC non è un requisito: rete, motore e credenziali sono sul telefono. Occorre però che JarvisWake e JarvisWake Link siano operativi per ricevere il comando; Android può sospendere o terminare il processo. La sessione salvata consente la riconnessione al comando, non promette disponibilità ininterrotta contro le restrizioni di Android. Ferma ascolto/consensi/telefono bloccato conservano i limiti precedenti del collegamento. Disinstallazione o cancellazione dati elimina la sessione; aggiornare con la stessa firma la conserva.

## Invio dal plugin

Stesso jarviswake_whatsapp, stessi contact_ref temporanei dal telefono e testo esplicitamente richiesto. Il Worker richiede109; versioni precedenti non ricevono un fallback UI. Il telefono ricontrolla il contatto, il numero internazionale e la registrazione WhatsApp, quindi passa il testo al client collegato. Un tentativo usa lo stesso UUID del journal e un ID messaggio stabile. Consenso, sblocco, lease e uscita del ciclo Voice sono ricontrollati prima del dispatch; niente nuovo tentativo dopo incertezza.

Il motore non interviene su microfono/routing/audio. La cover Voice presente resta aperta; solo una nuova richiesta esplicita può ripresentarla dopo Maps, se lo stesso ciclo è ancora Voice attiva senza stop pendente. Nessun nuovo shortcut, progetto, Voice start o audio di attesa. Non ripristinare una cover temporaneamente nascosta per export e non riaprirla da poll/timeout o dopo uscita volontaria del comando in corso. Gesti restano disponibili e annullano il messaggio non ancora partito. Una volta già trasmesso non è possibile promettere di annullarlo.

whatsapp_server_accepted prova l’ack del server, non consegna/lettura; whatsapp_not_linked richiede QR; whatsapp_connection_unavailable richiede verifica connessione. outcome_uncertain non autorizza retry con un nuovo UUID.

## Conservazione

Credenziali e stato del dispositivo in `noBackupFilesDir/whatsapp-linked/linked-device.db`, privati dell’app e protetti dall’isolamento/cifratura del dispositivo Android; non dichiarare una cifratura applicativa AES-GCM del database che non è implementata. Backup dell’app già disattivato. Il client gestisce chiavi/stato di protocollo; il nostro handler non salva o esporta messaggi ricevuti. Logger del motore disattivato; QR solo in memoria dell’interfaccia finché valido. Nessuna chiave/QR/sessione nel Worker, nell’APK, in Git o nei log diagnostici. Il messaggio richiesto transita nel relay precedente e nella conversazione secondo le condizioni già documentate per le azioni .81.

## Strumenti e verifiche

Sempre cloud Jarvis `/workspace/Jarvis`. JDK17/Gradle8.13/SDK36 come prima; aggiunti in `/workspace/.jarvis-cloud` Go1.27.3 (hash distribuzione fissato), NDK28.2.13676358 e x/mobile v0.0.0-20261011034528-197770444867. whatsmeow v0.0.0-20261031222104-5d4657a5c29e e sqlite3 v1.14.52 con go.sum. `scripts/setup-whatsapp-native.sh` per strumenti mancanti, `preBuild` costruisce l’AAR ARM64 in app/build tramite `scripts/build-whatsapp-native.sh`; nessun binario su Git. Licenze delle dipendenze fissate incluse nell’asset whatsapp-native-notices.txt. Verificare ELF LOAD≥16KB e zipalign16KB.

Test Go aprono solo un database temporaneo senza account/connessione; test Android usano un motore simulato per QR/scadenza/annullamento/ack e controllano che non si apra WhatsApp. Non costituiscono prova di pairing, rete WhatsApp o invio reale sul Galaxy. L’ambiente cloud ha egress ristretto e non è stato usato per accedere a WhatsApp; il client reale partirà sul telefono solo dalla scelta locale di abbinamento o ripresa di una sessione già salvata.

Fonti: https://github.com/tulir/whatsmeow , https://pkg.go.dev/go.mau.fi/whatsmeow , https://go.dev/wiki/Mobile . Libreria non ufficiale: protocollo/condizioni WhatsApp possono cambiare e il funzionamento non è garantito da Meta.
