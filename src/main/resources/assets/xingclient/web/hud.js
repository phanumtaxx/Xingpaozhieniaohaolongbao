(() => {
  "use strict";
  const widgets = new Map();
  const container = document.createElement("section");
  container.id = "xing-hud";
  container.hidden = true;
  document.body.append(container);
  async function loadFont() {
    for (let attempt = 0; attempt < 100; attempt++) {
      const faces = await document.fonts.load('400 16px "Xing Inter"');
      if (faces.length) { container.hidden = false; return; }
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    console.error("HUD: bundled Inter font did not load");
  }
  loadFont().catch(error => console.error("HUD font error:", error));
  function createWidget(widget) {
    const template = [...document.querySelectorAll("template[data-hud-template]")]
      .find(element => element.dataset.hudTemplate === widget.template);
    if (!template) return null;
    const element = document.createElement("div");
    element.className = "xing-hud-widget";
    element.dataset.hudId = widget.id;
    element.append(template.content.cloneNode(true));
    container.append(element);
    return element;
  }
  xing.onFrame(frame => {
    const keep = new Set();
    container.style.setProperty("--hud-scale", frame.guiScale ?? 1);
    for (const widget of frame.data.hud?.widgets ?? []) {
      keep.add(widget.id);
      let element = widgets.get(widget.id);
      if (!element) {
        element = createWidget(widget);
        if (!element) continue;
        widgets.set(widget.id, element);
      }
      if (element.dataset.hudDragging !== "true") {
        element.style.left = `${widget.x}px`;
        element.style.top = `${widget.y}px`;
        element.style.transform = `translate(${-widget.originX * 100}%, ${-widget.originY * 100}%)`;
      }
      element.style.setProperty("--hud-font-size", `${widget.fontSize}px`);
      for (const binding of element.querySelectorAll("[data-hud-text]")) {
        const value = binding.dataset.hudText.split(".").reduce((current, key) => current?.[key], widget.data);
        const text = value == null ? "" : String(value);
        if (binding.textContent !== text) binding.textContent = text;
      }
    }
    for (const [id, element] of widgets) {
      if (!keep.has(id)) { element.remove(); widgets.delete(id); }
    }
  });
})();
