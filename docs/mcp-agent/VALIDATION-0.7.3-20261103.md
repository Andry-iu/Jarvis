# Validazione 0.7.3 - 3 novembre 2026

## Stato finale
0.7.3 installata e collaudata su entrambi, branch
feature/agent-admin-control-20261103, repository Andry-iu/Jarvis.
Ambito approvato: lettura dei privilegi reali e riavvio elevato su richiesta.
Sorgenti pubblicati: b00e7e6d4fffd93e127c460408e274176e87ee9d, parent 6ff603a.
Albero 3af32ba70532f0cb5162027131f40598840107af identico al commit locale
b6a26da5e91f6e55aab003e94cb378c252a2f8d2 collaudato. I diversi SHA derivano dai
metadata di commit. Il commit locale e conservato in un branch backup Desktop1.

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
## Installazione e verifiche live
- Updater canonico scripts/update-agent.ps1 via batch separato, sequenziale.
- Desktop1: backup self-update-1c62eb8fb31a4710b0afe16054ce4e46.
- Desktop2: backup self-update-0614d81c440944f187bc47261b9ee018.
- Entrambi: backup verificato, rollback rehearsal PASS, hash installato uguale
  al proprio candidato, PASS common_batch_update_identity_config_maintenance.
- Avvio diretto Desktop1 e supervisore Start.ps1 Desktop2 preservati.
- machine_info e agent_status live: versione 0.7.3, token elevated=true,
  PID 187600 Desktop1 e 9332 Desktop2 alle 20:06-20:08 UTC del 3 novembre.
- agent_restart_admin via task_start/runtime: already_elevated, restarted=false,
  PID invariato su entrambi. Nessuna demozione artificiale dei processi operativi.
- tool_usage conta la nuova chiamata e conserva started_at preesistente:
  Desktop1 2026-11-03T17:53:58.4575006Z, Desktop2 18:12:52.4840496Z.
- agent_status e diretto: un tentativo nel wrapper runtime ha restituito unknown
  runtime operation; corretto usando lo strumento esistente, senza ricollegamenti.

## Problemi operativi risolti
Il push CLI Desktop1 e rimasto in attesa nel gestore credenziali Git locale.
Diagnosi con PID/nome/creazione/parent; annullata soltanto quella catena Git,
non l'agente. Nessun rifiuto di autorizzazione, nessun login nuovo richiesto.
Pubblicazione con il collegamento GitHub gia attivo: blob del commit locale,
confronto esatto dell'albero, ref remoto verificato. Fetch in lettura riuscito;
checkout allineati con reset --soft solo dopo confronto degli alberi identici,
indice verificato e backup del commit locale. Nessun force-push/reset hard/clean.
Gli script di allineamento hanno richiesto correzioni: nome funzione Git in
conflitto con l'eseguibile; stderr informativo trattato come errore PowerShell;
percorso CHANGELOG relativo errato. Correzioni limitate agli script di supporto,
poi confronto alberi e checkout puliti PASS. Non confondere questi errori con
problemi dell'agente o mancanza di autenticazione. Storico non tracciato preservato.
Riscontri privati: diagnostics/admin073-release-complete.log, self-update.log,
admin073-integration.log (Desktop1), admin073-serial-build.log (Desktop2).
