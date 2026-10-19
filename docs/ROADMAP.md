# Decisioni approvate dopo la 0.5.26

Stato al 19 ottobre 2026. Le voci seguenti descrivono lavoro futuro; non sono incluse nell'APK 0.5.26.

## Logging diagnostico

Approvato per la prossima versione: registro locale, nessun audio o trascrizione salvati, rotazione dei file ed esportazione manuale. Scopo: confrontare avvio, microfono, blocco/sblocco, ciclo Voice e chiusura anche su dispositivi diversi.

Da definire durante l'implementazione: schema degli eventi, limiti di spazio/numero file e modalità di esportazione. Non raccogliere cookie, credenziali, contenuti delle conversazioni o dati del DOM non necessari alla diagnosi. Il progetto attuale conserva soltanto messaggi di stato sintetici; non dichiarare già presente un sistema completo di logging.

## Conversazione dedicata e JarvisRuntime

Decisioni accettate:

- Memorizzare l'identificativo della conversazione Jarvis e riaprire quella conversazione; le chat personali create dall'utente non devono diventare la destinazione per errore.
- Usare il progetto ChatGPT **JarvisRuntime**, che l'utente dichiara di aver creato con memoria **Solo progetto** e istruzioni inserite.
- Prevedere più conversazioni nel progetto per evitare di prolungare indefinitamente una sola chat.

Non ancora verificati o definiti:

- URL/ID reale di JarvisRuntime e della conversazione da usare.
- Creazione automatica di nuove conversazioni all'interno del progetto e apertura diretta della chat dedicata tramite la sessione WebView.
- Eventuali differenze del compositore e del controllo Voice all'interno del progetto.
- Criterio di rotazione delle conversazioni e contenuto del passaggio di contesto. Non è stata fissata una soglia numerica.
- Testo esatto delle istruzioni di JarvisRuntime: è nell'account dell'utente e non è stato recuperato nei sorgenti. Il prompt di continuità di sviluppo in questo repository non lo sostituisce.

La 0.5.26 continua a caricare `https://chatgpt.com/` e ad avviare una nuova chat normale. Il progetto ChatGPT JarvisRuntime e il repository Git JarvisWake sono due elementi distinti: il primo ospita le conversazioni operative, il secondo conserva codice e documentazione tecnica.
