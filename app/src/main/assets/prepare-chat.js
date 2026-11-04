(() => {
  const project = __JARVIS_PROJECT_JSON__;
  const attempt = __JARVIS_ATTEMPT_JSON__;
  const message = 'leggi le istruzioni del progetto';
  let verification = {};
  const result = (phase, extra={}) => JSON.stringify({phase, ...verification, ...extra});
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
  // Observe the owned send before waiting for the URL: the user bubble may later be virtualized.
  const normal = text => text.replace(/\s+/g,' ').trim();
  const rawUsers=[...document.querySelectorAll('[data-message-author-role="user"]')];
  const users=rawUsers.filter(node => !rawUsers.some(parent => parent!==node && parent.contains?.(node)));
  const userBody = node => {
    if (!node) return {text:'',source:'none'};
    const candidates=node.querySelectorAll ? [...node.querySelectorAll('.whitespace-pre-wrap,[data-message-content]')] : [];
    const leaves=candidates.filter(item => !candidates.some(child => child!==item && item.contains?.(child)));
    if (leaves.length) return {text:normal(leaves.map(value).join(' ')),source:'body'};
    const direct=normal(value(node));
    if (direct===message) return {text:direct,source:'root'};
    if (node.cloneNode) {
      const copy=node.cloneNode(true);
      copy.querySelectorAll('button,[role="button"],.sr-only,[aria-hidden="true"]').forEach(item => item.remove());
      return {text:normal(copy.textContent || ''),source:'clean_root'};
    }
    return {text:direct,source:'root'};
  };
  const bodyCheck=users.length===1 ? userBody(users[0]) : {text:'',source:'none'};
  const userMatches=users.length===1 && bodyCheck.text===message;
  const accepted=value(editor)==='';
  if (userMatches && accepted) state.echoObserved=true;
  const chat=url.pathname.match(/\/c\/([0-9a-f-]{36})\/?$/i);
  const reason=users.length===0 ? 'message_absent' : users.length!==1 ? 'message_count' :
    !bodyCheck.text ? 'body_pending' : !userMatches ? 'message_mismatch' : !accepted ? 'pending_acceptance' : 'verified';
  verification={users:users.length,rawUsers:rawUsers.length,source:bodyCheck.source,userMatches,
    echoObserved:!!state.echoObserved,urlObserved:!!chat,reason};
  if (state.chatId && (!chat || state.chatId!==chat[1].toLowerCase()))
    return result('wrong_conversation',{reason:'url_changed'});
  if (!chat) return result('waiting_url');
  state.chatId=chat[1].toLowerCase();
  // Missing/empty nodes are a pending render, not evidence of another conversation.
  // A stable, nonempty mismatch or multiple independent messages stays ineligible and then stops.
  if (users.length>1 || (users.length===1 && bodyCheck.text && !userMatches)) {
    const key=users.length+':'+bodyCheck.text;
    if (state.mismatchKey!==key) {state.mismatchKey=key;state.mismatchAt=Date.now();}
    return result(Date.now()-state.mismatchAt>=15000 ? 'wrong_conversation' : 'waiting_user');
  }
  state.mismatchKey='';
  if (!accepted) return result('waiting_acceptance');
  if (!state.echoObserved) return result('waiting_user');
  const messages=[...document.querySelectorAll('[data-message-author-role="assistant"]')];
  const last=messages.at(-1);
  const body=last?.querySelector('.markdown,[data-message-content]') || last;
  const reply=body ? value(body) : '';
  const blocking=document.querySelector('[data-is-streaming="true"],[aria-busy="true"],[role="dialog"]') ||
    [...document.querySelectorAll('button,[role="button"]')].some(e => visible(e) &&
      (e.getAttribute('data-testid')==='stop-button' ||
       /^(stop generating|interrompi generazione|consenti|allow|approva|approve|continua|continue|conferma|confirm)(?:\s|$)/i.test(
         (e.getAttribute('aria-label') || e.innerText || '').trim())));
  if (!reply || reply.length<24 || blocking) {state.lastReply='';state.stableAt=0;return result('waiting_reply');}
  if (reply!==state.lastReply) {state.lastReply=reply;state.stableAt=Date.now();return result('waiting_reply');}
  if (Date.now()-state.stableAt<3000 || Date.now()-state.sentAt<300) return result('waiting_reply');
  return result('ready',{url:location.href,reply});
})()
