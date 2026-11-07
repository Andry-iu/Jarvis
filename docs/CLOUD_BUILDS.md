# Compilazioni richiamabili dalle altre chat

Il ponte estende **JarvisWake Link**, lo stesso plugin/Site privato già abbinato al telefono. Non richiede un nuovo plugin, un APK diverso o una pagina del telefono aperta. Le richieste di compilazione riguardano esclusivamente `Andry-iu/Jarvis`; l’esecutore usa `/workspace/Jarvis`, gli strumenti/cache in `/workspace/.jarvis-cloud` e la firma permanente. Non esegue shell ricevute dalla chat e non compila Watch o agenti Windows.

## Uso nella chat

Con JarvisWake Link disponibile, chiedere «verifica Jarvis su main» oppure «compila Jarvis da main e dammi l’APK firmato». L’assistente deve:

1. Leggere `jarviswake_cloud_status`: un heartbeat entro45secondi prova solo che il runner ha contattato il backend di recente. Non prova che sia sempre acceso.
2. Chiamare `jarviswake_cloud_start` con `operation: verify` oppure `build_signed`, `revision: main` oppure SHA completo40hex appartenente alla storia di main, `request_id: UUIDv4` nuovo per quella richiesta esplicita. Conservare e riusare lo stesso UUID se la risposta del tool è incerta. Nessun riavvio automatico con un altro UUID.
3. Consultare `jarviswake_cloud_job` con il `job_id` restituito. Comunicare queued/running/awaiting_runner/succeeded/failed/outcome_uncertain senza trasformare la coda in una build già riuscita. Consultare lo stato su richiesta o mentre si segue il lavoro, evitando polling serrato del modello.
4. Solo per un APK riuscito, chiamare `jarviswake_cloud_artifact` e consegnare il link privato `.apk`, versione, SHA256 e commit del verbale. Il download richiede l’accesso al Site con lo stesso account. Non pubblicare file o link anonimi. Il link scade dopo7giorni; i binari non vanno su Git.

`jarviswake_cloud_cancel` annulla **solo la coda**. Un lavoro già preso in carico non viene interrotto. `verify` esegue controlli interpretati, suite Android/JVM SDK34/36 e lint; non sintetizza audio e non chiama API Google. `build_signed` aggiunge assemble, firma permanente e controlli pacchetto/ZIP/ABI/16KiB/v2/v3/certificato. Non cambia automaticamente la versione dell’app: compila la versione presente nel commit richiesto.

I nuovi tool potrebbero richiedere una nuova chat o l’aggiornamento del catalogo del plugin già installato. La pubblicazione del Site non prova da sola che ogni chat abbia già aggiornato il proprio catalogo.

## Coda, isolamento e ripresa

Richieste e risultati sono separati per identità utente del Site. UUID e operazione/revisione sono immutabili. La coda accetta al massimo64marker, senza garanzia FIFO; i lavori rimasti in coda oltre24ore scadono. Una sola claim globale con scritture condizionali R2 impedisce esecuzioni concorrenti. Una claim non scade e non viene rubata perché manca il heartbeat. La ripetizione della claim richiede lo stesso UUID di claim, già salvato nel journal locale prima dell’HTTP.

Il runner risolve main **una volta**, verifica l’appartenenza alla storia di origin/main e crea un worktree distaccato nel medesimo cloud. Non sostituisce il checkout dell’utente e non sovrascrive modifiche locali. Riutilizza il modello Vosk e cache esistenti. Solo comandi fissi sono accettati. La cartella del runner installato è separata dai worktree compilati.

Il journal locale è atomico, fsync e0600. Il lock locale è ereditato dai sottoprocessi di build: un runner riavviato non parte mentre il precedente sottoprocesso conserva il lock. Dopo una ripresa con fase running, l’esito è `outcome_uncertain`: nessuna seconda compilazione automatica. Un upload già preparato può riprendere solo lo stesso APK con hash verificato; un ACK finale può essere ripetuto senza cambiare il risultato. Uno stato locale mancante con claim remota attiva richiede analisi dell’operatore, senza prendere possesso o ripetere il lavoro.

Il verbale tecnico con conteggi reali, SHA del sorgente e, se presente, checksum/certificato APK viene registrato su main con push fast-forward verificato **prima** di rendere disponibile l’APK. Nessun force-push, nessun log integrale o binario su Git. Se main avanza durante il push, il runner riparte soltanto dalla preparazione del verbale su main aggiornato; non ricompila. Il checkout principale può risultare indietro dopo un job: al successivo lavoro riallinearlo con fast-forward preservando eventuali modifiche locali.

## Configurazione dell’esecutore

Sorgenti: `cloud-runner/runner.py` e `signing.py`. Configurazione privata0600 fuori Git in `/workspace/.jarvis-cloud/cloud-runner/config.json`: `origin` esatto del Site, `runner_token` casuale256bit, `site_access` ottenuto con il meccanismo supportato Sites. Nel runtime del backend viene aggiunto solo `CLOUD_RUNNER_TOKEN_HASH`, senza alterare pairing, `SITE_DEVICE_ACCESS` o credenziali Google. I due header hanno scopi distinti: `OAI-Sites-Authorization` attraversa il confine privato della piattaforma; `Authorization` autentica il runner, limitato alle route `/cloud/runner/`. Il bypass non conferisce identità del visitatore né accesso ai dati del telefono. Mantenere il Site owner-private; non allargarne implicitamente l’audience.

Avvio nel cloud Jarvis con il segreto `Passphrase` già configurato:

```sh
umask 077
bash /workspace/Jarvis/cloud-runner/bootstrap.sh --repair --start
```

`--once` esegue una sola iterazione diagnostica. Il processo usa TLS e proxy ordinari, rifiuta redirect e invia i due bearer soltanto all’origine esatta. Non stampa i segreti. Git/Gradle/script del repository non ricevono passphrase e token nell’ambiente del sottoprocesso; questo non è un sandbox per eseguire codice ostile: il codice ammesso è la storia del repository autorizzato. Le chiavi in chiaro esistono solo in una directory temporanea privata durante la firma, rimossa in finally; hash del backup e certificato sono fissati. Nessuna chiave nuova.

Log completi e journal restano privati nel cloud, esclusi da Git e diagnostica telefono. MCP espone solo stati, codici fissi, conteggi letti dai report e metadati APK. `worker_tests`/`phonebook_tests` non vengono inventati: il job corrente espone i conteggi Android/lint effettivamente analizzati, con esito degli altri controlli rappresentato dal passaggio di fase/successo complessivo. Nessun contenuto della rubrica, testo chat, audio o credenziale viene inserito nel verbale. Dopo7giorni il backend nega il download; la rimozione fisica del binario è opportunistica quando viene richiesto il link, senza promessa di cancellazione temporizzata.

### Ambiente pronto prima della claim

`cloud-runner/environment-setup.json` registra il comando riutilizzabile di setup/avvio e quello di salute. Il bootstrap ripara i componenti aggiuntivi mancanti nel cloud Jarvis già configurato: Go1.27.3 con checksum fissato e archivio riusabile, NDK28.2.13676358, gomobile/gobind alla revisione fissata, moduli Go e pyrage1.4.0 per la firma. Riusa SDK/JDK/cache/modello; non compila l'app né crea un job. L'installazione Go diventa visibile soltanto dopo estrazione completa; una directory incompleta richiede ispezione. Il setup nativo già completo non reinstalla i binari Go. `--check` non scarica né modifica nulla.

`readiness.py` controlla esistenza/versione degli strumenti, NDK completo/clang, SDK36/Build Tools36, Gradle8.13, modello verificato, interprete firma e disponibilità della passphrase. Non espone valori di credenziali. Il runner ripete il controllo prima di ogni nuova claim: `runner_not_ready` lascia la richiesta in coda e non crea un journal di esecuzione. I percorsi di recupero di una claim già iniziata conservano la regola di non ricompilare e non dipendono dagli strumenti di build. Un heartbeat recente continua a provare soltanto contatto col backend, non prontezza completa.

La cache Gradle è riutilizzata, ma non si forza più `--offline`: una nuova istanza può avere strumenti completi e cache parziale. Gradle può recuperare normalmente le dipendenze fissate dai repository autorizzati, mantenendo proxy/TLS; `ready=true` non certifica che ogni artefatto transitivo sia già scaricato. Il job successivo `0ac83a9e-46e6-4d07-8472-8fa89f2313d8`, autorizzato dall'utente, ha compilato il componente nativo ma è terminato per cache Android offline incompleta. Il recupero locale sullo stesso commit/worktree non muta il suo esito remoto né crea un terzo job. Registrare separatamente l'esito effettivo di tale recupero e l'eventuale APK.

Il bootstrap conserva `config.json` e journal. Se la configurazione privata manca in una nuova istanza, può ricostruirla **soltanto** dai binding segreti esistenti `JARVIS_RUNNER_TOKEN` e `JARVIS_SITE_ACCESS`, oltre alla `Passphrase` già prevista. Nessuna rotazione o generazione automatica: se i binding non sono disponibili, si ferma con `runner_config_missing`. Questi valori si configurano nelle impostazioni dell'ambiente, mai su Git o in chat. L'avvio acquisisce il lock prima di installare il codice e lo trasferisce al runner/figli; un runner o build attivo non viene sostituito.

Per mantenere la preparazione tra **nuove istanze**, registrare il comando di setup/avvio e i binding privati nella configurazione gestita, quindi salvare e pubblicare lo snapshot dell'ambiente preparato. Un commit, un processo in esecuzione o la pubblicazione del Site non salvano lo snapshot cloud. In assenza degli strumenti di configurazione/pubblicazione non dichiarare eseguito questo passaggio: la procedura versionata e il controllo preventivo restano disponibili, ma non garantiscono da soli che la piattaforma li esegua al prossimo avvio. Nessuna promessa di cloud sempre acceso.

Riscontro7novembre2026: runtime corrente diverso da quello registrato per la .119; JDK/SDK/cache base presenti, componenti Go/NDK e directory privata runner assenti prima del ripristino. Questo non dimostra perché fossero assenti e non smentisce le build precedenti. Il 403 è superato nella policy corrente; token runner sostituito con autorizzazione esplicita, conservati firma/telefono/Google. Il job `25f43cf0-399c-4a74-8fce-431b65e34f0c` ha effettivamente eseguito i controlli interpretati e avviato Gradle sul commit `b52730345cdb81a93bf4ca7da777c7f43bad5549`, poi è terminato `failed/android_failed` su `:app:whatsappNative` per l'assenza del compilatore Go previsto (`Go: Unknown option: list` dal programma di sistema). Un job terminale è immutabile; nessuna nuova richiesta o riesecuzione viene introdotta dal bootstrap.

L’esecutore può essere sospeso o terminato insieme all’ambiente gestito. Non è un servizio sempre acceso e non riattiva da solo il cloud; una richiesta resta in coda fino alla riattivazione/riavvio del runner. Nessun ricorso a GitHub Actions o a un’altra macchina. Se il proxy rifiuta il Site con403, il runner rimane disconnesso: la pubblicazione MCP non risolve la policy di rete del cloud.

## Verifiche

Test simulati backend in `phone-bridge/test/cloud.test.mjs`, journal/runner in `cloud-runner/tests/test_runner.py`. Le prove comprendono identità/credenziali, parametri non ammessi, deduplicazione/conflitti, claim concorrenti/ACK perso, cancellazione, coda scaduta, heartbeat vecchio, progressi monotoni, risultato immutabile, callback dopo fine, metadati/hash/upload/download privato/scadenza; ripresa senza doppia build, pin di main, secret sanitization e conteggi XML.

Il test simulato di un APK usa byte fittizi: non certifica un APK Android. La verifica reale di firma usa gli strumenti del cloud e il certificato permanente; lo stato di rete e il collaudo reale tool→runner→build→download devono essere registrati separatamente nel verbale. Nessuna garanzia Galaxy deriva da questi controlli.
