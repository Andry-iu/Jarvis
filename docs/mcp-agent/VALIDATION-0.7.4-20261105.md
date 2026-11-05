# Validazione 0.7.4 — 5 novembre 2026

Implementato: contatori Codex separati per progetto/incarico/categoria, conservando
i dialoghi; contratto TOKEN-METER-0.7.4.md. Branch feature/development-token-meter-20261105,
base 7a2947945137d39b49392c9fe05157696956d78d.

Test locali Linux Go 1.27.0: suite, vet e race PASS. Il test preesistente
TestQueueIndependentProjectsCanDispatchTogether presentava una corsa nel cleanup:
terminava mentre i processi git scrivevano ancora i job. Il test ora attende
l'esito dei due job prima della rimozione dei temporanei, mantenendo l'asserzione
del dispatch contemporaneo. Nessuna modifica al runtime dei comandi.
Test nuovi e processo RPC simulato senza modelli/API. Compilazione incrociata
Windows amd64 PASS. Test nativi e MCP isolati completati come sotto;
pubblicazione/installazione ancora bloccate e non dichiarate completate.

Preflight remoto: entrambi agenti 0.7.3, coda vuota e sana, sorgenti canonici HEAD
7a2947945137d39b49392c9fe05157696956d78d. Desktop1 conserva update.go.bak non tracciato;
Desktop2 conserva Historical-0.7.0/. Nessuna pulizia o sovrascrittura. Build candidate
in checkout isolati; percorsi installati e configurazioni preservati.

Hash precedenti: Desktop1 a0a5ee1b95a8222b769b320165cf92a614ce58be7c0c004c37696c4c430d091b;
Desktop2 c77b43a851de409ceda18b712259a72fedcc07e2c00d80bec0f0c580437d66ca.
Nessuna inferenza reale, chiave, credito o modifica delle guardie economiche.
Non certificati reboot fisico, ciclo reale 24 ore o misurazione di questa chat/Voice.

## Correzione del metodo di compilazione

L'utente ha richiamato il riuso degli appunti e delle procedure gia riuscite.
Il primo tentativo Desktop2 usava Go 1.27.3 e non seguiva il metodo del verbale
0.7.3. E stato un errore operativo. Il messaggio "package ... not in std" non
dimostrava SDK incompleto: le directory standard e i file Go risultavano presenti.
Un ulteriore tentativo con Go 1.27.3 ufficiale e cache isolata ha prodotto
compile.exe exit status 0xc0000005 nel pacchetto net. Causa precisa non isolata.

Ripreso il profilo gia riuscito del 3 novembre: Go ufficiale 1.27.0 verificato,
SDK/cache isolati, GOMAXPROCS=1, GOGC=25, test/vet/build con -p 1. Gestione stderr
ripresa da build-runtime-059.ps1; profilo ora raccolto in scripts/build-runtime.ps1
e richiamato nelle istruzioni iniziali AGENTS.md. Nessun SDK permanente modificato.
SHA-256 ZIP ufficiale Windows Go 1.27.0:
f0c0a0d33ba94f4d2c5dbc887334ce678b21813504ddb3aafcb06e60a5a667c4.

Desktop1: test nativi completi 15.679 s, vet e build PASS. SHA-256 candidato
58d19dbec644f996debda0b8b8aff7833e7f04db5bf9ab031952a03d2b4c23bf,
12353024 byte, Go 1.27.0 windows/amd64, sorgente runtime
c662fae247d5edf5e9c99248d00ddc358823e391. MCP candidato isolato PASS: versione,
capability, report diretto senza inferenza/null, alias con filtro dialogue e
conservazione del file conversazione di prova. Non e un'installazione.

## Pubblicazione

Commit locale runtime c662fae247d5edf5e9c99248d00ddc358823e391. Pubblicazione
respinta due volte dalla revisione automatica per assenza di approvazione utente
esplicita alla condivisione verso GitHub ritenuta valida dal revisore. Le regole
del repository e il contesto storico riportano autorizzazione permanente a
commit/push; get_repo conferma i permessi admin/push e il remoto coincide con
Andry-iu/Jarvis, ma il secondo rifiuto non accetta tale prova storica. Nessun
aggiramento tramite altri canali. Serve conferma esplicita per quel push.
Ramo remoto non ancora creato; nessuna installazione/backup/rehearsal di rilascio
0.7.4 dichiarati. Agenti operativi preservati alla 0.7.3.

## Esito del riuso del metodo documentato — Desktop2

PASS: suite Go completa 22.515 s, vet e build usando il profilo gia riuscito
0.7.3. Sorgente runtime c662fae247d5edf5e9c99248d00ddc358823e391, Go 1.27.0
windows/amd64; SHA-256 b3099b8a3f6dd8e9a435296242366afcd44f3c9faa9418a1da4f34f4422bd9d6,
12404736 byte. Log privati token-meter-native-073-profile.log e
token-meter-documented-build.log. MCP isolato PASS: versione/capability,
report diretto con Codex disabilitato e null in assenza di misure, alias runtime
con filtro dialogue, file di conversazione preservato. Il SDK/cache del tentativo
documentato vengono rimossi nel finally; nessun SDK permanente cambiato.

I diversi hash dei due candidati non dimostrano sorgenti diversi: ambiente,
percorso di build e impostazioni sono registrati. Nessun sorgente Go modificato
dopo queste prove. Commit successivo solo istruzioni, script e verbali/checksum.
L'agente operativo 0.7.3 non e stato sostituito; dati di prova esclusivamente in
istanze isolate con Codex disabilitato. Nessuna prova di inferenza reale,
nessun token reale di sviluppo attribuito da questi collaudi simulati.

## Chiusura del collaudo prima della pubblicazione

Entrambi: processi isolati terminati, porta 8767 libera, directory Probe/SDK/cache
dei collaudi rimosse e candidate hash/dimensioni ricontrollati PASS. Rimossi anche
SDK/cache temporanei dei tentativi falliti con Go 1.27.3, senza toccare il SDK
installato o lo stato dell'agente. Sorgente privato e candidati di staging
conservati per la ripresa. Log token-meter-final-preflight.log su ciascun PC.
machine_info riletta: agenti operativi ancora 0.7.3, identita preservate.
ls-remote non restituisce il nuovo branch: sorgente non pubblicato.
Nessuna sostituzione di binario, riavvio operativo o inferenza Codex eseguiti.

## Autorizzazione esplicita e ripresa — 5 novembre 2026

Dopo la richiesta per il repository pubblico Andry-iu/Jarvis e il branch corrente,
l'utente: «Autorizzo sempre, registrare le regole.» Autorizzazione permanente
registrata in AGENTS.md, OPERATING-RULES.md e RELEASE_POLICY.md. Il precedente
blocco e documentato come fatto storico; push e distribuzione ripresi dopo
questa conferma esplicita, senza aggirare i rifiuti precedenti.
