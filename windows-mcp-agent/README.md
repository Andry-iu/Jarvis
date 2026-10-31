# Jarvis Windows MCP Agent

Sottoprogetto dell'ecosistema Jarvis, separato dall'app Android.

- Implementazione corrente eseguita sul notebook: **0.4.1** (Go, Windows amd64).
- Endpoint locale `http://127.0.0.1:8765/mcp`; pubblico tramite Cloudflare Tunnel protetto da Access Managed OAuth.
- Strumenti: `agent_status`, `task_start`, `task_stop`, `project_list`, `project_status`, `project_clone`.
- Limitazione: clone di NestingOpt bloccato da autenticazione GitHub sul notebook; Codex e automazione desktop non ancora implementati.
- Stato verificato e istruzioni per riprendere: [STATUS](STATUS.md).
- Politica release/commit/push: [RELEASE_POLICY](RELEASE_POLICY.md).

Il codice sorgente 0.4.1 originale e disponibile nel pacchetto sorgenti della conversazione; fino a quando tutti i file Go non sono stati committati sotto questa directory, **il repository non e ancora una copia completa del sottoprogetto**. Non dichiarare che il rilascio 0.4.1 e stato integralmente pubblicato.
