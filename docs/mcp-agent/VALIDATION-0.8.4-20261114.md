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
| Catalogo client Desktop2 | Refresh tools dalle impostazioni ChatGPT; scheda strumenti osservata con agent_update, agent_update_status e device_call che descrive entrambe le modalita. Verifica separata dal catalogo server. |

## Distribuzione osservata

| Destinatario di collaudo | Risultato realmente acquisito |
|---|---|
| Desktop2 | 0.8.4 live, amministratore; stato completed alle 18:22:28 UTC. API e identita riconfermate alle 19:44 UTC. Audit finale completo PASS. |
| Target LAN tramite gateway | Compilazione sul destinatario del commit finale; updater completed alle 18:23:23 UTC, cleanup true. Lettura autenticata 0.8.4/elevato tramite gateway alle 18:23:55 UTC. |
| Desktop1 | Ricevuta locale finale completed alle 18:26:02 UTC con identita/versione/hash e cleanup verificati dal runner. Successive chiamate al connettore esterno falliscono con errore interno -32603; riconnessione esterna e audit finale aggiuntivo non completati. |

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

Appunti Desktop2 aggiornati e riletti; appunti Desktop1 e target da aggiornare
quando il canale torna disponibile. Anche refresh del catalogo client Desktop1
e audit finale attraverso quel gateway restano pendenti. Non dichiarare chiuso
l'intero rollout: conservare le richieste originali e leggere gli stati, senza
rilanciare aggiornamenti gia completati.

Non certificati reboot fisico, ciclo reale di manutenzione di 24 ore o nuovo
consenso umano di pairing. Nessun merge su main o esportazione di log/credenziali.
