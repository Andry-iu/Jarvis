Prima di scegliere strumenti o diagnosi, leggere [problemi e metodi gia riusciti](KNOWN-BUILD-UPDATE-PROBLEMS.md).

# Aggiornamento uniforme degli agenti — 0.8.4

## Contratto approvato

Il coordinatore e la chat/sistema agentico. Ogni agente ha lo stesso codice:
nessun nome di PC, ruolo o livello cablato. Collegamento MCP diretto e percorsi
`via` di secondo, terzo e quarto livello trasportano lo stesso comando al target.
Il gateway non avvia una propria distribuzione ai discendenti e non compila
per loro. La chat sceglie destinatari, commit/versione e ordine che preservi i
percorsi ancora necessari; raccoglie gli esiti individuali.

## Modalita ordinaria: Git raggiungibile dal destinatario

`agent_update(machine_id,source_commit,target_version,request_id)` avvia sul
solo destinatario un batch separato con `scripts/update-from-git.ps1`, incorporato
nell'agente e presente nell'installer. Repository autorizzato Andry-iu/Jarvis;
commit completo di 40 cifre esadecimali, mai latest/branch risolto separatamente
su ciascun PC. `target_version` deve corrispondere ai sorgenti scaricati.

Esempio diretto:

```json
{"operation":"agent_update","machine_id":"<target>","source_commit":"<commit-40-hex>","target_version":"0.8.4","request_id":"<id-univoco>"}
```

Attraverso un gateway cambia soltanto il contenitore:

```json
{"machine_id":"<gateway>","device_id":"<alias-target>","call":{"operation":"agent_update","source_commit":"<stesso-commit>","target_version":"0.8.4","request_id":"<id-univoco-target>"}}
```

Ulteriori passaggi usano i record `via`: non annidare a mano `device_call`.
Leggere sempre prima l'identita corrente. Catalogo client vecchio: chiamata
locale tramite `task_start(operation=runtime,request={operation:agent_update,...})`.
Gli intermedi devono conoscere lo strumento pubblico; per portare agenti 0.8.3
alla prima versione che lo contiene si usa il bootstrap compatibile sotto.

Procedura comune sul target:

1. Verifica identita, hash/versione precedente, privilegi elevati e coda inattiva.
2. Verifica raggiungibilita Git dal destinatario. Errore esplicito `git_unreachable`.
3. Riusa Git/Go disponibili e adatti, oppure prepara strumenti ufficiali verificati
   sotto Tools/SourceUpdate. MinGit 2.56.0.4 e Go 1.27.0 con hash fissati; nessuna
   modifica al SDK di sistema. Anche questa preparazione e indipendente dal livello.
4. Scarica il commit Git in checkout temporaneo privato e ne verifica SHA/versione.
5. Esegue test completi, vet e build Windows locali con il profilo gia validato:
   Go1.27.0, GOMAXPROCS=1, GOGC=25, cache isolata e -p1.
6. Invoca esclusivamente `scripts/update-agent.ps1`: backup verificato, rehearsal
   candidato/precedente, stop del solo PID agente, sostituzione e avvio configurato.
7. Verifica ricevuta canonica, identita/versione/hash live e manutenzione; elimina
   checkout/cache temporanei/processi e regole firewall dei propri probe. Conserva
   SDK gestiti riutilizzabili, candidato validato, backup e ricevute.

## Modalita alternativa: Git non raggiungibile

Conferma successiva dell'utente: mantenere la modalita gia usata quando il target
non riesce a raggiungere Git, anche se ha altre connessioni Internet funzionanti.
La chat puo trasferire il pacchetto gia compilato e collaudato attraverso MCP,
anche in blocchi, usando `update_prepare/update_execute/update_status` inoltrati.
Verificare il commit di provenienza, versione e hash del candidato; stesso updater
canonico e stesse protezioni finali su qualsiasi destinatario o livello.

Un errore di test, commit errato o esito di promozione incerto non e un permesso
per aggirare i controlli con il pacchetto. Leggere prima stato/ricevute: la modalita
alternativa e una nuova operazione coordinata, mai replay dell'azione incerta.
I limiti 262144 byte/batch CRLF e le ricevute del trasporto rimangono invariati.
L'alternativa non e stata rimossa e non richiede nuove associazioni o tunnel.

## Stato, concorrenza e riavvio

`agent_update_status(machine_id,request_id)` legge il progresso durevole del
TARGET anche dopo riavvio: checking, checking_git, preparing_tools,
downloading_source, building, promoting, verifying_cleanup, completed/failed.
`completed` richiede anche cleanup verificato e corrispondenza con il processo
attivo. La ricevuta iniziale e solo dispatched. Conservare lo stesso request_id
per le letture; una ripetizione identica ritorna la ricevuta senza rilanciare.
Argomenti differenti con lo stesso ID e nuovi aggiornamenti sovrapposti/incerti
sono rifiutati. Uno stato incerto non viene automaticamente ripreso.

La chat non aggiorna un gateway mentre serve ancora per verificare un target.
Nessun task Windows per l'aggiornamento, taskkill /T, approvazione pairing
sintetizzata o esposizione di credenziali al modello.

## Prima adozione da 0.8.3

Gli agenti precedenti non conoscono ancora agent_update. Il piccolo comando di
bootstrap, identico per ingressi diretti e inoltrati, scarica da Git lo stesso
runner del commit pubblicato, ne verifica l'hash e lo avvia tramite il protocollo
update_prepare/update_execute esistente. Il runner scarica/compila sul target;
non trasferisce un eseguibile dal gateway. Dalla 0.8.4 l'entry point e incorporato.
Lo stato del bootstrap usa lo stesso schema di agent_update_status. Dopo il
riavvio la nuova API legge la stessa ricevuta, senza ripetere il lavoro.

## Catalogo

Il server tools/list contiene agent_update/agent_update_status e mantiene tutti
gli strumenti di aggiornamento a pacchetto. La descrizione di device_call esplicita
l'inoltro di entrambe le modalita. Il catalogo cache del client e una superficie
separata: verificarla prima di dire che e aggiornata; usare compatibilita MCP
quando il client non espone ancora i nuovi nomi. Verifiche e distribuzione in
VALIDATION-0.8.4-20261114.md.
