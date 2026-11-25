import java.util.*;

/** Turns the parsed code tree into notes. Channel 0 = melody, 1 = accompaniment, 2 = piano intro. */
public final class Composer {
    public static final int PPQ = 480, MAX_NOTES = 4000;
    public static final String[] SCALE_NAMES = {"Major", "Natural minor", "Pentatonic", "Dorian"};
    static final int[][] SCALES = {{0,2,4,5,7,9,11}, {0,2,3,5,7,8,10}, {0,2,4,7,9}, {0,2,3,5,7,9,10}};
    static final int[][] FOR_PAT = {{480,480}, {240,240,240,240}, {120,120,240,120,120,240}};            // evenly rising notes
    static final int[][] WHILE_PAT = {{720,240}, {360,120,240,240}, {180,60,180,60,120,120,240}};        // syncopated pulse
    static final int[] PEDAL = {0, 4, 2, 6};   // chord tone added per nesting level: root, 5th, 3rd, 7th

    public static final class Note {
        public final int ch, pitch, start, dur, vel;
        Note(int ch, int pitch, int start, int dur, int vel) { this.ch = ch; this.pitch = pitch; this.start = start; this.dur = dur; this.vel = vel; }
    }
    public static final class Settings {
        public int scale = 0, transpose = 0, tempo = 100, volume = 80;
        public Instruments.Inst melody = Instruments.ALL[0], accomp = Instruments.ALL[Instruments.ALL.length - 1];
        public Instruments.Inst[] channels() { return new Instruments.Inst[]{melody, accomp, Instruments.ALL[0]}; }
    }
    public static final class Result {
        public final List<Note> notes = new ArrayList<>();
        public int maxDepth, ticks; public boolean truncated;
    }

    private final Settings st; private final Result r = new Result();
    private int[] scale; private int key, depth, cursor, classDepth;

    private Composer(Settings st) { this.st = st; this.scale = SCALES[st.scale]; }

    public static Result compose(CodeParser.Node root, Settings st) {
        Composer c = new Composer(st); c.render(root);
        for (Note n : c.r.notes) c.r.ticks = Math.max(c.r.ticks, n.start + n.dur);
        return c.r;
    }

    private int pitch(int deg) {
        int len = scale.length;
        return 60 + st.transpose + key + scale[Math.floorMod(deg, len)] + 12 * Math.floorDiv(deg, len);
    }
    private void add(int ch, int p, int start, int dur, int vel) {
        if (r.notes.size() >= MAX_NOTES) { r.truncated = true; return; }
        r.notes.add(new Note(ch, Math.max(0, Math.min(127, p)), start, Math.max(1, dur), Math.min(127, vel)));
    }
    private void mel(int deg, int dur) { add(0, pitch(deg), cursor, dur, 80 + 8 * Math.min(depth, 3)); cursor += dur; }
    private int unit() { return depth == 0 ? PPQ : depth == 1 ? PPQ / 2 : PPQ / 4; }   // deeper nesting = faster rhythm
    private void kids(CodeParser.Node n) { for (CodeParser.Node c : n.kids) render(c); }
    private void pedal(int from, int to, int d) { if (to > from) add(1, pitch(PEDAL[Math.min(d, 3)] - scale.length), from, to - from, 55 + 5 * Math.min(d, 4)); }

    private void render(CodeParser.Node n) {
        if (r.truncated) return;
        switch (n.type) {
            case CLASS: intro(); classDepth++; kids(n); classDepth--; break;
            case METHOD: mel(Math.floorMod(n.name.hashCode(), scale.length) + scale.length, PPQ / 2); kids(n); cursor += PPQ / 2; break;
            case VAR: variable(n); break;
            case IF: branch(n, 5, false); break;     // key change up a fourth
            case ELSE: branch(n, -3, true); break;   // key change down + minor colour
            case FOR: case WHILE: loop(n); break;
            case RETURN: { int p = 60 + st.transpose; add(0, p, cursor, 2 * PPQ, 100); add(1, p - 12, cursor, 2 * PPQ, 70); cursor += 2 * PPQ; break; }
            default: kids(n);
        }
    }

    private void intro() {
        int[] prog = {0, 4, 5, 3}; int chords = classDepth == 0 ? 4 : 2, len = scale.length;
        for (int c = 0; c < chords; c++) {
            int root = prog[c] - len; int[] arp = {root, root + 2, root + 4, root + 2 + len};
            for (int k = 0; k < 4; k++) { add(2, pitch(arp[k]), cursor, PPQ / 2, 75); cursor += PPQ / 2; }
        }
    }

    private void variable(CodeParser.Node n) {
        int len = scale.length; boolean num = n.arg.matches("int|long|short|byte|char|float|double");
        int deg = Math.floorMod(n.name.hashCode(), len) + (num ? 0 : len);   // same name = same note
        int u = unit();
        if (depth >= 2) { add(0, pitch(deg + 1), cursor, u / 4, 70); cursor += u / 4; u -= u / 4; }   // grace-note ornament when deeply nested
        mel(deg, u);
    }

    private void loop(CodeParser.Node n) {
        int d = depth, reps = n.count; boolean isFor = n.type == CodeParser.Type.FOR;
        depth++; r.maxDepth = Math.max(r.maxDepth, depth);
        for (int i = 0; i < reps && !r.truncated; i++) {
            int s = cursor; int[] pat = (isFor ? FOR_PAT : WHILE_PAT)[Math.min(d, 2)];
            for (int k = 0; k < pat.length; k++) { add(0, pitch(isFor ? k : 4), cursor, pat[k], 85); cursor += pat[k]; }
            kids(n); pedal(s, cursor, d);
        }
        depth--;
    }

    private void branch(CodeParser.Node n, int shift, boolean minor) {
        int d = depth, oldKey = key; int[] oldScale = scale;
        key = ((key + shift) % 12 + 12) % 12; if (key > 6) key -= 12;
        if (minor) scale = st.scale == 1 ? SCALES[0] : SCALES[1];
        depth++; r.maxDepth = Math.max(r.maxDepth, depth);
        int s = cursor; mel(0, unit()); mel(2, unit());   // pivot notes announce the new key
        kids(n); pedal(s, cursor, d);
        depth--; key = oldKey; scale = oldScale;
    }
}
