# Jarvis - Registro e riuso obbligatorio dei metodi collaudati

**Decisione generale approvata dall'utente il 23/11/2026. Regola operativa per l'intero progetto Jarvis, non solo per l'agente Windows.**

## Regola fondamentale

**Ogni volta che si utilizza Jarvis, Jarvis Agent, JarvisWake, Jarvis Windows MCP, il bridge, il cloud, Android/Android Auto, ESP32, o qualunque componente del progetto, PRIMA di cercare nuove soluzioni bisogna cercare in Git i metodi gia' collaudati pertinenti e RIUTILIZZARLI come prima scelta se ancora applicabili.**

**Ogni nuovo metodo che risolve un problema e viene verificato deve essere registrato immediatamente in Git in forma di procedura riproducibile.** Non basta registrare la correzione al codice o il risultato finale: bisogna conservare *come* e' stata ottenuta. Non ripetere da capo in nuove chat un problema gia' risolto, non preferire workaround generici a una procedura specifica con prove.

La regola vincola il lavoro svolto con ChatGPT e gli agenti che leggono questo repository. Il solo commit Git non installa automaticamente nuove istruzioni nei client o nei telefoni: in ogni nuova chat e' necessario accedere effettivamente al repository aggiornato.

## Sequenza operativa obbligatoria

1. **Prima di operare:** leggere `AGENTS.md`, questo documento, i checkpoint aggiornati del componente e i runbook/problemi noti pertinenti. Se il compito riguarda Windows MCP leggere anche `windows-mcp-agent/AGENTS.md`, `docs/mcp-agent/PROCEDURES.md` e `docs/mcp-agent/STATUS-CONSOLIDATO-0.9.6-20261122.md` o il piu' recente stato verificato. Per JarvisWake leggere anche `PROMPT_CONTINUITA.txt`, `CONTINUITY.md` e `docs/PROCEDURA_BUILD_RILASCIO.md` quando si parla di build.
2. **Cercare il metodo:** individuare procedure con stesso obiettivo, stesso contesto e prerequisiti verificabili. Dare priorita' ai metodi `COLLAUDATO`. Non confondere `PROPOSTO`, `IMPLEMENTATO`, `COMMITTATO`, `INSTALLATO` e `VERIFICATO DAL VIVO`.
3. **Riutilizzare:** applicare prima il metodo gia' testato, usando gli strumenti generici Jarvis disponibili; per operazioni ripetibili sul PC usare le procedure esterne nella directory canonica `<Jarvis>\Procedures`. Non introdurre nel core MCP logica speciale per una singola applicazione.
4. **Controllare l'effetto:** fare un postflight indipendente (screenshot, versione live, esito terminale di job, ricevuta, log sanificato, hash/test). Una risposta `dispatched` o `verified=false` non e' prova di successo. Evitare replay automatici di operazioni con esito incerto.
5. **Se il metodo fallisce:** verificare differenze di versione, permessi, risoluzione, contesto, device e prerequisiti; documentare cause e tentativi non riusciti. Solo dopo un collaudo sostituire o revisionare il metodo precedente.
6. **Dopo ogni nuova soluzione efficace:** creare/aggiornare la scheda del metodo, il catalogo e i checkpoint pertinenti; committare e rileggere il Git remoto prima di dichiarare il lavoro chiuso.

**Sicurezza:** il riuso di una procedura non equivale ad autorizzazione a operazioni distruttive, disabilitazione di controlli o violazione di permessi. Validare sempre target, impatto, backup e rollback. Mai salvare token, password, chiavi, dati privati o installer nel catalogo Git.

## Formato delle schede di metodo

Ogni scheda deve riportare:
- **Identificativo stabile, titolo, scopo, ambito** (JarvisWake, Windows Agent, cloud, Android Auto, ESP32, ecc.);
- **Stato**: `PROPOSTO`, `COLLAUDATO`, `COLLAUDATO_CON_LIMITI`, `SUPERATO`, `BLOCCATO`;
- **Data e ambiente della verifica**, versione esatta e prerequisiti;
- **Quando usarlo e quando no**, autorizzazioni necessarie;
- **Procedura dettagliata** con tool/comandi/click/script e parametri, senza affidarsi a coordinate fragili;
- **Risultato misurato e prova**, criterio di esito e controlli postflight;
- **Errori gia' incontrati e strade da NON ripetere**, alternativa e rollback;
- **Link ai commit, test e documenti**, revisione e compatibilita'.

## Indice iniziale delle procedure gia' disponibili

| Area | Procedura/risorsa | Stato della conoscenza | Utilizzo |
| --- | --- | --- | --- |
| JarvisWake Android/cloud | [PROCEDURA_BUILD_RILASCIO.md](PROCEDURA_BUILD_RILASCIO.md) | Percorsi di build/diagnosi/consegna gia' collaudati nelle versioni documentate; non tutte le release successive lo sono | Riutilizzare prima di nuovi build, firma o recupero artefatti |
| Jarvis Windows MCP | [PROCEDURES.md](mcp-agent/PROCEDURES.md) e [HANDOFF-GENERIC-AGENT-PROCEDURES-20261119.md](mcp-agent/HANDOFF-GENERIC-AGENT-PROCEDURES-20261119.md) | Architettura generica e procedure Windows; verificare collaudo specifico del task | Preferire primitive, procedure e batch esistenti a tool specializzati |
| Jarvis Windows MCP | [STATUS-CONSOLIDATO-0.9.6-20261122.md](mcp-agent/STATUS-CONSOLIDATO-0.9.6-20261122.md) | Collaudi 0.9.6 e limiti esplicitamente separati | Prima di lavorare su mouse, tastiera, sessioni e prompt |
| Jarvis Windows MCP / IRCESP32 | [IRCESP32-USB-0.9.8.md](mcp-agent/IRCESP32-USB-0.9.8.md) | `COLLAUDATO_CON_LIMITI`: aggiornamento Desktop1 e instradamento MCP verificati, banco da provare | Riprendere il collaudo USB-TTL/Wi-Fi o verificare la versione live |
| Jarvis Windows MCP / IRCESP32 | [IRCESP32-PERSPECTIVE-CALIBRATION.md](mcp-agent/IRCESP32-PERSPECTIVE-CALIBRATION.md) | Doppia cattura verificata in simulazione, indisponibile sul banco attuale. Controllo visivo sul ponte da sviluppare | Riprendere il lavoro dove il bersaglio è accessibile solo via camera e HID |
| Server AUTECH con Jarvis | Catalogo del repo `Andry-iu/AutechSrv1`, `procedure/METODI_COLLAUDATI.md` | Metodi specifici di un altro progetto, da leggere con autorizzazione Git | Per lavori specifici del server, non duplicare nel core Jarvis |

L'indice va ampliato progressivamente. **Non catalogare un test fallito come metodo collaudato.** Gli script runtime Windows restano separati in `<Jarvis>\Procedures`; il Git registra procedure e relativa origine/versione, non sostituisce il runtime installato.

## Promemoria per tutte le nuove chat e per JarvisWake

> Prima di qualunque azione con Jarvis, leggi il Git aggiornato e cerca il metodo gia' verificato. Usa prima quello, se compatibile. Dopo una nuova soluzione testata, registra nel Git il metodo completo e il suo postflight. Non far riscoprire alla chat successiva una soluzione gia' trovata.

**Non e' stato modificato alcun APK, agente installato, stato del telefono o procedura locale tramite questo documento.**
