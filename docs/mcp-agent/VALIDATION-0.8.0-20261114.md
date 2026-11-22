# Validazione finale 0.8.0 — 14 novembre 2026

## Esito

Implementato, testato, pubblicato e installato su Desktop1 e Desktop2. Agenti
0.8.0 riconnessi e verificati da ChatGPT via MCP, con identita precedenti e
privilegi amministrativi effettivi preservati. Listener LAN autenticato pronto
su entrambi, configurazione persistente iniziale vuota e letta dal client.
Nessun nuovo computer fisico ancora associato: i nuovi PC richiedono l'installer
e il primo abbinamento privato, poi si usano i nomi.

Sorgenti runtime definitivi: 07760a3eb212bb63f9919e2702a46f1f0d290338,
branch feature/agent-gateway-20261114, repository Andry-iu/Jarvis.
Nessun merge su main, modifica Android, force-push o pubblicazione di binari.
Sorgenti Windows in un worktree isolato D:\Jarvis\MCP-Source\Gateway-0.8.0;
checkout storico e relativi file non tracciati preservati.

## Prove effettivamente eseguite

- Linux/amd64 Go 1.27.0 ufficiale, checksum verificato: suite completa, race e vet PASS.
- Windows/amd64 Desktop1: suite completa 18.507 s, vet e build PASS.
- Windows/amd64 Desktop2: suite completa 15.948 s, vet e build PASS.
- Stesso profilo documentato su entrambi: Go1.27.0, cache isolata,
  GOMAXPROCS=1, GOGC=25, -p1. SDK di sistema Desktop2 non modificato.
- Trasporto TLS reale tra istanze, piu passaggi, identita/pin/cicli/hop limit,
  operazioni runtime e legacy, parametri operation legacy preservati,
  ricevute sul destinatario, dismissione, CAS e nomi attivi duplicati rifiutati.
- Abbinamento da file privato, nessuna chiave nella risposta, chiave utilizzabile
  subito senza restart del gateway, discovery limitata a indirizzi privati.
- Windows: update_prepare/execute/status attraverso due gateway, batch reale
  monouso con marker e risposta persa simulata; nessun replay dopo ricostruzione
  dello stato in memoria. Il marker non sostituisce il test dell'updater, sotto.
- Installer finale eseguito su ENTRAMBI i PC: due installazioni isolate reali,
  nomi Unicode, task Interactive/Highest registrati, nessun dato Cloudflare nel
  locale, identita/chiavi/certificati distinti, abbinamento e controllo MCP,
  rifiuto della reinstallazione su cartella non vuota, pulizia PASS.
- Artefatto consegnato: binario cross-compilato Windows/amd64 da Go1.27.0 con
  -trimpath -buildvcs=false, stesso sorgente runtime verificato nativamente sopra.
  Manifest di tutti i file e integrita ZIP verificati. Le build native hanno
  hash propri per i rispettivi ambienti; non pretendere identita binaria.

## Aggiornamento effettivo dei desktop

Unico scripts/update-agent.ps1, sempre avviato dal batch separato dell'agente.
Entrambi: BACKUP_VERIFIED, ROLLBACK_REHEARSAL_PASS e
PASS common_batch_update_identity_config_maintenance, seguiti da verifica MCP.
Desktop1 mantiene D:\Jarvis\jarvis-mcp.exe e avvio diretto; Desktop2 mantiene
D:\Jarvis\Agent\jarvis-mcp.exe e supervisore Scripts\Start.ps1. Configurazioni,
root, stato, contatori e identita conservati; nessuna migrazione implicita.

| Computer | SHA-256 installato finale | Byte |
|---|---|---:|
| Desktop1 | f0f6cab58cc411325094b890858507bc949becd132398036fe119dd8f80024d8 | 13296640 |
| Desktop2 | b4054379e1bcf84e3d2ad5551d9bc09478137a979d767a680746284796e37af6 | 13332992 |

Candidati e ricevute: D:\Jarvis\Releases\0.8.0-r2 su ciascun PC.
Backup finali: Desktop1 self-update-6cb0ae38dab64237a26de14668ff566e;
Desktop2 self-update-b62e362dc66f436d90562a1d0d0967e3.
Installer 2.0.0 e checksum: CHECKSUMS-0.8.0.json.

## Ripresa

Leggere GATEWAY-0.8.0.md e CLIENT-INSTRUCTIONS-0.8.0.md, poi appunti e identita
correnti dei gateway. Cataloghi client precedenti: task_start operation=runtime
con request completa; device_list tramite compatibilita verificato da ChatGPT.
Nuovi comandi diretti nel catalogo del client non certificati; non chiedere un
ricollegamento solo per questa differenza. Non reinviare aggiornamenti riusciti.

I log privati diagnostics/gateway-080-final-{go,build,installer,deploy}.log,
self-update.log e ricevute in Backups restano sui PC, non sono esportati in Git.
Nessuna inferenza Codex tramite Jarvis, API a pagamento o modifica delle guardie.

## Limiti e diagnosi risolte

Nessun reboot/login fisico eseguito: l'avvio automatico e registrato e verificato
come configurazione, distinto dalla prova di riavvio. Nessuna rete fisica nuova
a piu sottoreti, UI UAC dell'installer o nuovo tunnel/account Cloudflare certificati.
Il ramo locale dell'installer e provato; il diretto riusa la procedura Cloudflare
esistente, il cui esito remoto va verificato sul nuovo PC/account.

Il primo test installer si e fermato PRIMA dell'installazione per una variabile
PowerShell home riservata nel test: rinominata, collaudi ripresi e superati.
Durante l'ultima verifica e stato corretto l'inoltro dei tool legacy con un
proprio campo operation: formato operation+arguments, coperto da regressione.
Desktop1 aveva gia ricevuto il candidato preliminare; aggiornato poi al definitivo
con hash distinti e altro ciclo canonico. Non usare il candidato preliminare.

Desktop2: download SDK lento; archivio ufficiale completato e verificato con SHA
f0c0a0d33ba94f4d2c5dbc887334ce678b21813504ddb3aafcb06e60a5a667c4.
Fermato soltanto l'unpacker preliminare superato, dopo verifica di PID, parent,
creazione e assenza Go attivo; archivio verificato recuperato, estrazione .NET
senza progress e build finale completata. Non usare Go1.27.3 come scorciatoia.
I due errori interni iniziali del plugin Desktop2 sono scomparsi: causa ignota.

## Chiusura e pulizia verificata

Entrambi i worktree sorgenti puliti; cartelle temporanee di compilazione,
installer, riavvio e batch rimosse/verificate. Rimossi 7 batch usati Desktop1 e
6 Desktop2; i soli batch di chiusura seguono la manutenzione ordinaria 30 giorni.
Nessun listener/processo di collaudo mantenuto. Backup e ricevute permanenti
conservati. Candidato preliminare Desktop1 marcato SUPERATO, definitivo in r2.

Per evitare il nuovo download costoso, l'archivio SDK ufficiale verificato di
Desktop2 e conservato come cache in
D:\Jarvis\Tools\Cache\go1.27.0.windows-amd64.zip. SHA sopra; non e un SDK
installato e non cambia PATH/GOROOT. In futuro riusare questo archivio,
estrarlo in una cartella temporanea dedicata con ZipFile.ExtractToDirectory,
poi usare il profilo documentato e ripulire estrazione/cache di compilazione.
