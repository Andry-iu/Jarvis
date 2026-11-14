# Allineamento layout, avvio amministratore e pulizia — 14 novembre 2026

Ambito richiesto: Desktop1 e Desktop2. Eseguibile sempre dentro Agent; avvio
all'accesso Windows con privilegi massimi come il nuovo PC. Pulizia obbligatoria
a fine prove, appunti locali e Git aggiornati. Stato corrente: preparazione e
collaudo in corso; non certificare ancora migrazione o pulizia completate.

## Rilevazione precedente alle modifiche

Desktop1: 0.8.3 elevato, binario nella root D:\Jarvis; nessun task/Run/collegamento
di startup Jarvis individuato. Agent esiste ma senza eseguibile, manifest assente.
Desktop2: 0.8.3 elevato, binario in Agent, supervisore Start.ps1 e task Highest
esistenti. Va verificato e riavviato, non migrare inutilmente un percorso corretto.
Entrambi conservano Config, stato, credenziali, collegamenti e versioni binarie.

## Procedura

scripts/align-agent-layout.ps1 e un intervento di layout sul binario esistente,
non un secondo updater di versione: non compila o cambia il candidato. Usa batch
separato, verifica macchina/hash/idle/elevazione, backup, task Highest con lo
stesso supervisore dell'installer, spostamento percorso e riferimenti firewall
senza cambiare profili/porte autorizzati, stop del solo PID verificato, avvio
tramite task e verifica token/percorso/identita. Backup e ripristino preservati.
Prima provare su fixture isolata solo loopback, senza generare popup firewall.

Appunti privati nei due Projects/appunti.txt aggiornati con i riscontri; nessuna
pubblicazione del loro contenuto o di credenziali. Esiti da completare.
