# Handoff permanente — Jarvis universale, strumenti generici e Procedures (19 novembre 2026)

## Decisione dell'utente: vincolante nelle prossime chat

Jarvis Windows MCP Agent deve essere **indipendente dalle applicazioni**. Non integrare nel binario nuovi moduli per software specifici (es. Proxmox, gestionali, CAD) solo per svolgere un'attivita occasionale. L'agente espone primitive generiche e verificate: identita, finestre, accessibilita UI, mouse, tastiera, screenshot, processi, file, shell/process_start, Git, compilazione, rete e inoltro autenticato via gateway.

**ChatGPT coordina e conosce il programma specifico**: decide se usare GUI, tasti, una CLI, API/documentazione del programma oppure uno script. La conoscenza delle singole applicazioni non deve essere compilata nel runtime di Jarvis.

## Percorso preferenziale per attivita ripetibili

1. Leggere anzitutto `windows-mcp-agent/AGENTS.md`, `docs/mcp-agent/CURRENT-RELEASE.md`, `docs/mcp-agent/PROCEDURES.md` e questo handoff. Controllare versione e capacita live con `machine_info`.
2. Riutilizzare le primitive MCP gia disponibili per osservare/verificare e interagire. Non dedurre che un tool manchi prima di aver verificato catalogo reale e fallback `task_start(operation=runtime)`.
3. Per operazioni composte o non comodamente esprimibili dalla UI, usare la strategia **batch/script** gia collaudata: esecuzione controllata dal PC destinatario, verifiche di precondizioni, log di esito, identificatore univoco e nessun replay automatico di esiti incerti. Non usare processi privilegiati per default; elevazione soltanto quando serve ed e autorizzata.
4. Conservare le procedure **permanenti, generiche e riutilizzabili** in `<Jarvis>\Procedures` (su Desktop1: `D:\Jarvis\Procedures`); `<Jarvis>\Projects` e riservato ai progetti, ai checkout Git e agli appunti. Usare `Invoke-Procedure.ps1` e le verifiche/cataloghi/hash gia previsti quando presenti; leggere `docs/mcp-agent/PROCEDURES.md`.
5. Le procedure specifiche per una singola applicazione possono essere salvate se realmente riutilizzabili, ma restano **script esterni**, non nuovi strumenti/hardcode nell'agente MCP. Favorire parametri, dry-run ove pertinente, conferme mirate per azioni distruttive, controlli di permessi e segreti locali protetti.
6. Aggiungere al codice Go di Jarvis soltanto **capacita generalizzabili** necessarie: per esempio input mouse affidabile, ispezione delle interfacce browser, osservabilita di risultati. Prima provare UIA/GUI, strumenti esistenti e batch: evitare integrazioni specialistiche.
7. Per software amministrativi (es. Proxmox) una CLI/API puo essere invocata da una procedura esterna tramite rete autorizzata. Non pubblicare su Git token, password, certificati privati, configurazioni locali o output sensibili.
8. Se si modifica una procedura, allineare **sorgente, catalogo, hash, documentazione, test e appunti**; verificare il contenuto installato separatamente da quello su Git. Se si modifica l'agente, mantenere stessa versione di **sorgente + binario + installer**, backup/rollback, test e verifica di deploy; nessuna installazione implicita su altri PC.
9. A ogni ripresa distinguere **progettato / codificato / testato / committato / installato / collaudato**. Scrivere nei documenti solo esiti realmente verificati.

## Stato concreto al 19 novembre 2026

- Ultima release verificata su Desktop1: `0.8.4`, non sostituita dai commit di sviluppo.
- Ramo Windows corrente: `feature/unified-agent-source-update-20261114` in `Andry-iu/Jarvis`; `main` contiene un Windows agent storico.
- Checkout di lavoro su Desktop1: `D:\Jarvis\Projects\Jarvis`; area delle procedure: `D:\Jarvis\Procedures`.
- Sono stati preparati su Git cambiamenti generici di controllo mouse e ispezione Edge, **non ancora rilasciati/installati**. La prima suite `go_test` lanciata su Desktop1 ha fallito per un lock di file temporaneo in `TestToolUsageAliasNoDoubleCount`; non attribuire il risultato a un collaudo positivo delle nuove funzioni. Vet/build non hanno ancora esito positivo.
- Una proposta di modulo Proxmox compilato dentro l'agente e stata **ritirata** su richiesta dell'utente: `proxmox.go` e stato eliminato e i relativi campi/dispatch sono stati rimossi da `runtime_api.go`. Non reintrodurlo implicitamente.
- In una release successiva l'installer dovra contenere le primitive generiche approvate e le relative procedure condivise. Aggiornamenti Desktop2 e SoftPlcTest1 sono **successivi**, non attestati da questa nota.
- Per il server AUTECH la rete amministrativa Proxmox e fisicamente isolata; non cambiare il disegno di rete o rendere permanente una connessione senza istruzione esplicita.

## Esempio operativo generico

Richiesta: spegnere un host da un'applicazione web su Desktop1. Prima rilevare se il comando e accessibile con l'interfaccia; quando non lo e, selezionare una procedura esterna controllata che usi CLI/API autenticata dell'applicazione, con controllo identita target e conferma dello stato. Non aggiungere `proxmox_shutdown` o altri tool di dominio al catalogo Jarvis.

**Questa decisione supera qualunque proposta precedente di aggiungere un modulo Proxmox al core.**
