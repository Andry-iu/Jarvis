0.5.121 / 143 — CAUSA ANDROID RECUPERATA E CORRETTA; VERIFICA CLOUD DA COMPLETARE (7 novembre 2026)

Il recupero autonomo ha pubblicato il report tecnico releases/cloud-diagnostics/ec528b06-3d5c-4365-844a-c0ab5b54811a.json su main41a55fc: due unresolved_reference in BridgeNavigationTest.kt righe38/82, task compileDebugUnitTestKotlin. Causa accertata: nuovi test usavano setIsKeyguardLocked, inesistente nello shadow Robolectric. Corretto in setKeyguardLocked come nelle suite esistenti FavoriteOperationsTest/PhoneContactExportTest. Nessun errore nei test Android eseguiti è stato dedotto: il compilatore si era fermato prima di eseguirli.

Rimossa la richiesta diagnostica temporanea; raccoglitore e27test interpretati passati conservati, hook preBuild disattivo se non esiste diagnostic-request.json. Nessun log integrale o valore privato pubblicato. Job diagnostico f405a463-cbb6-411d-bbb7-93da2675c1c2 sul vecchio sorgente a44061a ancora da seguire fino a termine; non modificarne l’esito. Avviare/verificare build firmata sul nuovo sorgente corretto, nello stesso cloud Jarvis. Backend precedente34 resta live finché APK143 è verificato; ripubblicare poi la versione Site35 .121 già salvata. Firma/collaudo/consegna .121 non ancora completati; tutti i residui roadmap conservati.

STATO PRECEDENTE CONSERVATO

0.5.121 / 143 — BUILD CLOUD FALLITA NELLA FASE ANDROID; NESSUN APK (7 novembre 2026, Europe/Rome)

Sorgenti/test/documentazione del gruppo navigazione in una richiesta pubblicati su main nel commit 60a510a624a66df2304775bf757019fc40b9f7b3.89test backend passati/0failure, guida e Worker ESM verificati. Job cloud ec528b06-3d5c-4365-844a-c0ab5b54811a realmente preso in carico sul medesimo commit: controlli interpretati completati, fase Android terminata failed/android_failed. Il bridge non espone il dettaglio del log; non è accertato se il fallimento riguardi compilazione, test o lint. Nessuna firma, nessun APK, checksum o collaudo Galaxy della .121 completati. La frase vocale «è pronta» era prematura e non corrisponde al risultato.

Log privato nel cloud Jarvis: /workspace/.jarvis-cloud/cloud-runner/logs/ec528b06-3d5c-4365-844a-c0ab5b54811a/build.log. Worktree: /workspace/.jarvis-cloud/builds/ec528b06-3d5c-4365-844a-c0ab5b54811a. Occorre leggere l’errore lì, correggerne la causa e completare le verifiche nel medesimo cloud. Nessun riavvio automatico dello stesso job; esito terminale immutabile. L’utente ha confermato l’autorizzazione permanente al lavoro nel cloud, inclusi lettura/raccolta log e correzioni: regole generali v40 e appunto consenso sul telefono revisione3 salvati. Nessun nuovo consenso da richiedere; resta il limite tecnico di accesso al log da questa chat. Non ricostruire toolchain altrove.

Backend .121 pubblicato temporaneamente come versione Site35 (sorgente8550e2720338919f63be970c1111eaa0d6aaf81c); ripristino della versione Site34 precedente riuscito (deploy appgdep_6aef4c1c38f48191ac4e1e2268538078, succeeded/has_mcp/env4), per conservare navigate sulle app142 e precedenti finché APK143 non è verificato. Stesso Site privato, pairing/R2/segreti conservati; sorgenti .121 restano in Git. Ultima release APK verificata resta .120/142; non consegnarla come se contenesse la modifica .121. Continuano tutti i residui della roadmap.

STATO PRECEDENTE CONSERVATO

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
