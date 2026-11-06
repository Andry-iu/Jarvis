(() => {
  const cfg = __JW_CONFIG__;
  const project = new URL(cfg.project);
  const url = new URL(location.href);
  const key = name => /^g-p-([a-f0-9]{32})(?:-[a-z0-9-]+)?$/i.exec(name)?.[1].toLowerCase() || name;
  const expectedProject = /^\/g\/(g-p-[a-z0-9_-]+)\/project\/?$/i.exec(project.pathname)?.[1];
  const clean = u => u.origin + u.pathname.replace(/\/$/, '');
  const projectRoute = /^\/g\/(g-p-[a-z0-9_-]+)\/project\/?$/i.exec(url.pathname);
  const chatRoute = /^\/(?:g\/(g-p-[a-z0-9_-]+)\/)?c\/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\/?$/i.exec(url.pathname);
  const routeSafe = url.origin === 'https://chatgpt.com' && project.origin === url.origin && expectedProject &&
    !url.hash && !url.username && !url.password && !/%/i.test(url.pathname) &&
    [...url.searchParams].every(([k,v]) => k==='model' && /^[A-Za-z0-9_.-]{1,128}$/.test(v));
  const inProject = projectRoute && key(projectRoute[1])===key(expectedProject);
  const ownedChat = chatRoute && chatRoute[1] && key(chatRoute[1])===key(expectedProject);
  const previous = window.__jarvisGoogleBinding;
  const previousDirect = chatRoute && previous?.project===key(expectedProject) && previous.url===clean(url);
  let ledger = window.__jarvisGoogleText;
  // Restart is a read-only clearance to leave this page for the configured project.
  // It never adopts a generic direct chat as the destination of a new send.
  const restarting=cfg.action==='restart' && (inProject || ownedChat || (chatRoute && !chatRoute[1]));
  const metrics={readOnlyRestart:!!restarting};
  let currentEditor=null, currentForm=null;
  const value=e => (e.value || e.innerText || e.textContent || '').trim();
  const lineEndings=s => s.replace(/\r\n?/g,'\n');
  const whitespace=s => s.replace(/\s+/g,' ').trim();
  // Diagnostic comparisons never authorize adoption of a replacement node or a modified draft.
  const diagnostics=() => {
    const state=ledger?.turns.get(cfg.turn);
    const d={...metrics,ledger:!!ledger,sendDispatched:!!state?.dispatched};
    if(currentEditor) {
      const actual=value(currentEditor);
      Object.assign(d,{draftChars:actual.length,hasDraft:!!actual,editorEnabled:enabled(currentEditor),
        contentEditable:currentEditor.isContentEditable===true,
        draftOwned:!!ledger?.turns && [...ledger.turns.values()].some(s=>s.editor===currentEditor && s.text===actual)});
      if(state) Object.assign(d,{sameEditor:state.editor===currentEditor,sameForm:state.form===currentForm,
        originalEditorConnected:document.contains(state.editor),currentEditorConnected:document.contains(currentEditor),
        textMatches:actual===state.text,lineEndingsMatch:lineEndings(actual)===lineEndings(state.text),
        normalizedTextMatches:whitespace(actual)===whitespace(state.text),writeVerified:state.writeVerified===true,
        structuredWrite:state.structuredWrite===true,insertedLineBreaks:state.insertedLineBreaks || 0,
        trustedInputSeen:ledger.trustedInputSeen===true,inputObservationAvailable:ledger.inputObservationAvailable===true,
        expectedChars:state.text.length,draftLineBreaks:(actual.match(/\n/g)||[]).length,
        expectedLineBreaks:(state.text.match(/\n/g)||[]).length});
    }
    return d;
  };
  const result = phase => JSON.stringify({phase,diagnostics:diagnostics(), ...(!restarting && (ownedChat || previousDirect || ledger?.url===clean(url)) && chatRoute
    ? {conversation:clean(url),directVerified:!chatRoute[1]} : {})});
  if (!routeSafe || (!restarting && !inProject && !ownedChat && !previousDirect && !(chatRoute && !chatRoute[1] && ledger?.turns &&
      [...ledger.turns.values()].some(t=>t.dispatched)))) return result('wrong_conversation');
  if (!restarting && ledger && ledger.session!==cfg.session) return result('old_session');
  if (!restarting && ledger?.url && ledger.url!==clean(url)) return result('wrong_conversation');
  const enabled = e => !e.disabled && !e.readOnly && e.getAttribute('aria-disabled') !== 'true';
  const visible = e => { const r=e.getBoundingClientRect(),s=getComputedStyle(e);
    return r.width>8 && r.height>8 && s.display!=='none' && s.visibility!=='hidden' && s.opacity!=='0'; };
  const pending=cfg.action==='send' ? ledger?.turns.get(cfg.turn) : null;
  const observing=!!pending?.dispatched;
  metrics.observingSend=observing;
  if(observing && pending.text!==cfg.text) return result('uncertain');
  if(observing) {
    // Pin the first canonical chat identity reached after this one consumed send.
    // A different chat during observation is never a fallback destination.
    if(pending.observationChatId && (!chatRoute || pending.observationChatId!==chatRoute[2].toLowerCase()))
      return result('wrong_conversation');
    if(chatRoute) pending.observationChatId=chatRoute[2].toLowerCase();
    metrics.receiptRoutePinned=!!pending.observationChatId;
  }
  const userBodies=() => [...document.querySelectorAll('[data-message-author-role="user"]')].map(e =>
    (e.querySelector('[data-message-content],.whitespace-pre-wrap') || e).innerText || e.textContent || '');
  const normalize=s => s.replace(/\s+/g,' ').trim();
  const bodies=observing ? userBodies() : [];
  if(observing) Object.assign(metrics,{userMessages:bodies.length,
    matchedUserMessages:bodies.filter(t=>normalize(t)===normalize(pending.text)).length,beforeUserMessages:pending.before});
  metrics.blockingDialog=[...document.querySelectorAll('[role="dialog"],[aria-modal="true"],dialog[open]')].some(visible);
  if (metrics.blockingDialog) return result(observing ? 'waiting_observation' : 'approval_or_dialog');
  const editors=[...document.querySelectorAll('textarea,input[type="text"],[contenteditable="true"],[role="textbox"]')]
    .filter(e => visible(e));
  metrics.editors=editors.length;
  if (editors.length!==1) return result(observing ? 'waiting_observation' : 'ambiguous_editor');
  const editor=editors[0];currentEditor=editor;
  // A form is required for writes/clicks, not for a read-only restart or receipt observation.
  const form=editor.closest('form');currentForm=form;
  metrics.formPresent=!!form;
  metrics.messageNodesPresent=!!document.querySelector('[data-message-author-role]');
  if(observing) {
    const observed=!value(editor) && metrics.matchedUserMessages>pending.before;
    if(observed && chatRoute) {
      ledger.url=clean(url);
      window.__jarvisGoogleBinding={project:key(expectedProject),url:ledger.url};
      return result('observed');
    }
    return result('waiting_observation');
  }
  if (!restarting && (!form || (!inProject && !metrics.messageNodesPresent))) return result('unverified_conversation');
  if (!restarting && inProject && metrics.messageNodesPresent) return result('project_has_messages');
  if (cfg.action==='probe' || restarting) {
    // A restart only checks that leaving this page will not discard a draft or interrupt work.
    if ([...document.querySelectorAll('button,[role="button"]')].some(e => visible(e) &&
        (['stop-button','voice-stop-button'].includes(e.getAttribute('data-testid')) ||
         /^(stop generating|interrompi generazione|end voice|termina|end conversation|termina conversazione)$/i.test(e.getAttribute('aria-label') || ''))))
      return result('busy');
    if(value(editor)) return result('draft');
    if(!enabled(editor)) return result('busy');
    if(restarting) return result('restart_ready');
    if(ownedChat || previousDirect) window.__jarvisGoogleBinding={project:key(expectedProject),url:clean(url)};
    return result('ready');
  }
  if (!cfg.turn || !cfg.text.trim()) return result('invalid');
  if (!ledger && cfg.fresh && !inProject) return result('wrong_conversation');
  if (!ledger) {
    ledger=window.__jarvisGoogleText={session:cfg.session,turns:new Map(),url:ownedChat || previousDirect ? clean(url) : ''};
    // Only a boolean about trusted edit events in this document; no event data or target text.
    // This is evidence for diagnostics, never proof that the draft was or was not edited by a person.
    const owner=ledger;
    const kinds=['beforeinput','input','paste','drop','compositionstart'];
    const observe=e => {if(e.isTrusted===true) owner.trustedInputSeen=true;};
    owner.disposeDiagnostics=() => {for(const kind of kinds) document.removeEventListener(kind,observe,true);};
    try {
      for(const kind of kinds) document.addEventListener(kind,observe,true);
      owner.inputObservationAvailable=true;
    } catch(_) {try {owner.disposeDiagnostics();} catch(_) {} owner.inputObservationAvailable=false;}
  }
  let state=ledger.turns.get(cfg.turn);
  if(cfg.resume && !state) return result('ledger_lost');
  if (!state) {
    // No action when Voice, generation or an approval is in progress.
  if ([...document.querySelectorAll('button,[role="button"]')].some(e => visible(e) &&
      (['stop-button','voice-stop-button'].includes(e.getAttribute('data-testid')) ||
       /^(stop generating|interrompi generazione|end voice|termina|end conversation|termina conversazione)$/i.test(e.getAttribute('aria-label') || ''))))
      return result('busy');
    if (ledger.turns.size>=100) return result('session_limit');
    if (!enabled(editor)) return result('busy');
    if (value(editor)) return result('draft');
    state={editor,form,text:cfg.text,dispatched:false,failed:false,writeVerified:false,before:userBodies().filter(t=>normalize(t)===normalize(cfg.text)).length};
    ledger.turns.set(cfg.turn,state); // consume before mutation, even if an exception follows
    ledger.trustedInputSeen=false;
    try {
      if (editor.isContentEditable) {
        if (!editor.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,cancelable:true,inputType:'insertText',data:cfg.text}))) {
          state.failed=true; return result('write_failed');
        }
        // A literal LF inside one HTML text node can become a space when a rich editor
        // reparses the DOM. Explicit BR nodes preserve the intended line boundaries.
        // Text nodes keep markup-looking instructions literal; never parse user HTML.
        const fragment=document.createDocumentFragment();
        const lines=cfg.text.split('\n');
        lines.forEach((line,index) => {
          if(index) fragment.appendChild(document.createElement('br'));
          fragment.appendChild(document.createTextNode(line));
        });
        editor.replaceChildren(fragment);
        state.structuredWrite=true;state.insertedLineBreaks=lines.length-1;
      } else {
        const proto=editor instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
        const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set;
        if (!setter) { state.failed=true; return result('write_failed'); }
        setter.call(editor,cfg.text);
      }
      editor.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:cfg.text}));
      editor.dispatchEvent(new Event('change',{bubbles:true}));
      if (value(editor)!==cfg.text) { state.failed=true; return result('write_failed'); }
      state.writeVerified=true;
      return result('written');
    } catch (_) { state.failed=true; return result('write_uncertain'); }
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
