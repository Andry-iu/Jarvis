# Preparazione della prossima chat — 0.5.99 / 121

La preparazione è un percorso testuale separato dalla cover e dalla sessione Voice.
Una seconda WebView, appartenente a Jarvis, carica il progetto configurato,
invia esattamente «leggi le istruzioni del progetto» e osserva la risposta.
Non apre Activity, non aggiunge finestre/overlay, non mette a fuoco il campo,
non chiede il microfono e non avvia Voice. Le richieste Web di audio e video sono negate.
Il telefono resta utilizzabile e la chiusura della cover mantiene le regole .96.

## Attivazione sul telefono

1. Terminare il ciclo Voice precedente e installare l’APK telefono .99 firmato.
2. Configurazione → ChatGPT e progetto, modalità App ChatGPT del telefono.
3. Aprire **Accesso WebView per preparazione**. Accedere con lo stesso account e
   spazio di lavoro dell’app nativa; verificare che il progetto sia quello giusto.
   L’accesso nell’app nativa non autentica automaticamente la WebView.
   Questa pagina di configurazione nega anche le richieste media; non usare il pulsante Voice del sito.
4. Tornare in Jarvis e verificare **URL progetto per preparazione** e nome progetto
   nativo. Salvare l’URL del progetto, non una vecchia chat o un link pubblico.
5. Abilitare **Prepara la prossima chat in background**, inizialmente OFF.
   L’abilitazione conferma che account/spazio/progetto della WebView sono quelli desiderati.
6. Avviare l’ascolto e tornare alle altre app. **Aggiorna stato preparazione** mostra
   l’avanzamento; **Copia link chat preparata** copia esplicitamente solo il link privato.

L’accesso di configurazione disabilita e invalida la preparazione precedente:
riabilitarla dopo aver controllato l’account. Cancellare i dati WebView ha lo stesso effetto.
Cambio URL/nome progetto invalida il vecchio slot. Passare alla modalità WebView interrompe la preparazione in volo. Non viene cambiato l’account nativo.

## Identificazione e consumo

Il documento deve partire dall’URL esatto del progetto, senza messaggi preesistenti
né bozze. Un solo editor e un solo Invia semantico; scrittura e invio sono consumati
prima delle azioni. Nessuna richiesta all’assistente di inventare o riferire il proprio ID.
Il link viene letto da `location.href` dopo l’invio della frase e il suo riscontro
come unico messaggio utente della nuova conversazione.

Sono ammessi soltanto link HTTPS privati chatgpt.com `/c/UUID` oppure
`/g/progetto-configurato/c/UUID`, senza credenziali, porta, frammenti o parametri
arbitrari. L’eventuale solo parametro `model` viene rimosso dal link salvato.
I link `/share/` non sono usati e non viene creata una condivisione pubblica.

Pronta significa: link della conversazione valido, campo svuotato, risposta assistente
presente e stabile per almeno3s, nessuna generazione/approvazione rilevata.
Non dimostra che il modello abbia davvero letto tutte le istruzioni o eseguito i tool.
Una richiesta di approvazione non viene mai accettata automaticamente.

Il link, progetto e stato sono cifrati localmente con Android Keystore in un namespace
separato dall’abbinamento del plugin. Della risposta resta soltanto il digest di un
breve frammento finale normalizzato, per il controllo aggiuntivo nella UI nativa.
URL, ID remoto, titolo e risposta non sono registrati nella diagnostica. Il contenuto
DOM è temporaneo nella WebView, che viene distrutta quando lo slot diventa pronto.
Una chat pronta scade dopo24ore: nessuna creazione periodica di chat per aggiornarla.

## Avvio successivo nell’app nativa

Al richiamo manuale o wake, se lo slot è pronto, recente e dello stesso progetto,
Jarvis lo assegna persistentemente al ciclo PRIMA dell’apertura. Usa `ACTION_VIEW`
con il link catturato e `setPackage(com.openai.chatgpt)`: nessun chooser/browser o
shortcut progetto di ripiego dopo un’apertura incerta.

Prima di Voice richiede una finestra ChatGPT valida, un campo unico vuoto e un
riscontro del frammento di risposta preparato. La schermata progetto con schede
Chat/Fonti e anteprime non basta. È un controllo aggiuntivo del contenuto visibile,
NON una lettura dell’ID remoto dalla UI nativa né una prova assoluta d’identità.
Il supporto effettivo al link, il corretto account/spazio e Voice nella chat aperta
DEVONO essere collaudati sul Galaxy. Risoluzione Android dell’Intent non prova da sola
che il client abbia mostrato la destinazione attesa.

Se i controlli passano non viene reinviato il messaggio iniziale. Il minimo300ms
è già trascorso durante l’osservazione della risposta; restano rilascio microfono,
minimo audio configurato, controllo Voice univoco e comando singolo per ciclo.
Il saluto configurabile dopo Voice resta separato.

Se lo slot non è pronto o la funzione è OFF si mantiene il percorso .98 nativo normale.
Un’apertura assegnata ma non confermata non ripiega su un’altra chat e non invia testo:
la cover resta chiudibile immediatamente e valgono le scadenze esistenti.
Dopo un ciclo senza conferma Voice, la preparazione si arresta; per riprovare serve
**Riprova preparazione**. Dopo un ciclo Voice osservato e terminato, la prossima chat
può essere preparata senza rallentare l’uscita: non si avvia nulla nel callback dei gesti.

## Limiti del background e collaudo

La WebView è staccata dalle finestre ma ha un viewport configurato e un proprio
controller nel processo Jarvis. Il servizio wake già attivo ne mantiene il lavoro;
non viene introdotto un altro servizio/microfono o un wakelock. Una preparazione già
iniziata può terminare mentre il percorso Voice normale è attivo; non se ne avvia
una nuova durante Voice. Se Android termina processo/servizio o il sito cambia documento,
il tentativo incerto non viene ricreato o reinviato automaticamente.

Timeout5min con watchdog indipendente anche se manca una callback del renderer, errori pagina/HTTP, login scaduto, challenge, renderer terminato, bozze,
controlli ambigui e approvazioni possono impedire la preparazione. Android/Chromium
possono limitare il lavoro di una WebView nascosta, soprattutto a schermo spento.
La continuità reale mentre si usano altre app e con schermo spento non è garantita
né dimostrata dai test JVM/DOM: va osservata sul telefono. Il limite wake30s resta invariato.

Fonti primarie: [WebView/evaluateJavascript e ciclo vita](https://developer.android.com/reference/android/webkit/WebView),
[Intent e setPackage](https://developer.android.com/reference/android/content/Intent),
[intent di apertura URL](https://developer.android.com/guide/components/intents-common#Browser).
Descrivono le API Android, non certificano il comportamento del sito o client ChatGPT.
