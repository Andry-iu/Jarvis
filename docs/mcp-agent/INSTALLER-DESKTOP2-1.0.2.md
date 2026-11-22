# Installer 1.0.2 — Codex non accessibile e ripresa della 1.0.1

31 ottobre 2026. L'utente ha segnalato dal secondo PC un errore NativeCommandFailed/Accesso negato avviando codex.exe --version da Common.ps1, durante l'installazione 1.0.1. Il precedente rilevamento opzionale non intercettava l'errore: interrompeva la copia dopo creazione di installation.json e secrets.env e prima di agent.env/task/avvio. Non e stato verificato il motivo del rifiuto Windows sul computer di destinazione.

## Correzione

Find-Codex gestisce errori del pacchetto, percorso e avvio/versione senza interrompere Jarvis. Restituisce ponte Codex disabilitato con avviso locale. Il rilevamento e stato spostato prima delle mutazioni di installazione. Nessuna modifica ACL di Codex, elevazione aggiuntiva, aggiramento di Windows, acquisto, API key o inferenza. L'agente rimane 0.6.1, binario e guardie economiche invariati.

Per una nuova installazione usare INSTALLA.cmd. Per la copia 1.0.1 gia fallita aprire RIPRENDI.cmd dal NUOVO pacchetto estratto e indicare la destinazione scelta prima, per esempio D:\Jarvis, non la cartella dello ZIP. Default Desktop2 e nome personalizzato preservati.

## Ripresa limitata e backup

RIPRENDI legge e verifica formato/versione, computer/utente/percorso, binario 0.6.1, marker install-failed, assenza agent.env, task non registrato, porta libera, struttura attesa e assenza di stato, progetti aggiunti e configurazioni ulteriori. Rifiuta copie gia utilizzate o ambigue.

La directory incompleta e spostata integralmente in un backup affiancato con suffisso incompleta-ID, senza cancellazioni o sovrascritture. Si esegue poi una prima installazione corretta con nome, percorsi e porta precedenti. La credenziale inutilizzata della copia fallita resta nel backup; la nuova copia riceve una nuova credenziale. Nessun machine_id operativo e copiato o sostituito. La configurazione Cloudflare si completa successivamente con il comando dedicato.

Su errore della ripresa la copia originale viene ripristinata e l'eventuale nuova prova conservata. L'avvio avviene solo dopo il completamento del recupero. Non e un aggiornamento o una riparazione generale di installazioni gia operative. Il secondo PC non e ancora collegato all'agente: l'utente deve eseguire il pacchetto sul computer di destinazione.

## Verifiche effettive

Test Windows in copie isolate su Desktop1: parser PASS; errore nativo Win32 5 Accesso negato riprodotto con un file codex.exe di prova cui e negata la sola esecuzione; nessuna ACL del Codex reale modificata. Rilevamento restituisce disabilitato e installazione prosegue con JARVIS_CODEX_ENABLED=0.

Pacchetto 1.0.1 originale eseguito con la fixture per riprodurre il punto di fallimento, poi RIPRENDI della 1.0.2 completa l'installazione. Backup della credenziale originale verificato per hash. Seconda copia fallita ripresa con payload corrotto: errore e ripristino dell'originale verificati, hash invariato. Ripetuti i test nomi/default, installazione, credenziali/ID, root, porte, task, MCP, autenticazione, riavvio e payload. Suite PASS COMPLETE.

installer-denied-102.json: PASS, servizio Desktop1 0.6.1 con hash invariato, nessun listener di prova rimasto; HEAD/origin di prova c728895b8a03b4b9bb5485a3bb29d6b4eb184880. Nessuna reinstallazione del servizio Desktop1 e nessun turno Codex.

Da verificare con l'utente: ripresa sul secondo PC fisico, accessibilita del suo Codex, eventuali successive configurazioni Cloudflare/OAuth/plugin e reboot.

## Pacchetto consegnato

Jarvis-MCP-Setup-1.0.2.zip, 6.764.813 byte, 18 file; ZIP e tutti gli hash payload verificati.
SHA-256 9934f72bf1df4c16c94fe85b1d9e1fdc4139430c838e1b2a66be59807cfc0312.
Binario agente invariato SHA-256 9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1.
Sorgenti, ripresa, test, manifest e istruzioni in windows-mcp-agent/installer; builder in scripts/build-desktop2-installer.ps1.
