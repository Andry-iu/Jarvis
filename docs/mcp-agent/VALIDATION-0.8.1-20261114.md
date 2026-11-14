# SUPERATO per il consenso grafico

Il difetto di visibilita del launcher Go e risolto dalla0.8.2. Questo verbale resta
storico; usare VALIDATION-0.8.2-20261114.md e il pacchetto0.8.2.

# Validazione finale0.8.1 — 14 novembre2026

## Esito verificato

Implementato, pubblicato, testato e installato su Desktop1 e Desktop2.
Riconnessione MCP osservata, identita originali e privilegi elevati preservati.
Nuovi begin/status/complete nel catalogo effettivo machine_info di entrambi.
Runtime sorgente aa2f2cbd454c8efe4df2dbbfefe9ed31d6e06733; pacchetto e updater
definitivi8ed7743900ee82529fcf40073fbe9e789ee4021e. Le modifiche successive al
runtime riguardano soltanto versione installer, collaudo e creazione log updater;
confronto Go e helper UI rispetto alla compilazione nativa verificato uguale.
Branch feature/agent-gateway-20261114; niente main/Android o force push.

## Prove concluse

- Linux Go1.27.0: suite completa, race, vet e cross-build Windows PASS.
- Windows Go1.27.0/cache isolata/GOMAXPROCS1/GOGC25/-p1: suite, vet e build
  PASS su entrambi. Suite Desktop1:19.338s; Desktop2:17.023s.
- Nuovi test: nessun controllo prima della conferma; rifiuto; TLS reale dopo
  abbinamento; persistenza/idempotenza; CAS; pin/identita/firma; scadenza; codice
  vincolato a tutte le identita e alla chiave. Credenziali assenti nelle risposte.
- Entrambi: due installazioni Windows isolate reali dal pacchetto definitivo,
  task Interactive/Highest, identita/chiavi distinte, nessuna richiesta Cloudflare
  locale, pairing compatibile e controllo, rifiuto sovrascrittura e pulizia PASS.
- Entrambi: AGGIORNA su installazione0.8.0 isolata reale ->0.8.1, tramite listener
  locale TLS autenticato, senza Cloudflare. Nome, identita, credenziali e task
  conservati. Backup verificato, rollback rehearsal e ricevuta canonica PASS.
- Parse PowerShell sul Windows reale del helper UI e script di installazione PASS.
  Il clic umano sul dialogo nativo non e stato simulato come collaudo fisico.
- ZIP consegnato: manifest27 file, hash/dimensioni/integrita verificati. Candidato
  cross-compilato dallo stesso runtime validato nativamente, non confondere gli
  hash diversi delle build. Dettagli CHECKSUMS-0.8.1.json.

## Aggiornamento operativo

Unico scripts/update-agent.ps1 via batch separato, sequenziale sui due gateway.
Entrambi BACKUP_VERIFIED, ROLLBACK_REHEARSAL_PASS e
PASS common_batch_update_identity_config_maintenance, poi machine_info remoto
0.8.1 con nuovi PID e identita corrette. Hash e backup in CHECKSUMS.
Desktop1 conserva root/eseguibile diretto; Desktop2 Agent e supervisore/tunnel
esistenti. Nessuna migrazione di configurazioni, stato, contatori o identita.
Sorgenti operativi nel worktree Gateway-0.8.0, candidati Releases/0.8.1 e pacchetto
nativo definitivo in Releases/0.8.1/Final.

## Diagnosi e limiti

Il primo test di aggiornamento locale ha provato l'assenza della cartella
Logs/diagnostics su installazione nuova; il vecchio binario era ancora quello
0.8.0, hash invariato. Il logger dell'updater falliva prima della sostituzione.
Corretto il motore comune creando la cartella prima del primo log.
Il collaudo usava Stop.ps1 senza Force in ambiente non interattivo; corretto,
ricevute preservate prima della pulizia e fixture residue identificate per
percorso/PID, arrestate e rimosse. Ripetizione finale PASS su entrambi.
Pubblicazione: dopo verifica del repository pubblico e permessi, push CLI
autorizzato ma senza credenziali locali; pubblicazione tramite connessione GitHub
esistente, fast-forward verificato e alberi identici. Nessun login aggiuntivo.

SoftPlcTest1 a192.168.1.101, identita e803423d08ab396b98262b3cf55df658, e ancora
0.8.0 non associato: l'utente deve eseguire AGGIORNA.cmd dal pacchetto0.8.1 e
indicare la cartella Jarvis esistente; poi richiesta da chat, confronto codice e
conferma locale. Nessun nuovo trust o collegamento creato per sola discovery.
Conferma UI fisica sul nuovo PC, reboot/login e nuove sottoreti non certificati.
Nessuna inferenza Codex, spesa o modifica alle guardie economiche.

## Chiusura

Pulizia verificata su entrambi: fixture e processi di prova assenti, directory
temporanee proprie e pacchetti preliminari rimossi; backup e ricevute permanenti
conservati. Configurazioni device_list invariate/vuote. Discovery finale /32 da
Desktop1 conferma SoftPlcTest1 ancora0.8.0 e non associato.
