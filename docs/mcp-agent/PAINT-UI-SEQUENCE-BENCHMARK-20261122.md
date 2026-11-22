> **Chiusura test e consensi — 22/11/2026, dopo la prova 0.9.6:** una richiesta di autorizzazione **ChatGPT/plugin host** e apparsa durante `ui_sequence` contenente `CTRL+A` e `DELETE`, con descrizione `cancellazione di contenuto potenzialmente persistente`. Non era dovuta al vecchio `session_token` (rimosso) e il server non controlla questo popup. Dopo il risultato finale verificato, `window_close` ha restituito `pending` per il dialogo `Salvare il lavoro?`; e stato selezionato **Non salvare**, quindi `window_list` non ha piu riportato Paint. Nessuna immagine o file Paint e stato salvato. **Non e stata ancora ottenuta una baseline one-pass comparabile**: le durate misurate sotto includono tentativi di recupero. Riepilogo generale e limiti: [STATUS-CONSOLIDATO-0.9.6-20261122.md](STATUS-CONSOLIDATO-0.9.6-20261122.md).

## Paint 0.9.6: test grafico completo, con misurazioni e limiti (22/11/2026, ~21:38–21:46 UTC)

**Desktop1 / Jarvis 0.9.6**, plugin ricaricato: confermata nel catalogo ChatGPT la disponibilita di `ui_session_open`, `ui_sequence`, `ui_mouse_screen` senza `machine_id` e senza `session_token`, con invocazioni dirette. Test di Paint reale sulla finestra `Senza titolo - Paint`, canvas 892 x 580 px. Il risultato finale e stato **verificato con screenshot**: un rettangolo nero, quattro cerchietti rossi, e il testo blu `Jarvis` vicino al centro, senza selezione attiva. Il testo e in font Calibri 12 e appare piccolo, ma leggibile. Non e stato salvato alcun file.

### Tempi misurati
- Riprova baseline dal canvas bianco al rettangolo: **13.298 s** da cattura iniziale a screenshot finale. Suddivisione: setup/screenshot/sessione **0.277 s**, sequenza iniziale di 7 azioni **7.662 s**, screenshot intermedio + drag diretto **3.882 s**, screenshot finale **1.477 s**. Il primo `ui_sequence drag` non ha prodotto il rettangolo; un successivo `ui_mouse_screen drag` l'ha prodotto.
- Sessione dedicata ai 4 cerchi: **14.065 s** incluse screenshot, session-open, verifica; `ui_sequence` di 7 passi **8.465 s** con 2.85 s di pause programmate. Il primo cerchio e stato ritentato perche il primo drag dopo cambio forma tende a non produrre contenuto.
- Sessione per testo: selezione + prima creazione casella **11.404 s** con screenshot finale ma casella non ancora visibile. Secondo click + screenshot **4.128 s**: box testo visibile. Digitazione + commit + screenshot **8.431 s**: in quel checkpoint il testo non risultava visibile.
- Sessione recupero testo: aprire sessione, selezionare testo, cliccare due volte, screenshot **13.285 s**: la casella conteneva gia `Jarvis` blu. Una successiva chiamata separata di `ui_sequence` per cliccare lo strumento Selezione ha confermato/reso visibile il testo; screenshot finale ha verificato il disegno.
- I tempi sono **intervalli di diverse prove e chiamate**, non una baseline one-pass unica. Non dichiarare un tempo totale della baseline riuscita concatenando quelle misure: la procedura include interventi di recupero e checkpoint. Usarle come punto di riferimento per ottimizzazioni della UI e della latenza.

### Esiti tecnici
1. Gli strumenti UI diretti sono disponibili sul client ChatGPT dopo aggiornamento del plugin, senza i parametri persistenti `machine_id` e `session_token` (0.9.6).
2. `ui_sequence` puo restituire `ui_sequence_dispatched; verified=false` anche quando Paint non disegna: **dispatch non equivale a conferma visiva**.
3. Le forme iniziali possono richiedere un'azione successiva per essere visibili; in particolare `ui_mouse_screen drag` ha funzionato dove `ui_sequence drag` non aveva prodotto una forma.
4. La casella di testo in Paint e sensibile al focus: click sul canvas dopo selezione testo e verifiche screenshot, poi input Unicode, poi conferma **in una chiamata distinta**. Certe sequenze confermano senza il testo visibile; un nuovo focus e commit del testo lo rendono visibile.
5. Almeno una richiesta `screen_capture(quality=91)` e stata rifiutata con `invalid capture size, quality, monitor or region`. Restare con quality 70..89 come nelle prove riuscite. Nessun blocco dimostrato della policy host in questa prova, ma il software Jarvis non puo impedire le conferme della piattaforma.
6. Prossima baseline da fissare: unico test ripetibile da bianco a disegno corretto, senza correzioni manuali e con misura `end-to-end` e screenshot finale; poi ridurre pause. Obiettivo ulteriore: incrementare dimensione testo e centratura con strumenti Paint verificati.

---

## Collaudo 0.9.5 con strumenti MCP diretti (22/11/2026, ore ~21:20–21:25 UTC)

**Esito:** parziale. Strumenti `ui_session_open`, `ui_sequence`, `ui_mouse_screen`, `window_inspect`, `ui_action` esposti direttamente da ChatGPT, senza `task_start` e senza `session_token`. Desktop1 in versione `0.9.5`, Paint finestra attiva con geometria stabile. **Rettangolo nero e quattro cerchi rossi ottenuti e verificati visivamente**. **Testo blu centrale "Jarvis" NON ottenuto**, quindi non esiste una nuova baseline completa certificata.

### Tempi misurati (singole prove, NON sommare in una falsa baseline)
- Prova iniziale `ui_session_open` + `ui_sequence` di 2 step (click/drag) + screenshot: **7365 ms** complessivi, `ui_sequence` **3063 ms**, apertura **1989 ms**, screenshot finale **1125 ms**. Esito visivo: tela vuota.
- Prova rettangolo con coordinate verificate da `window_inspect`, 7 step con `settle_ms=300` (2,1 s pause programmate): **6411 ms** dal primo screenshot al risultato finale, sequenza **5286 ms**, screenshot finale **1121 ms**. Esito visivo: nessuna forma visibile al termine della prova. Le misurazioni mostrano solo la latenza, non un successo.
- Trascinamento diretto `ui_mouse_screen`: **2515 ms** per l'operazione, **4902 ms** incluso screenshot iniziale e finale. Dopo l'azione compare una forma rettangolare con maniglie di selezione, poi viene verificato il rettangolo nero effettivo.
- Quattro cerchi: prima `ui_sequence` di 6 step (selezione ovale/rosso + 4 trascinamenti) **6182 ms**; **10708 ms** incluse apertura sessione, screenshot iniziale e finale. Screenshot ha mostrato **solo 3 cerchi**: un trascinamento aveva mancato il primo cerchio. Secondo tentativo sul solo angolo superiore sinistro: `ui_sequence` **2850 ms**, con **6385 ms** fino all'apertura della sessione; screenshot successivo ha confermato **4 cerchi rossi**.
- Testo blu `Jarvis`: tentativo di 4 step (selezione testo, colore blu, drag textbox, Unicode text), `ui_sequence` **4032 ms**, **8407 ms** compreso setup e screenshot. Nessun testo visibile. Ulteriori tentativi isolati (selezione Testo poi click; drag textbox poi text Unicode) non hanno prodotto contenuto visibile. `window_inspect` ha trovato `EditableText` con bounds `[0,0,0,0]`; `ui_action(type_text)` ha restituito `target_not_actionable`.
- **Le conferme ChatGPT di questo collaudo**: gli strumenti UI diretti sono disponibili, la nuova descrizione di `task_start` e' esposta, la rimozione del token non ha impedito screenshot/azioni. Non sono state rilevate richieste di consenso identiche alla schermata relativa al token all'interno di queste chiamate, ma questo non prova che non possano comparire in futuro.

### Prossima indagine tecnica
- Verificare in Paint Windows 11 come il widget `Testo` riceve focus e se `SendInput` Unicode viene inviato a un campo realmente attivo; distinguere creazione della casella da digitazione. Evitare retry di testo quando `ui_sequence` riferisce soltanto `verified=false`; verificare stato UI ed eventuali controlli editabili con `window_inspect`.
- Correggere l'eventuale problema del primo drag immediatamente dopo il cambio forma/colore, oppure aggiungere checkpoint screenshot e pause di stabilizzazione. Un output `ui_sequence_dispatched` non prova visivamente il successo.
- **Baseline completa richiesta:** partenza da tela vuota, rettangolo nero, 4 cerchi rossi, scritta `Jarvis` blu centrale, screenshot finale e successo visivo. Misurare `setup_ms`, somma `sequence_ms`, `capture_ms`, `total_ms` sull'intero run, annotando pause 300ms. Solo dopo successo confrontare riduzioni 200, 150 e 100ms.

---

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
