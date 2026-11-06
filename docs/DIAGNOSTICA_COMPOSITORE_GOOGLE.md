# Verifica del compositore Google — 0.5.113 / 135

Lo ZIP(5) della .110 mostra tre sequenze campo vuoto → `written` → `draft_changed`, circa165ms fra scrittura e blocco, senza dispatch. Questa volta è il messaggio automatico scritto nel tentativo corrente; la .110 non distingue quale parte della guardia sia cambiata. Non è una prova di errore Google, React o intervento dell’utente. La .111 aggiunge la verifica mirata richiesta, senza eliminare le protezioni o dichiarare il problema risolto sul Galaxy.

## Riscontro ZIP(8) e recupero .113

Il nuovo tentativo .112 ha conservato13LF con confronto esatto e consumato un solo Invia. Al controllo dopo il cambio pagina è arrivato unverified_conversation con campo vuoto e riferimenti originali editor/form scollegati. Screenshot coerente con il messaggio già nella chat; non è una nuova bozza da cancellare/reinviare. La .113 separa il riscontro in sola lettura dalla guardia per scrivere: attende il DOM, il proprio nuovo corpo utente, il campo vuoto e una chat canonica senza richiedere un form solo per leggere. Fissa il primo ID chat raggiunto durante l’osservazione; cambio chat o ledger perso continuano a bloccare, senza clic o riscritture.

Una wake nuova può ispezionare la vecchia pagina /c/UUID conservata anche senza binding precedente, esclusivamente per verificare che lasciarla non perda bozze o interrompa lavoro. Non la adotta come destinazione e non restituisce un nuovo binding. Se origine/percorso sono validi, campo unico vuoto/abilitato e nessun busy/dialogo, naviga al progetto JarvisRuntime configurato per una nuova chat. Non cancella bozze, messaggi o cookie, non preme Stop e non reinvia il testo vecchio. Questo risolve in codice il blocco di recupero riprodotto nella simulazione dopo chiusura di un invio non confermato; serve collaudo Galaxy.

Nuovi flag readOnlyRestart/observingSend/receiptRoutePinned/blockingDialog/formPresent/messageNodesPresent e conteggi userMessages/matchedUserMessages/beforeUserMessages separano transizione della pagina e mancanza del riscontro, senza registrare contenuti o URL. send_dispatched ora è un codice riconosciuto, non il generico failed. Per la prova riattivare Google se sospeso, lasciare la chat inviata con campo vuoto e avviare un ciclo; non occorre eliminarne il messaggio già inviato.

## Riscontro ZIP(7) e correzione .112

Due tentativi della .111 mostrano sameEditor/sameForm=true, entrambi i riferimenti collegati e writeVerified=true; i13LF previsti sono inizialmente presenti, poi diventano0 con uguale numero di caratteri e normalizedTextMatches=true. textMatches/lineEndingsMatch diventano false. Nessun evento trusted osservato, nessun dispatch. Il problema osservato è la differenza di rappresentazione degli spazi/a capo, non la sostituzione dei nodi. Il meccanismo interno di ChatGPT non è esposto dal log.

La .112 costruisce testo letterale e BR per ogni LF in un frammento e sostituisce i figli dell’editor una sola volta. Il valore deve continuare a coincidere esattamente, sullo stesso editor/form; nessuna normalizzazione utilizzata per autorizzare Invia e nessuna riscrittura automatica se il controllo fallisce. Non interpreta markup HTML. Il percorso nativo Accessibilità e prepare-chat restano invariati, come richiesto dall’utente, insieme a messaggi configurati/progetto. La modifica è confinata al nuovo ponte Google.

Il round trip simulato mantiene13LF e passa con confronto esatto/un solo invio; righe vuote e testo simile a markup restano letterali, testo alterato e scritture incerte continuano a bloccare il comando. Questa prova non sostituisce l’editor reale sul Galaxy.

## Prova sul Galaxy

1. Installare la .113 sopra l’app esistente, senza disinstallare o cambiare credenziali. Conservare i messaggi prima/dopo configurati.
2. Avviare un solo ciclo Google con Hey Jarvis o Parla con Jarvis. Se esiste già una bozza, verificarla con swipe sinistro e gestirla manualmente soltanto se riconosciuta; nessuna cancellazione automatica.
3. Alla comparsa del blocco fare doppio tocco in alto a destra e salvare lo ZIP. Non premere manualmente Invia né ripetere lo stesso tentativo incerto durante questa raccolta.
4. Condividere lo ZIP. La foto richiesta dal gesto resta separata in Galleria Pictures/JarvisWake; eventuale condivisione dell’immagine è una scelta distinta.

L’obiettivo è leggere il primo evento `draft_changed` dopo `written` in `google-voice-0.log` o nel file ruotato `google-voice-1.log`. La fase `pre` identifica il messaggio prima, `post` il messaggio automatico dopo, `send` il dettato vocale. Non occorre autorizzare chiamate Google dal cloud per questa verifica: nei tentativi bloccati prima dell’invio non è stato ancora aperto il microfono del nuovo percorso.

## Campi tecnici

| Campo | Interpretazione |
| --- | --- |
| `sameEditor`, `sameForm` | Identità del nodo corrente rispetto al riferimento salvato alla scrittura. Un valore false non identifica da solo la causa della ricreazione. |
| `originalEditorConnected`, `currentEditorConnected` | Presenza dei due riferimenti nel documento attuale, verificata con `document.contains`. |
| `contentEditable` | Il campo corrente espone isContentEditable; false può indicare textarea/input. |
| `writeVerified` | Il confronto esatto immediatamente dopo la propria scrittura è riuscito. Non prova stabilità successiva o invio. |
| `textMatches` | Il valore corrente, con lo stesso trim già usato dal bridge, è uguale al testo previsto. |
| `lineEndingsMatch` | Confronto con sola conversione CRLF/CR in LF. È soltanto diagnostica. |
| `normalizedTextMatches` | Confronto con sequenze di spazi normalizzate. Non dimostra equivalenza semantica né autorizza invio. |
| `expectedChars`, `draftChars`, `expectedLineBreaks`, `draftLineBreaks` | Conteggi del testo previsto/corrente e LF, senza il testo o un suo hash. |
| `trustedInputSeen`, `inputObservationAvailable` | Evento di editing isTrusted=true osservato nel documento dopo la scrittura e disponibilità dell’osservatore. Non certifica chi ha modificato la frase o che una modifica sia avvenuta; non è un criterio di invio. |
| `structuredWrite`, `insertedLineBreaks` | Uso della scrittura a nodi testo/BR e numero di interruzioni inserite, senza contenuti. Non prova l’accettazione successiva da parte dell’editor. |
| `draftOwned`, `sendDispatched` | Riconoscimento esatto della propria scrittura sul nodo salvato e comando di invio consumato. Dispatch resta distinto da messaggio osservato nella chat. |

Più condizioni possono cambiare insieme. Per esempio sameEditor=false/textMatches=true separa una sostituzione del nodo da una differenza di valore, ma il bridge continua a rifiutare il nodo sostitutivo. sameEditor=true/textMatches=false/normalizedTextMatches=true indica una differenza di rappresentazione degli spazi: non viene automaticamente accettata.

Le metriche sono lette al momento dell’esito, anche dopo la scrittura: `written` non conserva più soltanto il conteggio vuoto precedente. I listener beforeinput/input/paste/drop/compositionstart registrano esclusivamente un booleano, senza event.data, target, testo o coordinate; vengono rimossi alla chiusura/reset. Nessuna nuova scrittura, navigazione, focus, clic o normalizzazione del messaggio è introdotta dalla diagnostica.

## Cover e limiti

Il pannello piccolo/semitrasparente della cover contiene soltanto Dettato e risposte plugin destinate alla voce, mai i messaggi automatici pre/post. Parte nascosto a ogni nuovo ciclo, indipendentemente dal vecchio show_text salvato; il tocco invisibile basso-destra mostra/nasconde la cronologia della sessione. Non modifica la semitrasparenza della cover: il compositore e la chat sottostanti possono restare visibili attraverso di essa.

Test automatici con servizi/DOM simulati coprono nodo e form ricreati, differenze CRLF/a capo, modifica reale, invio singolo, perdita ledger/incertezza, privacy e chiusura. Non riproducono il renderer reale di ChatGPT sul Galaxy e non provano quale condizione fallisca nel caso utente. La .112 corregge l’inserimento con BR e mantiene il confronto esatto; serve il collaudo reale per verificarne l’efficacia, con bozza e invio singolo protetti. Nessuna API Google reale consumata nello sviluppo; Google OFF, Voice nativa, audio, Android Auto, screenshot Galleria e funzioni esistenti conservati.

Fonti consultate: [innerText e resa del testo](https://developer.mozilla.org/en-US/docs/Web/API/HTMLElement/innerText), [connessione dei nodi DOM](https://developer.mozilla.org/en-US/docs/Web/API/Node/isConnected), [isTrusted](https://developer.mozilla.org/en-US/docs/Web/API/Event/isTrusted). Le differenze documentate sono motivi per misurare separatamente le condizioni, non prove della causa sul telefono.

Fonti della correzione: [replaceChildren](https://developer.mozilla.org/en-US/docs/Web/API/Element/replaceChildren), [interruzioni BR](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/br), [testo letterale con createTextNode](https://developer.mozilla.org/en-US/docs/Web/API/Document/createTextNode).
