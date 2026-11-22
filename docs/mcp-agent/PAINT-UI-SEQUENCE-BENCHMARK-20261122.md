# Benchmark UI Sequence Paint — Desktop1, 22 novembre 2026

## Scopo
Misurare il tempo di una sequenza UI controllata e ripetibile e ridurlo progressivamente, senza confondere `dispatched` con successo visivo verificato. Target funzionale: pulire la tela di Microsoft Paint, disegnare un rettangolo e quattro piccoli cerchi rossi. Usare un solo screenshot iniziale, una `ui_session_open`, una `ui_sequence`, una cattura finale. Confrontare varie pause (es. 300ms, poi 200/150/100ms) **solo dopo** correttezza visiva raggiunta.

## Verifica sorgenti
- Versione agent live Desktop1: **0.9.4**.
- `desktop_extensions.ps1` riga 245: pausa `$pauseMs=90` per ogni step; `settle_ms` sostituisce la pausa se presente, con `Start-Sleep` esplicito dopo ogni step; protocollo accetta 0..500ms.
- Il risultato `ui_sequence_dispatched`, `verified:false` significa comandi inoltrati, **non** che le forme disegnate siano corrette.
- Coordinate del Paint e forme/colore cambiano secondo posizione e scala GUI; usare screenshot e test della selezione, non ipotizzare che un click su una coordinata sia lo strumento desiderato.

## Misurazioni concrete (orologio del coordinatore)
- Avvio: **2026-11-22T19:48:12.702Z**, Desktop1.
- Sequenza **7 azioni**: `ESC`, `CTRL+A`, `DELETE`, `ESC`, click figura, click colore, drag area tela. Ogni step `settle_ms=300`, totale pause intenzionali **2,1 secondi**.
- **Solo chiamata `ui_sequence`**: **5229 ms**, misurati da subito prima dell'invocazione a subito dopo la risposta lato coordinatore. Include overhead chiamata ChatGPT/MCP, worker e pause.
- **Cattura iniziale + `ui_session_open` + `ui_sequence`**: **7950 ms**. Include parte di setup, non include l'ultima cattura.
- **Intero tentativo inclusa cattura finale**: **9143 ms**. Non include il tempo di analisi visiva successiva.
- Risposta agente: `steps_dispatched=7/7`, `status=ui_sequence_dispatched`, `verified=false`. **La schermata finale mostra tela bianca, niente rettangolo e niente cerchi**: fallimento funzionale. Nessun guadagno di tempo puo essere proclamato.
- Prima prova in questa chat con 11 step e pause inferiori: azioni inoltrate ma Paint rimasto in selezione, non disegnato. Prima seconda prova con 9 step aveva prodotto **tre cerchi neri** senza rettangolo: questo suggerisce selezione strumenti/coordinate e colori sbagliata, non solo tempo.
- Tentativo successivo di verifica di un click sullo strumento, seguito da drag, non concluso: chiamata bloccata dai controlli, nessuna esecuzione confermata e nessun tempo utilizzabile.

## Prossimo criterio di successo
Misurare in ogni prova `setup_ms`, `sequence_ms`, `verify_capture_ms`, `total_ms`, numero step, pause per step, e **esito visivo**. Prima ottenere rettangolo con quattro cerchi rossi su tela pulita; poi confrontare tempi, differenze e mediana su prove ripetute. Evitare un ciclo di invio comandi che non si possono verificare o che vengono bloccati. Ogni errore o risultato incerto resta distinto da un collaudo riuscito.
