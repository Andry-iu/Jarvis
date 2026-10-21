# ChatGPT context probe — experimental only

Scope: independent Android test application with a distinct applicationId; do not alter production Jarvis behavior or data.

## Acceptance test
1. Sign in to ChatGPT within the experiment's own WebView.
2. Manually open an existing, non-sensitive test conversation (including a project conversation).
3. Capture the page URL, title, and DOM-visible user/assistant message text. Display extracted content locally; do not transmit it to Gemini or any third party.
4. Compare extracted text to the original conversation. Distinguish loaded DOM content from full server-side history and hidden project context.
5. With explicit user action, submit a harmless test question to the **same** conversation.
6. Wait for generation to finish; read the newly displayed assistant response and verify the conversation URL remains the same.
7. Report success/failure separately for: authentication, navigation, extraction, submission, response extraction, and continuity.

## Constraints
- Never assume that a DOM extract includes attachments, memories, project instructions, or all previous turns.
- No credentials, cookies, raw chat transcripts, or document content in diagnostics or remote logs.
- No background message sending. Require a visible confirmation before each test submission.
- Stop if ChatGPT changes its UI or if the correct target conversation cannot be identified.
- A separate Android application has a separate WebView data directory/session and may require login.
- Prototype not yet implemented or tested on a device.
