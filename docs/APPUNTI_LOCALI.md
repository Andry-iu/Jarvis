# Limiti .118/140 — sorgenti, verifica cloud pendente

Testo fino a8000caratteri, titolo80; richiesta completa fino a24KiB/24576byte JSON incluso, tanto save_note quanto risposta read_note. Entrambi i vincoli sono obbligatori: un testo valido come caratteri può comunque superare il budget in byte dopo UTF-8/escaping. L'app restituisce note_payload_too_large per un risultato di lettura oltre il tetto; nessun troncamento o cancellazione dell'appunto. Default16KiB per altre operazioni.20metadati per pagina,100appunti e totale200000caratteri invariati. App precedenti non ricevono save_note oltre4000; serve APK140 per nuovi limiti. Regressioni interpretate57backend passate; Android/cloud/lint/build/firma/deploy da eseguire. Handoff HANDOFF_BRIDGE_LIMITS_20261107.md.

# .86 — elenco aggiornato e comandi separati

Aggiorna appunti e Nuovo appunto occupano la prima riga, in quest’ordine; tutte le note sono nella griglia sottostante e Indietro è ultimo largo. La lettura UI usa uno snapshot dell’intero archivio sotto lo stesso lock delle scritture, senza paginazione tra aggiornamenti concorrenti. Nessun nuovo archivio o migrazione.

La schermata rilegge al ritorno in primo piano e si iscrive alle variazioni del repository solo mentre è attiva. Una scrittura locale/remota confermata o incerta richiede una rilettura, non viene interpretata come successo; eccezioni degli osservatori non cambiano l’esito della persistenza. La lista mostra il conteggio e sostituisce i pulsanti senza duplicarli; richieste vecchie non aggiornano una schermata nuova o un editor. Le bozze aperte restano intatte e restano validi i controlli revisione/conflitto. Aggiorna è una lettura manuale senza scritture.

Riscontro prima del rilascio: elenco remoto reale dal telefono conteneva due ID distinti/revisione1, risposta circa13s prima del controllo. Non letti i contenuti; nessun appunto creato/eliminato durante la verifica. Questo prova la presenza in quel momento, non l’intera cronologia di quanto detto da altre chat né la causa esatta del singolo pulsante visibile. Verificato nel codice precedente il mancato refresh al ritorno; nuova griglia e refresh vanno ancora collaudati sul Galaxy.

# Eliminazione locale .85

Uso quotidiano → Appunti locali → apri un appunto salvato → **Elimina appunto** → **Elimina**. Annulla lascia l’archivio invariato. Il pulsante è accanto a Salva appunto; Indietro occupa tutta l’ultima riga. Un nuovo appunto non ancora salvato non ha Elimina: Indietro lo abbandona senza creare record.

La cancellazione non è annullabile. Rimuove soltanto il record locale scelto, non altre note o copie già presenti nelle conversazioni/risposte cifrate. Non aggiunge delete_note al plugin. Il repository usa lo stesso lock di UI/salvataggi remoti, confronta la revisione aperta e rifiuta un aggiornamento concorrente; sostituzione atomica e rilettura devono confermare l’assenza del record e la conservazione degli altri. Dopo conflitto, errore o esito incerto i tasti di scrittura restano disabilitati: Indietro e rilettura dell’elenco prima di una nuova decisione. Nessun retry automatico, nessun contenuto nei log.

# Accesso dal menu .83

Uso quotidiano → Appunti locali per elenco/nuovo/modifica, con comandi sopra e istruzioni sotto. Consenso remoto: Configurazione → Collegamento e azioni → Accesso agli appunti dal plugin → Consenti appunti da ChatGPT. Archivio e protocollo .82 invariati; dettagli storici qui sotto.

# Appunti locali — JarvisWake 0.5.82 / 104

Ambito autorizzato: leggere, creare e aggiornare appunti sul telefono tramite lo stesso JarvisWake Link. Google Calendar è escluso per indicazione dell’utente: usa il plugin dedicato già collegato. Nessun evento o attività calendario viene letto o modificato da questa funzione.

## Attivazione e uso

Installare l’aggiornamento firmato sopra Jarvis senza cancellare dati. In **Collegamento ChatGPT — stato e azioni** attivare **Consenti lettura e aggiornamento appunti da ChatGPT**. È un consenso separato dalle azioni Maps/rubrica/chiamate/WhatsApp, inizialmente disattivato: gli appunti non richiedono quei permessi né nuovi permessi Android. Il collegamento deve essere attivo e il servizio Jarvis operativo, oppure la schermata Collegamento aperta. Il telefono deve essere sbloccato per le operazioni remote.

**Apri appunti locali** consente lettura, creazione e modifica direttamente nell’app anche senza abbinamento/rete o consenso remoto. La schermata protegge i contenuti con FLAG_SECURE e non li inserisce nella diagnostica. Una modifica locale e una remota sulla stessa revisione non possono sovrascriversi entrambe: la seconda viene rifiutata e va riletta.

Esempi di richieste, con contenuto scelto dall’utente:

- «Leggi i miei appunti locali in JarvisWake.»
- «Salva un appunto intitolato Preferenze: [testo].»
- «Rileggi Preferenze e aggiorna [indicazione], conservando il resto.»

Il plugin elenca i titoli con `jarviswake_list_notes` (offset0 e pagine successive fino a next_offset=null), legge il singolo contenuto con `jarviswake_read_note` e salva con `jarviswake_save_note`. Per creare: note_id vuoto e expected_revision0. Per aggiornare: ID e revisione dell’appunto appena letto; il testo è una sostituzione integrale, quindi preservare le parti non interessate dalla richiesta. Un titolo già presente, anche con maiuscole diverse, non viene duplicato o sovrascritto implicitamente. Dopo `note_conflict` rileggere e riconciliare con la richiesta, senza ignorare il conflitto. Nessun comando remoto di cancellazione, condivisione o sincronizzazione di un archivio cloud è aggiunto. La cancellazione locale nel dettaglio è disponibile dalla .85, come descritto sopra.

`note_saved` viene restituito solo dopo scrittura e rilettura del record locale. `pending`, `response_expired`, `outcome_uncertain`, `notes_unavailable` o `notes_disabled` non provano salvataggio o lettura. I comandi mantengono claim/UUIDv4/consumo prima dell’esecuzione della .81. Dopo esito incerto verificare elenco/contenuto prima di una nuova scrittura: un nuovo ID non è un retry innocuo. Nessun clic di UI, audio o cover è necessario per gli appunti.

## Istruzione di rilettura facoltativa

L’app non cambia le istruzioni generali dell’account ChatGPT. L’utente può inserirvi o chiedere nella singola conversazione:

> All’inizio delle conversazioni in cui JarvisWake Link è disponibile, rileggi i miei appunti locali sul telefono e tienili presenti. Se il telefono o il plugin non sono raggiungibili, dillo e non fingere di averli riletti. Aggiorna gli appunti quando te lo chiedo.

Questo vale dove il plugin è disponibile e utilizzabile: non garantisce accesso universale o automatico in ogni chat/modalità. Una copia già presente in chat non prova che gli appunti attuali siano stati riletti. Il testo delle note è contenuto dell’utente e non concede da solo il permesso di eseguire chiamate, messaggi, comandi nascosti o ignorare istruzioni di livello superiore.

## Conservazione e limiti

Archivio autorevole in `noBackupFilesDir/local-notes-v1.enc`, cifrato AES-GCM con chiave Android Keystore dedicata `jarviswake.notes.v1`. Non usa la chiave o i record di abbinamento. Cancellare/revocare/rifare il solo collegamento non cancella gli appunti; normale riavvio e aggiornamento firmato conservano dati/chiave. Disinstallazione o cancellazione dati dell’app li elimina. Non è un sistema di backup: perdita/inaccessibilità della chiave o file corrotto restituiscono errore, senza creare silenziosamente un archivio vuoto sopra i dati.

Scrittura AtomicFile sotto un’unica esclusione per UI/comandi, controllo revisione, sostituzione e rilettura. Limiti:100 appunti, titolo80 caratteri, testo8000, totale200.000 caratteri tra titoli e testi;20 metadati per pagina. Il contenuto non viene copiato nei log, export diagnostici, sorgenti Git o APK; l’app ha già allowBackup=false.

**Locale indica dove risiede l’archivio, non che i contenuti richiesti restino invisibili a ChatGPT.** Creazioni/aggiornamenti passano dal collegamento; le letture richieste restituiscono testo alla conversazione e possono restarvi secondo il normale funzionamento di ChatGPT. Il relay privato conserva temporaneamente richieste/risposte: scadenza logica2min dalla richiesta, rimozione alla successiva attività del dispositivo, nuova richiesta o consultazione dell’esito. Non c’è un timer di cancellazione fisica garantita in assenza di attività. Dopo la risposta vengono rimossi i parametri del comando. Restano metadati di deduplicazione senza testo per impedire replay; sul telefono ledger/outbox cifrati possono contenere la risposta richiesta, esclusi dalla diagnostica. Non salvare un segreto presumendo che una successiva lettura via ChatGPT resti esclusivamente sul telefono.

Test JVM verificano repository, revisioni, limiti, annullamento, scrittura fallita/rilettura e concorrenza con storage simulato. Test server verificano capability104, compatibilità103, schema/risultati, privacy e scadenza/replay. Non sostituiscono Android Keystore/AtomicFile sul Galaxy: collaudare creazione → lettura → aggiornamento, riavvio e lettura, poi disattivazione del consenso. Non sono stati letti o modificati appunti reali del telefono durante lo sviluppo.

Fonti primarie: [storage privato Android](https://developer.android.com/training/data-storage/app-specific), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [AtomicFile e responsabilità del chiamante per l’esclusione](https://developer.android.com/reference/android/util/AtomicFile), [Context.getNoBackupFilesDir](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()). Queste API documentano i meccanismi, non il successo sul telefono dell’utente.
