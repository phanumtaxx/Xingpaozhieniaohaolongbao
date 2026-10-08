/* Cached sakura artwork, drawn above the interface without receiving input. */
(() => {
  const canvas = document.querySelector('#blossoms');
  const context = canvas.getContext('2d');
  if (!context) return;
  const menu = document.querySelector('#interface');
  const toggle = document.querySelector('#show-blossoms');
  const TAU = Math.PI * 2;
  const random = (min, max) => min + Math.random() * (max - min);
  let width = 0, height = 0, ratio = 1, frame = 0, last = 0, time = 0;
  let particles = [];

  // Asymmetric lobes and a small notch distinguish a sakura petal from confetti.
  function petal(ctx, variation = 0) {
    ctx.beginPath();
    ctx.moveTo(0, 4);
    ctx.bezierCurveTo(-8, -2, -20, -16, -14, -29);
    ctx.bezierCurveTo(-11, -37, -5, -38, 0, -29);
    ctx.bezierCurveTo(5, -39, 14, -35, 16, -28);
    ctx.bezierCurveTo(23, -13, 8, 1, 0, 4);
    const fill = ctx.createLinearGradient(-12, -34, 13, 4);
    fill.addColorStop(0, '#fff6fa');
    fill.addColorStop(.35, variation ? '#f7c9e0' : '#ffdce9');
    fill.addColorStop(.73, '#e9a2c4');
    fill.addColorStop(1, '#c7659a');
    ctx.fillStyle = fill;
    ctx.fill();
    ctx.strokeStyle = '#fbe2ee99';
    ctx.lineWidth = .55;
    ctx.stroke();
    // A translucent fold and delicate veins give the petal depth as it turns.
    ctx.save();
    ctx.clip();
    ctx.beginPath();
    ctx.moveTo(0, 4);
    ctx.bezierCurveTo(4, -9, 0, -23, 0, -29);
    ctx.bezierCurveTo(13, -19, 8, -5, 0, 4);
    ctx.fillStyle = '#ffffff32';
    ctx.fill();
    ctx.strokeStyle = '#ae528c29';
    ctx.lineWidth = .6;
    for (const side of [-1, 1]) {
      ctx.beginPath();ctx.moveTo(0, 3);
      ctx.bezierCurveTo(side * 2, -10, side * 8, -14, side * 10, -27);
      ctx.stroke();
    }
    ctx.restore();
  }

  function sprite(isFlower, variation) {
    const image = document.createElement('canvas');
    image.width = image.height = 192;
    const ctx = image.getContext('2d');
    ctx.translate(96, 96);
    ctx.scale(2.35, 2.35);
    if (isFlower) {
      for (let i = 0; i < 5; i++) {
        ctx.save();ctx.rotate(i * TAU / 5);petal(ctx, i % 2);ctx.restore();
      }
      const center = ctx.createRadialGradient(0, 0, 0, 0, 0, 9);
      center.addColorStop(0, '#b65087');center.addColorStop(1, '#d77ba000');
      ctx.fillStyle = center;ctx.beginPath();ctx.arc(0, 0, 9, 0, TAU);ctx.fill();
      for (let i = 0; i < 10; i++) {
        const angle = i * TAU / 10, radius = i % 2 ? 7 : 10;
        const x = Math.cos(angle) * radius, y = Math.sin(angle) * radius;
        ctx.strokeStyle = '#ba658ca6';ctx.lineWidth = .65;
        ctx.beginPath();ctx.moveTo(0, 0);ctx.lineTo(x, y);ctx.stroke();
        ctx.fillStyle = '#ffe6a8';ctx.beginPath();ctx.arc(x, y, .9, 0, TAU);ctx.fill();
      }
    } else {
      ctx.translate(0, 15);ctx.rotate((variation - 1) * .18);petal(ctx, variation);
    }
    return image;
  }
  const sprites = [sprite(false, 0), sprite(false, 1), sprite(false, 2), sprite(true, 0)];

  function createParticle(index, initial) {
    const flower = index % 6 === 0;
    const depth = index % 5 === 0 ? random(.8, 1) : index % 5 === 2 ? random(0, .25) : random(.35, .65);
    return {
      x: initial ? random(0, width) : random(width * .15, width + 100),
      y: initial ? random(0, height) : random(-110, -35),
      size: (flower ? random(29, 36) : random(29, 43)) * (.9 + .4 * depth),
      speed: random(27, 48) * (1 - .22 * depth), fall: random(18, 34) * (1 - .2 * depth),
      angle: random(0, TAU), spin: random(-.34, .34),
      phase: random(0, TAU), flutter: random(.6, 1.2) * (1 - .15 * depth),
      opacity: random(.62, .82), flower, depth,
      sprite: flower ? sprites[3] : sprites[index % 3]
    };
  }
  function clear() { context.clearRect(0, 0, width, height); }
  function resize() {
    width = innerWidth;height = innerHeight;ratio = Math.min(devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * ratio);canvas.height = Math.round(height * ratio);
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    const count = width < 650 ? 6 : width < 1100 ? 9 : 12;
    particles = Array.from({length: count}, (_, i) => createParticle(i, true));
  }
  function tick(now) {
    frame = 0;
    const dt = last ? Math.min((now - last) / 1000, .05) : 0;
    last = now;time += dt;clear();
    const breeze = Math.sin(time * .23) * 11;
    particles.forEach((p, index) => {
      const gust = Math.pow(Math.max(0, Math.sin(time * .34 - .9 - p.depth * .3)), 4);
      p.x -= (p.speed + breeze + Math.sin(time * .8 + p.phase) * 9 + gust * (12 + p.depth * 10)) * dt;
      p.y += (p.fall + Math.cos(time * .65 + p.phase) * 7 - gust * 5) * dt;
      p.angle += p.spin * (1 + gust * .45) * dt;
      if (p.x < -70 || p.y > height + 70) {
        particles[index] = createParticle(index, false);return;
      }
      const edgeFade = Math.max(0, Math.min(1, (p.x + 35) / 65, (height + 35 - p.y) / 65, (p.y + 35) / 65));
      context.save();
      context.translate(p.x, p.y);
      context.rotate(p.angle + Math.sin(time * .7 + p.phase) * .24);
      const tilt = Math.cos(time * p.flutter + p.phase);
      context.scale(p.flower ? .8 + .2 * Math.abs(tilt) : .25 + .75 * Math.abs(tilt), 1);
      context.globalAlpha = p.opacity * edgeFade;
      context.drawImage(p.sprite, -p.size / 2, -p.size / 2, p.size, p.size);
      context.restore();
    });
    frame = requestAnimationFrame(tick);
  }
  function sync() {
    // The user's explicit effect toggle controls motion, including when Windows
    // animations are disabled. Do not silently override an enabled setting.
    const active = toggle.checked && !menu.hidden && !document.hidden;
    canvas.hidden = !active;
    if (!active) { cancelAnimationFrame(frame);frame = 0;last = 0;clear(); }
    else if (!frame) { last = 0;frame = requestAnimationFrame(tick); }
  }
  addEventListener('resize', resize);
  addEventListener('blossomsettingschange', sync);
  document.addEventListener('visibilitychange', sync);
  new MutationObserver(sync).observe(menu, {attributes: true, attributeFilter: ['hidden']});
  resize();sync();
})();
