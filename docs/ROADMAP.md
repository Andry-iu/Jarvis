# Roadmap — JarvisWake

Stato verificato al 19 ottobre 2026, versione sorgenti 0.5.30. Le attività “implementate” descrivono il codice; il comportamento su Galaxy S24+ resta da provare quando indicato.

## Stato implementato

- Riconoscimento offline «Hey Jarvis» tramite Vosk e avvio manuale del ciclo.
- Integrazione Android `VoiceInteractionService` e servizio microfono continuo; il comportamento dopo boot/process kill su One UI non è certificato.
- Avvio del progetto JarvisRuntime tramite WebView e ricerca DOM semantica del controllo Voice; al keyguard l'ordine spaziale inizia in basso.
- Una sola sequenza touch per ciclo, protezione contro invii/tocchi duplicati e saluto «ciao jarvis» in una sola submission quando compositore e pulsante sono pronti.
- HUD con X, fondo opaco predefinito, scelta semitrasparente persistente, note nascoste dopo Voice; X/Indietro/spegnimento schermo annullano il ciclo.
- Diagnostica locale a eventi sintetici, rotazione e esportazione manuale; niente audio o trascrizioni.
- Preloader sperimentale che carica la pagina progetto mentre la MainActivity ha avviato l'ascolto e osserva il controllo. Il WebView viene distrutto alla wake e il risultato DOM non è riutilizzato; riduzione tempi non dimostrata.

## Priorità aperte

1. **Collaudo 0.5.30 sul Galaxy S24+**: manuale, wake con display acceso/bloccato, risposta vocale, X/Indietro, nuovo spegnimento schermo, ripresa Vosk e avvio dopo riavvio. Registrare separatamente esiti e log.
2. **Tempo fino alla risposta**: inserire eventi per wake, rilascio microfono, apertura WebView, pagina pronta, controllo Voice, gesto, acquisizione microfono e primo audio remoto. La conferma che l'utente lo sente resta una verifica manuale; i log attuali arrivano al dispatch ma non misurano il parlato udibile.
3. **Preloader**: confrontare cicli con/senza preloader usando le stesse condizioni di rete e login. Se si mantiene, ridurre duplicazione riutilizzando in modo sicuro la pagina WebView o trasferendo uno stato DOM non fragile; non dichiarare sufficiente la sola cache.
4. **Nuova chat nel progetto**: verificare che ogni ciclo inizi una nuova conversazione dentro JarvisRuntime. La versione apre l'URL progetto ma non ha prova che crei automaticamente una chat nuova né conserva un ID affidabile per sessione.
5. **Posizione per dispositivo**: l'utente preferisce riutilizzare la posizione del controllo appresa al primo successo. Attualmente la posizione non viene memorizzata: il DOM viene interrogato di nuovo e le coordinate restano relative al viewport. Progettare cache per dispositivo/versione/layout con verifica semantica e fallback dinamico; niente coordinate assolute condivise tra telefoni.
6. **Risposta saluto e voce**: verificare su dispositivo che «ciao jarvis» venga ricevuto e che ChatGPT risponda vocalmente. Non confondere campo compilato, click Invia, media track live e audio effettivamente sentito.
7. **Consumi**: non è stato aggiunto un trigger hardware a basso consumo o un secondo gate di ampiezza. Raccogliere una baseline batteria di Vosk prima di valutare un filtro aggiuntivo.

## Diagnostica esistente

`DiagnosticLog` registra timestamp UTC, tempo monotono, evento e token ciclo; limita i log a tre file da 256 KiB e consente esportazione ZIP. Sono disponibili eventi di wake, caricamento pagina, ricerca/strategia Voice, primo dispatch e preloader. Non registra contenuti conversazione, audio o trascrizioni.

I campioni 19/10 mostrano 11,65 s da `CYCLE_STARTED` a `WEB_PAGE_READY` e 13,48 s a `VOICE_CONTROL_READY` in un ciclo; in un altro il controllo arriva a 11,41 s pur con pagina pronta a 3,82 s. Sono dati osservati, non prestazioni garantite; il riferimento non è sempre `WAKE_DETECTED`, e non c'è evento del primo audio udibile. Vedere `CONTINUITY.md` e `VERIFICATION.md`.

## Regole per la prossima modifica

- Non aggiungere pause arbitrarie o tocchi ripetuti per nascondere una condizione non osservata.
- Preservare X sempre disponibile, HUD opaco/semitrasparente persistente, funzionamento da schermata di blocco, cancellazione su X/Indietro/schermo spento, ripresa Vosk e un solo gesto Voice.
- Tenere separati wake word locale e dettatura Assistente Android.
- Usare la sessione ChatGPT dell'utente e il progetto JarvisRuntime, senza API/account alternativi o apertura dell'ultima chat generica.
- Aggiornare test e documenti nello stesso commit del codice. Build e firma sono locali; non usare GitHub Actions per compilare.
