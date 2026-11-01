# Installer 1.2.0: Cloudflare automatico dal secondo PC

## Stato verificato, 2026-11-01

Implementato sul branch `reconcile-windows-mcp-agent-20261031`, base `db57ce30cfdb348898c494fce55113c71b3917e2`. Agente Windows distribuito invariato 0.6.1, commit binario `164cb980bd1a936749d41b8692979e98b929cb04`, SHA-256 `9ff4a136951f464925cc55b96e1a968174204199fa9c7ff681b998b3542742c1`. Android distinto e non modificato.

**Testato:** 20 test Node con trasporto API simulato, eseguiti nel cloud Linux con Node v24.22.1; controllo sintattico JavaScript, manifest e ZIP. **Non testato:** esecuzione PowerShell/Windows della 1.2.0, download/esecuzione Node Windows, API Cloudflare reali, OAuth/plugin ChatGPT/Voice sul secondo PC. Nessun nuovo test, installazione o modifica operativa su Desktop1. Pubblicazione Git da verificare attraverso il commit remoto; non confondere sorgenti pubblicati e installazione riuscita.

## Installazione e ripresa

Estrarre il nuovo ZIP in una nuova cartella. Avviare `INSTALLA.cmd`, consentire UAC con lo stesso utente di Codex e indicare la destinazione precedente. Invio accetta il default tra parentesi quadre. Non cancellare cartella Jarvis, cache o registro di ripresa. Nome predefinito `Desktop2`, configurabile; plugin suggerito `Jarvis-Desktop2`.

Al prompt Cloudflare, Invio seleziona la configurazione automatica; `M` usa quella manuale. Il token API viene chiesto con input nascosto. Scegliere la zona se multipla, personalizzare eventualmente l'etichetta pubblica (default `jarvis-desktop2`) e indicare l'email **esatta** da autorizzare, preferibilmente l'utente gia usato per Access. Account Zero Trust e zona DNS attiva devono gia esistere. Il dominio pubblico ha un solo livello sotto la zona: nessun acquisto di certificati, domini o piani.

Creazione token: <https://dash.cloudflare.com/profile/api-tokens>, Create Token, Create Custom Token / Start from scratch. Permessi:

| Scope | Permission | Level |
|---|---|---|
| Account | Cloudflare Tunnel | Edit |
| Account | Access: Apps and Policies | Edit |
| Account | Access: Organizations, Identity Providers, and Groups | Read |
| Zone | Zone | Read |
| Zone | DNS | Edit |

Account Resources: Specific account; Zone Resources: Specific zone. TTL breve, ad esempio 7 giorni. Filtro IP solo se compatibile con l'IP del PC. Il segreto e mostrato una volta: conservarlo per il setup locale, non inviarlo in chat. E diverso dal token di esecuzione del tunnel; puo essere revocato dopo il provisioning senza revocare quest'ultimo.

## Implementazione e confini

`Setup.ps1` usa il contesto gia bloccato, senza riaprire il setup.lock. `Configure-Cloudflare-Auto.ps1` rileva un Node >=22 o usa Node 24 LTS privato: metadata e SHA-256 da nodejs.org, versione/hash salvati prima del download. `Setup-Worker.ps1` estrae il runtime completo, verifica `--version` prima della ricevuta completed. Nessun npm o pacchetto esterno. Restano i controlli 1.1.1 sull'estrazione Git e il recupero ristretto delle sole ricevute 1.1.0 riconosciute.

`Cloudflare-Provision.cjs` usa esclusivamente stdlib e HTTPS a `api.cloudflare.com/client/v4`, TLS verificato, nessun redirect o retry implicito. Il token API entra via stdin, mai argv/env, disco, journal o log. Errori restituiti come codici, senza body/header grezzi. Il token tunnel viene letto solo dal suo endpoint e scritto in una cartella gia protetta per SID/SYSTEM, con file temporaneo privato e rinomina atomica. Non salvare journal o directory Config in Git.

Sequenza: verifica token, zone/account e org/team; controllo conflitti; crea app self_hosted con policy inline allow per una singola email; rilettura app/policy; crea tunnel remoto dedicato; configura ingress locale e fallback 404; ripete i controlli Access/conflitti; crea CNAME proxied; riletture finali e esportazione locale di hostname, AUD, UUID e credenziale tunnel. Managed OAuth abilita DCR solo per `https://chatgpt.com/connector/oauth/*`, localhost/loopback disabilitati, access token 15m e grant 24h. Non modifica organizzazione, identity provider, policy riutilizzabili o app preesistenti.

Il registro privato `cloudflare-auto.json` e vincolato a computer/SID/root, zona, account, hostname, email, porta e team; nomi risorsa casuali con proprietario univoco. Gli ID osservati e gli intent/body hash vengono persistiti prima dei passaggi successivi. Dopo una disconnessione, una GET deve provare l'esito prima di proseguire. Un intento senza risultato osservabile resta incerto e NON viene rispedito. Anche una PUT non osservata resta bloccata; una PUT osservata con configurazione identica viene riconciliata. Un 4xx definitivo consente una nuova esecuzione dopo correzione; nessun ciclo di retry. Identita, drift, risorse estranee o multiple bloccano la ripresa. Un figlio ancora vivo impedisce un secondo provisioning; lock di processo non piu esistente conservato come inactive prima della riconciliazione.

Non esistono chiamate billing, sottoscrizioni, acquisti, API OpenAI a consumo o inferenza. Le guardie economiche dell'agente sono invariate; null resta sconosciuto, prove non copiate/prorogate. Lettura Codex finale separata dal provisioning. L'installer non crea il plugin ChatGPT: produce `Collegamento-ChatGPT.txt`, conserva `remote_verified=false` e richiede il collaudo da nuovo client. Avviare poi con Avvia.cmd. PC spenti hanno endpoint indisponibili indipendenti dagli altri PC.

## Backup e rollback

Aggiornamenti script riconosciuti hanno backup e ricevuta `Backups/installer-scripts-*`; nuovi script/helper vengono inclusi nella transazione con marker pending che blocca l'avvio incompleto. Per rollback, arrestare l'istanza specifica, usare la ricevuta per verificare gli hash prima/dopo, ripristinare gli script precedenti dal backup; eliminare i file nuovi solo se l'hash corrente e ancora quello della ricevuta. Ripristinare agent.env dal suo backup soltanto se ancora corrispondente al tentativo; non sostituire credenziali/identita/stato. Il binario agente non cambia. `Repair.ps1` mantiene il precedente backup integrale e ripristino di cartella/task per tentativi incompleti riconosciuti, ora include la 1.2.0.

Su errore API non si cancellano automaticamente risorse remote: eliminare Access mentre una creazione DNS e incerta potrebbe esporre il servizio. Per annullare, usare esclusivamente account/zona/ID e proprietario registrati: togliere **prima il DNS dedicato**, poi tunnel, infine app e policy dedicata. Verificare gli oggetti prima di ogni cancellazione; non adottare risorse per il solo nome e non toccare Desktop1. Se l'esito resta incerto, fermarsi e verificare da dashboard/API, senza cancellare il journal per forzare il retry.

## Riproduzione test e build

Nel repository:

```text
node --test windows-mcp-agent/installer/Test-Cloudflare.cjs
node --check windows-mcp-agent/installer/Cloudflare-Provision.cjs
powershell -NoProfile -File windows-mcp-agent/scripts/build-desktop2-installer.ps1 -AgentPath <binario-0.6.1-verificato> -OutputDirectory <cartella-nuova>
```

`Test-Cloudflare.cjs` usa mock HTTP e cartelle temporanee: nessun token reale, account o risorsa Cloudflare. I test Windows `Test-Bootstrap.ps1` e `Test-Installer.ps1` sono disponibili ma **non eseguiti** per questo rilascio; richiedono un ambiente Windows isolato, non il Desktop1 operativo. Il builder include ogni sorgente necessario nel manifest; il binario deve corrispondere all'hash sopra e non viene ricompilato in questo rilascio.

Fonti: [token](https://developers.cloudflare.com/fundamentals/api/get-started/create-token/), [tunnel API](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/get-started/create-remote-tunnel-api/), [Access create](https://developers.cloudflare.com/api/resources/zero_trust/subresources/access/subresources/applications/methods/create/), [Managed OAuth](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/managed-oauth/).
