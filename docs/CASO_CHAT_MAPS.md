# Caso problematico: conversazione JarvisRuntime durante la navigazione

Stato al 2 novembre 2026: **da migliorare, non risolto**. Registrazione documentale richiesta dall’utente dopo l’analisi di `Chat_JarvisRuntime.txt` e `jarvis-diagnostics _7_.zip`, riferiti alla .92/114. Non equivale a un via allo sviluppo o a un nuovo rilascio. Non conservare in Git la conversazione integrale, indirizzi, destinazioni o log grezzi.

## Problemi osservati e comportamento atteso

1. La prima destinazione proposta è stata contestata e successivamente corretta. Per luoghi dal nome ambiguo chiarire località e destinazione; non presentare una corrispondenza incerta come già verificata. L’export testuale non include le ricerche o i risultati strutturati dei tool: non dedurre che non siano mai stati chiamati.
2. Dopo l’apertura di Maps, la chat invita a riaprire l’app pur non potendone osservare lo stato. Dichiarare precisamente il limite di osservazione e leggere l’esito della richiesta; non dedurre che Maps sia chiuso. L’invio dell’Intent non prova navigazione attiva, chiusura dell’app o arrivo.
3. L’assistente afferma un blocco del cambio rotta durante la guida senza un errore del tool disponibile che lo attesti. Nessun blocco basato sulla guida è implementato nel ramo `navigate` di JarvisWake; il tool prevede una nuova destinazione. Non inventare restrizioni, né attribuirle ad Android/Maps senza un riscontro specifico.
4. Richiede di pronunciare una formula particolare per reimpostare la rotta. La formulazione naturale e sufficientemente chiara dell’utente deve bastare; chiarire soltanto l’eventuale destinazione ambigua, senza una frase obbligatoria.
5. Alla frase generica di accettazione dell’utente l’assistente risponde annunciando la chiusura. Non equiparare un’accettazione alla richiesta di terminare Voice. Nella conversazione segue poi una richiesta esplicita di chiusura: questa va distinta dalla precedente frase. Un annuncio non dimostra una chiusura effettivamente eseguita.
6. Segnalazione utente: con la navigazione aperta la chat sembra non poter svolgere altre attività fino alla chiusura di Maps. Il comportamento atteso è disponibilità degli altri comandi autorizzati, con i rispettivi controlli. Non attendere la fine del percorso per considerare conclusa la richiesta di apertura. Non dichiarare però un blocco tecnico generale accertato: la trascrizione mostra prosecuzione del dialogo e mancano gli esiti strutturati dei comandi nel momento segnalato.

## Evidenze e limiti della diagnosi

- Il codice .92 restituisce `intent_dispatched` dopo `startActivity`, senza attendere la fine del percorso. Questo non osserva lo stato della navigazione. Il risultato va distinto da `pending`, `expired` e `outcome_uncertain`; nessun rilancio automatico dopo un esito incerto.
- Nell’ultimo ciclo dello ZIP, Voice è confermata alle17:49:48 UTC; dalle17:50:53 un’altra app è in primo piano e ChatGPT non è selezionabile per l’automazione UI. Il proprietario è soltanto `other`: compatibile con Maps, non identificazione certa. Non prova l’interruzione del collegamento MCP; il percorso telefonata già prevede di riportare ChatGPT davanti prima dell’arresto Voice.
- Alle18:05:00–01 UTC si osservano chiusura cover, conferma rilascio audio e ripresa wake. Il tap del saluto è completato nel ciclo, ma non prova da solo accettazione del messaggio o risposta vocale.
- L’export esclude testo della chat/audio e non registra sistematicamente operazione/esito dei comandi Maps e successivi. Non dedurre errori specifici, invocazioni mancanti o la causa di ogni frase da tale assenza.

## Miglioramento da raccogliere e verificare

Rendere esplicite nelle istruzioni/descrizioni del collegamento le distinzioni tra apertura richiesta, navigazione osservata e disponibilità degli altri comandi; evitare restrizioni o formule inventate. Valutare diagnostica sintetica correlata della catena richiesta → esecuzione → risultato → consegna, con soli codici/tempi/stati, mai destinazioni, nomi, numeri, testi chat o credenziali. Prima di attribuire un blocco a Maps, distinguere esito non consegnato, Voice interrotta, UI ChatGPT non accessibile e risposta errata del modello.

Criterio per il successivo collaudo: durante una navigazione già aperta, una richiesta chiara di cambio destinazione o di un’altra azione deve essere gestita con il tool pertinente e con l’esito reale, senza attendere la chiusura di Maps o richiedere formule speciali. Conservare autorizzazioni, sblocco e gestione degli esiti incerti; per telefonare restano stop Voice unico e rilascio audio confermato prima della chiamata. Cover/gesti/wake .70/.79 non vanno sacrificati. Il nuovo gruppo di sviluppo richiede il via esplicito previsto da AGENTS.md; questo documento non dichiara implementazione o collaudo riusciti.
