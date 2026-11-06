# Handoff cloud Jarvis — .117 / 139

## Ambito approvato e stato

Il 6 novembre 2026 l’utente ha dato il via a: importazione del JSON dei soli nomi, ricerca locale dei nomi simili alla dettatura, interrogazione del telefono con il nome esatto scelto e salvataggio anche di un singolo numero nella rubrica locale. Ha poi chiesto commit completo di sorgenti, documentazione e stato; avvierà personalmente la compilazione nel cloud Jarvis.

Base sorgenti: main c1d2b44e766a5ed1f336071e125bd7bdc39f64cf (.116/138 verificata e rilasciata). Questa preparazione incrementa soltanto i sorgenti a .117/139. Nessun APK, firma, checksum nuovo, pubblicazione backend o importazione reale della .117 eseguiti qui. Nessuna dipendenza da file privati di questa chat: tutti i sorgenti e le procedure necessari sono nel repository.

## Implementazione

- `PhoneContactExport.collectNames`: un solo cursore Contacts, proiezione id/lookup_key/name; nessuna query Phone/Email o doppio confronto timestamp. Omonimi distinti e nomi vuoti conservati; limiti e consenso/revoca invariati. Non promette transazione globale Android.
- Tool `jarviswake_contacts_names_export_start` sullo stesso relay/plugin. APK139 minimo, parametri names_only fissati dal server, nessun fallback completo. Riusa job asincrono, heartbeat, pagine e hash; ready mode names richiede0numeri/0email e modalità coerente con il comando.
- `scripts/phonebook.py begin --names-only`: piano persistito con UUID e tool corretto, ripresa con lo stesso UUID. Schema names strettamente id/lookup_key/name; indice cifrato name_contacts separato dai dati dei numeri. Stato names/names_updated_at distinto da rubrica completa.
- `search-names --query`: fino20candidati grafici ordinati, normalizzazione accenti/maiuscole/punteggiatura e confronto ordine termini, score e data. Più candidati plausibili/omonimi/truncated richiedono scelta; la somiglianza non certifica identità. Poi find_contacts sul telefono con nome esatto scelto e riferimento corrente, senza enumerare nomi diversi.
- Importazione/salvataggio del singolo numero: riusa `begin --contact-ref` + plan/accept. Aggiornamento della sola riga scelta preservando altri numeri/email/contatti e indice nomi. Nessuna scrittura della rubrica Android, telefonata o invio implicito.
- Guida unica aggiornata in docs/guida-jarvis.json e proiezioni generate. Procedure: docs/RUBRICA_LOCALE.md. Requisiti e attività residue: AGENTS/README/CONTINUITY/PROMPT_CONTINUITA/ROADMAP/VERIFICATION aggiornati insieme.

## Verifiche già eseguite

17test Python,51test backend Node con soli dati fittizi; guida generata/coerente, controllo sintassi JavaScript e diff senza errori. Test Android aggiunti ma non eseguiti fuori dal cloud. Nessun uso Google STT/TTS o modifica Watch/Windows/Voice/cover.

## Prossimo passaggio richiesto dall’utente: compilazione cloud

1. Leggere AGENTS.md e stato corrente. Verificare identità/accessibilità effettiva del cloud Jarvis, checkout `/workspace/Jarvis` e cache `/workspace/.jarvis-cloud`; i riferimenti storici non provano disponibilità attuale.
2. Preservare eventuali modifiche altrui, aggiornare i riferimenti remoti e portare main al commit di preparazione presente su origin/main con avanzamento sicuro. Nessun force-push o nuova chiave.
3. Eseguire test Android SDK34/36, lint e suite consolidate indicate dal repository, più17test Python/51Node e controllo guida. Correggere eventuali errori di verifica nello stesso rilascio; non presentare i soli test interpretati come una build Android riuscita.
4. Compilare e firmare soltanto nel cloud Jarvis, con toolchain/cache e certificato permanente già configurati. Non chiedere password in chat, usare il meccanismo documentato. Verificare versione139/.117, ABI, ZIP, allineamento16KB e certificato; registrare checksum e risultati effettivi, senza APK o segreti in Git.
5. Per rendere disponibile il nuovo tool, pubblicare l’aggiornamento dello STESSO backend/Site/plugin privato secondo la procedura esistente: nessun nuovo pairing o plugin duplicato. La build APK da sola non aggiorna gli strumenti di ChatGPT. Le eventuali approvazioni richieste dal flusso di pubblicazione esistente rimangono distinte dal commit qui richiesto.
6. Aggiornare documentazione/verifiche reali e push fast-forward completo; verificare main remoto prima di consegnare l’APK direttamente.
7. Collaudo Galaxy: installazione sopra l’app, consenso copia e READ_CONTACTS, import soli nomi, contatori0numeri/0email, omonimi/vuoti, ricerca per nome dettato simile e query live con nome esatto, import di una singola voce conservando altre voci; revoca/lock/rete/lavoro lungo/ripresa. Nessuna riuscita fisica implicita nei test.

## Attività pendenti conservate

Diagnosi puntuale contacts_export_changed del vecchio export completo (nessuna attribuzione certa a modifiche reali della rubrica); prove fisiche .115/.116 di collegamento/mute/importazione asincrona; appunto regole non confermato e altri sviluppi già riportati nella roadmap. L’indice non implementa l’importazione automatica di tutta la rubrica un contatto alla volta né una sincronizzazione periodica. Conservare i requisiti di uscita immediata della cover e un solo gesto Voice.
