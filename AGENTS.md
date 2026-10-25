# Istruzioni per lavorare su JarvisWake

Prima di modificare il progetto leggere `README.md`, `CONTINUITY.md` e `VERIFICATION.md`. `PROMPT_CONTINUITA.txt` è il testo breve per riprendere il lavoro in una nuova conversazione; `docs/ROADMAP.md` distingue le decisioni approvate dalle funzioni implementate.

- Conservare i requisiti consolidati elencati in `CONTINUITY.md`; confrontare ogni modifica di comportamento con tali requisiti e con le nuove richieste dell'utente.
- **Discussione prima dello sviluppo:** quando l'utente sta valutando o discutendo modifiche, non iniziare a cambiare codice, documenti o configurazioni. Raccogliere e chiarire prima l'intero ambito della prossima release; iniziare solo dopo un'autorizzazione esplicita dell'utente a implementare. Questa regola vale anche quando si lavora partendo da Git e va mantenuta nei documenti di continuità.
- Un solo gesto Voice per ciclo. Dopo il gesto, nessuna ripetizione automatica, anche dopo ricreazione dell'attività o riconnessione dell'accessibilità.
- UI cover: niente pulsante X; tocco singolo centrale alterna mute/riattivazione del microfono Voice e colora l’animazione fucsia scuro durante il mute; tocco sotto l’animazione alterna opaco/semitrasparente, doppio tocco al centro chiude il ciclo, doppio tocco in alto a destra esporta la diagnostica. Uno swipe iniziato appena sotto la barra di stato e diretto verso il basso termina Voice e nasconde la cover per mostrare la Home. Una riattivazione con «Ehi Jarvis» deve riportare alla pagina mantenuta in background. Conservare l'annullamento del ciclo con Indietro e lo stop previsto quando lo schermo viene spento.
- Non sostituire implicitamente la sessione ChatGPT dell'account con API separate. Non usare l'ultima chat generica per implementare la futura continuità JarvisRuntime.
- Per diagnosi Android/ChatGPT consultare più fonti primarie pertinenti e distinguere codice, test locali e riscontro sul dispositivo. Non dichiarare risolto sul telefono ciò che è solo compilato o simulato.
- Aggiornare documentazione e stato di continuità insieme alle modifiche rilevanti. Non presentare attività pianificate come completate.
- Non inserire in Git cookie, credenziali, chiavi di firma, audio, trascrizioni, cache o output di build. Il modello Vosk si prepara tramite lo script con checksum fissato.
- Prima di ogni modifica, aggiornare i riferimenti remoti e partire da `main` allineato, completo e senza modifiche locali pendenti. Se il checkout non coincide con `origin/main`, riallinearlo in modo sicuro prima di intervenire.
- Un rilascio di APK firmato per il collaudo è completo solo quando sorgenti, test, documentazione e checksum sono committati su `main` e il push fast-forward è verificato: `HEAD` e `origin/main` devono puntare allo stesso commit prima di consegnare l'APK. Se il push fallisce, non dichiarare il rilascio allineato e riferire chiaramente il blocco. Non serve chiedere un ulteriore «Consolida». Non committare l'APK né chiavi o credenziali.
- L'autorizzazione all'implementazione segue la regola di discussione qui sopra; il rilascio e il push non anticipano mai la decisione di scope.
- Se esiste una cronologia remota, recuperarla e integrare i cambiamenti conservandola. Nessun force-push per sostituirla con una copia ricavata da ZIP.

## Build, firma e consegna APK — regola operativa consolidata

- Compilare localmente con JDK 17, Gradle Wrapper 8.13, Android SDK 36 e Build Tools 36.0.0. I workflow GitHub non devono compilare automaticamente sui push; non usare GitHub Actions per build o firma.
- Quando l'utente chiede un APK, eseguire davvero test/build locali, verificare versione, ABI, integrità ZIP, allineamento e certificato. Consegnare l'APK direttamente, non un archivio ZIP, e indicare esplicitamente se non è firmato con il certificato permanente.
- Consegnare direttamente il file `.apk` pronto da installare, non un link a uno ZIP, quando il file è effettivamente disponibile e verificato nell'ambiente corrente. Non inventare link locali né dichiarare verifiche non eseguite.
- Riservare all'utente il collaudo sul dispositivo fisico. Non dichiarare riusciti i test reali basandosi soltanto sulla build locale.
- Preservare la firma permanente; non generare una chiave sostitutiva. Non chiedere o stampare password in chat. Non inserire keystore, password, cookie, audio, trascrizioni, cache o output di build in Git.
- Mantenere il backup cifrato nel branch di handoff dedicato; verificare il certificato SHA-256 prima della firma e rimuovere i file in chiaro temporanei dopo.
- Partire sempre da `main` completo e aggiornato. Quando una modifica culmina nel rilascio di un APK firmato per il collaudo, aggiornare nello stesso rilascio sorgenti, test e documentazione con un commit fast-forward su `main`, senza attendere un'ulteriore richiesta «Consolida». Verificare il branch remoto e non usare force-push.
