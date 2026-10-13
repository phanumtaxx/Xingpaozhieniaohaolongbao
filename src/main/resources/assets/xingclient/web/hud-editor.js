(() => {
  "use strict";
  const anchors = ["TOP_LEFT", "TOP_CENTER", "TOP_RIGHT", "CENTER_LEFT", "CENTER", "CENTER_RIGHT", "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT"];
  const shade = document.createElement("div");
  shade.id = "xing-hud-editor-shade";
  shade.hidden = true;
  const editor = document.createElement("section");
  editor.id = "xing-hud-editor";
  editor.hidden = true;
  shade.setAttribute("data-world-backdrop", "");
  editor.innerHTML = `
    <aside class="hud-editor-window">
      <header class="hud-editor-window-title"><span>Hud Modules</span><button class="hud-editor-collapse" aria-label="Collapse Hud Modules" aria-expanded="true">?</button></header>
      <div class="hud-editor-window-content">
        <div class="hud-editor-widget-list"></div>
        <section class="hud-editor-inspector">
          <span class="hud-editor-label">Anchor</span><div class="hud-editor-anchors"></div>
          <span class="hud-editor-label">Position</span>
          <div class="hud-editor-position"><label class="hud-editor-field"><span>X</span><input data-field="x" aria-label="X offset" type="number" min="-16384" max="16384" step="0.25"></label><label class="hud-editor-field"><span>Y</span><input data-field="y" aria-label="Y offset" type="number" min="-16384" max="16384" step="0.25"></label></div>
          <label class="hud-editor-label hud-editor-font-label">Font size <span data-field="font-value"></span></label>
          <input type="range" data-field="fontSize" aria-label="Font size" min="4" max="24" step="0.25">
          <button class="hud-editor-button hud-editor-reset" data-action="reset">reset</button>
        </section>
        <section class="hud-editor-layout">
          <label class="hud-editor-label hud-editor-font-label">Background blur <span data-field="blur-value"></span></label>
          <input type="range" data-field="blur" aria-label="Background blur" min="0" max="12" step="0.5">
          <div class="hud-editor-options"><label><input type="checkbox" data-field="snap">snap</label><label><input type="checkbox" data-field="grid">grid</label></div>
        </section>
        <div class="hud-editor-actions"><button class="hud-editor-button" data-action="undo" title="Ctrl Z">undo</button><button class="hud-editor-button" data-action="redo" title="Ctrl Y">redo</button><button class="hud-editor-button" data-action="cancel">cancel</button><button class="hud-editor-button" data-action="save">save</button></div>
      </div>
    </aside>
    <div class="hud-editor-guide vertical" hidden></div><div class="hud-editor-guide horizontal" hidden></div>`;
  document.body.append(shade, editor);
  const field = name => editor.querySelector(`[data-field="${name}"]`);
  const list = editor.querySelector(".hud-editor-widget-list");
  const anchorGrid = editor.querySelector(".hud-editor-anchors");
  const inspector = editor.querySelector(".hud-editor-inspector");
  const panel = editor.querySelector(".hud-editor-window");
  const title = editor.querySelector(".hud-editor-window-title");
  const collapse = editor.querySelector(".hud-editor-collapse");
  let windowDrag = null, windowPending = null, expanded = true;
  const guideX = editor.querySelector(".hud-editor-guide.vertical");
  const guideY = editor.querySelector(".hud-editor-guide.horizontal");
  let state = null, frame = null, selected = null, drag = null, pending = null, editorScale = 1;
  const widgetElement = id => [...document.querySelectorAll(".xing-hud-widget")].find(element => element.dataset.hudId === id);
  const current = () => state?.items.find(item => item.id === selected);
  const origin = anchor => { const index = anchors.indexOf(anchor); return { x: index % 3 * .5, y: Math.floor(index / 3) * .5 }; };
  const point = style => {
    const o = origin(style.anchor), scale = frame.guiScale;
    return { x: frame.width * o.x + style.x * scale * (o.x === 1 ? -1 : 1), y: frame.height * o.y + style.y * scale * (o.y === 1 ? -1 : 1) };
  };
  const offsets = (position, anchor) => {
    const o = origin(anchor);
    return { x: (position.x - frame.width * o.x) / frame.guiScale * (o.x === 1 ? -1 : 1), y: (position.y - frame.height * o.y) / frame.guiScale * (o.y === 1 ? -1 : 1) };
  };
  function send(action, data = {}) {
    if (state) window.__xingEditorCommand?.(JSON.stringify({ action, ...data }));
  }
  function preview(style) {
    const element = widgetElement(selected);
    if (!element) return;
    const p = point(style), o = origin(style.anchor);
    element.style.left = `${p.x}px`; element.style.top = `${p.y}px`;
    element.style.transform = `translate(${-o.x * 100}%, ${-o.y * 100}%)`;
    element.style.setProperty("--hud-font-size", `${style.fontSize}px`);
  }
  function edit(style) {
    const item = current();
    if (!item) return;
    const next = { ...item.style, ...style };
    if (![next.x, next.y, next.fontSize].every(Number.isFinite)) return;
    next.x = Math.max(-16384, Math.min(16384, next.x));
    next.y = Math.max(-16384, Math.min(16384, next.y));
    next.fontSize = Math.max(4, Math.min(24, next.fontSize));
    item.style = next;
    pending = { id: item.id, style: next, revision: state.revision };
    preview(next);
    send("edit", { id: item.id, ...next });
    refreshInspector();
  }
  function select(id) {
    selected = id;
    list.querySelectorAll(".hud-editor-module").forEach(element => {
      element.classList.toggle("open", element.dataset.id === id && expanded);
      const item = state.items.find(item => item.id === element.dataset.id);
      element.classList.toggle("enabled", !!item?.enabled);
      element.querySelector(".hud-editor-widget").setAttribute("aria-pressed", !!item?.enabled);
      const options = element.querySelector(".hud-editor-module-options");
      options.textContent = element.dataset.id === id && expanded ? "?" : ">";
      options.setAttribute("aria-expanded", element.dataset.id === id && expanded);
    });
    document.querySelectorAll(".xing-hud-widget").forEach(element => element.dataset.hudSelected = String(element.dataset.hudId === id));
    refreshInspector();
  }
  function refreshInspector() {
    const item = current();
    inspector.hidden = !item || !expanded;
    if (!item) return;
    const module = [...list.querySelectorAll(".hud-editor-module")].find(element => element.dataset.id === item.id);
    if (module && inspector.parentElement !== module) module.append(inspector);
    for (const name of ["x", "y", "fontSize"]) {
      const input = field(name);
      if (document.activeElement !== input && !drag) input.value = Math.round(item.style[name] * 100) / 100;
    }
    field("font-value").textContent = `${document.activeElement === field("fontSize") ? field("fontSize").value : item.style.fontSize}px`;
    anchorGrid.querySelectorAll("button").forEach(button => button.setAttribute("aria-pressed", button.dataset.anchor === item.style.anchor));
  }
  for (const anchor of anchors) {
    const button = document.createElement("button");
    button.className = "hud-editor-anchor"; button.dataset.anchor = anchor;
    button.title = anchor.toLowerCase().replaceAll("_", " "); button.setAttribute("aria-label", button.title);
    button.onclick = () => { const item = current(); if (item) edit({ anchor, ...offsets(point(item.style), anchor) }); };
    anchorGrid.append(button);
  }
  editor.querySelectorAll("[data-action]").forEach(button => button.onclick = () => {
    if (button.dataset.action === "reset") send("reset", { id: selected });
    else send(button.dataset.action);
  });
  field("blur").oninput = event => {
    const blur = Number(event.target.value);
    field("blur-value").textContent = blur;
    send("layout", { blur });
  };
  for (const name of ["snap", "grid"]) field(name).onchange = event => send("layout", { [name]: event.target.checked });
  collapse.onclick = () => {
    panel.classList.toggle("collapsed");
    collapse.textContent = panel.classList.contains("collapsed") ? "+" : "?";
    collapse.setAttribute("aria-expanded", !panel.classList.contains("collapsed"));
  };
  function positionWindow(x, y) {
    x = Math.max(0, Math.min(frame.width / editorScale - panel.offsetWidth, x));
    y = Math.max(0, Math.min(frame.height / editorScale - panel.offsetHeight, y));
    panel.style.left = `${x}px`; panel.style.top = `${y}px`;
    return { windowX: x, windowY: y };
  }
  title.onpointerdown = event => {
    if (!state || event.button !== 0 || event.target.closest("button")) return;
    const bounds = panel.getBoundingClientRect();
    windowDrag = { startX: event.clientX, startY: event.clientY, x: bounds.x / editorScale, y: bounds.y / editorScale };
    title.setPointerCapture(event.pointerId);
    event.preventDefault();
  };
  for (const name of ["x", "y"]) field(name).onchange = event => edit({ [name]: Number(event.target.value) });
  field("fontSize").oninput = event => {
    const item = current(); if (!item) return;
    const size = Number(event.target.value);
    field("font-value").textContent = `${size}px`;
    preview({ ...item.style, fontSize: size });
  };
  field("fontSize").onchange = event => edit({ fontSize: Number(event.target.value) });
  function snapAxis(value, extent, o, targets) {
    let best = null;
    for (const target of targets) for (const edge of [0, .5, 1]) {
      const delta = target - (value + (edge - o) * extent);
      if (Math.abs(delta) <= 7 && (!best || Math.abs(delta) < Math.abs(best.delta))) best = { delta, target };
    }
    return best ? { value: value + best.delta, guide: best.target } : { value, guide: null };
  }
  document.addEventListener("pointerdown", event => {
    if (!state || event.button !== 0 || windowDrag) return;
    const element = event.target.closest(".xing-hud-widget");
    if (!element) return;
    expanded = true;
    select(element.dataset.hudId);
    const item = current(); if (!item) return;
    const bounds = element.getBoundingClientRect();
    drag = { element, startX: event.clientX, startY: event.clientY, point: point(item.style), style: { ...item.style }, width: bounds.width, height: bounds.height };
    element.dataset.hudDragging = "true";
    element.setPointerCapture(event.pointerId);
    event.preventDefault();
  });
  document.addEventListener("pointermove", event => {
    if (windowDrag) {
      positionWindow(windowDrag.x + (event.clientX - windowDrag.startX) / editorScale,
        windowDrag.y + (event.clientY - windowDrag.startY) / editorScale);
      return;
    }
    if (!drag) return;
    let x = drag.point.x + event.clientX - drag.startX, y = drag.point.y + event.clientY - drag.startY;
    const o = origin(drag.style.anchor);
    if (!event.shiftKey && field("grid").checked) {
      const grid = 8 * frame.guiScale; x = Math.round(x / grid) * grid; y = Math.round(y / grid) * grid;
    }
    guideX.hidden = true; guideY.hidden = true;
    if (!event.shiftKey && field("snap").checked) {
      const xs = [8 * frame.guiScale, frame.width / 2, frame.width - 8 * frame.guiScale];
      const ys = [8 * frame.guiScale, frame.height / 2, frame.height - 8 * frame.guiScale];
      document.querySelectorAll(".xing-hud-widget").forEach(element => {
        if (element === drag.element) return;
        const r = element.getBoundingClientRect(); xs.push(r.left, r.left + r.width / 2, r.right); ys.push(r.top, r.top + r.height / 2, r.bottom);
      });
      const sx = snapAxis(x, drag.width, o.x, xs), sy = snapAxis(y, drag.height, o.y, ys);
      x = sx.value; y = sy.value;
      if (sx.guide !== null) { guideX.hidden = false; guideX.style.left = `${sx.guide / editorScale}px`; }
      if (sy.guide !== null) { guideY.hidden = false; guideY.style.top = `${sy.guide / editorScale}px`; }
    }
    x = Math.max(o.x * drag.width, Math.min(frame.width - (1 - o.x) * drag.width, x));
    y = Math.max(o.y * drag.height, Math.min(frame.height - (1 - o.y) * drag.height, y));
    drag.style = { ...drag.style, ...offsets({ x, y }, drag.style.anchor) };
    preview(drag.style);
    field("x").value = Math.round(drag.style.x * 100) / 100; field("y").value = Math.round(drag.style.y * 100) / 100;
  });
  function endDrag(commit) {
    if (!drag) return;
    const style = drag.style;
    delete drag.element.dataset.hudDragging;
    drag = null; guideX.hidden = true; guideY.hidden = true;
    if (commit) edit(style);
  }
  function endWindowDrag(commit) {
    if (!windowDrag) return;
    windowDrag = null;
    if (commit) {
      windowPending = { windowX: Number.parseFloat(panel.style.left), windowY: Number.parseFloat(panel.style.top), revision: state.revision };
      send("layout", { windowX: windowPending.windowX, windowY: windowPending.windowY });
    }
  }
  document.addEventListener("pointerup", () => { endWindowDrag(true); endDrag(true); });
  document.addEventListener("pointercancel", () => { endWindowDrag(false); endDrag(false); });
  document.addEventListener("keydown", event => {
    if (!state) return;
    const input = event.target.closest("input");
    if (event.key === "Enter" && input?.type === "number") { input.blur(); return; }
    if (input) return;
    if (event.ctrlKey && ["z", "y"].includes(event.key.toLowerCase())) {
      event.preventDefault(); send(event.key.toLowerCase() === "y" || event.shiftKey ? "redo" : "undo"); return;
    }
    if (!current() || !["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)) return;
    event.preventDefault();
    const style = current().style, p = point(style), amount = (event.shiftKey ? 10 : 1) * frame.guiScale;
    p.x += event.key === "ArrowLeft" ? -amount : event.key === "ArrowRight" ? amount : 0;
    p.y += event.key === "ArrowUp" ? -amount : event.key === "ArrowDown" ? amount : 0;
    edit(offsets(p, style.anchor));
  });
  xing.onFrame(next => {
    frame = next;
    const incoming = next.data.hudEditor;
    editor.hidden = shade.hidden = !incoming;
    document.body.classList.toggle("xing-hud-editing", !!incoming);
    if (!incoming) { endWindowDrag(false); endDrag(false); state = null; pending = null; windowPending = null; return; }
    state = incoming;
    const color = value => `#${value.toString(16).padStart(6, "0")}`;
    editor.style.setProperty("--accent", color(state.theme.accent));
    editor.style.setProperty("--secondary", color(state.theme.secondary));
    editor.style.setProperty("--panel-opacity", state.theme.opacity / 100);
    for (const name of ["snap", "grid"]) field(name).checked = state.options[name];
    if (document.activeElement !== field("blur")) field("blur").value = state.options.blur;
    field("blur-value").textContent = field("blur").value;
    shade.classList.toggle("has-grid", field("grid").checked);
    if (windowPending && incoming.revision > windowPending.revision) windowPending = null;
    if (pending && incoming.revision > pending.revision) pending = null;
    if (pending) {
      const item = state.items.find(item => item.id === pending.id);
      if (item) item.style = pending.style;
    }
    editorScale = Math.min(1, next.width / 1000);
    editor.style.setProperty("--editor-scale", editorScale);
    editor.style.setProperty("--editor-height", `${next.height / editorScale}px`);
    if (!windowDrag) positionWindow(windowPending?.windowX ?? state.options.windowX, windowPending?.windowY ?? state.options.windowY);
    if (!state.items.some(item => item.id === selected)) selected = state.items[0]?.id;
    const signature = JSON.stringify(state.items.map(item => [item.id, item.name]));
    if (list.dataset.signature !== signature) {
      list.dataset.signature = signature;
      inspector.remove(); list.replaceChildren();
      for (const item of state.items) {
        const module = document.createElement("section"); module.className = "hud-editor-module"; module.dataset.id = item.id;
        const row = document.createElement("div"); row.className = "hud-editor-module-row";
        const button = document.createElement("button"); button.className = "hud-editor-widget"; button.textContent = item.name;
        const options = document.createElement("button"); options.className = "hud-editor-module-options"; options.setAttribute("aria-label", `${item.name} settings`);
        button.onclick = () => {
          select(item.id);
          const currentItem = state.items.find(value => value.id === item.id);
          send("toggle", { id: item.id, enabled: !currentItem.enabled });
        };
        const expand = () => { expanded = selected === item.id ? !expanded : true; select(item.id); };
        options.onclick = expand;
        row.oncontextmenu = event => { event.preventDefault(); expand(); };
        row.append(button, options); module.append(row); list.append(module);
      }
    }
    select(selected);
    editor.querySelector('[data-action="undo"]').disabled = !state.canUndo;
    editor.querySelector('[data-action="redo"]').disabled = !state.canRedo;
    queueMicrotask(() => {
      if (drag) preview(drag.style);
      else if (pending && pending.id === selected) preview(pending.style);
      else if (current() && document.activeElement === field("fontSize")) preview({ ...current().style, fontSize: Number(field("fontSize").value) });
    });
  });
})();
