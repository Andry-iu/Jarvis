# Jarvis Windows MCP Agent 0.5.6

Agente Windows distinto dall'app Android Jarvis. Installato e verificato sul PC Jarvis-Desktop1, con privilegi amministrativi. [Stato corrente](../docs/mcp-agent/STATUS.md), [controllo desktop e collaudi](../docs/mcp-agent/DESKTOP-0.5.6.md).

## Controllo desktop senza coordinate

```json
{"operation":"desktop_inspect"}
{"operation":"desktop_action","desktop":{"snapshot_id":"<nuovo snapshot>","element_id":"<ID esatto>","action":"invoke"}}
{"operation":"desktop_action","desktop":{"snapshot_id":"<nuovo snapshot>","element_id":"<campo esatto>","action":"set_text","text":"Testo richiesto"}}
```

Richiamare tramite task_start e monitorare agent_status. Azioni: invoke, set_text, select, toggle secondo i pattern disponibili.
set_text sostituisce l'intero campo, non simula digitazione: richiede un'intenzione esplicita di sostituzione. Il contenuto originale non viene restituito.
Ogni snapshot dura 90 secondi ed è monouso. Ripetere l'ispezione dopo ogni azione o cambiamento.
I controlli sono individuati tramite UI Automation e, per Edit/Button nativi noti, HWND/classe/PID verificati. Si usano API dirette al controllo, senza muovere il cursore, SendInput/SendKeys o coordinate.
Se finestra, controllo, posizione, contenuto Edit o primo piano cambiano, l'azione viene rifiutata.
Non scegliere fra pulsanti omonimi senza contesto sufficiente; il solo nome non identifica il bersaglio.
Nessun retry automatico dopo esiti incerti; il dispatch non prova da solo l'effetto applicativo.

Il desktop deve essere sbloccato. Password, controlli non supportati, tasti/scorciatoie globali, trascinamenti e mouse libero sono esclusi; le pagine web non sono garantite.
UIA dipende dall'applicazione: non è una garanzia di infallibilità.
Configurazione locale JARVIS_DESKTOP_ENABLED=1; per disabilitare impostare 0 e riavviare in modo controllato.

## Test e compilazione Go

```json
{"operation":"go_test","project":"windows-mcp-agent"}
{"operation":"go_build","project":"windows-mcp-agent"}
```

go_test usa go test ./...; go_build compila in Staging/jarvis-mcp.exe. Timeout 120 s e annullamento dell'albero dei processi Windows, ereditati dalla 0.5.5.
Per test senza cache: go test -count=1 ./....
Il codice dei moduli gira con i permessi dell'agente: la root autorizzata non è una sandbox del codice.
L'output dei task Go conserva gli ultimi 12000 byte, dopo acquisizione in memoria.

## Installazione verificata

- Binario operativo D:\Jarvis\jarvis-mcp.exe, fuori da Staging.
- Sorgenti D:\Jarvis\MCP-Source\windows-mcp-agent; root autorizzata D:\Jarvis\MCP-Source.
- Configurazione D:\Jarvis\Config; credenziali fuori da Git.
- Endpoint locale 127.0.0.1:8765/mcp; Cloudflare Tunnel e Access esistenti.
- Backup 0.5.5 D:\Jarvis\Backups\mcp-0.5.6-20261031.
- Go 1.27.0 Windows/amd64. Nessuna compilazione tramite GitHub Actions.

Gli script bootstrap in Agent\ sono esempi: il percorso operativo verificato è quello sopra.
La cattura schermo diagnostica è stata acquisita e mostrata; il comando MCP dedicato e l'avvio automatico dopo reboot restano da verificare/implementare.
Seguire [RELEASE_POLICY](../docs/mcp-agent/RELEASE_POLICY.md).
