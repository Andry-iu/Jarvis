# Installer 1.0.3 — CLI Codex obbligatoria

31 ottobre 2026. Agente Windows distinto dall'app Android; branch reconcile-windows-mcp-agent-20261031. Binario 0.6.1 invariato, sorgente 164cb980bd1a936749d41b8692979e98b929cb04, SHA-256 9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1. Nessuna reinstallazione del servizio Desktop1.

## Problema e comportamento

La prima copia 1.0.1 falliva su codex.exe --version con Accesso negato Windows. La 1.0.2 consentiva Jarvis senza Codex: l'utente ha chiarito che questo non completa il suo obiettivo. La 1.0.3 richiede una CLI compatibile prima di creare la nuova installazione. Cerca codex.exe nativo nel PATH, quindi nel percorso ufficiale LOCALAPPDATA/Programs/OpenAI/Codex/bin, infine nel pacchetto App. Percorso assoluto esplicito supportato; versione esatta 0.165.0-alpha.1.2. Non modifica ACL WindowsApps e non separa l'eseguibile dal suo runtime.

L'utente ha eseguito l'installer ufficiale OpenAI fissato alla versione richiesta e riportato codex-cli 0.165.0-alpha.1.2 dal secondo PC. Questo conferma il comando secondo l'utente, non prova ancora il collegamento MCP di quel computer.

## Procedura sul secondo PC

1. Estrarre il pacchetto in una cartella nuova, senza sovrascrivere il vecchio setup.
2. Eseguire COLLEGA-CODEX.cmd con lo stesso utente Windows, consentendo UAC.
3. Indicare la cartella Jarvis precedente; Invio accetta D:\Jarvis.

Il comando riconosce la prima installazione 1.0.1 fallita prima della configurazione: backup integrale con hash, nuova installazione nello stesso percorso e rollback dell'originale su errore. Recupero rifiutato se ci sono dati o configurazioni aggiunti, task o porta attivi, altro utente/computer o binario diverso. Per copia completa 1.0.2 configura solo il ponte; agent.env precedente conservato in Backups con sostituzione atomica.

Nessun arresto automatico: agente attivo con configurazione identica consultato; con configurazione diversa il comando si ferma prima di modificarla. Terminare i lavori e usare Arresta.cmd, poi ripetere. Un avvio automatico disabilitato resta disabilitato: il collegamento puo avviare il runner una volta, senza riabilitare il task. Non vengono ripetute attivita.

Verifica App Server con codex_status, senza sessioni o turni di inferenza. Config/codex-connection.json conserva versione, account_type/plan_type, machine_id, checked_at, usage osservata e decisione inference_allowed/blocked_reason separata. Nessun token, email o credenziale. Account diverso da ChatGPT produce errore esplicito; usare codex login col proprio account ChatGPT Business, mai API key. Valori sconosciuti restano null e quote basse restano visibili. Collegamento riuscito non equivale ad autorizzazione economica di un turno.

Per installazione nuova INSTALLA.cmd, nome predefinito Desktop2 configurabile, plugin suggerito Jarvis-Desktop2. Cloudflare dedicato e plugin ChatGPT rimangono passaggi distinti: niente copia di tunnel, segreti, identita, stato o prova costi da Desktop1.

## Validazione e limiti

Suite Windows Test-Installer.ps1 eseguita in directory/porte isolate: parser; Win32 5 reale; CLI negata rifiutata senza installazione parziale; discovery nativa prioritaria; fallimento 1.0.1 originale e ripresa con backup intatto; rollback su payload corrotto; MCP autenticato; identita/credenziali distinte e persistenti; task interattivo elevato senza timeout; nomi e percorsi; lettura reale account ChatGPT/quota senza inferenza; consultazione senza riavvio e rifiuto configurazione attiva diversa. Verificati anche avvio a freddo dopo ripresa e avvio senza riabilitare il task disabilitato. Risultati in Logs/diagnostics/installer-061-test.log. La fixture di discovery nativa usa la CLI reale compatibile disponibile su Desktop1; il runtime standalone non e stato installato su Desktop1.

Non collaudati: secondo PC fisico, UAC da doppio clic, reboot fisico, tunnel/DNS/Access/OAuth remoti, nuovo plugin, inferenza sul secondo PC. Nessuna API key, acquisto, ricarica, upgrade o estensione delle prove costi. Test/vet/build del binario invariato gia documentati in VALIDATION-0.6.1-20261031.md: questa release cambia solo installer e test PowerShell.

## Riproduzione da Git

Da checkout del branch, con il binario 0.6.1 verificato: scripts/build-desktop2-installer.ps1 -AgentPath <binario> -OutputDirectory <directory-nuova>. Normalizzazione LF prima dei checksum; nessuna credenziale nel pacchetto. Test-Installer.ps1 -PackageDir <payload> -LegacyZipPath <zip-1.0.1-originale> include la regressione originale. Il pacchetto storico 1.0.1 serve solo alla fixture e non va installato nuovamente sul secondo PC.

Per rollback del solo ponte usare il backup agent.env della medesima installazione con agente fermo; preservare identita e stato correnti. Non tornare alla modalita Codex opzionale per dichiarare completato l'obiettivo. Conservare i backup della ripresa e verificare gli esiti incerti prima di qualsiasi nuova azione.

## Pacchetto consegnato

Jarvis-MCP-Setup-1.0.3.zip, 6750113 byte, 20 file. SHA-256 38A83C452A75E76BB031A146628C4817A776779EF57BFD8666222301AC09F9F2.
