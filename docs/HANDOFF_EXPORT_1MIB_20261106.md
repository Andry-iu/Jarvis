# Handoff cloud Jarvis — .118/140, trasferimento1MiB

## Ambito approvato

6novembre2026: «modifica quella funzione … file in un colpo solo fino ad un megabyte», via esplicito «Sì, avvia la modifica», poi «Oltre … a blocchi», «500K o un megabyte». Scelti blocchi da1MiB: file fino a1048576byte UTF-8 inclusi in una pagina dati, oltre pacchetti1MiB/ultimo residuo. Manifest iniziale separato, nessuna nuova lettura arbitraria di file Android.

## Stato concreto

Sorgenti .118/140 preparati da main remoto bb6f9f3. Android PhoneContactExport.CHUNK=1MiB; backend accetta6144 o1MiB con limiti reali in byte, padding Base64 canonico e correlazione offset/snapshot alla richiesta; envelope HTTP fino a1406296byte soltanto per device/respond pagina. Limite ordinario16KB mantenuto. Utility ricava il chunk dal manifest e aumenta stdin in modo limitato; hash/schema/contatori/AES-GCM/transazione conservati. Consenso/READ_CONTACTS/sblocco e TTL/heartbeat invariati, app precedenti e staging6144 compatibili.

20test Python e53backend simulati passati. Test Android di soglia aggiunto, NON eseguito fuori cloud. Guida, stato, roadmap, changelog e verifica aggiornati. Nessuna build/firma/deploy/API Google/lettura di contatti reali eseguita per questo gruppo. Non è un APK pronto.

## Richiesta finale di APK firmata e blocco corrente

L’utente ha richiesto anche la consegna dell’APK firmata. Tale fase è autorizzata, ma questa chat non espone strumenti per accedere al cloud Jarvis o avviarne attività: ricerca delle capacità e del plugin Codex Tasks effettuata, nessun collegamento idoneo disponibile. Nessuna compilazione/firma avviata e nessuna chiave richiesta. Proseguire nel cloud già configurato con il presente handoff; non serve un nuovo consenso allo stesso gruppo.

## Passaggio cloud ancora necessario

1. Identificare il cloud Jarvis configurato; fetch/fast-forward main e working tree pulito, riusare toolchain/cache/firma permanenti. Leggere AGENTS, README, CONTINUITY, VERIFICATION e questo handoff.
2. Eseguire test PhoneContactExportTest e bridge/outbox SDK34/36, suite completa/lint/build,20Python/53backend e guida. Verificare trasferimenti fino a1MiB e oltre via MCP realmente pubblicato senza troncamenti: i test simulati non certificano limiti della piattaforma.
3. Pubblicare aggiornamento sullo stesso backend/plugin/Site privato, preservando pairing, risorse e audience. Backend prima dell’APK140, compatibilità vecchi telefoni da mantenere. Nessun nuovo plugin duplicato. Aggiornare catalogo dove necessario.
4. Firmare APK Telefono .118/140 con certificato permanente, verificare ABI/ZIP/16KB/certificato/versione, registrare checksum fuori segreti e push fast-forward verificato. Nessuna compilazione Desktop o GitHub Actions.
5. Installazione sopra APK139 e collaudo Galaxy con nomi, numero singolo, copia completa, esatta soglia1MiB e file maggiore, blocco/revoca/rete/process death/ripresa. I vecchi snapshot restano6144byte, nessun riavvio automatico per cambiare formato. Il JSON1155nomi da163483byte osservato in chat non è ancora una copia locale completa e non è una fixture di test.

Restano diagnosi contacts_export_changed, prove fisiche .115/.116/.117 e tutti gli sviluppi non completati nella roadmap. Non associare al nuovo gruppo prove APK/deploy storiche. L’autorizzazione permanente agli appunti è salvata nelle regole generali e sul telefono, ma questo commit non pretende una nuova lettura degli appunti del Galaxy.
