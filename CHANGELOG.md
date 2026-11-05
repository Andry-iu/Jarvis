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
