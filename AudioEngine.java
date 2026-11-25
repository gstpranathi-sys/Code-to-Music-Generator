import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.io.*;

/** Built-in additive synth (used for playback AND WAV export, so they match) + standard MIDI file writer. */
public final class AudioEngine {
    public static final int SR = 44100;
    public static final AudioFormat FMT = new AudioFormat(SR, 16, 1, true, false);
    private static final float[] SIN = new float[4096];
    static { for (int i = 0; i < 4096; i++) SIN[i] = (float) Math.sin(2 * Math.PI * i / 4096); }

    public static byte[] render(Composer.Result res, Composer.Settings st) {
        Instruments.Inst[] chi = st.channels();
        double spt = 60.0 / (st.tempo * Composer.PPQ);
        int total = (int) ((res.ticks * spt + 2) * SR); float[] buf = new float[total];
        for (Composer.Note n : res.notes) {
            Instruments.Inst in = chi[n.ch];
            double f = 440 * Math.pow(2, (n.pitch - 69) / 12.0), inc = f * 4096 / SR, amp = n.vel / 127.0 * (n.ch == 0 ? 1 : 0.6);
            int s0 = (int) (n.start * spt * SR), len = (int) (n.dur * spt * SR), rel = Math.max(1, (int) (in.r * SR));
            for (int i = 0; i < len + rel && s0 + i < total; i++) {
                double t = (double) i / SR, env;
                if (in.pluck) env = Math.min(1, t / 0.004) * Math.exp(-t / in.d);
                else if (t < in.a) env = t / in.a;
                else if (t < in.a + in.d) env = 1 - (1 - in.s) * (t - in.a) / in.d;
                else env = in.s;
                if (i >= len) env *= Math.max(0, 1 - (double) (i - len) / rel);
                double ph = i * inc, v = 0;
                for (int k = 1; k <= in.h.length && f * k < SR / 2.2; k++) v += in.h[k - 1] * SIN[(int) ((long) (ph * k) & 4095)];
                buf[s0 + i] += (float) (v * env * amp);
            }
        }
        float peak = 0; for (float v : buf) peak = Math.max(peak, Math.abs(v));
        float g = peak > 0 ? 0.9f / peak * (float) gain(st.volume) : 0;
        byte[] out = new byte[total * 2];
        for (int i = 0; i < total; i++) { int s = (int) (buf[i] * g * 32767); out[2 * i] = (byte) s; out[2 * i + 1] = (byte) (s >> 8); }
        return out;
    }

    /** Slider position (0-100) to loudness. Squared, so the slider changes the sound clearly all the way along. */
    public static double gain(int volume) {
        double x = Math.max(0, Math.min(100, volume)) / 100.0;
        return x * x;
    }

    /** Returns a copy of 16-bit PCM audio made quieter or louder by the given factor (used when the sound card has no volume control). */
    public static byte[] scale(byte[] pcm, double factor) {
        byte[] out = new byte[pcm.length];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = (int) Math.max(-32768, Math.min(32767, Math.round(((short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8))) * factor)));
            out[i] = (byte) s; out[i + 1] = (byte) (s >> 8);
        }
        return out;
    }

    public static void writeWav(File f, byte[] pcm) throws IOException {
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), FMT, pcm.length / 2), AudioFileFormat.Type.WAVE, f);
    }

    public static void writeMidi(File f, Composer.Result res, Composer.Settings st) throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, Composer.PPQ);
        Track[] tr = {seq.createTrack(), seq.createTrack(), seq.createTrack()};
        Instruments.Inst[] chi = st.channels();
        int mpq = 60_000_000 / st.tempo;
        tr[0].add(new MidiEvent(new MetaMessage(0x51, new byte[]{(byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq}, 3), 0));
        for (int ch = 0; ch < 3; ch++) {
            tr[ch].add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, ch, chi[ch].program, 0), 0));
            int cc = (int) Math.min(127, st.volume * 1.27 * (ch == 0 ? 1 : ch == 1 ? 0.7 : 0.85));
            tr[ch].add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 7, cc), 0));
        }
        for (Composer.Note n : res.notes) {
            tr[n.ch].add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, n.ch, n.pitch, n.vel), n.start));
            tr[n.ch].add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, n.ch, n.pitch, 0), n.start + Math.max(1, n.dur - 1)));
        }
        MidiSystem.write(seq, 1, f);
    }
}
