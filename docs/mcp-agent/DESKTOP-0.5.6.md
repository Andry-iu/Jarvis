# Desktop Windows 0.5.6 — implementazione e collaudo

## Stato verificato — 31 ottobre 2026

**Installata e verificata sul PC Jarvis-Desktop1, con privilegi amministrativi.**
Endpoint operativo 127.0.0.1:8765/mcp; tunnel e autenticazione Cloudflare esistenti preservati.

- Binario: D:\Jarvis\jarvis-mcp.exe.
- Sorgenti compilati: eb5370396736283c1aed111e30001b95bd72c1b4.
- SHA-256: C230A1FF4EE852744318931FB1BF620022985B01606DFED5D1B1061182F410A5.
- Go 1.27.0 Windows/amd64; build e test svolti sul notebook, non con GitHub Actions.
- Branch: reconcile-windows-mcp-agent-20261031. I commit successivi al binario aggiornano fixture, test e documentazione, senza modificare i sorgenti incorporati nel binario.
- Configurazione persistente: JARVIS_DESKTOP_ENABLED=1 in D:\Jarvis\Config\agent.env.
- Verifica finale: 14:39:49 Europe/Rome, PID 476784, Elevated=true, nessun listener temporaneo e sorgenti tracciati puliti.

## Funzionamento

Si usa il task_start esistente, senza aggiungere comandi MCP:
```json
{"operation":"desktop_inspect"}
{"operation":"desktop_action","desktop":{"snapshot_id":"<snapshot corrente>","element_id":"<ID esatto>","action":"invoke"}}
{"operation":"desktop_action","desktop":{"snapshot_id":"<nuovo snapshot>","element_id":"<campo esatto>","action":"set_text","text":"Testo richiesto"}}
```

Monitorare agent_status fino al completamento: task.result contiene il JSON dell'ispezione o dell'esito.
Operazioni disponibili: invoke, set_text, select, toggle, secondo i pattern effettivamente esposti.
set_text **sostituisce l'intero contenuto del campo**: usarlo solo quando la sostituzione è autorizzata, non come digitazione generica in una bozza esistente.
Il valore del campo non viene restituito dall'ispezione; non presumere che sia vuoto.

Due trasporti sul controllo identificato:
1. Windows UI Automation: InvokePattern, ValuePattern, SelectionItemPattern, TogglePattern.
2. Controlli Edit/Button Win32 e WindowsForms standard riconosciuti tramite classe nativa, PID e HWND: WM_SETTEXT/WM_GETTEXT e BM_CLICK, con SendMessageTimeout limitato a un secondo.

Non vengono usati SetCursorPos, SendInput, SendKeys, clic assoluti o coordinate provenienti dall'assistente. I rettangoli servono a rilevare cambiamenti, non a posizionare clic.
Gli HWND sono ricavati dall'osservazione UIA; il chiamante non può specificare un HWND arbitrario o un messaggio Windows.

## Protezioni

- Snapshot casuale, monouso, valido 90 secondi, invalidato da nuova ispezione o da un tentativo di azione accettato.
- Un solo task alla volta; task_stop interrompe il worker. Se il dispatch è già avvenuto, lo stop non annulla l'effetto applicativo.
- Confronto prima dell'azione di HWND della finestra, PID e avvio del processo, runtime ID del controllo, classe, nome/automation ID, geometria, stato e primo piano.
- Stabilità per 250 ms e ulteriore controllo immediatamente prima dell'API.
- Stesso titolo con altra finestra, controllo spostato, disabilitato o fuori schermo, identità non univoca, snapshot scaduto/riusato: rifiuto.
- Campi password esclusi, anche per i controlli Edit nativi (stile password e EM_GETPASSWORDCHAR).
- Valori Edit non esposti; hash interno del contenuto impedisce sovrascritture se il campo cambia dopo l'ispezione.
- Campo readonly rifiutato; non viene tentato un altro percorso di scrittura dopo il rifiuto.
- Coordinate e tasti/comandi arbitrari rifiutati dal decoder dei parametri desktop.
- Worker incorporato nel binario, ambiente senza variabili JARVIS contenenti configurazione/credenziali, esecuzione senza console, timeout 15 s, output massimo 64 KiB.
- Nessun retry automatico di azioni dopo un esito incerto. È consentita una nuova ispezione in sola lettura.
- Un'azione invoke segnala il dispatch, non certifica da sola il risultato dell'applicazione. Verificare l'effetto con una nuova osservazione.

## Collaudi effettuati

Fixture dedicate WinForms e WPF, senza azioni sui documenti dell'utente:

| Prova | Risultato |
| --- | --- |
| Scrittura Unicode con virgolette e simboli | Testo riletto uguale al richiesto |
| Due pulsanti omonimi | Bersaglio esatto invocato; contatore del pulsante esca sempre zero |
| Snapshot riutilizzato | Rifiutato |
| Controllo spostato | Rifiutato target_changed |
| Finestra spostata | Rifiutato window_changed |
| Campo modificato dopo ispezione | Rifiutato; testo dell'utente preservato |
| Altra finestra con stesso titolo | Rifiutata per HWND/primo piano differente |
| Pulsante disabilitato dopo ispezione | Rifiutato |
| Campo readonly | Rifiutato |
| Checkbox | Stato applicativo verificato, sia native invoke sia UIA Toggle |
| Coordinate x/y | Rifiutate come parametri non ammessi |
| Due monitor, origini X=0 e X=-1920 | Nuova osservazione e azione corrette |
| Schermata di blocco/copertura segnalata dall'utente | Nessuna finestra utilizzabile; rifiuto senza azioni |
| Privilegi del processo installato | TokenElevation=true |

Test Go, inclusa regressione cancellazione processi figli e guardie desktop: superati. Ultima build del codice incorporato: ok jarvis-mcp-prototype 2.887s.
Dopo installazione, go_test chiamato direttamente da Jarvis-Desktop1: completed, 100%, ok jarvis-mcp-prototype 3.313s.
Collaudo completo WinForms ripetuto via MCP locale sul processo installato (porta 8765): superato.
desktop_inspect chiamato direttamente dal plugin sul desktop reale: task-26 completed, 11 controlli, truncated=false.

La prima ispezione del desktop reale dopo il collaudo aveva restituito unavailable, senza azioni; una verifica in sola lettura e una nuova ispezione sono riuscite. Non è stata identificata una causa certa: mantenere il comportamento di arresto conservativo, non trasformare questo errore in un retry di azioni.

## Limiti

Non è una garanzia matematica di infallibilità: UIA dipende dai provider dell'applicazione e non rende atomici controllo dello stato e azione. La scelta semantica del bersaglio resta responsabilità del chiamante: non scegliere fra nomi uguali senza contesto sufficiente.
Non implementati: mouse libero, trascinamento, doppio clic/arbitrario clic destro, tasti globali e scorciatoie arbitrarie.
Documenti UIA sono trattati come foglie; moduli dentro pagine web non sono garantiti. Elementi di processi diversi da quello della finestra sono esclusi.
Controlli custom senza pattern supportati o classe nativa ammessa vengono rifiutati.
Il desktop deve essere accessibile e la finestra bersaglio in primo piano. Non vengono aggirate schermata di blocco, desktop protetti o UAC.
Altri DPI, altri framework e altri programmi non sono stati tutti collaudati fisicamente.
Cattura schermo diagnostica già acquisita e mostrata all'utente (area 3840x1080, immagine ridotta 960x270); comando MCP dedicato e avvio automatico dopo reboot restano attività distinte.

## Backup e ripristino

Backup 0.5.5: D:\Jarvis\Backups\mcp-0.5.6-20261031.
File: jarvis-mcp-0.5.5.exe, replaced-0.5.5.exe, agent.env, avvio originale, old.sha256/new.sha256.
SHA-256 0.5.5: 0769E1597B37628B3197A545F10FB42491D85913B8491B3E722C0D398B47E47E.
Conservare anche i backup 0.5.4, stash backup update.go locale 20261031 e update.go.bak.
Ripristino provato isolatamente su 8767: 0.5.5 → 0.5.6 → 0.5.5, con hash finale identico.
Un primo tentativo ha incontrato un file ancora in uso dopo l'arresto del processo di prova; la procedura con attesa e tentativi limitati di copia è poi passata.
Installatore controllato: D:\Jarvis\Scripts\updates\1f308e97e83e46fa8ec14c08076108de.cmd; include ripristino automatico in caso di errore. Non rieseguirlo come rollback.

Ripristino manuale, se necessario: verificare hash del backup, arrestare solo il processo di D:\Jarvis\jarvis-mcp.exe che occupa 8765, ripristinare binario e agent.env dal backup, rimuovere l'override JARVIS_DESKTOP_ENABLED dal processo di avvio e riavviare l'eseguibile con JARVIS_HOME=D:\Jarvis. Verificare /health e agent_status versione 0.5.5.
Non stampare o pubblicare secrets.env.

## Log locali

D:\Jarvis\Logs\diagnostics:
- desktop-build-056.log
- desktop-integration-056-winforms.log
- desktop-integration-056-wpf.log
- desktop-rollback-056.log
- desktop-install-056.log
- desktop-postinstall-056.log
- desktop-final-056.log

Le correzioni intermedie del collaudo hanno riguardato policy PowerShell limitata al processo, attivazione della fixture, contatori PowerShell con nomi non sovrapposti ai pulsanti e lettura UTF-8 dei risultati. I test non inviano dati o messaggi ad altre persone.

Riferimenti primari:
- https://learn.microsoft.com/en-us/windows/win32/winauto/uiauto-controlpatternsoverview
- https://learn.microsoft.com/en-us/dotnet/api/system.windows.automation.valuepattern.setvalue
- https://learn.microsoft.com/en-us/windows/win32/controls/bm-click
- https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendmessagetimeoutw
