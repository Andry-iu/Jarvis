# Jarvis Windows MCP 0.8.8 — mouse da screenshot

Data: 22 novembre 2026. **Nuovo protocollo 0.8.8**. Non confondere implementazione su Git, test, merge e installazione sulle singole macchine.

## Motivazione operativa
Nei test interattivi su Desktop1 (0.8.7), un semplice click sulla taskbar o sull'anteprima di Edge ha richiesto numerosi passaggi diagnostici. `ui_mouse` accetta coordinate **fisiche** e rifiuta punti fuori dalla finestra verificata. Le anteprime Edge compaiono quando più finestre sono aperte; un click sull'icona Edge non equivale sempre ad aprirne una in primo piano. Aprire Blocco note per leggere file di log durante un collaudo mouse sposta inaspettatamente la finestra in primo piano e va evitato.

## Protocollo nuovo
1. `machine_info` (identificare sempre la macchina) e `screen_capture` diretto. Il risultato contiene l'immagine JPEG con dimensioni `image_width, image_height`, geometria originale `original_width, original_height`, `origin_x, origin_y`, `scale_x, scale_y`, e il **nuovo** `capture_id`.
2. Individuare nell'immagine il centro del bersaglio, ad esempio `x=600, y=400` nel JPEG 1280×720. Queste sono coordinate **dell'immagine**, non coordinate fisiche.
3. Eseguire `ui_mouse_screen` sul **medesimo** computer, con `capture_id`, `x`, `y`, `mouse_action=move|click|double_click|right_click|wheel`, un `request_id` unico (e `wheel_delta` solo per wheel). L'agente converte internamente la posizione in pixel fisici e invia l'input al desktop Windows. Il `capture_id` viene consumato da una singola azione, anche se rifiutata.
4. Eseguire un nuovo screenshot soltanto per verificare il risultato quando opportuno, non aprire Blocco note né creare script usa-e-getta per azioni GUI ordinarie. Se il risultato è incerto, consultare `request_status` e osservare lo schermo: **mai ripetere automaticamente la stessa azione**.

## Formula di conversione
Per pixel JPEG intero `(x,y)` e geometria della cattura:

```text
physical_x = origin_x + floor((x + 0.5) * original_width  / image_width)
physical_y = origin_y + floor((y + 0.5) * original_height / image_height)
```

Equivalentemente `scale_x=image_width/original_width`, quindi `(x+0.5)/scale_x` prima dell'arrotondamento e dell'origine; idem per `y`. Non invertire fattori per errore. Usare due assi distinti perché l'arrotondamento dello screenshot può renderli leggermente differenti.

Esempio Desktop1, schermo 1920×1080, screenshot 1280×720, origine 0,0: il punto dell'immagine `(400,200)` corrisponde a `(600,300)` in pixel reali. Con un monitor secondario a sinistra (origine negativa), aggiungere sempre l'origine.

## Sicurezza e limiti
- Cattura autorizzata solo su desktop interattivo Windows non bloccato, mai su UAC/desktop sicuro.
- `ui_mouse_screen` può agire sulla taskbar e sulle anteprime perché **non impone** che il punto appartenga alla finestra in primo piano. A differenza del vecchio `ui_mouse`, il bersaglio deve essere scelto dall'operatore o dal modello **sulla cattura immediatamente precedente**, non da coordinate inventate.
- `capture_id` privato, valido al massimo **45 secondi**, monouso e relativo a una sola macchina. Vengono rifiutati coordinate fuori dalla cattura, layout virtuale non valido, sessione bloccata e tasti modificatori premuti. Il worker invia `SendInput` e non simula pressioni di tasti non richieste.
- `dispatched=true` significa richiesta di input inviata, **non** certezza del risultato dell'applicazione; verificarlo con una successiva cattura. Nessuna operazione senza l'esplicita istruzione utente durante il collaudo guidato.
- `ui_mouse` tradizionale resta invariato, con snapshot finestra e vincoli precedenti, per la compatibilità esistente.
- Non aprire strumenti diagnostici o cambiare il foreground durante la sequenza screenshot → click; non registrare dati privati degli screenshot su Git.

## Piano di convalida e distribuzione
- Test unitari Go della mappatura (scala 1,5×, bordi immagine, monitor con origine negativa, input non validi), schema MCP, `go test ./...`, `go vet ./...`, build Windows e parsing PowerShell.
- Test live Desktop1 con screenshot, movimento e click sulla taskbar e sulla finestra voluta, una sola azione per ordine dell'utente. Verificare anche gli eventi rifiutati e la loro ricevuta.
- Installare su **Desktop1 e Desktop2 separatamente** con `agent_update` da SHA Git esatto dopo merge con `main`, raccogliendo `agent_update_status=completed`, `cleanup_verified=true`, versione live, identità e nuovo PID. Su Desktop1, l'aggiornamento deve preservare la pianificazione corretta ma l'autostart a nuovo login resta un collaudo separato.
- Aggiornare `CURRENT-RELEASE.md` e `RELEASE_STATUS` soltanto con risultati effettivamente verificati.

## Limitazioni note
Il connettore ChatGPT può memorizzare la lista precedente dei tool MCP: `ui_mouse_screen` è comunque disponibile tramite `task_start(operation=runtime, request={operation:ui_mouse_screen,...})` quando il catalogo non viene aggiornato. Le sessioni con un catalogo MCP già caricato possono richiedere riconnessione per mostrare direttamente il nuovo strumento.
