# Jarvis Windows MCP Agent

Sottoprogetto dell'ecosistema Jarvis, separato dall'app Android.

**Versione sorgenti in Git: 0.4.1**. I singoli file Go, i test, il modulo e le istruzioni sono pubblicati in chiaro in questa cartella, non in un archivio ZIP.

## Struttura e funzionalita
- `main.go`: agente, API MCP Streamable HTTP, task diagnostici, autenticazione e stato.
- `cloudflare_auth.go`: validazione JWT di Cloudflare Access.
- `projects.go`: elenco repository Git e stato dei progetti, limitati alla cartella autorizzata.
- `clone.go`: clone asincrono e annullabile dei repository autorizzati dell'owner Andry-iu.
- `*_test.go`: test MCP, autenticazione, progetto e clone.
- `go.mod`: modulo Go 1.23.2.
- `README.txt`, `UPGRADE-0.4.0.txt`, `UPGRADE-0.4.1.txt`: istruzioni originarie.

## Compilazione
Da questa cartella con Go 1.23.2 o compatibile:

```sh
go test ./...
go test -race ./...
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -o jarvis-mcp-windows-v0.4.1-amd64.exe .
```

## Configurazione Windows
Conservare fuori da Git tutti i segreti. Variabili d'ambiente:
- `JARVIS_MCP_TOKEN`: token locale casuale, almeno 24 caratteri;
- `JARVIS_CF_TEAM_DOMAIN`: URL HTTPS completo del team Cloudflare Access;
- `JARVIS_CF_APP_AUD`: audience dell'applicazione Cloudflare Access;
- `JARVIS_PROJECTS_ROOT`: directory esplicitamente autorizzata, attualmente `D:\Jarvis\Projects`.

Il server ascolta soltanto su `127.0.0.1:8765/mcp`. La connessione remota e protetta da Cloudflare Tunnel e Managed OAuth; non aprire porte pubbliche dirette.

## Stato e continuita
- [Stato avanzamento e punto di ripresa](../docs/mcp-agent/STATUS.md)
- [Obbligo di commit e push per ogni rilascio](../docs/mcp-agent/RELEASE_POLICY.md)

Il collegamento remoto ChatGPT e stato verificato. Il clone del repository privato NestingOpt e fallito per assenza di credenziali GitHub usabili sul notebook. Non e stato implementato il motore Codex o il controllo desktop completo.
