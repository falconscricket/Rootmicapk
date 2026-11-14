package com.example.alpha.shizuku;

interface IAlphaShizukuService {
    void destroy();
    String runAudioDiagnostics();
    String inspectPackageState(String packageName);
    String getDeviceAudioSummary();
}
