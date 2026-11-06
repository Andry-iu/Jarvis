# Handoff cloud Jarvis — .118/140,7novembre2026

## Completamento cloud — 7 novembre 2026, Europe/Rome

Richiesta utente di scaricare Git, compilare e consegnare APK. Checkout pulito/fetch/fast-forward dal rilascio .117 al main1495b4fbc86211a720383b930a7afa2f71482750, toolchain/cache/firma Jarvis riusati. Mirati Android nuovi limiti/pagine/diagnostica e regressioni export passati. Il primo run completo ha rilevato solo una vecchia asserzione LocalNotesRepositoryTest che rifiutava4001: aggiornata a8001 coerentemente con8000 ammessi; nuovi NotesExtendedLimitsTest già coprono accettazione8000 e rifiuto8001. Nessuna correzione dei sorgenti di produzione necessaria.

Verifica finale654test/69suite SDK34/36 senza failure/error/skipped, lint55warning/0errori/fatal e assemble riusciti.5suite Node DOM/audio,57test backend e20test Python passati; guida --check33argomenti/13712parole e asset finali confrontati. Nessuna API Google reale, telefonata/messaggio o contatto reale usati come fixture.

Stesso backend owner-private aggiornato: source c922d76f1367dc4b673bba41699e24805984c8a6, deployment appgdep_6aee680c1fec81918560ddd9ed013d16 succeeded/has_mcp=true/env2, stessi pairing/segreti/risorse/audience. Guida reale riletta .118/140. Nessun nuovo plugin; cataloghi MCP già aperti possono ancora richiedere aggiornamento per offset/8000caratteri.

Unico APK Telefono .118/140 firmato col certificato permanente:90.019.583byte, SHA256 8a25e3be22debeca8e6a2ca5dce43d6d3b717881bcb0340673f8036ebc9d4acf. Versione/ABI/CRC ZIP/v2/v3/certificato e zipalign/3ELF16KB verificati, temporanei privati rimossi, checksum in releases/0.5.118/SHA256SUMS e binario/report fuori Git. Nessun APK intermedio1MiB, Watch o agente Windows. Verbale completo in VERIFICATION.md.

Restano i collaudi fisici e le diagnosi indicati sotto e nella roadmap: questa compilazione non ha avviato una nuova importazione, non ha popolato l’indice e non certifica la causa del precedente cancelled. Le sezioni successive conservano le evidenze e lo stato iniziale della preparazione, ora seguito dal completamento cloud qui sopra.

## Decisioni approvate e stato iniziale

L'utente ha dato via esplicito a preparare modifica, test, documentazione e commit; ha poi concordato ogni limite e confermato di continuare. Non richiedere nuovamente consenso allo stesso gruppo. La scelta256KiB sostituisce il precedente1MiB preparato ma mai rilasciato. Stessa versione candidata .118/140.

| Operazione | Limite concordato |
| --- | --- |
| Default richiesta HTTP |16KiB, JSON incluso |
| Export contatti/soli nomi |256KiB di JSON UTF-8 per pagina, totale8MiB |
| Envelope export/Base64 |357720byte; pagina fino a349528caratteri Base64 |
| Appunto singolo |8000caratteri; titolo80 |
| Richiesta appunti, completa |24KiB/24576byte, entrambi i limiti obbligatori |
| Elenchi appunti e preferiti |20voci per pagina, invariato |
| Ricerca contatti live |20risultati per pagina e continuazione |
| Testo Google TTS |4500byte UTF-8, invariato |
| WhatsApp |2000caratteri, invariato |
| Destinazione Maps |300caratteri, invariato |

Snapshot6144byte già esistenti conservati e riconosciuti; nessun nuovo export implicito per cambiare blocco. Ricerca con offset0 poi next_offset, stesso nome/dispositivo, snapshot RAM10min senza rinnovo e riferimenti stabili; process death/nuova ricerca invalida continuazione. Limite snapshot10000righe/8MiB, errore contacts_search_too_large senza successo troncato. I numeri completi restano solo sul telefono finché non richiesti attraverso il percorso dedicato; resolve revalida live prima di uso. Nessuna chiamata o invio autorizzati dalla sola selezione.

Appunti: LocalNotes/UI/server/poll/outbox allineati;24KiB non estesi agli altri endpoint. Si rifiuta il messaggio troppo grande, non si tronca il testo. Vecchie APK restano utilizzabili con testi<=4000 e prima pagina legacy; per nuovi limiti/continuazione serve140.

## Log per capire dove si ferma l'export

File native-runtime.log già incluso nello ZIP diagnostico. Categoria contact_export, cycle=request_id tecnico valido o -, t_ms monotono, stage/code/phase/offset/bytes/base64_chars/chunk_bytes/elapsed_ms/remaining_ms/reason/http. Nessun nome, numero, identificativo contatto, hash, JSON/Base64, credenziale o messaggio d'eccezione grezzo.

- polled: richiesta realmente letta dal telefono e lease residua; claim_begin/claimed: acquisizione e durata.
- started/progress/heartbeat: raccolta o pagina in esecuzione, fase corrente e rete del heartbeat.
- guard_rejected: generation_changed/link_disabled/copy_disabled/contact_permission/deadline/screen_locked. Primo motivo osservato, non deduzione da cancelled.
- result_ready/result_queued: risultato prodotto/persistito; send_begin/send_ack/send_deferred: consegna al relay e tempi, HTTP/timeout/DNS/TLS/network distinti.
- result_revoked: scadenza risultato, perdita permesso/consenso o blocco prima dell'invio; HTTP409 è consumo/scadenza, non ACK di consegna.
- worker_refresh/worker_stopped: ricreazione/arresto collegamento. Se l'ultimo evento è send_ack e manca un nuovo polled, il telefono attende una nuova richiesta; non attribuire senza prove il ritardo alla rete.

I log sono asincroni e bounded nel canale esistente; non cambiano timeout, priorità o uscite Voice/cover. ACK conferma solo il relay, non il salvataggio locale.

## Verifiche effettive qui

57test backend simulati e20Python passati; soglie export163483/262143/262144/262145/524288byte, vecchio6144, SHA256/schema/commit atomico/ripresa. Nuovi test backend coprono noteUnicode8000/8001,24KiB esatti/+1, altri endpoint ancora16KiB, vecchie APK,20contatti/next_offset e limiti. Nessuna API Google, chiamata o invio reale. Guida da JSON unico generata/check, sintassi JS e diff controllati.

Android preparati: PhoneContactExportTest aggiornato; ContactSearchPaginationTest (45righe20/20/5, refs, device); NotesExtendedLimitsTest (save/read/reopen8000,8001 conserva copia); ContactExportDiagnosticsTest (whitelist, motivi rete, nessun testo privato). NON eseguiti qui: riservati a cloud Jarvis, come lint/build/firma.

## Completamento nel cloud

1. Identificare il cloud Jarvis già configurato, leggere AGENTS/README/CONTINUITY/VERIFICATION e questo handoff, fetch main/fast-forward con working tree pulito. Riutilizzare toolchain/cache/firma, nessuna Actions/Desktop o nuova chiave.
2. Eseguire mirati nuovi test e suite Android SDK34/36, regressioni BridgeLinkWorkerIntegrationTest/BridgeLinkServiceTest/NativeExitState, lint/build. Comando di riferimento dell'ultimo rilascio: ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PjarvisUnsigned=true -Pkotlin.compiler.execution.strategy=in-process -PjarvisTestJava=/usr/lib/jvm/java-21-openjdk-amd64/bin/java -PjarvisTestSdks=34,36. Verificare i percorsi della toolchain effettiva prima di usarli.
3. Pubblicare stesso backend/Site/plugin privato prima di installare140, mantenendo pairing/segreti/audience/risorse. Ricaricare catalogo MCP per offset facoltativo e testo8000. Nessun plugin duplicato.
4. Firmare unico APK Telefono .118/140 col certificato permanente, verificare versione/ABI/ZIP/16KB/certificato, aggiornare checksum e risultati, push fast-forward verificato; niente APK o credenziali in Git.
5. Installare sopra .117/139 conservando dati. Collaudo reale: export nomi/numero selezionato,256KiB esatti/+1, pagine contatti oltre40 e omonimi, note8000/8001/24KiB, vecchi formati, blocco/revoca/rete/process death. Esportare ZIP e leggere nuovi log. La precedente copia1155nomi/163483byte è terminata cancelled e l'indice locale resta vuoto: causa specifica non provata, nessun nuovo export automatico. Non usare contatti reali come fixture.

Restano tutti i collaudi e gli sviluppi non completati della roadmap precedente, compresa diagnosi contacts_export_changed e prove .115/.116/.117. Questo commit prepara sorgenti e continuità; non certifica APK, deploy o successo Galaxy.
