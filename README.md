# Decode

Android signal-forensics prototype.

## V0.1
- Import an audio file using Android's document picker.
- Decode supported Android audio formats to mono PCM.
- Run a Hann-windowed FFT.
- Report prominent spectral components.
- Automatically label probable 50/60 Hz mains harmonics.
- Keep unexplained peaks explicitly classified as structured/unidentified rather than claiming a decoded message.

## Roadmap
Interactive waveform and waterfall spectrogram; persistent-carrier detection; band isolation/playback; envelope extraction; autocorrelation/repetition analysis; AM/FM/FSK/ASK/OOK candidate detection; symbol-rate estimation; evidence/confidence scoring; IQ/SDR input.

Open the project in Android Studio and build the `app` module.
