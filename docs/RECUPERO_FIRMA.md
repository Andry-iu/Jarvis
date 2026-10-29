# Recupero della firma permanente JarvisWake

Procedura da eseguire nel cloud Jarvis esistente per recuperare il materiale temporaneo di firma e firmare un APK compatibile con l'installazione esistente sul Samsung Galaxy S24+ (SM-S926B, Android 16). La chiave non è specifica del modello: Android richiede che l'APK di aggiornamento abbia lo stesso certificato dell'app installata.

## Materiale versionato

- Sorgenti: branch `main` di `https://github.com/Andry-iu/Jarvis`.
- Backup: branch `signing-handoff-2026-10-19`, file `signing-transfer/jarvis-signing-backup.tar.age`.
- SHA-256 dell'archivio cifrato: `155b8afc72265dae8bfe5e8d4a09a49929375d88e9972bd9b2777723d2502e58`.
- Alias della chiave: `jarvis`.
- SHA-256 del certificato atteso: `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`.
- L'archivio cifrato contiene il keystore permanente e i file con le password. Il certificato della 0.5.30 firmata è stato confrontato con quello atteso e coincide.

## Limite deliberato: passphrase

Indicazione aggiornata del 29 ottobre 2026: usare prima il segreto `Passphrase` già configurato nel cloud Jarvis (`/workspace/Jarvis`, strumenti `/workspace/.jarvis-cloud`), senza leggerlo a video, copiarlo in comandi o richiederlo di nuovo. Verificarne presenza e utilizzabilità tramite ambiente e decrittazione autenticata. Il recupero di helper temporanei rimossi non autorizza a ricostruire la toolchain in un altro ambiente.

La passphrase age **non è salvata su Git** e non va aggiunta alla documentazione. Senza di essa non è possibile aprire il backup nel nuovo ambiente. Era stata fornita dall'utente nella conversazione precedente e il backup è stato decrittato con successo per la 0.5.30. Se il segreto configurato non è accessibile, riportare il blocco preciso senza chiederne automaticamente il reinvio in chat. In alternativa, il keystore permanente resta sul telefono in Termux al percorso `~/jarvis-signing/jarvis-signing.jks` e la password viene inserita localmente.

## Recupero nel cloud Jarvis fidato

1. Riprendere esclusivamente il checkout `/workspace/Jarvis` nel cloud Jarvis e aggiornare i riferimenti remoti, preservando ogni modifica locale; controllare che la baseline sia il `main` remoto pulito. Non usare workflow GitHub Actions per build o firma.
2. Recuperare il branch di handoff e verificare l'archivio cifrato:

   ```sh
   git fetch origin signing-handoff-2026-10-19
   git show origin/signing-handoff-2026-10-19:signing-transfer/jarvis-signing-backup.tar.age > /tmp/jarvis-signing-backup.tar.age
   printf '%s  %s\n' '155b8afc72265dae8bfe5e8d4a09a49929375d88e9972bd9b2777723d2502e58' '/tmp/jarvis-signing-backup.tar.age' | sha256sum -c -
   ```

3. Riutilizzare JDK 17, SDK/Build Tools 36.0.0 e cache già disponibili nel cloud Jarvis. Per aprire il backup usare `age` se presente oppure pyrage nel medesimo ambiente con il segreto configurato passato solo in memoria. Creare una directory privata e lasciare che `age` chieda la passphrase interattivamente, senza passarla come argomento:

   ```sh
   umask 077
   private_dir=$(mktemp -d)
   age --decrypt --output "$private_dir/credentials.tar" /tmp/jarvis-signing-backup.tar.age
   tar -tf "$private_dir/credentials.tar"
   tar -xf "$private_dir/credentials.tar" -C "$private_dir"
   ```

   L'elenco `tar -tf` mostra soltanto i nomi dei file; non stampare il contenuto delle password. Non copiare la directory privata nel repository e cancellarla dopo la firma.

4. Individuare il keystore e i file password nella directory privata. Esportare il certificato dall'alias `jarvis`, usando il prompt protetto di `keytool`, e confrontarne il digest SHA-256 con quello atteso sopra. Fermarsi se non coincide. Non procedere con una chiave nuova o con il debug keystore.
5. Compilare i sorgenti locali con Gradle Wrapper 8.13/JDK 17 e Android SDK 36. Per un aggiornamento installabile, allineare l'APK prima della firma, firmare con `apksigner` Build Tools 36.0.0 e l'alias `jarvis`, poi verificare la firma e il certificato risultante. Usare i file password tramite `file:` o un prompt/variabili d'ambiente protetti; non metterle nella riga di comando, nella cronologia della shell o nei log. Abilitare gli schemi v2 e v3; l'APK deve mantenere `minSdk 31` e ABI `arm64-v8a` per questa configurazione.
6. Verificare `apksigner verify --verbose --print-certs --min-sdk-version 31` e controllare che il certificato sia esattamente `a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a`. Verificare anche `zipalign -c -P 16 -v 4` e l'identificativo/versione del pacchetto. Consegnare il file `.apk` firmato.

La configurazione Gradle attuale usa una sola variabile `JARVIS_SIGNING_PASSWORD` per password keystore e chiave. Se i due segreti nel backup differiscono, non forzare quella configurazione: usare `apksigner` con i due file password separati e verificare il risultato. Non registrare i segreti né le password nell'APK o in Git.

## Firma sul Galaxy con Termux

Il keystore è stato mantenuto anche nel telefono in `~/jarvis-signing/jarvis-signing.jks`. La procedura `scripts/firma-jarvis-0.5.30-termux.sh` risolve l'errore PerfettoTrace degli strumenti Java Termux, ma accetta **solo** l'APK 0.5.30 il cui hash è fissato nello script. Per una versione successiva occorre aggiornarne intenzionalmente nome, hash dell'APK e verifica, poi ricontrollare il certificato. Non usare lo script 0.5.30 su un APK diverso: si fermerà al controllo hash.

Per la build diagnostica 0.5.47 è incluso `scripts/firma-jarvis-0.5.47-termux.sh`: accetta esclusivamente l'APK non firmato con SHA-256 fissato nello script, chiede le password solo nel terminale Termux, verifica il fingerprint permanente, firma v2/v3 e ricontrolla il certificato prima di scrivere il risultato. Non installare l'APK non firmato sopra l'app esistente.

Per ogni versione, la verifica firmata deve precedere l'installazione sopra l'app esistente. Il checksum APK e il fingerprint certificato vanno riportati nella documentazione della release; il keystore, le password, la passphrase e l'APK binario restano fuori da Git.
