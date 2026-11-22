# Desktop1 — verifica e correzione avvio Jarvis MCP (22/11/2026)

## Perimetro
Solo Desktop1 / `DESKTOP-1ESSB0K`, agente 0.8.7, installazione `D:\Jarvis`. Nessuna modifica a Desktop2, identità, token, sorgenti eseguibili, configurazioni Cloudflare, progetti o storage.

## Evidenze verificate in locale
- `machine_info` e `agent_status`: versione 0.8.7, amministratore, PID 155868, stato `idle`; coda operativa vuota dopo l'intervento.
- `D:\Jarvis\Config\installation.json` esiste; installazione gestita, `installer_version=0.8.3`, `task_registered=true`, nome task `JarvisAgent-1b927a030fa3`. Versione binaria nel manifest 0.8.7.
- Identità utente, SID, cartella installazione, nome computer, eseguibile e SHA256 atteso dal manifest tutti coerenti.
- Task Scheduler: attività `JarvisAgent-1b927a030fa3` esistente, abilitata (`Ready`), esecuzione all'accesso dell'utente, `RunLevel=Highest`, `LogonType=Interactive`. Azione: `powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "D:\Jarvis\Scripts\Start.ps1" -HomeDir "D:\Jarvis"`.
- Prima della modifica: `LastTaskResult=1` (errore), `StartWhenAvailable=false`, `RestartCount=1`. Non è stato dimostrato il motivo storico specifico del risultato 1.
- L'agente attualmente attivo proveniva invece da un avvio diretto con padre `cmd.exe`; finestra console visibile. Il file `Config\running.json` era rimasto con un PID storico 147264, diverso dal PID live 155868; non sono stati alterati né forzati processi sulla base del solo file stale.
- Il sorgente dell'installer genera anche `Avvia.cmd` con un `pause`, coerente con l'osservazione dell'utente «Premere un tasto per continuare», ma l'origine esatta della finestra precedente non è attestata.

## Intervento eseguito e verificato
- Backup XML originale: `D:\Jarvis\Backups\startup-fix-20261122\JarvisAgent-1b927a030fa3.xml`.
- Modificata **la stessa attività**, senza crearne un duplicato: trigger `AtLogOn` con delay `PT30S`; `StartWhenAvailable=true`; `RestartCount=3`, `RestartInterval=PT1M`; mantenuti action, script, utente, `RunLevel=Highest`, hidden window, `MultipleInstances=IgnoreNew`, esecuzione illimitata e opzioni batteria.
- Postflight PowerShell: `state=Ready`, `trigger_delay=PT30S`, `trigger_enabled=True`, `start_when_available=True`, `restart_count=3`, `restart_interval=PT1M`, `runlevel=Highest` e action invariata. Marker testuale locale: `D:\Jarvis\Projects\JarvisMouseQA\_startup_fix_result_d1.txt`.
- Il processo Jarvis live **non è stato arrestato**; postflight MCP `machine_info` 0.8.7, PID 155868, admin; `queue_list` zero operazioni, healthy.

## Residui e collaudo futuro
- **Non è stato eseguito reboot/logout**, né uno stop del processo manuale: non dichiarare ancora verificato l'avvio automatico reale. Il task precedente era fallito; le nuove impostazioni migliorano il recupero, ma non provano che la causa iniziale sia risolta.
- Al successivo login Windows verificare senza avvio manuale: `Get-ScheduledTaskInfo` e stato `Running`, PID/proprietà e port 8765, `machine_info` 0.8.7 admin, assenza della finestra `cmd.exe`; documentare eventuale errore e log, senza ripetere cambi alla cieca.
- L'attività esegue avvio **al login**, non prima del login. Per funzioni desktop, non convertire implicitamente in task come SYSTEM all'avvio del computer.
- Un update dell'agente è distinto dalla registrazione task e può lasciare in esecuzione una versione aggiornata senza garantire il riavvio alla login successiva. Conservare backup e receipt e preferire verifiche read-only.
