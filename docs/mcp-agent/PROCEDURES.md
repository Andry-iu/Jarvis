## Regola di utilizzo permanente — 19 novembre 2026

Jarvis e un **agente universale**: la logica dei singoli programmi non appartiene al runtime Go/MCP. Prima di creare un nuovo tool specifico, riutilizzare primitive UI, screenshot, mouse/tastiera, file, processi, rete, oppure eseguire batch e procedure esterne. Salvare le procedure permanenti in `<Jarvis>\Procedures` (Desktop1: `D:\Jarvis\Procedures`) e non in `<Jarvis>\Projects`. Se necessario usare CLI/API di applicazioni con script parametrici, con credenziali solo locali, controlli sul destinatario, ricevute, verifiche e nessun replay incerto. Solo funzionalita generiche e comprovate possono giustificare modifiche dell'agente. Regola completa: [HANDOFF-GENERIC-AGENT-PROCEDURES-20261119.md](HANDOFF-GENERIC-AGENT-PROCEDURES-20261119.md).

# Procedures — percorso canonico

Dal 14 novembre 2026 le procedure permanenti si trovano in
`<Jarvis>\Procedures`, fuori da Projects. Usare questo percorso nei comandi,
negli script, nei cataloghi, nella documentazione e negli appunti.

## Uso

- Pulizia: `<Jarvis>\Procedures\Invoke-JarvisCleanup.ps1`;
  [parametri e protezioni](CLEANUP.md).
- Le altre procedure gia installate si richiamano con
  `<Jarvis>\Procedures\Invoke-Procedure.ps1`. Impostare la cartella di lavoro
  su `<Jarvis>\Procedures`, mantenendo parametri e RequestId del runner.
- Aggiornare la lista dei percorsi leggibili dello script condiviso e gli hash
  del catalogo dopo ogni modifica; la sola rinomina non aggiorna i riferimenti.

## Verifiche

Rinomina, documentazione locale e appunti allineati sulle installazioni coinvolte.
Parsing PowerShell e suite Test-JarvisCleanup superati. Dove presenti, verificati
anche cataloghi, suite Test-Procedures, runner, ricevuta durevole, protezione
anti-replay e lettura di un documento direttamente dalla nuova cartella.
Assenza della precedente cartella e dei riferimenti operativi verificata.
Il binario agente e i progetti di lavoro non sono stati modificati.

Gli originali di audit e i log conservano i percorsi osservati alla loro data.
La mappa di migrazione e i dettagli delle installazioni rimangono negli appunti
privati; non sono riferimenti operativi da riusare o dati da pubblicare su Git.
