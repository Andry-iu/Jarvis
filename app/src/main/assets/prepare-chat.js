(() => {
  const project = __JARVIS_PROJECT_JSON__;
  const attempt = __JARVIS_ATTEMPT_JSON__;
  const message = __JARVIS_MESSAGE_JSON__;
  let verification = {};
  const result = (phase, extra={}) => JSON.stringify({phase, ...verification, ...extra});
  if (!message.trim()) return result('empty_message');
  const url = new URL(location.href);
  if (url.origin !== 'https://chatgpt.com') return result('login');
  let state = window.__jarvisPreparation;
  if (state && state.attempt !== attempt) return result('foreign_attempt');
  if (!state) {
    const expected=new URL(project);
    if (url.origin!==expected.origin || url.pathname.replace(/\/$/,'')!==expected.pathname.replace(/\/$/,'') ||
        url.hash || [...url.searchParams].some(([key,value]) => key!=='model' || !/^[A-Za-z0-9_.-]{1,128}$/.test(value)))
      return result('wrong_project');
    if (document.querySelectorAll('[data-message-author-role]').length) return result('existing_conversation');
    state = window.__jarvisPreparation = {attempt, wrote:false, submitted:false};
  }
  if (state.submitted) {
    if (!/\/c\/[0-9a-f-]{36}\/?$/i.test(url.pathname)) return result('waiting_url');
    return result('ready',{url:location.href,submitted:true});
  }
  const enabled = e => !e.disabled && !e.readOnly && e.getAttribute('aria-disabled') !== 'true';
  // This renderer has no window; its measured DOM is used without focusing or opening a keyboard.
  const visible = e => {
    const r=e.getBoundingClientRect(), s=getComputedStyle(e);
    return r.width>8 && r.height>8 && s.display!=='none' && s.visibility!=='hidden' && s.opacity!=='0';
  };
  const editors=[...document.querySelectorAll('textarea,input[type="text"],[contenteditable="true"],[role="textbox"]')]
    .filter(e => visible(e) && enabled(e));
  if (editors.length!==1) return result('waiting_editor');
  const editor=editors[0];
  const value=e => (e.value || e.innerText || e.textContent || '').trim();
  if (!state.wrote) {
    if (value(editor)) return result('draft');
    // Latch before mutation, including rejected input events and exceptions.
    state.wrote=true;state.editor=editor;
    try {
      if (editor.isContentEditable) {
        const event=new InputEvent('beforeinput',{bubbles:true,cancelable:true,inputType:'insertText',data:message});
        if (!editor.dispatchEvent(event)) return result('write_failed');
        editor.textContent=message;
      } else {
        const proto=editor instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
        const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set;
        if (!setter) return result('write_failed');
        setter.call(editor,message);
      }
      editor.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:message}));
      editor.dispatchEvent(new Event('change',{bubbles:true}));
    } catch (_) { return result('write_failed'); }
    if (value(editor)!==message) return result('write_failed');
    return result('written');
  }
  if (!state.submitted) {
    if (state.editor!==editor || !document.contains(editor) || value(editor)!==message) return result('draft');
    const form=editor.closest('form') || document;
    const names=new Set(['send','send message','send prompt','invia','invia messaggio']);
    const buttons=[...form.querySelectorAll('button,[role="button"]')].filter(e => visible(e) && enabled(e))
      .filter(e => e.getAttribute('data-testid')==='send-button' ||
        [e.getAttribute('aria-label'),e.getAttribute('title')].some(s => s && names.has(s.trim().toLowerCase())));
    if (buttons.length!==1) return result('waiting_send');
    state.submitted=true;state.sentAt=Date.now();
    try { buttons[0].click(); } catch (_) { return result('send_uncertain'); }
    return result('submitted');
  }
  return result('waiting_url');
})()
