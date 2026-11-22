# Validazione Windows MCP 0.5.5 — 31 ottobre 2026

## Artefatto distribuito

Commit sorgenti: b982ed77bdbf4921eabf16e04461bfa89b944675.
Branch: reconcile-windows-mcp-agent-20261031.
SHA-256: 0769E1597B37628B3197A545F10FB42491D85913B8491B3E722C0D398B47E47E.
Compilatore: Go 1.27.0, Windows/amd64.
La successiva pubblicazione della documentazione non cambia i sorgenti compilati né questo riferimento incorporato.

## Evidenze

I log sono locali, sotto D:\Jarvis\Logs\diagnostics; non contengono credenziali.

| Log | Risultato |
| --- | --- |
| go-build-055-20261031.log | Primo go_build MCP isolato superato sul candidato originale 2f8e278 |
| safety-055-20261031.log | Rifiuti dei parametri non autorizzati e timeout originale |
| cancel-child-055.log | Difetto riprodotto: test figlio ancora vivo dopo cancelled; poi arrestato |
| fix-build-055-retry.log | Fast-forward al commit corretto, test -count=1 superati, build superata |
| cancel-child-fixed-055.log | Processo figlio realmente avviato e terminato dopo task_stop |
| timeout-fixed-055.log | Timeout 120.4126 s, context deadline exceeded, nessun figlio vivo |
| rollback-proof-055.log | Backup con hash e prova 0.5.4 → 0.5.5 → 0.5.4 su 8767 |
| install-055.log | Installazione, health 0.5.5 e checksum operativo |
| final-verify-055.log | Hash staging/operativo uguali, sorgenti puliti, backup conservati, porte di prova chiuse |

Dopo l'installazione, ChatGPT ha chiamato direttamente Jarvis-Desktop1:
- agent_status: versione 0.5.5.
- task_start operation=go_test, project=windows-mcp-agent: task-1, completed, 100%, ok jarvis-mcp-prototype 2.925s.
- task_start operation=go_build, project=windows-mcp-agent: task-2, completed, 100%, go_build succeeded.

Il primo tentativo di fetch remoto si è interrotto nel wrapper PowerShell perché il messaggio di avanzamento su stderr è stato trattato come errore; nessun merge in quel tentativo. La ripetizione ha controllato il codice di uscita nativo ed eseguito un fast-forward verificato. Non è stata alterata la storia Git.

## Changelog 0.5.5

Aggiunte operazioni go_test e go_build al task_start esistente, con selezione del modulo nella root autorizzata e timeout 120 secondi, senza nuovi strumenti MCP.

La sola cancellazione di go.exe lasciava vivo il processo .test.exe. Su Windows cmd.Cancel usa ora taskkill.exe dal percorso SystemRoot\System32, con PID e argomenti separati, timeout 5 secondi e WaitDelay 2 secondi.
Il test di regressione aspetta il PID del test figlio e attende la sua terminazione con un handle Windows.
La correzione è stata verificata sia in go test -count=1 sia con una vera invocazione MCP isolata.

## Ripristino manuale, se necessario

Eseguire sul notebook in PowerShell con i permessi dell'agente. Non è stato necessario eseguire questo ripristino in produzione; il cambio/ripristino del binario è stato collaudato su una copia isolata.

1. Backup: D:\Jarvis\Backups\mcp-0.5.5-20261031.
2. Verificare che jarvis-mcp-0.5.4.exe abbia SHA-256 7A29039C153409C35135D11C9CD891BC7366A3C9713AA77C3CFF947780B0288E e che agent.env sia presente.
3. Individuare il listener 127.0.0.1:8765; controllare che il processo appartenga esattamente a D:\Jarvis\jarvis-mcp.exe prima di arrestarlo. Non arrestare tunnel o altri processi.
4. Ripristinare il binario dal backup e agent.env in D:\Jarvis\Config\agent.env.
5. Avviare D:\Jarvis\jarvis-mcp.exe con JARVIS_HOME=D:\Jarvis e JARVIS_PROJECTS_ROOT coerente con il file ripristinato (D:\Jarvis\Projects). Le credenziali vengono caricate localmente; non stamparle.
6. Verificare /health sulla porta 8765, versione 0.5.4, poi agent_status dal plugin.

L'installatore controllato salvato in D:\Jarvis\Scripts\updates\5b9e878ea6834b1eaa64248b52cfc9de.cmd contiene anche il ripristino automatico su errore durante l'installazione. Non rieseguirlo come comando di rollback: è un installatore monouso.

Conservare stash backup update.go locale 20261031 e update.go.bak. La fixture temporanea di cancellazione/timeout è stata eliminata; le copie di prova e i backup dei binari restano disponibili.
