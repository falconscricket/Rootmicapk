// ALPHA Mic Extension V32.3 Background Worker (Replaced by DataStore SettingsManager & FloatingPopupService on Android)
chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg.type === 'ALPHA_APPLY_PRESET') {
    chrome.storage.local.set({ activePreset: msg.preset });
    sendResponse({ ok: true });
  }
});
