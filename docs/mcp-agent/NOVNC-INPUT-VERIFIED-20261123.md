# Windows MCP: mouse e tastiera attraverso noVNC, collaudo 23/11/2026

**Ambito:** Desktop1, agente Jarvis 0.9.6, Windows 11 host, Edge e console Proxmox noVNC della VM 119 AUTECH-TEST10. Stato: **metodo riuscito su una VM**, non prova di affidabilità generale su tutte le VM. Non è stata modificata la sorgente dell'agente.

## Problema e diagnosi

Durante la configurazione degli account delle TEST, alcune sequenze `ui_sequence` riportavano `dispatched=true, verified=false`, mentre la console Windows guest non mostrava il comando atteso. La ricevuta attesta la consegna al worker locale, non che l'app o il guest abbia ricevuto l'evento. Si erano ripetuti i tentativi anziché isolare focus e geometria della console. La causa precisa dei tentativi falliti non è dimostrata.

## Prove effettivamente osservate

1. **Windows locale:** su Desktop1, finestra Blocco note nuova e vuota; `window_focus` e `screen_capture` 1280x720; `ui_session_open` e una `ui_sequence` con click sul documento, testo innocuo `JARVIS-KEYBOARD-MOUSE-QA-2311` e `ENTER`. Screenshot successivo: testo presente e cursore sulla seconda riga. **PASS mouse + tastiera locale.**
2. **Focus noVNC:** `window_focus` per una finestra Edge non in primo piano restituiva `foreground_changed`. Da cattura fresca si è individuata una porzione visibile della barra titolo QEMU; `ui_mouse_screen` ha cliccato la barra e una cattura successiva ha mostrato la finestra QEMU in primo piano. Doppio click sulla barra titolo: finestra massimizzata.
3. **Geometria:** Edge/noVNC visualizzava una superficie guest maggiore della viewport, con barre di scorrimento; l'immagine noVNC esposta come `ControlType.Image` misurava anche 2048x2048. Le coordinate di screenshot precedenti non andavano riutilizzate, soprattutto dopo massimizzazione, scroll e cambio finestra.
4. **Mouse nel guest:** da cattura fresca 1280x720, `ui_mouse_screen(mouse_action=double_click)` sull'icona `This PC` *visibile nella console guest*. Screenshot successivo: Esplora file **dentro Windows TEST10** effettivamente aperto. **PASS mouse tramite noVNC.**
5. **Tastiera nel guest:** con la finestra QEMU effettivamente in primo piano, `screen_capture` e `ui_session_open` sulla stessa finestra, poi `ui_sequence`: click nel campo **Search This PC** di Esplora file e testo innocuo `JarvisInputQA2311`. Screenshot successivo: `jarvisinputqa2311` comparso nel campo e vista `Search Results in This PC`. **PASS tastiera tramite noVNC.**
6. **Falso test:** il chord `CTRL+ESC` ha aperto Start del **Desktop1 host** (profilo host, non guest); le scorciatoie globali intercettate dall'host non sono test affidabili di forwarding alla VM.

## Procedura replicabile

1. `machine_info`, `window_list`, identificare esattamente la finestra `QEMU (<VM>) - noVNC`; controllare VMID, operatività guest e desktop sbloccato.
2. `screen_capture` sul desktop host; se la finestra QEMU non è foreground e `window_focus` fallisce, usare **una nuova** `ui_mouse_screen` sulla barra titolo QEMU effettivamente visibile nel JPEG. Non presumere che una finestra coperta accetti i tasti.
3. Se la console è piccola o tagliata, applicare la procedura generale noVNC PVE-UI-001 del progetto AutechSrv1 per schermo intero oppure massimizzare la finestra QEMU; riacquisire screenshot. Non confondere coordinate del JPEG con pixel fisici.
4. Fare prima un'azione mouse **innocua e osservabile dentro il guest** da `ui_mouse_screen` e verificarla con screenshot. Se non cambia nulla nel guest, non inviare sequenze di configurazione.
5. Sul guest, identificare un campo di testo innocuo effettivamente visibile, quindi `screen_capture` nuova, `ui_session_open(window_id,capture_id)` e `ui_sequence` con click sul campo e `kind=text` senza comandi amministrativi. Ricatturare screenshot e controllare che il testo compaia **dentro la VM**, non nell'host.
6. Solo dopo questo PASS, inviare sequenze operative mirate, verificando ogni modifica dall'interno di Windows. `dispatched` non basta; mai replay cieco su risultati incerti. Mantenere mouse e tastiera come strumenti generici primari dell'agente; nessun modulo Proxmox hardcoded nel runtime.

## Sicurezza, limiti e ripresa

- Nessun segreto, password o file di credenziali è stato aperto, copiato in chat o inserito nel repository da queste prove.
- Il test non dimostra l'attivazione dell'account Administrator, né la rinomina/declassamento di User sulle dieci TEST. Il lavoro account resta **da eseguire e verificare**; non collegare i guest alla rete mentre hanno un account amministrativo senza password.
- Non è stato dimostrato un difetto stabile del codice `SendInput`; un invio fallito in una console non è prova di tastiera/mouse guasti. L'uso di ISO come scorciatoia non sostituisce il collaudo dei due strumenti principali.
- La ripetibilità su più finestre/VM e le scorciatoie globali restano da valutare separatamente; evitare di dedurre che qualunque chord raggiunga il guest.

**Evidenza:** risposte MCP Desktop1 e catture schermo della sessione 23/11/2026 ~23:00 CET; screenshot temporanei non pubblicati. Testato con plugin desktop 0.9.6. Metodo collaudato sul caso concreto, nessuna release software nuova.
