# Preparazione della prossima chat — 0.5.104 / 126

Una seconda WebView senza finestre carica il progetto, invia una sola volta
«leggi le istruzioni del progetto», cattura il link privato e viene distrutta.
Non attende il messaggio nel DOM né la risposta: pronto significa link salvato,
non istruzioni elaborate o generazione completata lato server.

## Configurazione

Installare l’APK telefono .104 sopra la versione precedente. In Configurazione →
ChatGPT e progetto, modalità nativa, usare Accesso WebView per preparazione con
lo stesso account/spazio di lavoro e progetto dell’app ChatGPT. Salvare URL progetto
per preparazione e abilitare Prepara la prossima chat in background (default OFF).
L’accesso nativo non autentica la WebView. Se già configurato correttamente, dopo
un vecchio errore basta Riprova preparazione fuori Voice; non serve ripetere il login.
Aprire la configurazione accesso/cancellare dati WebView invalida e disabilita
la preparazione; cambio progetto invalida lo slot. La modalità WebView interrompe
il lavoro nativo. Aggiorna stato preparazione mostra l’esito; Copia link della chat
copia esplicitamente l’indirizzo privato senza creare condivisioni pubbliche.

## Segnale di avvio e telefono libero

Il servizio ascolto può avviare una preparazione quando nessun ciclo è attivo.
Chiudere la cover con doppio tap centrale, swipe basso o sinistro autorizza anche
una preparazione per quel ciclo, accodata dopo la rimozione dell’HUD, senza
aspettare lo stop della vecchia Voice. Sinistro continua a nascondere soltanto
la cover lasciando Voice attiva; basso/doppio tap mantengono lo stop singolo.
Una chat già in corso o pronta viene conservata. La stessa chiusura non ripete
un tentativo fallito; una successiva chiusura di un nuovo ciclo o Riprova può
avviare una nuova preparazione. Uscite tecniche/export non simulano questo gesto.
La WebView non apre Activity/finestre, non prende focus né microfono/video;
le richieste media vengono negate. Nessun nuovo servizio audio o wakelock.
Rilascio microfono e ripresa wake restano indipendenti dalla chiusura visuale.

## Cattura e conservazione

Partenza solo dall’URL progetto configurato, senza messaggi preesistenti o bozze,
con editor e Invia semantico unici. Scrittura/invio sono consumati prima delle
azioni; nessun retry dopo incertezza. Android registra il callback submitted
prima di accettare un risultato con URL: è riscontro del dispatch, non ricevuta
server. Il link deriva da location.href, mai da un ID dichiarato dall’assistente.
Dopo l’invio il controllo non legge più messaggi/risposta né richiede un editor.

URL ammessi: HTTPS chatgpt.com /c/UUID oppure /g/progetto-configurato/c/UUID,
senza credenziali, porta, frammenti o parametri arbitrari; solo model viene rimosso.
Nessun link /share/. Link/progetto/stato/timestamp sono cifrati in un archivio
separato dal pairing. I nuovi slot non contengono digest di risposte. URL, ID,
titoli e contenuti non entrano nei log. Scadenza24ore, nessun rinnovo periodico.

## Apertura nativa

Slot pronto dello stesso progetto assegnato persistentemente una sola volta al
ciclo prima di ACTION_VIEW con setPackage(com.openai.chatgpt). Nessun browser,
chooser o altra chat di ripiego dopo handoff incerto; nessun reinvio del messaggio.
Per i nuovi slot si richiedono finestra ChatGPT, editor unico vuoto e controllo
Voice univoco/attivo, escludendo la lista progetto Chat/Fonti. Non essendoci una
risposta acquisita, non si confronta il contenuto. Gli slot legacy mantengono
il confronto del digest. Non si legge l’ID remoto dall’app nativa: la risoluzione
dell’Intent e questi controlli non provano che il client abbia aperto la chat giusta.
Account/spazio, link e Voice effettivi vanno collaudati sul Galaxy.

Minimo300ms dal dispatch registrato, senza nuova attesa se trascorso; restano
minimo audio configurato, rilascio microfono e singolo comando Voice per ciclo.
Saluto dopo Voice separato. Se OFF/non pronta resta il normale flusso .98.

## Limiti e diagnosi

ZIP _12_ .100: invio e URL osservati, selettori messaggi sempre0; non dimostra
la struttura DOM precisa. La .101 elimina quell’attesa secondo richiesta utente.
Timeout5min indipendente dalle callback, errore pagina/login/challenge,
navigazione di documento o renderer terminato fermano il tentativo senza replay.
Android può limitare WebView nascoste e terminare il processo; nessuna garanzia
a schermo spento e limite wake30s invariato. Distruggere la WebView non prova
che ChatGPT continui o finisca l’elaborazione sul server.

Fonti primarie consultate: [WebView](https://developer.android.com/reference/android/webkit/WebView),
[Intent/setPackage](https://developer.android.com/reference/android/content/Intent),
[apertura URL](https://developer.android.com/guide/components/intents-common#Browser).
Documentano Android, non il comportamento effettivo del sito/client ChatGPT.

## Correzione .102 dopo ZIP _13_

La .101 ha avviato la preparazione56–79ms dopo tre rimozioni cover osservate,
ma tutti gli8 tentativi recenti hanno restituito ready dal JS e invalid_result
dal controllo Android. Nessun link_saved/claim: apertura nativa ancora non provata.
Il vecchio log non conserva URL o ragione distinta, quindi non identifica la guardia.

Riprodotto in test il rifiuto di un link con stesso ID progetto completo32hex e
nome decorativo dopo l’ID. La .102 confronta quell’identità completa anche quando
i nomi nell’URL progetto e conversazione differiscono; non ammette altri ID,
prefissi o percorsi codificati. Gli identificatori di formato diverso conservano
il confronto esatto. Origine/UUID/parametri e protezione bozze restano richiesti.
Questo è un difetto locale riprodotto, non prova dell’URL preciso sul telefono.

Il log ora distingue link_received (risultato JS) da link_saved (persistenza riuscita),
link_origin/parameters/encoding/path/id/project/project_settings/syntax,
submission_marker_missing/missing/time, attempt_mismatch e storage_error.
Sono solo codici fissi: niente URL, ID, nomi, query private o eccezioni nel log.
Dopo aggiornamento usare Riprova preparazione fuori Voice per il vecchio stato fallito;
nessuna necessità di rifare accesso se account/progetto corretti.

Ulteriori fonti primarie consultate: [URI Java17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URI.html)
per componenti raw/decodificati, oltre a Intent Android per il dispatch nel pacchetto.
Non documentano né provano il formato specifico del link osservato sul Galaxy.

## Correzione apertura nativa .103 dopo ZIP _14_

Riscontro Galaxy .102:4link_saved e2claimed; nelle aperture ChatGPT è visibile
ma resta prepared_chat_pending e nessun comando Voice. L’utente conferma elenco
chat progetto sotto la cover. La validazione/salvataggio .102 è dunque osservata,
ma la conversazione non viene mostrata nei due richiami riportati.

L’handoff valida prima integralmente l’URL originale (origine/progetto/UUID/query),
poi ricava lo stesso UUID e passa ACTION_VIEW https://chatgpt.com/c/UUID al solo
pacchetto ChatGPT. Non passa più /g/progetto/c/UUID al client. Lo slot conserva
l’URL catturato per stato/copia; nessun nuovo messaggio o cambio ID. La chat pronta
già salvata può essere usata dopo aggiornamento senza reset/login/ripreparazione.

Non si aggiungono secondi tentativi di Intent, browser, shortcut o selezioni chat
per titolo. La lista progetto continua a impedire Voice; il nuovo log distingue
native_open_dispatched route=conversation_id da native_ui_pending con motivi
project_list/composer/voice_control/content, conteggi campi/Voice e flag vuoto.
Mai URL/ID/titoli/testo nei log. Un dispatch è solo richiesta: il client potrebbe
ancora non gestire la destinazione come atteso, da collaudare sul Galaxy.

Fonti consultate: [Deep link Android](https://developer.android.com/training/app-links/create-deeplinks)
e Intent/setPackage citato sopra: il destinatario deve gestire il percorso;
la risoluzione dell’Activity non certifica quale conversazione il client mostri.
Non documentano il routing effettivo dell’app ChatGPT installata sul Galaxy.

## Correzione lettura controlli .104

Il manual_export della .10320:09:49UTC contiene la schermata giusta: starts=0,
editor unico vuoto e conversazione aperta. La catena osservata ha contenitore
comune compositore a profondità37 (radice0), gruppo icone a39 e i due rettangoli
terminali a40. NativeObservationWalker tagliava ogni visita oltre40: nessun figlio
ulteriore poteva raggiungere il riconoscitore Voice. Il vecchio dump .97 mostrava
invece etichetta Avvia una conversazione vocale esattamente a profondità40,
con genitore cliccabile nella stessa zona destra. Non sono visibili nel dump i figli che il limite escludeva.

La .104 alza il limite fisso a64. Restano1500nodi visibili/2500nodi grafo, protezione
da gerarchie eccessive e nessun allargamento di etichette o salita al genitore.
Il verificatore diagnostico antenati usa65visite coerenti col nuovo massimo,
per non oscurare controlli profondi scambiandoli per provenienza non verificata;
editor/password restano protetti. Nei3dump strutturali durante prepared_chat_pending
si salva anche native-buttons.log completo, oltre all’esportazione manuale.

Test riproducono wrapper fino40 seguiti dal controllo semantico: taglio precedente
non trova Voice, nuovo walker la risolve. Il controller con chat preparata e radice
più profonda invia un solo ACTION_CLICK, senza scritture/gesti a coordinate, e il
dump conserva l’etichetta del controllo. Una gerarchia ancora più profonda resta
limitata. È riproduzione del difetto del limite, non cattura dei figli ignoti del
telefono: avvio effettivo Voice da collaudare sul Galaxy. Non serve rifare login o
preparazione se esiste una chat pronta valida.
