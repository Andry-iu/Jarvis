# Validazione 0.7.3 - 3 novembre 2026

## Stato prima dell'installazione
Candidati verificati su entrambi; pubblicazione/installazione e riscontro sui
processi operativi da completare. Base Git 6ff603aa9b2f6877c06ea51872edc0f878a7fc3e,
branch feature/agent-admin-control-20261103, repository Andry-iu/Jarvis.
Ambito approvato: lettura dei privilegi reali dell'agente e riavvio elevato su richiesta.

## Prove effettive Windows
- Desktop1: suite Go completa 13.348 s, go vet, build e test executable PASS.
- Desktop2: prima build fallita per internal compiler error in runtime/debug,
  prima del collaudo della nuova funzione. SDK ufficiale Go 1.27.0 e checksum
  verificati. Ripetizione con SDK/cache nuovamente isolati, GOMAXPROCS=1, GOGC=25
  e -p 1: suite completa 13.234 s, vet/build/test executable PASS.
  La causa precisa dell'ICE non e isolata; non attribuirla con certezza al codice,
  al compilatore, all'estrazione o al sistema. Nessuna toolchain permanente cambiata.
- Entrambi: scripts/test-agent-admin.ps1 PASS per stato token reale elevato e
  standard, no-op gia amministratore, compatibilita e ricevuta idempotente;
  rifiuto con diagnostica attiva; gate mutazioni durante l'attesa e processo
  originale ancora vivo prima dell'helper; passaggio reale standard -> helper
  elevato -> nuovo agente con stesso machine_id e token elevato; PID diverso;
  ripetizione ricevuta senza nuovo restart e contatori conservati.
- Unita Go: token ignoto, attivita incerta/attiva, cancellazione simulata,
  sblocco mutazioni, autenticazione/loopback e esclusione dati privati.
- Binario test dotato di TestMain usato esclusivamente nelle cartelle temporanee.
  La seam di lancio non e selezionabile nel binario di produzione.
- Pulizia di processi/cartelle delle suite PASS. Gli ambienti Go/cache/SDK sono
  temporanei e vengono rimossi dal batch; nessun uso di Projects per l'agente.
- Nessun turno Codex tramite Jarvis e nessuna modifica alle guardie economiche.

## Candidati definitivi
Su ciascun PC: D:\Jarvis\Releases\0.7.3\jarvis-mcp.exe.
Desktop1: a0a5ee1b95a8222b769b320165cf92a614ce58be7c0c004c37696c4c430d091b,
12299264 byte.
Desktop2: c77b43a851de409ceda18b712259a72fedcc07e2c00d80bec0f0c580437d66ca,
12333568 byte.
I binari hanno hash propri; non pretendere identita binaria tra ambienti di build.
Sorgenti Go non modificati dopo questi test/build.

## Limiti e protocollo
L'ack awaiting_elevation non e successo; consultare agent_restart_status.
L'UI UAC nativa non e stata automatizzata: il collaudo inietta soltanto il lancio
dell'helper gia elevato nel test executable, mentre token, handoff, uscita, nuovo
avvio e verifica sono reali. Non dichiarare collaudato il clic UAC/credenziali.
L'elevazione deve conservare lo stesso utente, profilo e configurazione.
Lavori attivi/accodati/incerti rifiutano lo stop; nessuna forzatura.
Nessun nuovo task/servizio o modifica alle policy UAC. Avvio diretto/supervisore
esistente preservati. Applicazioni normali standard per default.
Catalogo client diretto, reboot fisico e ciclo 24 ore non certificati.
La compatibilita task_start/runtime evita il refresh necessario per la sola consultazione.
Installer 1.3.1/agente 0.7.1 restano una release separata.
