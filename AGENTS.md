# Istruzioni per lavorare su JarvisWake

Prima di modificare il progetto leggere `README.md`, `CONTINUITY.md` e `VERIFICATION.md`. `PROMPT_CONTINUITA.txt` è il testo breve per riprendere il lavoro in una nuova conversazione; `docs/ROADMAP.md` distingue le decisioni approvate dalle funzioni implementate.

- Conservare i requisiti consolidati elencati in `CONTINUITY.md`; confrontare ogni modifica di comportamento con tali requisiti e con le nuove richieste dell'utente.
- Un solo gesto Voice per ciclo. Dopo il gesto, nessuna ripetizione automatica, anche dopo ricreazione dell'attività o riconnessione dell'accessibilità.
- Mantenere X disponibile e cancellazione del ciclo prima della chiusura del WebView. Distinguere avvio da telefono già bloccato da un nuovo spegnimento dello schermo, che chiude la conversazione.
- Non sostituire implicitamente la sessione ChatGPT dell'account con API separate. Non usare l'ultima chat generica per implementare la futura continuità JarvisRuntime.
- Per diagnosi Android/ChatGPT consultare più fonti primarie pertinenti e distinguere codice, test locali e riscontro sul dispositivo. Non dichiarare risolto sul telefono ciò che è solo compilato o simulato.
- Aggiornare documentazione e stato di continuità insieme alle modifiche rilevanti. Non presentare attività pianificate come completate.
- Non inserire in Git cookie, credenziali, chiavi di firma, audio, trascrizioni, cache o output di build. Il modello Vosk si prepara tramite lo script con checksum fissato.
- Prima di ogni modifica, aggiornare i riferimenti remoti e partire da `main` allineato, completo e senza modifiche locali pendenti. Se il checkout non coincide con `origin/main`, riallinearlo in modo sicuro prima di intervenire.
- Al rilascio di un APK firmato per il collaudo, committare e pubblicare direttamente su `main` sorgenti, test, documentazione e checksum della versione. Non serve chiedere un ulteriore «Consolida». Non committare l'APK né chiavi o credenziali.
- Se esiste una cronologia remota, recuperarla e integrare i cambiamenti conservandola. Nessun force-push per sostituirla con una copia ricavata da ZIP.

## Build, firma e consegna APK — regola operativa consolidata

- Compilare localmente con JDK 17, Gradle Wrapper 8.13, Android SDK 36 e Build Tools 36.0.0. Non usare GitHub Actions per build o firma.
- Quando l'utente chiede un APK, eseguire davvero test/build locali, verificare versione, ABI, integrità ZIP, allineamento e certificato. Consegnare l'APK direttamente, non un archivio ZIP, e indicare esplicitamente se non è firmato con il certificato permanente.
- Consegnare direttamente il file `.apk` pronto da installare, non un link a uno ZIP, quando il file è effettivamente disponibile e verificato nell'ambiente corrente. Non inventare link locali né dichiarare verifiche non eseguite.
- Riservare all'utente il collaudo sul dispositivo fisico. Non dichiarare riusciti i test reali basandosi soltanto sulla build locale.
- Preservare la firma permanente; non generare una chiave sostitutiva. Non chiedere o stampare password in chat. Non inserire keystore, password, cookie, audio, trascrizioni, cache o output di build in Git.
- Mantenere il backup cifrato nel branch di handoff dedicato; verificare il certificato SHA-256 prima della firma e rimuovere i file in chiaro temporanei dopo.
- Partire sempre da `main` completo e aggiornato. Quando una modifica culmina nel rilascio di un APK firmato per il collaudo, aggiornare nello stesso rilascio sorgenti, test e documentazione con un commit fast-forward su `main`, senza attendere un'ulteriore richiesta «Consolida». Verificare il branch remoto e non usare force-push.
