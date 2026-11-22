# Procedura corrente 0.8.4 — aggiornamento uniforme dal destinatario

Prevale [UPDATE-FROM-GIT-0.8.4.md](UPDATE-FROM-GIT-0.8.4.md): coordinamento nella
chat, stesso agent_update diretto o inoltrato a qualsiasi livello, download Git e
compilazione sul destinatario. Nessun nome o ruolo gateway cablato.
Quando il destinatario non raggiunge Git resta disponibile il pacchetto verificato
trasferito tramite MCP. Entrambe le modalita usano l'unico updater canonico.
[Stato effettivo delle prove](VALIDATION-0.8.4-20261114.md).

## Documentazione precedente

# Agente universale e gateway LAN — 0.8.0

## Ambito approvato il 14 novembre 2026

Ogni agente e completo: controllo del proprio computer e inoltro verso altri
agenti, senza ruolo gateway da selezionare. Nomi liberi e univoci nella
configurazione attiva; identita stabile separata dal nome per riconoscere un PC
sostituito. Non assegnare automaticamente a un nuovo PC l'identita di uno dismesso.

Esempi: ingresso ChatGPT su Desktop1 oppure Desktop2; Desktop1 raggiunge Desktop3
sulla rete principale e Desktop4 su USB LAN; Desktop4 raggiunge Desktop5. Nel
secondo caso servono piu passaggi applicativi, senza rendere Windows un router.
Cloudflare rimane solo sui punti d'ingresso esterni. I collegamenti tra agenti
usano TLS e credenziali proprie, senza tunnel o connettore ChatGPT per ogni PC.

## Trasporto e autorizzazione

L'endpoint storico loopback `/mcp` e il controllo Cloudflare Access dei suoi
strumenti privilegiati restano invariati. Un listener distinto `/gateway`,
TLS >=1.2 e bearer separato, consente gli stessi strumenti pubblici, compresi
strumenti legacy, file, immagini MCP, processi/UI, coda, Codex e aggiornamenti.
Restano tutti i controlli locali su identita, percorsi, privilegi, sessione,
concorrenza, ricevute e costi; un gateway non aggiunge permessi a Windows.

L'installer genera certificato, chiave e credenziale unici sul nuovo PC. Il file
privato `Config/peer-enrollment.json` trasferisce l'autorizzazione al gateway.
`Abbina.cmd`/`device_pair` verificano pin TLS e machine_id prima di salvare il
segreto in `Config/peer-credentials`. Mai segreti negli argomenti MCP, nei log o
in Git. La risposta di abbinamento contiene solo riferimenti, non la chiave.
Le chiavi nuove sono disponibili immediatamente, senza riavvio del gateway.

`GET /gateway/identity` pubblica solo nome, machine_id e versione, senza eseguire
comandi. `device_discover` invia letture senza credenziali: ricerca limitata alle
reti IPv4 private, /24 o piu piccole, massimo 1024 indirizzi e 32 richieste
concorrenti. In automatico una rete piu ampia viene limitata al /24 dell'indirizzo
locale. Specificare un CIDR per un'altra porzione. Scoperta non equivale a fiducia:
un nome univoco seleziona il PC, ma non sostituisce l'autorizzazione iniziale.
Nessuna scansione permanente o installazione su PC senza accesso autorizzato.

## Configurazione persistente

`Config/devices.json` conserva un array compatibile con i record precedenti.

| Campo | Significato |
|---|---|
| device_id | alias stabile locale al gateway |
| name | nome libero; duplicati attivi rifiutati senza distinzione maiuscole |
| machine_id | identita univoca; non sostituibile riusando device_id |
| endpoint | HTTPS `/gateway`, oppure precedente endpoint `/mcp` diretto |
| token_env | riferimento al segreto locale, mai il valore |
| certificate_sha256 | pin del certificato LAN; alternativa a CA fidata |
| via | device_id del prossimo gateway direttamente raggiungibile |
| kind | stable/temporary facoltativo; non influenza le capacita |
| status | active (anche omesso) oppure retired |

Per un record via, endpoint e credenziali appartengono solo al prossimo gateway;
su quello successivo configurare lo stesso device_id finale e machine_id.
Il destinatario originale resta vincolato lungo tutti i passaggi. Le tracce
sono controllate su ogni listener: cicli, identita errate e oltre 8 nodi rifiutati.
Non annidare manualmente device_call in call; descrivere il percorso con via.

`device_list` restituisce SHA della configurazione, disponibilita, osservazioni
con ultimo contatto e record dismessi; le letture di rete sono limitate e parallele.
`device_configure`, `device_pair` e `device_retire` richiedono expected_sha256 e
request_id: scrittura atomica, copia precedente e ricevuta persistente. I nuovi
file usano expected_sha256="missing". Stato osservato separato in
State/runtime-v1/device-observations.json; offline non significa dismesso.
Un nodo retired blocca anche i percorsi che passano da lui. Ogni gateway mantiene
la propria configurazione: la dismissione su uno non modifica gli altri.

## Installazione e aggiornamento

Installer 2.0.0, Windows x64, unico agente 0.8.0. Avvio INSTALLA.cmd con UAC dello
stesso utente; chiede nome, cartella e collegamento locale oppure diretto
Cloudflare. Nessuna richiesta Cloudflare nel percorso locale. Nel diretto la
procedura esistente prepara tunnel e dati del connettore, da verificare in ChatGPT.
Le dipendenze Codex/toolchain/account rimangono quelle del PC, senza acquisti o
copie di attestazioni economiche. La CLI Codex esistente viene rilevata; nessuna
inferenza di prova o installazione implicita di toolchain aggiuntive.

Tutti i nuovi agenti hanno il listener di rete: auto:<porta> apre solo interfacce
private e loopback, rileva adattatori aggiunti/rimossi ogni 5 secondi e usa lo
stesso certificato. Porta predefinita 8766. Regola firewall per eseguibile/porta,
profili Private/Domain e LocalSubnet. Reti Windows Public richiedono una scelta
amministrativa esplicita; il setup non ne modifica il profilo.

Avvio automatico persistente all'accesso Windows tramite attivita pianificata
Interactive/Highest; task senza limite temporale e senza console. Non e un
servizio headless prima dell'accesso. UI solo con sessione attiva/sbloccata.
Nessuna installazione sopra una cartella non vuota; agenti esistenti si aggiornano
tramite l'unico updater. `network_setup` abilita il medesimo listener su agenti
preesistenti mantenendo ingresso esterno e credenziali precedenti.

Gli aggiornamenti remoti attraversano gli stessi percorsi con update_prepare,
update_execute, update_status e log_read. Stessi byte CRLF, hash e update_id
immutabile. Ricevute in State/update-receipts persistono preparazione,
dispatch, PID ed eventuale uscita; dopo un riavvio una partenza non conclusa e
incerta e non viene rilanciata. Batch uscito non significa agente installato.
Verificare ricevuta/hash/versione/identita dell'unico scripts/update-agent.ps1,
backup e rehearsal rollback. Nessun task di aggiornamento e mai taskkill /T
sull'agente. Per il layout corrente prevale la successiva richiesta esplicita
del 14 novembre: eseguibile sotto Agent su tutte le installazioni.
Vedere MCP-CATALOG-0.8.3.md per descrizioni client obsolete e trasferimento remoto.

## Ripresa e prove

Branch feature/agent-gateway-20261114, base ae7d8608d47f72d96bbb6d3c98ec633836f2cd51.
Build: profilo Go1.27.0, GOMAXPROCS=1/GOGC=25/cache isolata/-p1 come gia riuscito.
Installer: scripts/build-network-installer.py oppure .ps1; entrambi includono
sorgenti di supporto, manifest completo e il candidato verificato. Solo pacchetto
generato fuori da Git; mai binari/ZIP nel repository.

Suite Go: trasporto TLS reale, piu gateway, errori di identita/certificato,
richieste legacy, ricevute, dismissione, CAS, nomi, abbinamento privato immediato,
limiti discovery; test Windows aggiornamento remoto con risposta persa e mancato
replay dopo ricostruzione dello stato. Test-NetworkInstaller.ps1 esegue due
installazioni isolate reali, task di avvio, identita/chiavi distinte, assenza dati
Cloudflare nel locale, abbinamento/controllo MCP e pulizia. Prove fisiche di
piu sottoreti, reboot, login e Cloudflare nuovo restano distinte dai test isolati.
Stato effettivo aggiornato in VALIDATION-0.8.0-20261114.md.

Per strumenti legacy con un proprio campo operation (es. task_start) usare
call={operation:"task_start",arguments:{operation:"go_test",project:"..."}}.
Il formato piatto precedente rimane valido per gli altri strumenti. I gateway
preservano l intero oggetto arguments anche nei passaggi successivi; il wrapper
runtime di task_start non va annidato nel trasporto: usare il nome runtime diretto.
