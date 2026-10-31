# Candidato 0.5.6 — controllo desktop tramite UI Automation

Richiesta autorizzata il 31 ottobre 2026: uso di mouse/tastiera senza clic su coordinate sbagliate.

Implementazione candidata:
- task_start operation=desktop_inspect osserva la finestra in primo piano.
- task_start operation=desktop_action, desktop={snapshot_id,element_id,action,text?}.
- Azioni: invoke (pulsante), set_text (campo Edit con ValuePattern), select, toggle.
- Nessun SendInput, SendKeys, spostamento del mouse o clic a coordinate: API UIA sull'oggetto individuato.
- Snapshot monouso 90 s, controllo PID/avvio processo/HWND/runtime ID/geometria/stato/primo piano, stabilità 250 ms.
- Testo password escluso; valori Edit non esposti; hash interno impedisce sovrascrittura di un campo modificato dopo l'ispezione.
- Finestra spostata o diversa, controllo rimpiazzato/non univoco, disabilitato/offscreen, desktop bloccato, pattern assente: rifiuto.
- Nessun retry automatico dopo dispatch incerto. Invoke conferma solo dispatch; verificare effetto applicativo con nuova ispezione.
- Opt-in locale JARVIS_DESKTOP_ENABLED=1. Worker incorporato nel binario, credenziali agente escluse dal suo ambiente, output limitato 64 KiB.
- UIA dipende dal provider dell'applicazione: impossibile promettere assenza assoluta di errori o atomicità fra controllo dello stato e azione. Nessun fallback basato su pixel.
- Non supportati: trascinamento, tasti globali, scorciatoie arbitrarie e app prive dei pattern necessari.

Stato al commit: codice e test preparati; Windows/fixture/MCP, backup e distribuzione ancora da collaudare.
Agente 0.5.5 operativo preservato durante il collaudo del candidato.
Riferimenti: https://learn.microsoft.com/en-us/windows/win32/winauto/uiauto-controlpatternsoverview e https://learn.microsoft.com/en-us/dotnet/api/system.windows.automation.valuepattern.setvalue
