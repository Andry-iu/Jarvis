## Stato operativo verificato: Desktop1 e Desktop2 aggiornati alla 0.8.7 (22/11/2026)

**Desktop1**: installazione 0.8.7 eseguita e verificata con `agent_update` request_id `jarvis-d1-update-087-20261122-155814`, sorgente `d8936a750c9c8513342b5a699941486105838faf`. Esito `completed` alle 16:00:44 UTC, `cleanup_verified=true`, versione live `0.8.7`, PID `147264`, SHA256 installato `4364e65959c2955a0094fec5a2c737bfc05ac4fe4ae8bf0da865bba15cb12fd4`. Backup `D:\Jarvis\Backups\self-update-5dd8dbf312f348ea9e88b70c69728efd`. Un tentativo successivo di interrogare il connettore ha restituito errore interno; non annulla la ricevuta precedente.

**Desktop2**: partenza confermata da `0.8.4` con agente amministratore inattivo. Il vecchio aggiornamento alla `0.8.5` (`jarvis-source-update-d2-085-20261119-01`) risultava da oltre 48 ore in `building`; runner lock libero e nessun processo corrispondente. Solo il riferimento `active.json` del vecchio tentativo e stato **archiviato**, senza cancellare la ricevuta del precedente aggiornamento ne reinstallare la 0.8.5. Nuova richiesta separata `jarvis-d2-update-087-20261122-170630` dal commit `d8936a750c9c8513342b5a699941486105838faf`: `completed` alle 16:08:11 UTC, `cleanup_verified=true`, `machine_info.version=0.8.7`, PID `5476`, SHA256 `072277d7ef9f04481f344b469d14accb83f8030ff62db90d3f2666316fa7c789`. Backup `D:\Jarvis\Backups\self-update-83cea8fcba0f4cc48b490cb66ed80d76`. Dopo il riavvio `agent_status.state=idle`; rimossi i quattro file diagnostici temporanei dalla root progetti di Desktop2.

**Release complessiva:** il codice coordinato 0.8.7 e in `main`; le due installazioni Windows sono verificate. Rimangono **non completati** il pacchetto ZIP installer 0.8.7, gli APK Android firmati/da distribuire, il collaudo hardware ESP32 e l'esposizione del tool `ui_mouse` nel connettore ChatGPT con prova reale di spostamento e clic. Non presentare questi controlli come gia superati. Le annotazioni inferiori, con stato precedente dei desktop, sono storico e non il quadro corrente.

---

## Rettifica operativa del 22 novembre 2026: Desktop1

**Desktop1 e stato gia modificato durante questa conversazione:** il deploy 0.8.6 e stato eseguito e verificato tramite `agent_update` (`jarvis-mouse-086-desktop1-20261122-152330`), con versione live 0.8.6, backup, hash installato e pulizia verificati. Solo **in seguito** il connettore Desktop1 ha iniziato a restituire `The tool failed internally`. Non confondere questo errore successivo con il mancato intervento iniziale.

Il successivo ordine di aggiornare Desktop1 alla **0.8.7** e stato ricevuto ma **non eseguito**: il comando `agent_update` 0.8.7 non e stato inviato, perche la connessione diretta risultava indisponibile. Non esistono quindi ricevute che dimostrino una 0.8.7 installata.

L'utente ha chiesto espressamente di **non toccare Desktop2**. Nel tentativo precedente sono state compiute interrogazioni di sola lettura su Desktop2 per cercare una via di comunicazione verso Desktop1; non sono state inviate modifiche, accoppiamenti o aggiornamenti a Desktop2. Dopo la correzione dell'utente, **non contattare ulteriormente Desktop2, neppure come gateway, per questa attivita**.

Il ripristino del collegamento diretto Desktop1 resta necessario per procedere all'aggiornamento alla 0.8.7 e per controllare i comandi mouse. Nessun aggiornamento di un computer deve essere inferito da test Git o compilazioni.

> **Stato del repository 22/11/2026:** il sorgente coordinato **0.8.7** e stato integrato in `main` con PR #1 e merge commit `f0408ac4a2d1826bc74b71319462cc638fc33a28`. Test Go tripli, vet, build Linux/Windows e parser PowerShell superati (GitHub Actions `45040466751`). **Non e ancora una release binaria installata, firmata o distribuita.** L'ultima versione Windows effettivamente verificata su Desktop1 resta 0.8.6. Vedere [UNIFIED-RELEASE-0.8.7-20261122.md](UNIFIED-RELEASE-0.8.7-20261122.md). Non confondere la numerazione sorgente con APK firmati o un installer ZIP collaudato.

> **Aggiornamento verificato 22 novembre 2026:** Desktop1 / DESKTOP-1ESSB0K e stato aggiornato alla versione **0.8.6** dal commit sorgente `f8bec6ee37c0c4bc15315815f3768e8a7d3ba3b2`, tramite `agent_update` con request_id `jarvis-mouse-086-desktop1-20261122-152330`. Riscontro: `agent_update_status.phase=completed`, `cleanup_verified=true`, `machine_info.version=0.8.6`, nuovo PID 149104, installato SHA256 `a538b7e70e20976ad79b8221437afb461e9e8bb8ce4f5fc26a6aa096d15838fc`. Sorgenti/test Go/vet/build e parser PowerShell superati prima del deploy. **Non e stata verificata la disponibilita di `ui_mouse` nell'elenco strumenti ChatGPT**: il nuovo supporto a `move` resta non collaudabile da questa chat finche il connettore non viene aggiornato/ricollegato. La documentazione sottostante mantiene lo storico osservato il 19/11 per confronto; non usarlo come ultima versione corrente. Installer 0.8.6: script allineati nel ramo, pacchetto ZIP finale non ancora verificato/pubblicato.

## Regola di ripresa architetturale (19 novembre 2026)

Prima di introdurre un modulo MCP o predisporre un nuovo installer, leggere [HANDOFF-GENERIC-AGENT-PROCEDURES-20261119.md](HANDOFF-GENERIC-AGENT-PROCEDURES-20261119.md) e [PROCEDURES.md](PROCEDURES.md). Il core di Jarvis deve restare **indipendente dalle applicazioni**. Per azioni occasionali usare gli strumenti generici e batch/PowerShell in `<Jarvis>\Procedures`, non un modulo dedicato (es. Proxmox). Aggiornare agente/installer solo per primitive universali, con test e verifica distinta. I commit di sviluppo per input mouse/UI web non costituiscono una nuova release: ultima installata/verificata Desktop1 0.8.4.

# Jarvis Windows MCP Agent — release corrente e ripresa obbligatoria

> Stato osservato il **19 novembre 2026**. Questo documento e il punto di ingresso
> per rispondere a "qual e l'ultima versione Jarvis?", riprendere lo sviluppo
> dell'agente Windows o consegnare l'installer. **Non** e una dichiarazione che
> questa rimarra sempre la release piu recente: prima di ogni risposta verificare
> Git remoto e, quando possibile, gli agenti in esecuzione.

## Identificazione del progetto

- Progetto: **Jarvis Windows MCP Agent / JarvisRuntime**, distinto da **JarvisWake Android**.
- Repository: `Andry-iu/Jarvis`.
- **Ramo di lavoro piu avanzato verificato per Windows MCP:** `feature/unified-agent-source-update-20261114`.
- **Ultima release pubblicata e collaudata verificata al 19/11/2026:** **0.8.4**.
- Versione **unificata** di sorgenti, agente e installer: `0.8.4` (non usare
  separatamente un numero `Setup 1.x` o `2.0.0` come numero dell'agente).
- Sorgente della release: commit esatto
  `ff2038d4b250c9d3e7e6590c2409ad55e5081380`.
- `main` non comprende necessariamente le evoluzioni dell'agente Windows:
  **non consultare solo `main` e non dedurre l'ultima versione dal README di JarvisWake**.
- Il ramo `feature/agent-gateway-20261114` contiene lo storico fino a `0.8.2`;
  e **superato** dal ramo di lavoro sopra. La `0.8.3` ha introdotto il codice
  da digitare nel pairing; la `0.8.4` ha introdotto `agent_update` da commit Git
  anche attraverso gateway. Non consegnare `0.8.2` come ultima release solo
  perche e la versione trovata su un ramo precedente.

## Artefatto installabile verificato

- Nome: `Jarvis-Agent-Setup-0.8.4.zip` per Windows x64.
- SHA-256 del **pacchetto ZIP**:
  `d10fa8e6d5e9d174660f0ca77e2d2ed1873ed9e8c4b305d66cb26c2494c2ccbe`.
- Dimensione: `7472565` byte.
- Posizione della copia privata nella Library: `/JarvisRuntime/Jarvis-Agent-Setup-0.8.4.zip`.
- Manifest verificato: [CHECKSUMS-0.8.4.json](CHECKSUMS-0.8.4.json).
- Verbale: [VALIDATION-0.8.4-20261114.md](VALIDATION-0.8.4-20261114.md).
- Nuovo PC: estrarre ZIP ed eseguire `INSTALLA.cmd`; scelta LAN via altro
  gateway o accesso diretto Cloudflare. Ogni agente e anche gateway.
- Agente gia installato: **non** usare `INSTALLA.cmd` sulla cartella esistente;
  utilizzare `AGGIORNA.cmd` quando compatibile oppure
  [agent_update](UPDATE-FROM-GIT-0.8.4.md) dal commit esatto, con backup,
  ricevute, rollback rehearsal e verifica finale.
- In Git solo sorgenti/test/manifest/documentazione: **mai** binari, ZIP,
  credenziali, private key, token Cloudflare, copie di `secrets.env` o
  configurazioni locali segrete.

## Stato delle installazioni (separare storico e lettura live)

| Dispositivo | Ultima versione verificata | Origine della verifica |
| --- | --- | --- |
| Desktop1 / DESKTOP-1ESSB0K | **0.8.4** | **Lettura live** `machine_info` e `agent_status` da ChatGPT il 19/11/2026 alle 09:56 UTC: `version=0.8.4`, `elevated=true`, `state=idle` |
| Desktop2 / AUTECHVPC2 | **0.8.4** | Deploy e audit finali verificati il 14/11/2026; **non** una lettura live del 19/11 |
| Target LAN gia associato tramite gateway | **0.8.4** | Deploy e letture autenticate via gateway verificati il 14/11/2026; **non** una lettura live del 19/11 |

Per Desktop1, `machine_id=1b927a030fa3b7dfa15c40cfccb4ff60`.
Prima di operare sui PC, rileggere `machine_info` per confermare identita,
versione, privilegi e capacita. Un errore del connettore non dimostra che l'agente
sia offline, disinstallato o retrocesso. PID e stati operativi sono transitori.

## Procedura **obbligatoria** a ogni nuova chat / ripresa

1. **Riconoscere il prodotto:** distinguere Jarvis Windows MCP Agent, JarvisWake
   Android, ESP32 e altri progetti con nome Jarvis. Non incrociare le versioni.
2. Leggere questo file, [STATUS.md](STATUS.md),
   [PROJECT-RECORD.md](PROJECT-RECORD.md) e
   [UPDATE-FROM-GIT-0.8.4.md](UPDATE-FROM-GIT-0.8.4.md) per l'ultimo stato noto.
3. Su GitHub **verificare i rami Windows MCP esistenti**, in particolare quello
   indicato qui e ogni ramo piu recente: confrontare commit, README, AGENTS,
   `CHECKSUMS-<versione>.json` e validazioni. Non presumere che `main` sia
   il ramo corretto o che un ramo datato sia il piu recente. Distinguere un
   commit documentale da una nuova release del binario.
4. Se l'utente chiede la **versione installata**, usare `machine_info` o
   `agent_status` sul PC specifico; se la macchina e raggiungibile via
   gateway, usare `device_list`/`device_call` preservando l'identita del
   destinatario. Non sostituire osservazioni live con documentazione storica.
5. Se chiede uno **ZIP**, preferire l'artefatto originale verificato e confrontare
   nome/dimensione/SHA-256 con il manifest. Non creare uno ZIP di soli sorgenti
   spacciandolo per installer eseguibile. Non inventare link di download.
6. Riportare sempre separatamente: ultima release verificata nel repository,
   ramo/commit, ultima versione osservata su ciascun dispositivo e data,
   eventuale installazione/connessione non verificabile.
7. Dopo **ogni nuova release** aggiornare **nello stesso lavoro** questo
   documento, `STATUS.md`, `windows-mcp-agent/README.md`,
   `windows-mcp-agent/AGENTS.md`, continuita e registro del progetto:
   versione, branch, commit di sorgente, manifest SHA-256, artefatto,
   dispositivi realmente verificati e limiti, prima di dichiarare completato.
   Eseguire commit e push su ramo autorizzato, verificarne il risultato.
   Nessun merge su `main` o force-push implicito.
8. La documentazione e una **memoria persistente consultabile**, non una
   garanzia che ChatGPT rilegga Git automaticamente: nelle nuove chat
   richiamare il progetto e verificare nuovamente le fonti correnti.

## Funzionalita recuperabili della 0.8.4

Gateway universali su TLS LAN (porta predefinita 8766), collegamenti su
piu nodi con `via`, pairing con codice inserito sul PC target, permessi e
identita proprie, `agent_update` + `agent_update_status` da commit Git su
qualsiasi destinatario, alternativa con pacchetto verificato quando Git non
e raggiungibile, e un unico updater canonico con controlli di backup/rollback.

L'accesso Cloudflare per l'agente di ingresso, l'avvio all'accesso Windows,
la UI con sessione sbloccata e le autorizzazioni del destinatario sono
condizioni distinte. Non affermare collaudi di reboot fisico non effettuati.

## Origine della registrazione

Richiesta esplicita del 19/11/2026: conservare su Git impostazioni, note e
appunti per **ritrovare sempre l'ultima versione su cui stavamo lavorando**.
La correzione nasce da una risposta che riportava erroneamente `0.8.2` come
ultima dopo aver letto soltanto `feature/agent-gateway-20261114`, mentre
`feature/unified-agent-source-update-20261114` documentava gia `0.8.4`.
