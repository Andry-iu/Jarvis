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
