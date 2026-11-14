# Validazione 0.8.4 — 14 novembre 2026

Runtime pubblicato: `ff2038d4b250c9d3e7e6590c2409ad55e5081380`, ramo
`feature/unified-agent-source-update-20261114`. Sorgenti, agente e installer
condividono la versione 0.8.4. Il coordinatore e la chat; il destinatario scarica
il commit esatto, testa/compila e si aggiorna con lo stesso runner a ogni livello.
La modalita pacchetto resta disponibile se il destinatario non raggiunge Git.

## Prove eseguite

| Ambito | Esito e limite |
|---|---|
| Suite Linux Go1.27.0, race, vet e cross-build Windows | PASS. Test di comando diretto e quattro livelli TLS reali in isolamento; non quattro PC fisici. Solo il target crea lo stato dell'aggiornamento. |
| Ricevute e guardie | PASS: deduplicazione dopo risposta persa/ricostruzione, ID con argomenti diversi rifiutato, busy/non-admin/commit errato rifiutati, completamento senza hash live rifiutato, quoting PowerShell e record legacy UTF-8 BOM. |
| Windows PowerShell e modalita Git non raggiungibile | Parse PASS. Fixture con agente reale su loopback e sola richiesta Git simulata: git_unreachable, agente/hash originali conservati, cleanup e rifiuto del replay PASS. |
| Installer e updater su Windows | Due installazioni isolate, identita/credenziali distinte, pairing e controllo autenticati, rifiuto reinstallazione e cleanup PASS. Upgrade isolato 0.8.3 verso candidato 0.8.4: backup/rehearsal, identita/configurazione/task/hash PASS. Fixture eseguite sul candidato nativo del commit 1d9d9e9; non sul binario cross finale. |
| Build native del rilascio operativo | Test completi, vet e build locali superati durante i deploy. Sul target inizialmente privo di Git/Go riuscita la preparazione ufficiale gestita; suite finale Windows 33.402s. |
| Desktop2, API nuova | Aggiornamento realmente avviato da agent_update sul commit finale, poi agent_update_status completed; lettura ripetuta senza nuovo deploy. Audit finale di identita/hash/versione, elevazione, manutenzione 24h, ambiente ripristinato, cleanup e catalogo server PASS. |
| Cataloghi client Desktop1 e Desktop2 | Refresh tools dalle impostazioni ChatGPT; entrambe le schede strumenti osservate con agent_update, agent_update_status e device_call che descrive entrambe le modalita. Verifica separata dal catalogo server e dalla cache della chat gia avviata. |
| Audit finali Desktop1 e target | PASS dopo riconnessione: hash/eseguibile/identita/versione/ricevuta, elevazione, ambiente ripristinato, zero checkout temporanei propri, probe chiusi, manutenzione24h e catalogo server. Nuova API agent_update_status realmente inoltrata al target. |

## Distribuzione osservata

| Destinatario di collaudo | Risultato realmente acquisito |
|---|---|
| Desktop2 | 0.8.4 live, amministratore; stato completed alle 18:22:28 UTC. API e identita riconfermate alle 20:02 UTC. Audit finale completo PASS. |
| Target LAN tramite gateway | Compilazione sul destinatario del commit finale; updater completed alle 18:23:23 UTC, cleanup true. Lettura autenticata 0.8.4/elevato tramite gateway riconfermata alle 20:02 UTC, con agent_update_status completed e audit finale PASS. |
| Desktop1 | Ricevuta locale finale completed alle 18:26:02 UTC con identita/versione/hash e cleanup verificati dal runner. Dopo indisponibilita temporanea -32603, riconnessione autenticata alle 20:02 UTC: 0.8.4/elevato, identita e stato completed riconfermati, audit finale PASS. |

I nomi identificano soltanto i PC del collaudo; non compaiono nel contratto di
instradamento. Hash e provenienza: [CHECKSUMS-0.8.4.json](CHECKSUMS-0.8.4.json).
ID richieste, backup e ricevute integrali restano negli appunti/log privati.

## Installer consegnato

Jarvis-Agent-Setup-0.8.4.zip costruito da checkout pulito del commit finale con
Go1.27.0, metadati vcs.modified=false. ZIP integro, 28 file del payload verificati
per hash/dimensioni/versione/provenienza, incluso update-from-git.ps1.
Il candidato Windows cross finale non e stato eseguito nella fixture Windows:
le prove native finali riguardano le build locali effettivamente installate.

## Problemi corretti e limiti aperti

Corrette la scrittura atomica File.Replace con backup esplicito, l'uscita del
bootstrap PowerShell, la compatibilita BOM degli stati e il ripristino di
TEMP/TMP/PATH prima del riavvio. Il test di avvio applicazioni ora verifica il
rifiuto sicuro quando il token della shell e elevato: nessuna guardia runtime
allentata. [Problemi e procedure riuscite](KNOWN-BUILD-UPDATE-PROBLEMS.md).

Il tentativo di rinnovo accesso a Desktop1 si e fermato sul login Cloudflare con
HTTP403. Non dimostra password errata, causa del guasto o difetto dell'updater;
nessun successo di autenticazione attribuito al tentativo. Nessuna nuova build
o ripetizione del deploy per risolvere il collegamento.

Alla ripresa delle 20:02 UTC Desktop1 e tornato raggiungibile; causa e passaggio
decisivo non accertati. Nessun rinnovo credenziali, nuova build o replay eseguito.
Completati audit finali Desktop1/target e lettura dello stato del target attraverso
la nuova API inoltrata. Refresh client Desktop1 osservato, come gia Desktop2.
Appunti di tutti e tre i PC aggiornati con hash atteso, backup e rilettura esatta:
storico conservato. Chiusura del rollout verificata; conservare le richieste
originali e non rilanciare aggiornamenti gia completati.

Non certificati reboot fisico, ciclo reale di manutenzione di 24 ore o nuovo
consenso umano di pairing. Nessun merge su main o esportazione di log/credenziali.
