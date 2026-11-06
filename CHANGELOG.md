## 0.5.112 / 134 — correzione multilinea del solo bridge Google

- Il campo contenteditable riceve testo letterale e interruzioni BR esplicite con una sola sostituzione, conservando gli a capo anche nel round trip simulato dell’editor.
- Riscontro ZIP(7): editor/form invariati,13LF letti dopo scrittura e0al controllo seguente, differenze limitate agli spazi normalizzati; individuata la guardia che bloccava l’invio.
- Confronto esatto, scrittura/invio singoli e protezioni bozze/editor/sessione rimangono attivi. Nessuna accettazione indiscriminata delle differenze di spazi o nuovo retry.
- Diagnostica senza contenuti estesa con structuredWrite e insertedLineBreaks. Invio nativo Accessibilità e prepare-chat invariati, come richiesto dall’utente; stessa configurazione pre/post/JarvisRuntime.
- Cover nascosta a ogni ciclo e testi solo vocali .111 conservati. Test simulati e controlli Android completi; inserimento/invio reale da collaudare sul Galaxy, nessuna API Google reale nei test. Watch/Windows invariati.

## 0.5.111 / 133 — testi cover solo vocali e verifica del compositore Google

- Sovrimpressione nascosta a ogni nuovo ciclo; tocco invisibile basso-destra per mostrarla/nasconderla, senza ricordare il ciclo precedente.
- Esclusi i messaggi automatici prima/dopo dall’archivio della cover; invii ChatGPT e risposte vocali del plugin conservati. La chat sottostante resta visibile con cover semitrasparente.
- Diagnostica Google mirata dopo scrittura: identità editor/form, connettività, confronto esatto/a capo/spazi e conteggi, osservazione booleana input trusted senza testo. Fasi pre/post/send distinte e metriche aggiornate anche dopo la scrittura.
- Invio bloccato in caso di cambiamento, nessun retry o allargamento delle guardie. Non è ancora una correzione certificata del blocco sul Galaxy: nuovo ZIP necessario per distinguerne la causa.
- Test simulano editor ricreato, form sostituito, a capo variati e testo modificato; privacy, uscita e Google OFF verificati insieme alle regressioni. Nessuna API Google reale, Watch/Windows invariati.

## 0.5.110 / 132 — dialogo continuo, screenshot in Galleria e diagnostica Google

- Doppio tocco alto-destra cattura la cover prima di nasconderla e salva in Pictures/JarvisWake, poi apre l’esportazione dei log. PNG esclusi dallo ZIP; schermate credenziali protette.
- Log tecnici Google e snapshot separati, con fasi DOM/bozze/invii/OAuth/STT/TTS/audio e codici pubblici HTTP/gRPC, senza testo/audio/credenziali/URL.
- Chat in sovrimpressione alternata con tocco invisibile in basso-destra. Nessun pulsante visibile Testi o Nuova dettatura.
- Si può parlare di nuovo durante l’attesa della risposta; nuovo turno nella stessa chat, senza cancellare lavori desktop.
- Interruzione parlando con AEC e route compatibili controllati; fallback esplicito alla ripresa automatica dopo risposta se AEC/routing non sono disponibili. Gestione cambi uscita/Android Auto senza forzare Bluetooth, SCO o AudioManager.mode. Disponibilità reale da collaudare su Galaxy/auto.
- Nuovo risultato plugin interrupted, stesso pairing/UUID/tombstone. Voice nativa precedente confermata dall’utente; funzioni esistenti conservate. Nessuna API Google reale consumata nei test.

## 0.5.109 / 131 — nuova chat corrente, messaggi di avvio e voce economica

- Ogni wake/avvio manuale apre il compositore del progetto già configurato JarvisRuntime. Invio singolo del messaggio prima, attivazione locale Google e messaggio dopo con risposta plugin; ciascun campo vuoto resta disattivato.
- Eliminato il campo manuale del link: destinazione ricavata dall’invio corrente osservato. Nessuna preparazione per avvii successivi, riuso di vecchie chat o ChatGPT Voice nel ramo Google.
- Controllo bozze prima della navigazione, binding progetto/chat e blocco dei reinvii dopo perdita del ledger/risultato incerto. Cover libera immediatamente anche durante avvio.
- Voce iniziale maschile economica it-IT-Wavenet-F, configurabile; migrazione del precedente default Enceladus preservando scelte personalizzate e successive modifiche. Credenziali e rinnovo .108 conservati.
- Test simulati e anti-regressione, firma permanente e prove Galaxy in VERIFICATION.md. Nessuna API Google reale consumata, Watch/Windows invariati.

## 0.5.108 / 130 — rinnovo automatico Google

- Aggiunta autenticazione opzionale con account di servizio custodito e cifrato nel backend esistente; pagina privata per configurare/rimuovere la chiave del telefono abbinato.
- Token temporanei recuperati e rinnovati dal telefono prima di cattura e sintesi, senza rinnovo manuale ogni ora. Attesa dei lavori lunghi conservata.
- Token manuali e relativo archivio preservati. Nessuna chiave nell’APK o nei tool del modello, nessuna attivazione API/fatturazione, nessuna modifica Watch/Windows.
- Test simulati, anti-regressione Android, firma permanente e limiti Galaxy in VERIFICATION.md.

# Changelog

## 0.5.107 / 129 — 5 novembre 2026

- Modalità Google STT/TTS opzionale e reversibile; ChatGPT rimane testuale nell’account e nella conversazione esistenti. Default ChatGPT Voice conservato.
- Configurazione protetta OAuth, lingua/voce, catalogo esplicito, VAD locale, preroll RAM400ms, pause/durata, cancellazione e audio a turni senza barge-in.
- Invio DOM singolo con protezione bozze/destinazione e riscontro distinto dal dispatch; risposta tramite stesso JarvisWake Link, sessione/turno/UUID, progressi, deduplicazione/scadenza e outbox.
- Testi opzionali su tutta la cover, piccoli e semitrasparenti, toggle in basso a destra, scorrimento automatico/due dita; cronologia solo RAM cancellata alla chiusura.
- Quantità tecniche Google separate; token modello non disponibili, nessuna classificazione automatica dialogo/sviluppo.
- Guida, continuità e test simulati aggiornati. Firma/APK/push e limiti fisici riportati in VERIFICATION.md. Nessun aggiornamento Watch o agenti Windows.
