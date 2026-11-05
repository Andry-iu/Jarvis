(() => {
  const cfg = __JW_CONFIG__;
  const result = phase => JSON.stringify({phase});
  const url = new URL(location.href), expected = new URL(cfg.url);
  if (url.origin !== 'https://chatgpt.com' || url.origin !== expected.origin ||
      url.pathname.replace(/\/$/, '') !== expected.pathname.replace(/\/$/, '') || url.search || url.hash)
    return result('wrong_conversation');
  const enabled = e => !e.disabled && !e.readOnly && e.getAttribute('aria-disabled') !== 'true';
  const visible = e => { const r=e.getBoundingClientRect(),s=getComputedStyle(e);
    return r.width>8 && r.height>8 && s.display!=='none' && s.visibility!=='hidden' && s.opacity!=='0'; };
  if ([...document.querySelectorAll('[role="dialog"],[aria-modal="true"],dialog[open]')].some(visible))
    return result('approval_or_dialog');
  const editors=[...document.querySelectorAll('textarea,input[type="text"],[contenteditable="true"],[role="textbox"]')]
    .filter(e => visible(e));
  if (editors.length!==1) return result('ambiguous_editor');
  const editor=editors[0], value=e => (e.value || e.innerText || e.textContent || '').trim();
  // Bind to the observed composer form, never any unrelated button in the document.
  const form=editor.closest('form');
  if (!form || !document.querySelector('[data-message-author-role]')) return result('unverified_conversation');
  if (cfg.action==='probe') {
  // No action when Voice, generation or an approval is in progress.
  if ([...document.querySelectorAll('button,[role="button"]')].some(e => visible(e) &&
      (['stop-button','voice-stop-button'].includes(e.getAttribute('data-testid')) ||
       /^(stop generating|interrompi generazione|end voice|termina|end conversation|termina conversazione)$/i.test(e.getAttribute('aria-label') || ''))))
    return result('busy');
    return result(value(editor) ? 'draft' : enabled(editor) ? 'ready' : 'busy');
  }
  if (!cfg.turn || !cfg.text.trim()) return result('invalid');
  let ledger=window.__jarvisGoogleText;
  if (ledger && ledger.session!==cfg.session) return result('old_session');
  if (!ledger) ledger=window.__jarvisGoogleText={session:cfg.session,turns:new Map()};
  let state=ledger.turns.get(cfg.turn);
  const userBodies=() => [...document.querySelectorAll('[data-message-author-role="user"]')].map(e =>
    (e.querySelector('[data-message-content],.whitespace-pre-wrap') || e).innerText || e.textContent || '');
  const normalize=s => s.replace(/\s+/g,' ').trim();
  if (!state) {
    // No action when Voice, generation or an approval is in progress.
  if ([...document.querySelectorAll('button,[role="button"]')].some(e => visible(e) &&
      (['stop-button','voice-stop-button'].includes(e.getAttribute('data-testid')) ||
       /^(stop generating|interrompi generazione|end voice|termina|end conversation|termina conversazione)$/i.test(e.getAttribute('aria-label') || ''))))
      return result('busy');
    if (ledger.turns.size>=100) return result('session_limit');
    if (!enabled(editor)) return result('busy');
    if (value(editor)) return result('draft');
    state={editor,form,text:cfg.text,dispatched:false,failed:false,before:userBodies().filter(t=>normalize(t)===normalize(cfg.text)).length};
    ledger.turns.set(cfg.turn,state); // consume before mutation, even if an exception follows
    try {
      if (editor.isContentEditable) {
        if (!editor.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,cancelable:true,inputType:'insertText',data:cfg.text}))) {
          state.failed=true; return result('write_failed');
        }
        editor.textContent=cfg.text;
      } else {
        const proto=editor instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
        const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set;
        if (!setter) { state.failed=true; return result('write_failed'); }
        setter.call(editor,cfg.text);
      }
      editor.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:cfg.text}));
      editor.dispatchEvent(new Event('change',{bubbles:true}));
      if (value(editor)!==cfg.text) { state.failed=true; return result('write_failed'); }
      return result('written');
    } catch (_) { state.failed=true; return result('write_uncertain'); }
  }
  if (state.dispatched) {
    return result(!value(editor) && userBodies().filter(t=>normalize(t)===normalize(cfg.text)).length>state.before
      ? 'observed' : 'waiting_observation');
  }
  // No action when Voice, generation or an approval is in progress.
  if ([...document.querySelectorAll('button,[role="button"]')].some(e => visible(e) &&
      (['stop-button','voice-stop-button'].includes(e.getAttribute('data-testid')) ||
       /^(stop generating|interrompi generazione|end voice|termina|end conversation|termina conversazione)$/i.test(e.getAttribute('aria-label') || ''))))
    return result('busy');
  if (state.failed || state.text!==cfg.text) return result('uncertain');
  if (state.editor!==editor || state.form!==form || !document.contains(editor) || value(editor)!==cfg.text) {
    state.failed=true; return result('draft_changed');
  }
  const names=new Set(['send','send message','send prompt','invia','invia messaggio']);
  const buttons=[...form.querySelectorAll('button,[role="button"]')].filter(e => visible(e) && enabled(e) &&
    (e.getAttribute('data-testid')==='send-button' || [e.getAttribute('aria-label'),e.getAttribute('title')]
      .some(s => s && names.has(s.trim().toLowerCase()))));
  if (buttons.length!==1) return result('waiting_send');
  state.dispatched=true;
  try { buttons[0].click(); return result('dispatched'); } catch (_) { return result('send_uncertain'); }
})()
