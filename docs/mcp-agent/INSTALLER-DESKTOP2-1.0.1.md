# Installer 1.0.1 — nome dispositivo configurabile

31 ottobre 2026. Richiesta utente: Desktop2 come nome iniziale, modificabile durante l'installazione.

La prima domanda e ora "Nome del dispositivo Jarvis [Desktop2]". Invio conserva Desktop2; un altro nome viene salvato in installation.json e usato per cartella proposta C:\Jarvis-NOME, task e collegamento, messaggi e nome suggerito del plugin Jarvis-NOME. Ammessi 1–40 caratteri: lettere ASCII, numeri, spazi, trattino e underscore; percorsi e caratteri di comando sono rifiutati prima delle mutazioni. Esempio collaudato: Officina CNC, cartella proposta C:\Jarvis-Officina CNC, plugin Jarvis-Officina CNC, task con prefisso Jarvis-Officina-CNC.

Il nome e l'etichetta di questa installazione. Non cambia il nome del computer Windows o il machine_id; Verifica mostra separatamente nome scelto, nome Windows e ID. La configurazione avviene nella prima installazione: nessuna rinomina automatica di installazioni gia presenti. Agente invariato 0.6.1 e relativo hash verificato; nessuna nuova installazione del servizio Desktop1.

## Collaudo

Test-Installer.ps1 eseguito su Windows in copie isolate: PASS COMPLETE, inclusi default Desktop2, personalizzazione con spazi, cartella proposta, metadati/plugin/task e rifiuto nomi non sicuri. Ripetuti i controlli di installazione, autenticazione MCP, credenziali e ID separati, avvio al login, arresto/riavvio, disabilitazione avvio senza fermare il lavoro corrente, payload corrotto e protezione stato. installer-name-101.json: PASS, hash produzione invariato, nessun listener temporaneo; HEAD/origin della prova a5e3cb30759a8b7e400d32fcf948c9d7200a3b48. Le successive modifiche sono verbali.

Restano invariati i limiti e i passaggi account descritti in INSTALLER-DESKTOP2-1.0.0.md: secondo PC fisico, Cloudflare remoto, OAuth/plugin e reboot ancora da collaudare. Configurazione tunnel locale riporta il nome scelto; il plugin viene creato dall'utente nel proprio account.

## Pacchetto

Jarvis-MCP-Setup-1.0.1.zip, 6.760.526 byte, 16 file.
SHA-256 d35c87fb3ffc749dac6de2111866cbcf0838e1641dca96faa6ec78cce0df8a09.
Integrita ZIP e tutti gli hash payload del manifest verificati. Binario agente invariato:
9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1.
Sorgenti/test/manifest in windows-mcp-agent/installer; builder in scripts/build-desktop2-installer.ps1 aggiornato al nome generico del pacchetto. Nessuna inferenza o modifica delle prove costi.
