# Installer 1.1.1 — estrazione PortableGit e ripresa 1.1.0

31 ottobre 2026. Agente Windows 0.6.1 invariato. Segnalazione utente dal secondo PC: checking_prerequisites / resolved_git / downloading_git / installing_git / waiting_resume, poi "Componente installato ma verifica fallita: git". Nessuna disponibilita MCP del secondo PC ancora verificata.

## Causa individuata

La 1.1.0 invocava PortableGit con `-y -gm2 -InstallPath="percorso-con-barre-raddoppiate"`, ricavato da una guida storica. Il parser SFX effettivo della versione attuale riconosce soltanto `-y` e `-o...`: si ferma al primo argomento sconosciuto. La destinazione resta non impostata e viene usato il percorso di default dell'archivio; gli argomenti residui vengono passati al post-install. Il processo puo restituire zero, mentre cmd/git.exe non esiste nel target richiesto da Jarvis. Questo e un difetto reale della 1.1.0 coerente con il messaggio utente; il contenuto effettivo del secondo PC non e stato letto in questa sessione.

Fonte primaria verificata: git-for-windows/7-Zip, ref v26.05-VS2022-sfx, CPP/7zip/Bundles/SFXSetup/SfxSetup.cpp, Git blob 1776a01141307a2af250c5e4200f24fe954dcb8c. Il builder PortableGit in git-for-windows/build-extra/portable/release.sh imposta il fallback InstallPath=%%S/PortableGit e lancia git-bash per post-install.bat. La documentazione pubblica storica -InstallPath non descrive il parser attuale.

## Correzione

- Comando esatto `-y -o"<target>"`, senza opzioni storiche, senza barre raddoppiate e senza argomenti successivi. Conservata l'estrazione privata, senza installazione di Git globale.
- Git/Go non vengono marcati completed dal worker finche il loro eseguibile atteso non supera una vera richiesta di versione. Codex, versione/profilo e controlli economici invariati.
- Probe native senza shell o finestra, stdout/stderr separati, timeout di 15 secondi limitato al comando di versione. Nessun timeout o stop dei turni Codex. Codici distinti per eseguibile mancante, errore di avvio (incluso codice Win32), exit nonzero, versione non riconosciuta e timeout.
- Diagnostica privata per richiesta in <ID>.verification.json, senza token/account; errore del probe conservato nella ricevuta failed. Una estrazione con exit zero e output assente non e piu dichiarata riuscita.

## Ripresa senza cancellare il registro

La stessa cartella di installazione seleziona lo stesso registro privato per PC/utente/percorso, quindi niente reset o eliminazione di progress.json. Cache riusata dopo hash.

Il caso 1.1.0 e riconosciuto solo per Git: ricevuta completed/exit zero senza i metadati della nuova versione, worker non vivo, richiesta originale formato 1 priva della nuova versione, ID concorde, stesso utente/PC/state_dir, stessa versione/hash/asset/target e percorsi canonici derivati dal contesto; archivio nuovamente verificato e Git nel target non funzionante. La correzione di estrazione e separata da qualunque elaborazione software. Richiesta e ricevuta originali NON vengono riscritte; repaired_legacy_jobs conserva motivo e riferimento e la nuova richiesta ha un nuovo ID.

Un worker attivo, un dispatch privo di ricevuta, un esito incerto, una richiesta non corrispondente o una ricevuta nuova non vengono trattati come questo caso. Nessun replay automatico di Codex, Git sul progetto o altri lavori. Le copie parziali del target vengono conservate come prima; l'eventuale directory di fallback Downloads/PortableGit non viene cancellata o adottata implicitamente.

Compatibilita: recupero dei tentativi Jarvis incompleti 1.1.0 e 1.1.1, oltre al caso legacy 1.0.1; storico hash della 1.1.0 incluso per aggiornare script riconosciuti senza sovrascrivere modifiche locali. Versione agente e segreti/stato/prova costi non modificati.

## Procedura per l'utente

Estrarre Jarvis-MCP-Setup-1.1.1.zip in una nuova cartella e usare INSTALLA.cmd, scegliendo la stessa destinazione precedente (D:\Jarvis). Non eliminare registro, cache o cartella incompleta. L'installer riprende prima Git, poi i passaggi mancanti. Se un ulteriore blocco e presente, riferire il messaggio/codice mostrato, senza condividere token, auth.json o credenziali.

## Verifiche effettivamente eseguite

Nessuna chiamata Jarvis su Desktop1, nessuna installazione, modifica di configurazioni o nuova prova su quel PC in questa correzione. Nessuna inferenza o API a consumo.

Nel cloud Linux, Test-SfxRegression.py ha verificato l'identita del sorgente upstream, estratto e compilato con g++ il ciclo parser ORIGINALE usando un adattatore stringhe, quindi eseguito regressioni: vecchi flag lasciano il target non impostato; nuovi flag mantengono percorso esatto, spazi e Unicode senza residui. Verificato staticamente che il worker usa il nuovo comando, verifica la versione prima di completed e mantiene i vincoli del recupero legacy. Il frammento originale non e distribuito nel repository: fornire il sorgente upstream della ref indicata per ripetere il test.

Test-Bootstrap.ps1 aggiornato con fixture che riproduce anche il falso exit zero dei vecchi argomenti, recupero della vecchia ricevuta, rifiuto di altro utente/ricevuta nuova e worker che fallisce quando l'estrattore restituisce zero senza creare Git. Questi NUOVI test Windows sono predisposti ma NON eseguiti qui. Il test della 1.1.0 aveva una fixture che implementava proprio -InstallPath e per questo non aveva colto l'incompatibilita reale: il precedente COMPLETE non valida la 1.1.1 su Windows.

Il pacchetto e stato controllato con hash di tutti i payload, integrita ZIP e hash invariato del binario 0.6.1. Nuova installazione/ripresa effettiva sul secondo PC e collaudo Windows completo restano da confermare. Non dichiarare il secondo PC pronto prima della prova reale.

## Riproduzione

`python windows-mcp-agent/installer/Test-SfxRegression.py --sfx-source <SfxSetup.cpp-ufficiale>` richiede Python standard e g++. Fonte: https://github.com/git-for-windows/7-Zip/blob/v26.05-VS2022-sfx/CPP/7zip/Bundles/SFXSetup/SfxSetup.cpp . L'identita Git del file e verificata prima di compilarne il ciclo di parsing. Non esegue alcun programma Windows o download.

## Pacchetto

Jarvis-MCP-Setup-1.1.1.zip, 6770469 byte, 26 file, SHA-256 F12D85BEB33C55836DA8C04A36C38939665C3C4F1523C70CC0E0D669E6A7B3CD.
