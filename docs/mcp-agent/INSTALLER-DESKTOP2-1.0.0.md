# Installer Desktop2 1.0.0 — pacchetto per agente 0.6.1

Richiesto dall'utente il 31 ottobre 2026: preparare l'installazione sul secondo PC, con collegamento indipendente da Desktop1. Non e un aggiornamento del servizio Desktop1 e non modifica l'app Android.

## Uso

Copiare lo ZIP sul secondo PC Windows x64, estrarlo e aprire INSTALLA.cmd. Consentire UAC con il proprio utente amministratore e scegliere cartella installazione e root dei progetti. Default C:\Jarvis-Desktop2 e C:\Jarvis-Desktop2\Projects. L'installer rifiuta directory non vuote, alias/junction e root di progetto sovrapposte allo stato privato.

Genera credenziale casuale e stato nuovi; non copia identita, configurazioni, progetti, archivio, login Codex o prova costi di Desktop1. Il binario gia verificato 0.6.1 e incluso; Go non serve per installarlo. Le toolchain dei progetti restano prerequisiti separati.

Un task Windows interattivo/elevato avvia l'agente all'accesso del medesimo utente, senza limite temporale. Non e un servizio prima del login. Desktop1 puo essere spento; il nuovo PC deve essere acceso, con sessione disponibile. Avvia/Arresta/Verifica e Disabilita-avvio sono nella cartella installata. Lo stop e esplicito e riguarda solo PID/percorso/tempo di avvio registrati; stato e archivio sono conservati.

## Cloudflare e plugin

Sono automatizzati download ufficiale cloudflared con SHA-256 del release asset, configurazione locale e avvio del tunnel. La configurazione remota nell'account Cloudflare NON e automatizzata: mancano un collegamento di gestione Cloudflare e credenziali autorizzate per quel servizio. Non dichiarare che basti un login per creare tutte le risorse.

Creare un NUOVO tunnel e hostname per Desktop2 (non una replica di Desktop1), collegato all'HTTP locale sulla porta scelta. Configurare Access su tutto l'hostname, policy del proprio account e Managed OAuth/DCR per il client ChatGPT. La procedura chiede hostname HTTPS, team domain, AUD, UUID e token del nuovo tunnel, con input segreto locale. Verifica che il token appartenga all'UUID indicato. Token in Config privata, mai in argomenti dei processi, chat o Git; comando cloudflared con --token-file.

Se non pronti, rinviare questa fase e riprenderla da Configura-Cloudflare.cmd a processo fermo. Dopo avvio collegare il NUOVO plugin Jarvis-Desktop2 con endpoint https://HOSTNAME/mcp e OAuth. Il file Collegamento-ChatGPT.txt contiene solo URL e indicazioni pubbliche. Verifica locale non equivale a collaudo remoto/OAuth. Non cambiare tunnel, Access o plugin Desktop1.

Nessun dominio acquistato, upgrade, abbonamento, credito o API OpenAI: utilizzare solo risorse gia incluse. Se il provider richiede spesa fermarsi. Il pacchetto non accede all'account Cloudflare, non crea risorse remote e non cambia fatturazione.

## Codex e sicurezza economica

Auto-rilevato solo il pacchetto locale OpenAI.Codex con CLI codex-cli 0.165.0-alpha.1.2. Altrimenti resta disabilitato; Abilita-Codex.cmd permette rilevamento successivo. Login ChatGPT Business sul secondo PC resta a cura dell'utente. Nessuna inferenza durante installazione, nessuna API key o attestazione economica generata/coperta: un saldo null puo bloccare i turni fino a verifica indipendente valida per nuovo account/PC. Non trasferire la prova di Desktop1.

## Provenienza e riproduzione

- Binario incluso 0.6.1 Windows amd64, 12.092.416 byte.
- SHA-256 9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1.
- Commit codice binario 164cb980bd1a936749d41b8692979e98b929cb04.
- Base operativa precedente 18f7d2fe83defa762e9aca6c12dfb8c083194fc8.
- Sorgenti installer e test in windows-mcp-agent/installer, incluso package.json con hash dei payload; binario non committato.
- scripts/build-desktop2-installer.ps1 riceve AgentPath del binario esatto e OutputDirectory nuova; normalizza CRLF, verifica tutti gli hash e produce ZIP. RunTests opzionale esegue le prove isolate Windows.
- File LEGGIMI, COLLAUDO e licenze inclusi. Pacchetto non firmato Authenticode; hash rileva corruzione, non sostituisce una firma.

## Verifiche effettuate e limiti

Suite Test-Installer.ps1 completata su Desktop1: parser, due installazioni reali isolate su porte 8768/8769 e percorsi con spazi, credenziali e ID distinti, nessuna prova costi copiata, rifiuto installazione esistente/root privata/porta occupata/payload corrotto, input Access invalidi, task interattivo/elevato senza limite, avvio via task, MCP con autenticazione e rifiuto HTTP 401 senza token, catalogo/coda, riavvio con identita conservata e disabilitazione task senza fermare attivita corrente. Processi/task delle copie di prova rimossi; stato di prova conservato. COLLAUDO.txt e log locale diagnostics/installer-061-test.log.

Primo confezionamento rifiutato prima di mutazioni per hash CRLF: corretta normalizzazione. Prima asserzione sul task errata: State puo restare Running dopo Disable; controllo corretto su Settings.Enabled. Il test finale e passato. Nessuna modifica alle guardie economiche o al binario operativo.

Non collaudati: destinazione fisica Desktop2, interazione UAC al doppio clic, reboot completo, download cloudflared, Cloudflare/DNS/OAuth, nuovo plugin e inferenza Codex sul secondo PC. Nessuna installazione sul secondo PC dichiarata.

Installazione solo nuova: nessun upgrade/sovrascrittura di un servizio esistente. Config precedenti salvate in Backups nelle modifiche atomiche. Su errore la cartella viene conservata per diagnosi; disabilitare soltanto il task proprio e arrestare esplicitamente i processi posseduti. Non cancellare automaticamente stato o archivio per riprovare.

Fonti primarie: https://developers.cloudflare.com/tunnel/reference/run-parameters/ ; https://developers.cloudflare.com/tunnel/reference/tunnel-tokens/ ; https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/managed-oauth/ .

## Consegna e verifica finale

Archivio consegnato Jarvis-Desktop2-Setup-1.0.0.zip, 6.741.728 byte, 16 file:
SHA-256 67819dad148f5c33eba12994595090199b75d5beef70ecf44391423757325606.
Integrita ZIP e hash di ciascun payload verificati sul file finale, incluso il binario 0.6.1. Nessun file State/Config/segreto/progetto utente incluso.

Il builder PowerShell e stato eseguito su Desktop1, parser e tutti gli hash payload superati. Verifica finale installer-final-100.json: PASS; servizio operativo 0.6.1 con hash invariato, zero listener di prova 8768/8769 e zero task di prova rimasti. HEAD/origin allora ae57ef3bbef753da0065d20d16ae13bcb1f51fa1, tracciati puliti. Il presente aggiornamento e solo documentale e non richiede nuova installazione. Il contenitore ZIP prodotto da strumenti diversi puo avere hash diverso per metadati/compressione; gli hash dei payload del manifest sono il controllo dei contenuti.
