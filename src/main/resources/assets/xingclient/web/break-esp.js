(() => {
  "use strict";
  const namespace = "http://www.w3.org/2000/svg";
  const make = name => document.createElementNS(namespace, name);
  const root = make("svg");
  root.id = "xing-break-esp";
  root.setAttribute("aria-hidden", "true");
  const markers = new Map();
  document.body.append(root);

  function createMarker(id) {
    const group = make("g"), fills = make("g"), edges = make("path"), label = make("text");
    group.dataset.block = id;
    edges.classList.add("break-esp-edges");
    label.classList.add("break-esp-label");
    group.append(fills, edges, label);
    root.append(group);
    return { group, fills, edges, label, polygons: [] };
  }
  function edgeKey(a, b) {
    const first = a.map(value => value.toFixed(2)).join(",");
    const second = b.map(value => value.toFixed(2)).join(",");
    return first < second ? `${first}:${second}` : `${second}:${first}`;
  }
  xing.onFrame(frame => {
    const data = frame.data.break_esp;
    root.style.display = data && frame.worldReady ? "block" : "none";
    root.setAttribute("viewBox", `0 0 ${frame.width} ${frame.height}`);
    const keep = new Set();
    if (data && frame.worldReady) for (const marker of data.markers) {
      keep.add(marker.id);
      let view = markers.get(marker.id);
      if (!view) { view = createMarker(marker.id); markers.set(marker.id, view); }
      const style = data.style, scale = frame.guiScale ?? 1;
      view.group.style.opacity = marker.alpha;
      view.group.style.color = marker.color;
      view.group.dataset.kind = marker.self ? "self" : "other";
      view.group.style.filter = style.glow > 0 ? `drop-shadow(0 0 ${style.glow * scale}px ${marker.color})` : "none";
      while (view.polygons.length < marker.faces.length) {
        const polygon = make("polygon"); view.polygons.push(polygon); view.fills.append(polygon);
      }
      while (view.polygons.length > marker.faces.length) view.polygons.pop().remove();
      const unique = new Set(), segments = [];
      marker.faces.forEach((face, index) => {
        const points = [];
        for (let i = 0; i < face.points.length; i += 2) points.push([face.points[i], face.points[i + 1]]);
        view.polygons[index].setAttribute("points", points.map(point => point.join(",")).join(" "));
        for (let i = 0; i < points.length; i++) {
          const a = points[i], b = points[(i + 1) % points.length], key = edgeKey(a, b);
          if (unique.has(key)) continue;
          unique.add(key); segments.push(`M${a.join(",")}L${b.join(",")}`);
        }
      });
      view.fills.setAttribute("fill", marker.color);
      view.fills.setAttribute("fill-opacity", style.fill);
      view.edges.setAttribute("d", segments.join(""));
      view.edges.setAttribute("stroke", marker.color);
      view.edges.setAttribute("stroke-opacity", style.outline);
      view.edges.setAttribute("stroke-width", style.lineWidth * scale);
      view.edges.setAttribute("stroke-dasharray", style.dashed ? `${5 * scale} ${3 * scale}` : "none");
      view.edges.setAttribute("stroke-dashoffset", style.dashed ? -performance.now() / 1000 * style.dashSpeed * 16 * scale : 0);
      view.label.style.display = style.labels && marker.labelVisible ? "block" : "none";
      view.label.setAttribute("x", marker.x);
      view.label.setAttribute("y", marker.y);
      view.label.setAttribute("font-size", style.fontSize * scale);
      view.label.textContent = `${Math.round(marker.progress * 100)}%`;
      root.append(view.group);
    }
    for (const [id, view] of markers) if (!keep.has(id)) { view.group.remove(); markers.delete(id); }
  });
})();
