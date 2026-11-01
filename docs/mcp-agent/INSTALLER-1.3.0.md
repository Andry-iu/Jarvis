# Installer 1.3.0 — agente 0.7.0 e aggiornamento comune

1 novembre 2026. Il pacchetto sorgente e allineato all'agente 0.7.0 gia operativo sui due PC. Questa revisione aggiorna l'installer e le regole su Git; non reinstalla le due macchine di produzione.

## Contenuto e comportamento

- Nuove installazioni: `<root scelta>\Agent\jarvis-mcp.exe`, con `Scripts\update-agent.ps1` copiato dalla sola sorgente canonica `windows-mcp-agent/scripts/update-agent.ps1`. Nessun duplicato dell'algoritmo nel sorgente installer, nessun task di aggiornamento. Il task di accesso Windows riguarda l'avvio ordinario.
- Manifest 1.3.0 / agente 0.7.0: runtime sorgente `54aed96d94f2b02f35b31168ca8cafee217b4158`, updater pubblicato in `a00ba7e164bea83b35f6d4b1767ddb0d82d13b5d`. Binario di riferimento Windows amd64, Go 1.27.0, 12.193.792 byte, SHA-256 `1542248e430042d1dab7e913c889a1242318e6cdfd276ebec22a927f4ff16b22`. Sono metadati per costruire/verificare il pacchetto: il binario non entra in Git.
- Hash e dimensioni dei 37 file payload nel manifest, script-history aggiornato per le versioni precedenti. Costruzione con `scripts/build-desktop2-installer.ps1`; opzione `-NoArchive` per il collaudo isolato senza ZIP. Conservate le modalita di configurazione locale, ripresa, Cloudflare e le guardie economiche.
- Prima di intervenire su un'installazione completa con agente precedente, il setup rifiuta con `AGENT_UPDATE_REQUIRED_USE_EXISTING_AGENT_BATCH`: prima deve aggiornarsi l'agente stesso tramite batch. Il recupero delle installazioni incomplete riconosciute resta disponibile e verificato. Non sostituire questo rifiuto con un altro meccanismo di aggiornamento.
- Nuove funzioni: avvio applicazioni non amministratore per default, cartelle note, finestre/controlli/documenti, tasti, dialoghi Apri/Salva verificati, manutenzione log/batch 30 giorni e controllo avvio/24 ore. Dettagli e vincoli in [OPERATING-RULES.md](OPERATING-RULES.md) e [verbale runtime](VALIDATION-0.7.0-20261101.md).

## Verifica effettiva

Esecuzione del 1 novembre su Desktop1 identificato con `machine_info`, PowerShell 5.1 x64; payload costruito dal sorgente e dal candidato gia verificato, sotto Staging. Installazioni di prova in radici nuove sotto `D:\Jarvis\InstallerTests`, separate dalla produzione, senza collegamenti Desktop. I task di avvio delle fixture vengono rimossi nel finally. Nessuna inferenza e nessuna API Cloudflare reale per questa revisione.

| Prova | Esito |
|---|---|
| `Test-PackageContract.py` | PASS: versioni, tutti gli hash/dimensioni sorgenti, updater canonico, layout e contenuto sorgente |
| `node --test installer/Test-Cloudflare.cjs` | 45/45 PASS, mock; nessuna mutazione Cloudflare |
| `Test-SetupSettings.ps1` Windows | 51 asserzioni PASS, lettura letterale, whitelist, ACL e nessuna chiamata esterna |
| `Test-Bootstrap.ps1` Windows | 32 verifiche + COMPLETE, PASS; rifiuto agente completo precedente, ripresa/rollback, ricevute e worker, identita e credenziali preservate |
| `Test-Installer.ps1` Windows | 32 verifiche + COMPLETE, PASS; parser PowerShell, due radici, identita distinte, MCP reale, agente 0.7.0, dieci nuove capability, updater comune installato, manutenzione, autenticazione, riavvio e payload corrotto |
| Controllo produzione | PASS: PID/start-time e hash agente Desktop1 conservati; MCP successivo risponde 0.7.0 con identita corretta |

Evidenze locali: `diagnostics/installer130-suite.log`, `diagnostics/bootstrap-130-test.log`, `diagnostics/installer-130-test.log`; risultati per fixture in `RESULTS.txt`. I log grezzi e lo stato privato non sono committati. Il controllo Codex legge account/quota senza avviare turni; non interpreta saldo sconosciuto come autorizzazione economica. La versione CLI attesa nel test viene letta dall'eseguibile verificato invece di essere fissata nella singola asserzione.

## Limiti dichiarati

Non eseguiti in questa revisione: scenari opzionali con archivio legacy (`-LegacyZipPath` assente), download reali `-CheckNetwork`, modifiche Cloudflare reali, installazione 1.3.0 in produzione, collaudo diretto del nuovo catalogo plugin dopo refresh utente, reboot fisico e attesa reale di 24 ore. Le prove gia svolte per l'agente installato sui due PC restano nel relativo verbale; non sono nuove prove del setup 1.3.0. Nessun sorgente Go cambiato in questa revisione.

## Riproduzione

Dal sottoprogetto, eseguire `python installer/Test-PackageContract.py` e `node --test installer/Test-Cloudflare.cjs`. Su Windows x64 usare il builder con `-AgentPath` del binario che coincide col manifest, `-OutputDirectory` nuova e `-NoArchive`; quindi eseguire le tre suite PowerShell con `-PackageDir` puntato al payload. I test Windows richiedono il contesto manutenzione amministratore gia autorizzato, CLI Codex compatibile/login presente e porte fixture libere. Mai usare la root di produzione come TestRoot.

Git contiene tutto il sorgente, i test e le risorse dell'installer, non il payload compilato. Pubblicazione sul solo branch `reconcile-windows-mcp-agent-20261031`, nessun merge implicito su main o modifica Android.
