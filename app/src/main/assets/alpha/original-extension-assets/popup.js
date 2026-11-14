// ALPHA Mic Extension V32.3 Popup Controller (Ported natively to Kotlin/Compose in Alpha Mic Injector)
async function initAlphaPopup() {
  const res = await fetch(chrome.runtime.getURL('effects-config.json'));
  const config = await res.json();
  const grid = document.getElementById('presetGrid');
  config.presets.forEach((preset) => {
    const btn = document.createElement('button');
    btn.textContent = preset.name;
    btn.onclick = () => chrome.runtime.sendMessage({ type: 'ALPHA_APPLY_PRESET', preset });
    grid.appendChild(btn);
  });
}
document.addEventListener('DOMContentLoaded', initAlphaPopup);
