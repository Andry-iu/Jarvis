# Agente 0.7.1 — batch senza finestre

1 novembre 2026. Richiesta utente: eliminare le finestre nere dei batch, aggiornare entrambi gli agenti e l'installer, pubblicando tutto sul branch reconcile-windows-mcp-agent-20261031.

Il lanciatore CMD usa CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW e HideWindow; DETACHED_PROCESS e CREATE_NEW_CONSOLE sono esclusi. Il token dell'agente, inclusa l'elevazione, resta ereditato. L'updater canonico e il supervisore hanno gia CreateNoWindow; nessun percorso alternativo di aggiornamento, task o cambio dei privilegi applicativi.

Verificato: go test -count=1 ./... su Desktop1, job 608603245d56acce3ac43994508eac60, exit 0. La nuova integrazione esegue CMD -> PowerShell -> helper nativo dopo l'uscita del lanciatore, controllando console non visibile e token invariato. Verifica flag separata impedisce la combinazione NO_WINDOW/DETACHED che Windows ignorerebbe.

Stato di questo commit: codice testato; vet/build, installer 1.3.1, backup/rollback, distribuzione e collaudo MCP sui due computer ancora da completare. Il verbale sara aggiornato con gli esiti effettivi. Nessun reboot fisico o prova di programmi che richiedano esplicitamente una nuova console.
