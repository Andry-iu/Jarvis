# Istruzioni per lavorare su JarvisWake

Prima di modificare il progetto leggere `README.md`, `CONTINUITY.md` e `VERIFICATION.md`. `PROMPT_CONTINUITA.txt` è il testo breve per riprendere il lavoro in una nuova conversazione; `docs/ROADMAP.md` distingue le decisioni approvate dalle funzioni implementate.

- Conservare i requisiti consolidati elencati in `CONTINUITY.md`; confrontare ogni modifica di comportamento con tali requisiti e con le nuove richieste dell'utente.
- Un solo gesto Voice per ciclo. Dopo il gesto, nessuna ripetizione automatica, anche dopo ricreazione dell'attività o riconnessione dell'accessibilità.
- Mantenere X disponibile e cancellazione del ciclo prima della chiusura del WebView. Distinguere avvio da telefono già bloccato da un nuovo spegnimento dello schermo, che chiude la conversazione.
- Non sostituire implicitamente la sessione ChatGPT dell'account con API separate. Non usare l'ultima chat generica per implementare la futura continuità JarvisRuntime.
- Per diagnosi Android/ChatGPT consultare più fonti primarie pertinenti e distinguere codice, test locali e riscontro sul dispositivo. Non dichiarare risolto sul telefono ciò che è solo compilato o simulato.
- Aggiornare documentazione e stato di continuità insieme alle modifiche rilevanti. Non presentare attività pianificate come completate.
- Non inserire in Git cookie, credenziali, chiavi di firma, audio, trascrizioni, cache o output di build. Il modello Vosk si prepara tramite lo script con checksum fissato.
- Se esiste una cronologia remota, recuperarla e integrare i cambiamenti conservandola. Nessun force-push per sostituirla con una copia ricavata da ZIP.

## Consegna APK e verifica CI — regola operativa consolidata

- Quando l'utente chiede un APK, seguire autonomamente GitHub Actions fino a conclusione: verificare il run riferito al commit richiesto, esito di build/test/lint e artifact.
- Scaricare l'artifact ZIP, estrarre l'APK, controllare integrità SHA-256 e firma/certificato quando pertinente; non chiedere all'utente di controllare hash o stato del workflow.
- Consegnare direttamente il file `.apk` pronto da installare, non un link a uno ZIP, quando il file è effettivamente disponibile e verificato nell'ambiente corrente. Non inventare link locali né dichiarare verifiche non eseguite.
- Se il recupero diretto dell'APK non è tecnicamente possibile, dichiarare il limite e fornire il link GitHub all'artifact, distinguendo chiaramente questa soluzione di ripiego.
- Riservare all'utente il collaudo sul dispositivo fisico. Non dichiarare riusciti i test reali basandosi soltanto sulla CI.
- Rispettare la compilazione selettiva: i commit ordinari non devono avviare l'APK; usare `[build-apk]` quando la build è richiesta, o il dispatch manuale se necessario. Preservare la firma persistente.
