(() => {
  "use strict";
  let frame = { width: 0, height: 0, worldReady: false, anchors: {}, data: {} };
  const listeners = new Set();
  window.xing = Object.freeze({
    get frame() { return frame; },
    data(channel) { return frame.data[channel]; },
    onFrame(callback) {
      if (typeof callback !== "function") throw new TypeError("Expected a frame callback");
      listeners.add(callback);
      return () => listeners.delete(callback);
    },
    receive(next) {
      frame = next;
      for (const listener of listeners) listener(next);
      document.querySelectorAll("[data-world-anchor]").forEach(element => {
        const anchor = next.anchors[element.dataset.worldAnchor];
        element.hidden = !next.worldReady || !anchor?.visible;
        element.toggleAttribute("data-world-visible", !element.hidden);
        if (anchor) {
          element.style.setProperty("--world-x", `${anchor.x}px`);
          element.style.setProperty("--world-y", `${anchor.y}px`);
          element.style.setProperty("--world-depth", anchor.depth);
        }
      });
      window.dispatchEvent(new CustomEvent("xing-frame", { detail: next }));
      if (window.__xingReportBackdrops) {
        const rectangles = [];
        for (const element of document.querySelectorAll("[data-world-backdrop]")) {
          const bounds = element.getBoundingClientRect();
          if (!bounds.width || !bounds.height || bounds.right < 0 || bounds.bottom < 0 || bounds.left > next.width || bounds.top > next.height) continue;
          const corners = Number.parseFloat(getComputedStyle(element).borderTopLeftRadius) || 0;
          rectangles.push([bounds.x, bounds.y, bounds.width, bounds.height, corners]);
          if (rectangles.length === 256) break;
        }
        window.__xingReportBackdrops(JSON.stringify(rectangles));
      }
    }
  });
})();
