/** Instrument list: General MIDI program number (for MIDI export) + a simple additive-synth timbre (for playback/WAV). */
public final class Instruments {
    public static final class Inst {
        public final String name; public final int program; public final boolean pluck;
        public final double a, d, s, r; public final double[] h;   // envelope (pluck: d = decay time constant) + harmonic amplitudes
        Inst(String name, int program, boolean pluck, double a, double d, double s, double r, double... h) {
            this.name = name; this.program = program; this.pluck = pluck; this.a = a; this.d = d; this.s = s; this.r = r; this.h = h;
        }
        @Override public String toString() { return name; }
    }

    public static final Inst[] ALL = {
        new Inst("Piano", 0, true, .004, .7, .2, .3, 1, .5, .28, .12, .06),
        new Inst("Electric Piano", 4, true, .004, .9, .3, .3, 1, .1, .35, .04),
        new Inst("Harpsichord", 6, true, .002, .35, .1, .1, 1, .8, .6, .5, .3, .2),
        new Inst("Music Box", 10, true, .002, .5, .1, .4, 1, 0, 0, .35, 0, 0, .12),
        new Inst("Vibraphone", 11, true, .003, 1.2, .2, .5, 1, 0, 0, .15, 0, 0, 0, .05),
        new Inst("Marimba", 12, true, .002, .3, .1, .2, 1, 0, 0, 0, .3),
        new Inst("Church Organ", 19, false, .03, .05, .9, .12, 1, .8, .6, .5, .4),
        new Inst("Nylon Guitar", 24, true, .003, .5, .1, .3, 1, .6, .35, .2, .1),
        new Inst("Violin", 40, false, .08, .1, .8, .2, 1, .7, .5, .4, .3, .2),
        new Inst("Cello", 42, false, .07, .1, .8, .25, 1, .8, .55, .35, .2),
        new Inst("Harp", 46, true, .003, .8, .1, .4, 1, .4, .2, .1),
        new Inst("Trumpet", 56, false, .04, .08, .85, .1, 1, .9, .7, .5, .35, .2),
        new Inst("Flute", 73, false, .06, .05, .9, .12, 1, .15, .05),
        new Inst("Synth Lead", 80, false, .01, .05, .9, .1, 1, .5, .33, .25, .2),
        new Inst("Synth Pad", 88, false, .25, .2, .8, .5, 1, .4, .2, .1),
        new Inst("Acoustic Bass", 32, true, .01, .35, .5, .15, 1, .5, .2),
    };
}
