// ALPHA Mic Extension V32.3 WebAudio DSP Reference (Ported natively to 48kHz Kotlin AudioEffectProcessor)
// Signal chain: Input -> NoiseGate + HPF -> 3-Band Biquad EQ -> PitchShifter -> Robot RingMod -> Monster Growl -> Distortion -> Echo -> Limiter -> Output
window.__ALPHA_V32_DSP_SPEC__ = {
  sampleRate: 48000,
  chain: [
    "noise_gate_rms_hpf",
    "biquad_eq_3band",
    "dual_tap_pitch_shifter",
    "ring_mod_comb_robot",
    "sub_octave_monster",
    "tanh_overdrive",
    "circular_delay_echo",
    "soft_clip_limiter"
  ]
};
