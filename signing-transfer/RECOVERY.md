# Recupero della chiave di firma Jarvis

`jarvis-signing-backup.tar.age` contiene il keystore Android permanente e i file password, cifrati con age in modalità passphrase. La passphrase non è salvata nel repository.

In un ambiente fidato con age installato, decifrare in una directory privata (age chiederà la passphrase senza visualizzarla):

```sh
umask 077
mkdir -m 700 jarvis-signing-private
age --decrypt --output jarvis-signing-private/credentials.tar signing-transfer/jarvis-signing-backup.tar.age
```

Verificare i nomi con `tar -tf jarvis-signing-private/credentials.tar`, quindi estrarre solo in quella directory. Non stampare il contenuto dei file password, non inserirli in Git e cancellare i temporanei dopo la firma. Senza la passphrase, il keystore non è recuperabile.
