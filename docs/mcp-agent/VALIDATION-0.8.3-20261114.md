# Validazione 0.8.3 — 14 novembre 2026

## Implementazione e pubblicazione

Codice da digitare sul nuovo PC, nessun valore atteso nei controlli visibili,
doppia verifica UI/Go, rifiuto input vuoto/errato/marcatore ALLOW e contesti scaduti.
Conto alla rovescia da 1:50, rosso negli ultimi 20 secondi; tre errori chiudono.
Versione unica sorgenti/agente/installer; AGGIORNA ammette 0.8.0, 0.8.1 e 0.8.2.
Sorgente runtime pubblicato 50edce2c868664fb9ad2c040746284642a0e555c su
feature/pairing-code-required-20261114, base remota 4215ffd. Albero remoto
fffa82cf930c5638c9927cbdab32e8efe49a5a5e identico al commit locale collaudato.
Istruzione finale dell'installer corretta in 2a7ae7613f1ea3603976cd2eec6f314c882cd082;
nessun cambiamento Go/GUI dopo la suite nativa. Documentazione di stato aggiornata
in seguito, senza cambiare il runtime.

## Prove effettive

- Linux Go 1.27.0: suite completa, race e vet PASS; cross-build Windows e test
  executable PASS. Casi: codice esatto, maiuscole/minuscole/separatori, vuoto,
  errato, incompleto, Unicode simile/invisibile, chatter, ALLOW, DENY, oversized,
  valore atteso assente, cancellazione e contesto scaduto. Binding dei codici,
  consenso richiesto, TLS/identita/replay/CAS e credenziali private restano testati.
- Desktop1: suite completa Windows 19.509s, vet e build PASS.
- Desktop2: suite completa Windows 28.351s, vet e build PASS.
- Entrambi: Go ufficiale 1.27.0, cache isolata, GOMAXPROCS=1/GOGC=25/-p1,
  JARVIS_TEST_PAIRING_UI=1. Launcher Go reale, non launcher sostitutivo:
  finestra visibile; valore atteso non presente nei testi dei controlli;
  input vuoto non abilita Consenti; countdown letto via API Windows e avanzato;
  tre codici errati nella sola finestra dummy terminano con DENY. Nessun peer,
  consenso reale o trust sintetizzato. Risposta corretta testata nelle funzioni
  isolate; non attribuire alla prova un nuovo abbinamento umano reale.
- Entrambi: parse PowerShell GUI e installer PASS; due installazioni isolate,
  identita/credenziali distinte, controllo autenticato, rifiuto sovrascrittura e
  pulizia PASS. Lo scambio di test usa fixture, non approvazione umana automatica.
- Entrambi: aggiornamento isolato reale 0.8.2->0.8.3, identita/credenziali/nome/task
  conservati, BACKUP_VERIFIED, ROLLBACK_REHEARSAL_PASS,
  PASS common_batch_update_identity_config_maintenance e cleanup PASS.
- ZIP consegnato: integrita, manifest, tutti gli hash/dimensioni payload e nuova
  istruzione di inserimento verificati; costruito dal commit 2a7ae76 con identici
  sorgenti Go/GUI al runtime nativamente collaudato. Hash candidati distinti per
  ambiente/build, registrati in CHECKSUMS-0.8.3.json. Nessun binario o ZIP in Git.

## Aggiornamenti operativi verificati

Aggiornamento sequenziale con unico updater canonico e batch separato; nessun
nuovo task di aggiornamento o cambio di layout. Desktop1 conserva eseguibile
storico nella root, Desktop2 Agent e supervisore. Entrambi backup verificato,
rollback rehearsal, hash installato uguale al candidato, identita/configurazioni/
manutenzione PASS. Riconnessione machine_info 0.8.3 con identita attese e PID nuovi
osservata. Device_list Desktop1 conserva il destinatario LAN online; collegamento
persistente esistente conservato, nessuna nuova richiesta di abbinamento.
Backup e hash in CHECKSUMS-0.8.3.json. Log privati pair083-{build-native,
validation,installer,update-installer,deploy}.log e self-update.log.

## Limiti e diagnosi

Non eseguiti reboot/login fisico, scadenza reale di 110 secondi osservata a video,
nuovo consenso umano con codice corretto o aggiornamento del destinatario LAN.
Scadenza del contesto verificata in test e controllo del deadline presente nel
click/output; non confonderli con il trascorrere del timer completo sul PC.
Un destinatario ancora 0.8.2 conserva il confronto a vista: aggiornare il target
per la nuova UI, senza ripetere abbinamenti gia salvati.

Il codice deriva dal transcript pubblico: verifica di corrispondenza e contrasto
al clic distratto, non password segreta o secondo fattore. Restano le protezioni
TLS, firme e cifratura. Nessuna automazione di un consenso reale.

Il primo push shell e stato bloccato dalla revisione automatica per mancato
riconoscimento dell'autorizzazione. Rilette AGENTS/RELEASE_POLICY preesistenti,
autorizzazione permanente confermata; il nuovo tentativo shell ha superato la
revisione ma fallito per assenza di credenziali CLI. Pubblicazione tramite il
connettore GitHub gia autenticato: alberi identici, ref remoto verificato,
nessun force push. Un primo device_call diagnostico aveva schema errato e ha
restituito INVALID_ARGUMENT senza mutazioni; corretto al formato operation.
