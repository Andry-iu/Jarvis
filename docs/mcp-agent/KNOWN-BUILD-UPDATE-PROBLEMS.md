# Problemi riscontrati e procedure riuscite

Questa pagina raccoglie esperienza operativa da riusare, non nuovi tentativi da
ripetere. Gli appunti privati mantengono inventario e ricevute per ogni computer.

| Problema osservato | Metodo riuscito e vincolo corrente |
|---|---|
| Desktop2: arresto interno del compilatore Go1.27.3, codice 0xc0000005; errori package not in std non provavano file assenti | Go1.27.0 ufficiale con hash verificato, cache isolata, GOMAXPROCS=1, GOGC=25, -p1; test, vet e build Windows superati. SDK di sistema preservato. Causa interna del compilatore non isolata. |
| Updater Desktop2 terminato con il genitore da taskkill /T | Batch CMD separato e stop del solo PID agente verificato; stesso updater canonico su tutti i PC, mai task di aggiornamento. |
| Riavvio su installazione con supervisore | Start.ps1 esistente, proprietario/percorso del manifest verificati, attesa rilascio runner.lock prima del nuovo avvio; conservare identita, configurazione e token elevato. |
| Finestra di abbinamento creata ma invisibile nella 0.8.1 | CREATE_NO_WINDOW senza HideWindow/SW_HIDE sul solo helper GUI; prova con launcher Go reale, non ProcessStartInfo sostitutivo. I batch di aggiornamento restano senza console. |
| Avvisi firewall ripetuti per copie di test su percorsi diversi | Fixture solo loopback e pulizia di processi/cartelle/regole proprie. Non disabilitare notifiche/firewall o cambiare profilo rete. |
| Descrizione client di device_call obsoleta | Confrontare tools/list del server e sorgenti: inoltro aggiornamenti gia presente. Distinguere catalogo server e cache client, senza attribuire refresh non eseguiti. |
| Preflight/dispatch riferiti come aggiornamento concluso | Conferma soltanto da ricevuta canonica, riconnessione autenticata e identita/versione/hash, manutenzione e pulizia. |
| Primo collaudo 0.8.4: File.Replace con backup null rifiutato da Windows PowerShell | Percorso di backup esplicito per la scrittura atomica dello stato. Tentativo fermo prima della compilazione; agente0.8.3 preservato, stato originale conservato e fallimento verificato prima di un nuovo ID. Successiva preparazione 0.8.4 PASS. |

Il comando nuovo usa scripts/build-runtime.ps1, cioe il profilo gia riuscito,
e scripts/update-agent.ps1, cioe il motore gia collaudato. Non scegliere un SDK
nuovo perche appare in PATH o ricostruire ogni volta una procedura diversa.
Modalita da Git ordinaria; se Git non e raggiungibile dal destinatario, mantenere
il pacchetto verificato via MCP. Le due acquisizioni convergono sullo stesso
updater e sulle stesse verifiche. Dettagli UPDATE-FROM-GIT-0.8.4.md.
