/* Offline quick-list parsing; speech, when available, is provided by the browser. */
(function () {
  const units = new Set(['kg','g','mg','l','ml','litre','litres','liter','liters','grams','gram','kilo','kilos']);
  function parseQuickItems(text) {
    return text.split(/\s*(?:[,;\n]+|\s+and\s+)\s*/i).map(x => x.trim().replace(/\s+/g, ' ')).filter(Boolean).slice(0, 50).map(part => {
      const m = part.match(/^(\d+)\s*(?:[xX]\s*)?(.+)$/);
      const name = m ? m[2].trim() : '';
      const qty = m ? Number(m[1]) : 0;
      return m && qty >= 1 && qty <= 9999 && name && !units.has(name.split(' ')[0].toLowerCase())
        ? {name, quantity:qty} : {name:part, quantity:1};
    });
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = {parseQuickItems};
  if (typeof document === 'undefined') return;
  const container = document.querySelector('#list');
  if (!container) return;
  const button = document.createElement('button');
  button.type = 'button'; button.textContent = 'Add multiple items or speak';
  button.style.cssText = 'width:100%;margin:12px 0;padding:12px;border:1px solid #14583e;border-radius:12px;background:white;color:#14583e;font-weight:600';
  container.prepend(button);
  const dialog = document.createElement('dialog');
  dialog.innerHTML = '<form method="dialog"><h2>Add multiple items</h2><p>Separate items with commas, “and” or new lines. Example: 2 milk, bread, 1 kg rice.</p><label for="quickText">Shopping items</label><textarea id="quickText" rows="4" style="width:100%;box-sizing:border-box"></textarea><p><button type="button" id="quickSpeak">Speak items</button></p><p id="quickInfo" style="font-size:.85em"></p><p id="quickReview" aria-live="polite"></p><p style="font-size:.85em">Items are added without a store or price. Use Add item to enter a price.</p><div><button type="button" id="quickCancel">Cancel</button> <button type="button" id="quickSubmit" class="primary">Add items</button></div></form>';
  document.body.appendChild(dialog);
  const input = dialog.querySelector('#quickText'), review = dialog.querySelector('#quickReview');
  const info = dialog.querySelector('#quickInfo'), speak = dialog.querySelector('#quickSpeak'), submit = dialog.querySelector('#quickSubmit');
  function updateReview() {
    const rows = parseQuickItems(input.value);
    review.textContent = rows.length ? `Ready to add ${rows.length} item(s): ${rows.slice(0,5).map(x => `${x.quantity} x ${x.name}`).join(', ')}${rows.length > 5 ? '…' : ''}` : '';
    submit.disabled = rows.length === 0;
  }
  input.addEventListener('input', updateReview);
  window.khayaQuickAdd = (text = '') => { input.value = text; updateReview(); dialog.showModal(); };
  button.addEventListener('click', () => window.khayaQuickAdd());
  dialog.querySelector('#quickCancel').addEventListener('click', () => dialog.close());
  const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Speech) { speak.disabled = true; info.textContent = 'Speech is unavailable in this preview. Type your items instead.'; }
  else {
    info.textContent = 'Speech uses your browser’s recognition service. Review items before adding.';
    speak.addEventListener('click', () => {
      const rec = new Speech(); rec.lang = navigator.language || 'en-ZA'; rec.interimResults = false;
      rec.onresult = e => { const spoken = e.results[0][0].transcript; input.value += (input.value.trim() ? ', ' : '') + spoken; updateReview(); };
      rec.onerror = () => { info.textContent = 'Speech could not start. Check microphone permission or type your items instead.'; };
      try { rec.start(); } catch (_) { info.textContent = 'Speech could not start. Type your items instead.'; }
    });
  }
  submit.addEventListener('click', () => {
    const rows = parseQuickItems(input.value); if (!rows.length) return;
    const names = new Set(items.map(item => String(item.name).trim().toLocaleLowerCase()));
    rows.filter(row => { const key = row.name.trim().toLocaleLowerCase(); if (names.has(key)) return false; names.add(key); return true; }).forEach(row => items.push({id:String(Date.now()) + '-' + Math.random().toString(36).slice(2), name:row.name, quantity:row.quantity, store:'', price:0, checked:false}));
    persist(); dialog.close();
  });
}());
