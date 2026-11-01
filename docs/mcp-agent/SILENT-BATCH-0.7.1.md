# Verifica finale 0.7.1 — installata su entrambi i computer

1 novembre 2026. Desktop1 DESKTOP-1ESSB0K e Desktop2 AUTECHVPC2 sono stati aggiornati e riavviati tramite i rispettivi batch e lo stesso updater canonico. machine_info da ChatGPT conferma 0.7.1 e le identita precedenti. Entrambi i self-update.log riportano BACKUP_VERIFIED, ROLLBACK_REHEARSAL_PASS e PASS common_batch_update_identity_config_maintenance. Una prova avviata dai due agenti installati conferma console_visible=false ed elevated=true per il processo PowerShell discendente.

Sorgente runtime a8b0bca1901d96de9604388bbec5ec9b8aecc847; installer 1.3.1 nel commit b858e546c524982a3bc255fe92231aae6c7f9287. Desktop1 SHA-256 aa3625aed0d3d008a615ed0e5666d820b69bd6abdb86cb44fe4b55f596ebde00; Desktop2 SHA-256 83f6ff40c96d53c9930230737108f49c95f02c2f655576dd674aa7e5eb9eed27. La differenza deriva dalle build locali gia registrate (Go 1.27.0/1.27.3; Desktop2 standard dopo errore interno nel tentativo -trimpath). Updater identico dopo normalizzazione CRLF/LF; nessuna migrazione di percorsi o privilegi applicativi.

Backup finali: Desktop1 D:\\Jarvis\\Backups\\self-update-948d80db4d8c436da61506cb99b95432; Desktop2 D:\\Jarvis\\Backups\\self-update-e0d051ba9786415c9f69c0baa2c786b5. Configurazioni durevoli e manutenzione verificate dall'updater. Le sezioni seguenti documentano gli stati precedenti e non descrivono piu lo stato operativo corrente. Resta esclusa una prova di reboot fisico, un ciclo reale di 24 ore e programmi che richiedano esplicitamente una nuova console; gli errori restano consultabili tramite log e ricevute, senza apertura automatica di una console.

# Agente 0.7.1 — batch senza finestre

1 novembre 2026. Richiesta utente: eliminare le finestre nere dei batch, aggiornare entrambi gli agenti e l'installer, pubblicando tutto sul branch reconcile-windows-mcp-agent-20261031.

Il lanciatore CMD usa CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW e HideWindow; DETACHED_PROCESS e CREATE_NEW_CONSOLE sono esclusi. Il token dell'agente, inclusa l'elevazione, resta ereditato. L'updater canonico e il supervisore hanno gia CreateNoWindow; nessun percorso alternativo di aggiornamento, task o cambio dei privilegi applicativi.

Verificato: go test -count=1 ./... su Desktop1, job 608603245d56acce3ac43994508eac60, exit 0. La nuova integrazione esegue CMD -> PowerShell -> helper nativo dopo l'uscita del lanciatore, controllando console non visibile e token invariato. Verifica flag separata impedisce la combinazione NO_WINDOW/DETACHED che Windows ignorerebbe.

Stato di questo commit: codice testato; vet/build, installer 1.3.1, backup/rollback, distribuzione e collaudo MCP sui due computer ancora da completare. Il verbale sara aggiornato con gli esiti effettivi. Nessun reboot fisico o prova di programmi che richiedano esplicitamente una nuova console.
