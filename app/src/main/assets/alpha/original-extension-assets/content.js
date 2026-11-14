// ALPHA Mic Extension V32.3 Content Bridge (Replaced by UsageStats ForegroundAppMonitor + FloatingPopupService on Android)
window.addEventListener('message', (event) => {
  if (event.data && event.data.source === 'ALPHA_V32_POPUP') {
    window.postMessage({ source: 'ALPHA_V32_ENGINE', state: event.data.payload }, '*');
  }
});
