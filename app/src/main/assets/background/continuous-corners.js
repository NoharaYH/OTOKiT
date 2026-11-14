/* Only opted-in background containers are clipped; decorative circles/bars are untouched.
 * Matches ContinuousCorners.kt. Rebuild on layout changes, never on animation frames.
 */
(() => {
  const script = document.currentScript;
  const selector = script.dataset.continuousTarget;
  if (!selector || !CSS.supports('clip-path', 'path("M0 0L1 0L1 1Z")')) return;
  const update = element => {
    const width = element.offsetWidth, height = element.offsetHeight;
    const limit = Math.min(width, height) / 2;
    const radius = Math.min(limit, Math.max(0,
      parseFloat(getComputedStyle(element).getPropertyValue('--continuous-radius')) || 0));
    if (limit <= 0) return;
    const s = radius > 0 ? Math.min(1, Math.max(0, (limit / radius - 1) * 2)) : 0;
    const e = radius * (1 + 0.5 * s);
    const a = 0.7347835 + (0.76 - 0.7347835) * s;
    const b = 0.1053568 * (1 - s);
    const c = 0.4804296 + (0.38 - 0.4804296) * s;
    const d = 0.2928932 + (0.19 - 0.2928932) * s;
    const corner = (x, y, index) => {
      const point = (u, v) => [x + e * [-v, -u, v, u][index],
        y + e * [u, -v, -u, v][index]].join(' ');
      return `C${point(0, a)} ${point(b, c)} ${point(d, d)} C${point(c, b)} ${point(a, 0)} ${point(1, 0)}`;
    };
    const path = `M${e} 0 L${width - e} 0 ${corner(width, 0, 0)} ` +
      `L${width} ${height - e} ${corner(width, height, 1)} ` +
      `L${e} ${height} ${corner(0, height, 2)} L0 ${e} ${corner(0, 0, 3)} Z`;
    element.style.clipPath = `path("${path}")`;
    element.style.borderRadius = '0';
  };
  const elements = [...document.querySelectorAll(selector)];
  const observer = new ResizeObserver(entries => entries.forEach(entry => update(entry.target)));
  elements.forEach(element => { update(element); observer.observe(element); });
  // A media query can change the radius even when the element's dimensions stay constant.
  window.addEventListener('resize', () => elements.forEach(update), { passive: true });
})();
