(() => {
  "use strict";
  const tags = new Map();
  const container = document.createElement("section");
  container.id = "xing-nametags";
  document.body.append(container);
  function createTag(player) {
    const element = document.createElement("div");
    element.className = "nametag";
    element.dataset.worldAnchor = player.id;
    element.hidden = true;
    const heading = document.createElement("div");
    heading.className = "nametag-heading";
    heading.dataset.worldBackdrop = "";
    const name = document.createElement("span");
    const health = document.createElement("span");
    health.className = "nametag-health";
    heading.append(name, health);
    const equipment = document.createElement("div");
    equipment.className = "nametag-equipment";
    element.append(heading, equipment);
    container.append(element);
    return { element, name, health, equipment, equipmentKey: "" };
  }
  container.hidden = true;
  async function waitForFont() {
    for (let attempt = 0; attempt < 100; attempt++) {
      const faces = await document.fonts.load('500 16px "Xing Inter"');
      if (faces.length) { container.hidden = false; return; }
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    console.error("Nametags: bundled Inter font did not load");
  }
  waitForFont().catch(error => console.error("Nametags font error:", error));
  xing.onFrame(frame => {
    const state = frame.data.nametags;
    const players = state?.players ?? [];
    const keep = new Set();
    const rgb = state?.backgroundColor ?? "#ffffff";
    const alpha = state?.backgroundAlpha ?? .2;
    const value = Number.parseInt(rgb.slice(1), 16);
    container.style.setProperty("--nametag-background", `rgba(${value >> 16 & 255}, ${value >> 8 & 255}, ${value & 255}, ${alpha})`);
    container.style.setProperty("--ui-scale", frame.guiScale ?? 1);
    container.style.setProperty("--nametag-font", `${state?.fontSize ?? 6}px`);
    container.style.setProperty("--nametag-padding-x", `${state?.paddingX ?? 3.25}px`);
    container.style.setProperty("--nametag-padding-y", `${state?.paddingY ?? 3.25}px`);
    container.style.setProperty("--nametag-corners", `${state?.cornerRadius ?? 2.25}px`);
    for (const player of players) {
      keep.add(player.id);
      let tag = tags.get(player.id);
      if (!tag) { tag = createTag(player); tags.set(player.id, tag); }
      if (tag.name.textContent !== player.name) tag.name.textContent = player.name;
      const health = `${Number(player.health ?? 0).toFixed(1).replace(/\.0$/, "")} HP`;
      if (tag.health.textContent !== health) tag.health.textContent = health;
      tag.health.style.color = player.healthColor ?? "#84e1ae";
      const items = player.equipment ?? [];
      const key = JSON.stringify(items);
      if (key !== tag.equipmentKey) {
        tag.equipmentKey = key;
        tag.equipment.replaceChildren();
        for (const item of items) {
          if (!item.icon) continue;
          const slot = document.createElement("span");
          slot.className = "nametag-item";
          slot.title = item.name;
          const image = document.createElement("img");
          image.src = item.icon; image.alt = item.name;
          slot.append(image);
          if (item.count > 1) {
            const count = document.createElement("span");
            count.className = "nametag-count"; count.textContent = item.count;
            slot.append(count);
          }
          tag.equipment.append(slot);
        }
      }
    }
    for (const [id, tag] of tags) {
      if (!keep.has(id)) { tag.element.remove(); tags.delete(id); }
    }
  });
})();
