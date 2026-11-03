# Aggiornamento agente — procedura unica con batch separato

Richiesta esplicita dell'utente: stessa procedura sui due computer, avviata dall'agente stesso, sempre mediante batch in processo separato. Non creare attivita Windows d'aggiornamento, broker o percorsi alternativi per singolo computer.

## Flusso canonico

1. machine_info, identita e versione del computer bersaglio; nessuna elaborazione attiva.
2. Preparare il candidato e verificarne SHA-256, sorgente pubblicato e versione; predisporre lo stesso scripts/update-agent.ps1 su ogni computer.
3. L'agente riceve il batch tramite update_prepare (ID casuale monouso di 32 cifre esadecimali, CRLF e SHA-256 esatti) e lo avvia tramite update_execute. Il comando Go e identico sui computer: cmd.exe /D /C, CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW, HideWindow=true (dalla 0.7.1).
4. Il batch richiama update-agent.ps1 con HomeDir, Candidate, ExpectedHash, ExpectedMachine, ExpectedPreviousVersion, ExpectedPreviousHash, TargetVersion e SourceCommit. L'esecutore sopravvive all'arresto del processo agente. Mai taskkill /T sull'agente che lo ha generato.
5. Unico algoritmo: lock d'aggiornamento, verifica listener/percorso/hash/identita, verifica coda inattiva, backup privato con hash e ACL, copia stato, rehearsal candidato/precedente e identita, arresto del solo PID agente, attesa del lock del supervisore se presente, sostituzione binario, aggiornamento manifest se presente, avvio configurato, verifica salute/MCP/hash/identita/configurazioni/manutenzione e tunnel configurato, ricevuta.
6. Errore dopo l'arresto: rollback binario e manifest, preservando lo stato corrente. Nessun ripristino cieco dello stato e nessun replay di operazioni incerte. RehearsalOnly esegue soltanto le protezioni e la prova isolata.
7. Dopo riconnessione, verificare machine_info, self-update.log e ricevuta del backup prima di dichiarare completato.

## Dati di installazione dichiarati

| Campo | Desktop1 | Desktop2 |
|---|---|---|
| HomeDir | D:\Jarvis | D:\Jarvis |
| Eseguibile attivo | D:\Jarvis\jarvis-mcp.exe | D:\Jarvis\Agent\jarvis-mcp.exe |
| Avvio configurato | Eseguibile diretto, argomento listen | PowerShell, Scripts\Start.ps1, HomeDir |
| installation.json | Assente | Presente, verificato per proprietario/computer/percorso |
| Supervisore e tunnel legati al ciclo agente | Assenti dal profilo di questo updater | Presenti, mantenuti tramite avvio esistente |

Questi valori vengono rilevati e registrati dal medesimo codice. Nessun ramo per nome del computer. I percorsi fisici non sono stati migrati. Il supervisore/startup gia presente su Desktop2 resta un dato del profilo; l'updater non registra ne avvia task Windows d'aggiornamento.

Gli entrypoint storici deploy-runtime-070.ps1 e deploy-managed-runtime-070.ps1 sono alias sottili dello stesso motore, con lo stesso contratto. Non aggiungere implementazioni divergenti.

## Scheletro del batch

    REM JARVIS-UPDATE-ID: <32-hex-nuovo>
    @echo off
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "<percorso-update-agent.ps1>" -HomeDir "<home>" -Candidate "<binario>" -ExpectedHash <sha-candidato> -ExpectedPreviousHash <sha-installato> -ExpectedPreviousVersion <versione-installata> -TargetVersion <versione-candidata> -ExpectedMachine <machine_id> -SourceCommit <commit-runtime>

Lo scheletro non e un aggiornamento pronto: i byte finali devono avere CRLF e hash calcolato, ed essere preparati/eseguiti dall'agente bersaglio. Non inserire segreti nei parametri o nel batch. I processi applicativi ordinari usano process_start non elevato; l'updater conserva il contesto operativo gia autorizzato dell'agente.

## Verifiche

Collaudo finale 1 novembre 2026: stesso motore, stesso protocollo batch e intero ciclo di rimpiazzo/riavvio 0.7.0 su entrambi, senza task temporaneo. Entrambi self-update.log terminano PASS common_batch_update_identity_config_maintenance; identita e hash preservati. Vedere VALIDATION-0.7.0-20261101.md. Il test non certifica versioni future o reboot fisico.

## Contatori e temporanei dalla 0.7.2

Backup protetto di State\tool-usage.json quando presente; nessun azzeramento nel rollback. Rehearsal e manifest intermedio nella cartella temporanea dedicata, rimossa e verificata nel finally. Sostituzione atomica della configurazione definitiva; backup permanenti protetti.
