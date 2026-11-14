Prima di scegliere strumenti o diagnosi, leggere [problemi e metodi gia riusciti](../docs/mcp-agent/KNOWN-BUILD-UPDATE-PROBLEMS.md).

# Agente 0.8.4 — procedura uniforme in validazione

agent_update e agent_update_status implementati: stessi parametri e runner sul
destinatario, tramite MCP diretto o piu gateway; Git e compilazione locali.
Modalita pacchetto conservata per Git non raggiungibile. Suite Linux/race/vet e
cross-build PASS; distribuzione Windows ancora da verificare.
[Specifica](../docs/mcp-agent/UPDATE-FROM-GIT-0.8.4.md), [validazione](../docs/mcp-agent/VALIDATION-0.8.4-20261114.md).

## Stato precedente

La chat coordina: niente Desktop1 fisso o build delegata al gateway. Appunti di tutti i destinatari aggiornati dopo le verifiche.

# Aggiornamenti remoti: regola operativa 14 novembre 2026

Leggere ../docs/mcp-agent/MCP-CATALOG-0.8.3.md. `device_call` inoltra anche
update_prepare/update_execute/update_status/log_read. Il testo storico del client
che lo nega non descrive il runtime live. Verificare tools/list e sorgente prima
di dichiarare limiti. Su PC gia associato trasferire automaticamente il candidato
verificato e invocare lo stesso updater canonico, senza passaggi manuali o nuovo
pairing. Separare preflight, dispatch, completamento e verifica con ricevuta/hash.
Registrare esito e causa di precedenti indicazioni errate in Git e negli appunti.

## Regole utente — 14 novembre 2026: layout, elevazione e pulizia

- Percorso unico per tutte le installazioni: <Jarvis>\Agent\jarvis-mcp.exe.
  Indicazione iniziale root corretta dall'utente prima delle modifiche: non applicarla.
  Allineare Desktop1 e verificare anche Desktop2; migrazione ora esplicitamente autorizzata.
- Agente sempre amministratore al termine degli interventi. Avvio automatico dopo
  riavvio, all'accesso dell'utente, con attivita pianificata Interactive/Highest,
  come SoftPlcTest1. Verificare percorso e token elevato effettivi dopo il riavvio
  dell'agente; registrare il test di task separatamente da un reboot fisico.
- Dopo ogni prova/elaborazione, anche fallita o interrotta, arrestare i processi
  di test e rimuovere/verificare cartelle temporanee e regole firewall provvisorie
  proprie. Non lasciare residui nei percorsi operativi o in Projects; tutelare
  altre elaborazioni, stato, progetti, appunti, backup e ricevute definitive.
- Aggiornare anche D:\Jarvis\Projects\appunti.txt su entrambi i desktop,
  oltre a documentazione/continuita Git. Non dichiarare pulito o riavviato senza
  verifica. Non disabilitare globalmente firewall/notifiche o cambiare il profilo
  di rete per eliminare i prompt dei test.

# Inserimento codice 0.8.3

Per nuovi abbinamenti leggere PAIRING-0.8.3.md e VALIDATION-0.8.3-20261114.md.
Codice dal gateway alla chat, casella vuota sul target, corrispondenza obbligatoria
anche nel launcher Go. Conto alla rovescia 110s e tre errori. Non inserire codici
o approvare reali richieste tramite automazione. Test GUI solo dummy e rifiuto;
matching corretto verificato nelle funzioni isolate, senza creare fiducia.
Versione unica 0.8.3. Non confondere sorgenti pubblicati con agenti aggiornati.

# Correzione GUI0.8.2

Prima di intervenire leggere FIX-0.8.2.md e VALIDATION-0.8.2-20261114.md in
docs/mcp-agent. Helper di consenso: CREATE_NO_WINDOW, mai HideWindow/SW_HIDE.
Verificare il launcher Go reale con JARVIS_TEST_PAIRING_UI=1 su Windows sbloccato;
non sostituire questa prova con ProcessStartInfo o parse. Dummy chiudibile, mai
automatizzare un Consenti. Versione unica0.8.2 sorgenti/agente/installer.

Versione unificata concordata: sorgenti, agente e installer condividono la stessa
release; nome pacchetto Jarvis-Agent-Setup-<versione>.zip. Non introdurre un altro
contatore per il solo installer.

# Abbinamento0.8.1

Leggere docs/mcp-agent/PAIRING-0.8.1.md. Usare begin, confronto codice e conferma
locale, status e complete CAS; nessun file da trasferire. Non automatizzare la
conferma via UI remota. PC0.8.0 non associato: AGGIORNA.cmd del pacchetto0.8.1.
Stato reale in VALIDATION-0.8.1-20261114.md; niente promesse di collaudo fisico.

# Gateway universali — 14 novembre 2026

Per la 0.8.0 leggere docs/mcp-agent/GATEWAY-0.8.0.md,
CLIENT-INSTRUCTIONS-0.8.0.md e VALIDATION-0.8.0-20261114.md dalla root Git.
Ogni agente ha controllo e inoltro; nessun ruolo gateway da selezionare. Nomi
liberi univoci; configurazione persistente su ogni gateway, retired distinto da
offline. Installer chiede nome e ingresso locale oppure diretto Cloudflare;
locale senza dati Cloudflare. Avvio automatico all'accesso Windows.
Via esplicito dell'utente a implementare, documentare, committare/pubblicare e
aggiornare automaticamente Desktop1 e Desktop2, con installer riutilizzabile.
Non richiedere un altro via per la stessa distribuzione. Usare sempre il comune
update-agent.ps1 dopo test, hash, backup e rehearsal; verificare esiti reali.
Le istruzioni successive restano valide; i dettagli 0.8.0 prevalgono sullo storico.

# Jarvis Windows MCP Agent — istruzioni di progetto

## Autorizzazione permanente — 5 novembre 2026

Alla richiesta di autorizzare il push della 0.7.4 nel repository pubblico
Andry-iu/Jarvis, ramo feature/development-token-meter-20261105, l'utente ha
risposto: «Autorizzo sempre, registrare le regole.»

Per gli interventi Jarvis gia approvati, commit e push dei sorgenti/test/documenti
su Andry-iu/Jarvis sono autorizzati stabilmente, anche sui relativi branch di
lavoro. Non chiedere nuovamente il consenso per ogni push o nome del branch.
Questa autorizzazione comprende la pubblicazione del lavoro 0.7.4 corrente.
Conservare ambito, verifiche e procedure gia approvati; nessun merge implicito
su main, force-push, segreto, log grezzo o stato privato pubblicato. Le richieste
di nuove funzioni restano distinte dall'autorizzazione alla pubblicazione.

Questo sottoprogetto e l'agente Windows Jarvis MCP / Jarvis-Desktop1, distinto dall'app Android nello stesso repository. Le regole Android su APK, firma, cover, Gradle e cloud Android non definiscono l'ambiente di questo agente: l'utente ha autorizzato sviluppo, test e installazione su Desktop1.

Prima di intervenire leggere README.md, CONTINUITY.md, CHANGELOG.md e ../docs/mcp-agent/{STATUS,RELEASE_POLICY,OPERATING-RULES,VALIDATION-0.7.0-20261101,SELF-UPDATE}.md. Verificare identita e versione correnti con machine_info; non assumere che gli ID dei verbali siano ancora il bersaglio corretto. Consultare coda, job, ricevute e sessioni del progetto esatto prima di nuove mutazioni.

Compilazione Windows: prima di scegliere un SDK leggere VALIDATION-0.7.3-20261103.md.
Il metodo gia riuscito su Desktop2 usa Go ufficiale 1.27.0 verificato, SDK e cache
isolati, GOMAXPROCS=1, GOGC=25 e -p 1 per test/vet/build. Non ricominciare con il
Go 1.27.3 rilevato da machine_info: ha gia fallito. Usare scripts/build-runtime.ps1,
che riprende questo profilo e la gestione stderr del precedente build-runtime-059.ps1.
Nessun cambio al SDK permanente. Errori "package ... not in std" non dimostrano da
soli file assenti: in questa sessione le directory esistevano e il tentativo con
cache nuova ha restituito un arresto del compilatore 0xc0000005. Causa non isolata.


- Branch di riferimento: reconcile-windows-mcp-agent-20261031. Dal 3 novembre 2026 l utente autorizza permanentemente creazione/uso di ulteriori branch di Andry-iu/Jarvis, con commit/push per i lavori approvati, senza nuova conferma per il solo nome del branch. Non anticipare il via allo sviluppo. Verificare il remoto attuale e le modifiche locali; nessun merge implicito su main, force push o modifica Android.
- Sorgenti operativi D:\Jarvis\MCP-Source\windows-mcp-agent; root Git D:\Jarvis\MCP-Source. Checkout sparse: includere windows-mcp-agent e docs/mcp-agent. I documenti remoti sono la fonte se non presenti localmente.
- Ogni rilascio richiede sorgenti/test/documenti/versione/checksum su Git remoto, test/vet/build Windows, backup verificato, rollback isolato, installazione e collaudo MCP. Distinguere ciascun esito. Non sostituire l'agente funzionante prima di queste protezioni.
- Conservare backup, stato/identita/ricevute, update.go.bak e stash backup update.go locale 20261031. Non usare git clean o cancellazioni ricorsive indiscriminate. Staging e binari non vanno aggiunti a Git.
- Zero costi aggiuntivi: niente API key, crediti/acquisti/ricariche, upgrade, tier a pagamento o fallback. Non modificare/estendere prove o guardie per far passare un turno. Saldo null significa sconosciuto. Prova costi privata con account/PC e scadenza massima sette giorni; mai segreti o dati account in Git/chat/log.
- Consultazione quota: codex_status, senza inferenza. Prima del turno controllare disponibilita all'avvio effettivo. Dopo ogni turno attendere jarvis/usage/final_usage e riferire il dato corrente o l'errore, senza confronti o riuso di letture precedenti.
- command_start/codex_turn restituiscono queue_id: consultare queue_list per job_id/turn_id e gli eventi esatti. queue_cancel riguarda solo attesa; lavori attivi richiedono job_cancel/codex_interrupt e verifica finale. Riordino non interrompe il lavoro attivo; rispettare depends_on.
- Un lavoro per progetto/percorso sovrapposto, fino a quattro progetti indipendenti. Letture disponibili durante elaborazioni; file in scrittura possono essere indisponibili. Coordinare risorse globali e processi esterni. Nessun replay automatico dopo disconnessione/esito incerto.
- Usare runner fisso e API file quando sufficienti. update_prepare/update_execute servono per distribuzione e script di collaudo/diagnosi non eseguibili con i profili, con byte/hash esatti e verifica successiva. Non usarli per aggirare protezioni o decisioni sulle approvazioni.
- Catalogo precedente: task_start con operation=runtime e request completa (operation e machine_id). Non dichiarare aggiornato il catalogo client senza verificarlo. Non promettere tutte le chat/Voice, altri PC o reboot sulla sola base delle prove locali.
- Non riprendere NestingOpt, Android o configurare altri computer senza la relativa richiesta.


## Ambito approvato e consegnato 0.6.1
Leggere docs/mcp-agent/IMPROVEMENTS-0.6.1.md e PROJECT-RECORD.md (percorsi relativi alla root Git). Nessuno stop temporale o avviso programmato per i turni Codex. queue_list e operativo; activity_history recupera terminali. Ogni progetto mantiene su Git un registro curato delle attivita significative aggiornato insieme a commit/rilasci, mai esportazione automatica dello stato privato. Dichiarare risorse condivise note, preservando parallelismo dei lavori indipendenti.

## Regole consolidate 0.7.0 / installer 1.3.0

Leggere e applicare `../docs/mcp-agent/OPERATING-RULES.md`: raccoglie le decisioni approvate fino al 1 novembre 2026. Aggiornamento sempre avviato dall'agente tramite batch CMD separato e unico `scripts/update-agent.ps1`, mai task di aggiornamento o `taskkill /T`. Dichiarare differenze di configurazione e risolvere le cause dei problemi; Desktop1 conserva il percorso storico nella root, Desktop2 e nuovi installer usano Agent. Nessuna migrazione implicita.

Per applicazioni usare strumenti diretti MCP e salvataggio nell'app, non batch sostitutivi. Processi non amministratore per default; elevazione solo esplicita. Manutenzione log/batch 30 giorni, avvio/24 ore, proteggendo batch attivi e stato. Installer 1.3.0 contiene agente 0.7.0 e updater canonico, identita/credenziali nuove per ogni PC; agente completo precedente prima aggiornato tramite il proprio batch.

Git: tutto il sorgente dell'installer, test, risorse e documentazione; nessun ZIP o binario generato, neppure come sostituto dei sorgenti. Pubblicazione autorizzata sul branch concordato senza nuova conferma; verificare HEAD remoto e distinguere prove effettive da limiti aperti. Le regole Android restano nel loro ambito.

## Misurazione token dalla 0.7.4

Per lavori di sviluppo inviati tramite codex_turn usare usage_category=development.
Dialoghi: dialogue; altri impieghi: other. Se l'intento non e noto omettere o usare
unclassified, senza dedurlo retroattivamente da testo o quota. Lo steering mantiene
la categoria del turno: per cambiare categoria aprire un altro turno. Leggere
codex_token_usage per progetto; default development. Report e ledger misurano solo
Codex inoltrato dall'agente, mai questa chat/Voice/API esterne. Nessun dialogo da
eliminare. Input include cache; output include ragionamento. Non sommare i
sottoinsiemi; conservare unknown/null e stato incompleto. Vedere TOKEN-METER-0.7.4.md.
