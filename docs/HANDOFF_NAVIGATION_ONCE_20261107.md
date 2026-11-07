# .121/143 — navigazione in una richiesta

Richiesta approvata: APK firmato da provare; una sola invocazione operativa navigate, con indirizzo completo o query. Nessun’altra richiesta di consenso per questo gruppo.

## Implementazione

NavigationDestination risolve solo etichette esatte: Casa / Indirizzo di casa, Indirizzo SCM Rimini, ecc. Snapshot locale unico e revalidazione della stessa nota/revisione al dispatch. Prefissi semplici «Casa è in …», «Indirizzo: …» o corpo indirizzo; contenuto multilinea/complesso non è interpretato. Casa e casa di mamma restano distinte; niente fuzzy matching o indirizzi in Git. Indirizzi con strada/civico e coordinate sono passati senza leggere note; altre query geografiche sono risolte da Maps. Un risultato mancante/ambiguo/invalido/cambiato blocca l’avvio e richiede chiarimento. Note dati, nessuna istruzione eseguita. Nessuna nuova UI/chiave/API esterna.

BridgeActions conserva sblocco, consenso azioni, gate Voice, politica cover/Android Auto; note lette solo con consenso. Intent ufficiale Google Maps, URI encoding e pacchetto fisso, un dispatch sotto il journal esistente. Nuovi esiti navigate code-only accettati dal backend, rifiutati per altre operazioni o con dati privati aggiunti. Tool richiede APK143: aggiornare app e backend nello stesso rilascio. Guida canonica rigenerata.

## Verifiche e prossimi passi

- Fuori cloud completati:89test Node backend passati/0failure, sync-guide --check, Worker ESM e git diff --check. Le suite Android preparate non sono state eseguite qui.
- Cloud Jarvis: suite Android SDK34/36 incluse nuove NavigationDestinationTest/BridgeNavigationTest e regressioni cover/uscite; lint/assemble; firma permanente/checksum/ZIP/ABI16KiB; verbale/push su main prima consegna.
- Pubblicare stesso Site privato e verificare catalogo/guida .121/143; preservare R2, pairing e runtime env. Nessuna nuova identità o rotazione.
- Galaxy: dopo aggiornamento provare casa, indirizzo esplicito e luogo; controllare itinerario realmente aperto e una sola richiesta. Niente chiamate/API Google reali durante test automatici.

Il trasporto NDJSON .120 non cambia. Restano collaudo reale streaming/gateway, latenza end-to-end/batteria e tutti gli altri residui della roadmap. Nessuna promessa di latenza≤5s o conferma navigazione da intent_dispatched. La telefonata richiesta durante il lavoro non appartiene alla patch: ultimo tentativo scaduto prima del dispatch e utente ha annullato ulteriori tentativi; non ritentare.
