# .92 / 114 — guida saluto nativo

Solo guida aggiornata al riconoscimento del controllo Invia affiancato o sotto il compositore nell’app. Tool, parametri, consensi, abbinamenti, dati e trasporto .91 invariati. Nessuna azione reale sul telefono eseguita in cloud.

# .91 / 113 — Preferiti remoti autorizzati

Sei tool aggiunti per i soli Preferiti JarvisWake: list/add/create/rename/change_number/delete. Consenso dedicato sul telefono; mutazioni con revisioni, parametri privati120s e stesso UUID/claim/journal. Nessuna scrittura Android Contacts. Risultati metadata con ultime4cifre, conflitto rubrica e cache_status, mai numero completo. Solo create/change ammettono numero esplicito dell’utente. Nuova guida20argomenti e menu ⋮. Stesso progetto/plugin/audience/pairing/segreti. Le limitazioni numero/elenco locale delle .89/.90 sotto sono storiche, superate solo per questi sei strumenti; i comandi di chiamata continuano a richiedere risoluzione locale.

# .90 / 112 — percorso guida

Guida aggiornata: Home → Preferiti e cache contatti, a destra di Appunti locali nella prima riga. Nessuna modifica dei tool, trasporto, consensi o abbinamenti.

# .89 / 111 — Preferiti locali separati

Stesse operazioni/pairing/audience. Ricerca e chiamata risolvono anche Preferiti locali scelti dalla rubrica o creati dall’utente con nome/numero manuali. Il comando remoto continua a contenere soltanto nome/contact_ref: nessun numero arbitrario o elenco completo dei Preferiti. Fonti e revisioni revalidate sul telefono, omonimi richiedono scelta. Cache20 indipendente, Preferiti persistenti fino a100.

# .88 / 110 — chiamata unica per nome

Nuovo jarviswake_call_by_name richiede110: nome esplicito, risoluzione sul telefono/cache e rubrica attuale, nessuna scelta in ambiguità. contacts_found significa nessuna chiamata: chiarire e usare call_contact. Stessi UUID/claim/journal/pairing/audience. Poll200ms durante Voice/burst, non garanzia200ms complessivi. Numeri completi/cache restano sul telefono.

# .87 — WhatsApp QR sul telefono

Stesso Site/plugin/abbinamento: WhatsApp richiede APK109 e usa un client collegato sul telefono, senza UI. Risultati whatsapp_not_linked, whatsapp_connection_unavailable e whatsapp_server_accepted; l’ultimo prova soltanto accettazione server. Vecchi client devono aggiornarsi, nessun fallback silenzioso ai tasti. Non transitano QR o chiavi sessione nel Worker. UI di collegamento nell’app Android. Codice/actions Maps e telefonate conservano stessi strumenti; nuova gestione cover e arresto lato telefono. Guida unica aggiornata.

# Guida .86 / 108

Guida aggiornata sullo stesso plugin per Appunti primo, refresh automatico/manuale dell’elenco, Aggiorna/Nuovo nella prima riga, aiuto contestuale modale dal ? e saluto multilinea. Operazioni telefono, pairing, consensi e protocolli restano invariati; nessuna nuova azione remota o cancellazione nel plugin.

# Guida .85 / 107

Stesso plugin/origine/abbinamento. La guida descrive pulsanti a coppie con Indietro largo in fondo e cancellazione locale degli appunti con conferma. Tool note invariati: elenco, lettura, salvataggio; nessun delete remoto. Una cancellazione locale non rimuove copie già presenti nelle conversazioni. Fonte canonica docs/guida-jarvis.json, rigenerata con sync-guide.py.

# Aggiornamento guida .84

Stesso jarviswake_guide, stessa origine e stessi abbinamenti: solo contenuto/versione della guida aggiornati con Indietro ultimo nell’elenco e indicazioni su correzione avvio/stato ascolto.19 argomenti/0.5.84/106. Nessuna modifica alle operazioni telefono o ai loro esiti;27 test server passati.

# Guida interrogabile .83

Nuovo jarviswake_guide: tool read-only autenticato sullo stesso plugin, indice/argomento/ricerca domanda, nessun telefono/pairing necessario. Guida canonica docs/guida-jarvis.json nel repo Android, generata in asset app/Markdown e blocco statico Worker con scripts/sync-guide.py.19 argomenti, versione dichiarata0.5.83/105; non rappresenta stati reali né autorizza azioni. Ricerca lessicale normalizzata, due sezioni al massimo, nessun match esplicito.27 test server includono guida senza pairing, query e argomenti reali, input errati e nessuna scrittura storage. Le quattro azioni, appunti e abbinamenti precedenti sono conservati.

# Aggiornamento .82 — appunti locali

Tre tool aggiunti sullo stesso Site/plugin: list_notes, read_note, save_note (capability APK104; azioni103 e status102 compatibili). Consenso appunti separato e archivio autorevole cifrato sul telefono, nessun calendario. Salvataggio con revisione e rilettura, claim/UUID/fingerprint invariati. Il relay trasporta i contenuti richiesti: scadenza logica2min per gli scambi note, pulizia alla successiva attività/poll/lettura/nuova richiesta, non timer fisico garantito. Dopo risposta eliminare i parametri; tombstone senza testo per deduplicazione. Test server24 casi; protocollo/tool descriptions distinguono dati locali da copie nella conversazione e non promettono lettura universale. Vedi docs/APPUNTI_LOCALI.md nel repository Android.

Deduplicazione .81: un tombstone con solo fingerprint rimane anche dopo la pulizia dei vecchi parametri/risultati, così un vecchio request_id non diventa una nuova chiamata/invio.

# Aggiornamento0.5.81 / protocollo0.2

Stessa identità Site/App/plugin e stessi abbinamenti. Nuove operazioni: find_contacts, navigate (anche cambio destinazione), call_contact, whatsapp. Le azioni usano un request_id UUIDv4 fornito dal chiamante e conservato ai retry, fingerprint degli argomenti, creazione e claim condizionali R2, consumo persistito sul telefono, risultato/outbox cifrati. Mai ripetere con nuovo ID dopo esito incerto senza una nuova richiesta esplicita dell’utente.

Le query ai dispositivi .80 ricevono ancora solo status. Le nuove operazioni richiedono capability dichiarata da APK103 e attivazione/permessi sul telefono. Contatti: massimo8 nomi/etichette/suffissi e riferimenti locali temporanei, nessuna rubrica intera o numero completo in risposta. Nessun risultato dichiara consegna WhatsApp, risposta del chiamato o navigazione visibile dal solo dispatch. Parametri del comando eliminati dal record server dopo il risultato; ulteriori record vecchi vengono puliti opportunisticamente. Sedici test server simulati passati; collaudo delle nuove azioni sul Galaxy/Android Auto ancora richiesto.

Il primo giro status .80 è riuscito realmente nella conversazione: risposta paired_device corrispondente alla richiesta, app.80/code102, servizio e cattura wake verificati, ciclo IDLE e cover assente. Non è prova delle nuove azioni o di una sessione Voice in background.

# JarvisWake Link — server della prova di stato

Sorgente canonico del servizio privato usato dalla 0.5.80; istruzioni complete in [COLLEGAMENTO_CHATGPT](../docs/COLLEGAMENTO_CHATGPT.md).

Worker ESM senza dipendenze: `node --test test/*.test.mjs`, `bash scripts/build.sh`, `node scripts/validate-artifact.mjs`. Il build deriva dal template Worker ESM Sites letto tramite skill. I helper di workflow/setup del plugin non sono montati nel filesystem cloud e le loro risorse non erano leggibili: usati template Worker ESM disponibile, Git con credenziale passata solo in memoria e operazioni native Sites. Nessun cambio di hosting o audience.

R2 binding BUCKET; segreto runtime SITE_DEVICE_ACCESS tramite Sites. Nessuna credenziale nel repository. Pubblicazione privata del1nov2026: Site appgprj_6ae75ae6cdd08191ab126c1d392a603a, sorgenti Site cf06d19d54768dc6fe6f9bf4af0b1d46fa30d9e2, deployment appgdep_6ae75d98fa48819194d0a52e59bb55cb succeeded/has_mcp=true/revisione ambiente1. Plugin provisionato plugin_asdk_app_sites_20b3e95cbcfc8191b4b5c0f04aee3679; installazione e disponibilità strumenti da verificare nella chat.

Nove test simulati verificano discovery limitata, identità e origine dell’abbinamento, giro richiesta/risposta, schema chiuso senza audio/testo chat, altro utente/token/azione rifiutati, scadenza, risposta duplicata, coda limitata e revoca. Non sono collaudi sul telefono né chiamate alla produzione con stati finti.
