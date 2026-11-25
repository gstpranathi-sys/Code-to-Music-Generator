import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.function.Consumer;

public class CodeToMusicApp extends JFrame {
    private final JTextArea src = new JTextArea(Samples.get(LanguageSupport.Kind.JAVA, 1), 24, 48);
    private final JComboBox<LanguageSupport.Lang> langBox = new JComboBox<>(LanguageSupport.ALL);
    private final JComboBox<String> samples = new JComboBox<>(new String[]{"Load a sample...", Samples.LABELS[0], Samples.LABELS[1], Samples.LABELS[2]});
    private final JComboBox<Instruments.Inst> melodyBox = new JComboBox<>(Instruments.ALL), accompBox = new JComboBox<>(Instruments.ALL);
    private final JComboBox<String> scaleBox = new JComboBox<>(Composer.SCALE_NAMES);
    private final JSlider tempo = new JSlider(40, 220, 100), pitch = new JSlider(-12, 12, 0), vol = new JSlider(0, 100, 80);
    private final JCheckBox cue = new JCheckBox("Play a sound after compiling", true);
    private final JLabel banner = new JLabel();
    private final javax.swing.Timer bannerTimer = new javax.swing.Timer(10000, e -> hideBanner());
    private static final String ERROR_NOTE = "Your code contains errors. Music will be generated based on the available source code, but the output may be incomplete.";
    private final JTextArea console = new JTextArea(7, 60);
    private final PianoRoll roll = new PianoRoll();
    private final JLabel stats = new JLabel(" "), status = new JLabel("Ready");
    private final javax.swing.Timer timer = new javax.swing.Timer(40, e -> tick());
    private final DefaultHighlighter.DefaultHighlightPainter errorPaint = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 190, 190));
    private final DefaultHighlighter.DefaultHighlightPainter warnPaint = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 236, 160));
    private JButton compileBtn, runBtn;
    private LanguageSupport.Lang lang = LanguageSupport.ALL[0];
    private Composer.Result result; private Clip clip; private double playSecs; private boolean softwareVolume;

    public CodeToMusicApp() {
        super("Code to Music Converter");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        src.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13)); src.setTabSize(4);
        accompBox.setSelectedIndex(Instruments.ALL.length - 1);
        samples.addActionListener(e -> { int i = samples.getSelectedIndex(); if (i > 0) setCode(Samples.get(lang.kind, i - 1)); });
        langBox.addActionListener(e -> switchLanguage());
        vol.addChangeListener(e -> applyVolume());
        src.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { clearMarks(); }
            public void removeUpdate(DocumentEvent e) { clearMarks(); }
            public void changedUpdate(DocumentEvent e) { }
        });

        console.setEditable(false); console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        console.setBackground(new Color(30, 32, 38)); console.setForeground(new Color(220, 225, 235));
        console.setText("Pick a language, then press Compile to look for errors, or Compile & Run to run your program.\nLines with problems are highlighted in the editor.");

        JPanel c = new JPanel(new GridLayout(0, 1, 0, 4));
        c.add(btn("Open code file...", this::open)); c.add(samples);
        c.add(labeled("Language", langBox));
        c.add(new JLabel("Melody instrument")); c.add(melodyBox);
        c.add(new JLabel("Accompaniment instrument")); c.add(accompBox);
        c.add(new JLabel("Scale")); c.add(scaleBox);
        c.add(slider("Tempo", tempo, " bpm")); c.add(slider("Pitch", pitch, " st")); c.add(slider("Volume", vol, "%"));
        c.add(pair(btn("Play", this::play), btn("Stop", this::stopPlayback)));
        compileBtn = btn("Compile", () -> checkCode(false)); runBtn = btn("Compile & Run", () -> checkCode(true));
        c.add(pair(compileBtn, runBtn)); c.add(cue);
        c.add(pair(btn("Export MIDI", this::exportMidi), btn("Export WAV", this::exportWav)));
        JPanel east = new JPanel(new BorderLayout()); east.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8)); east.add(c, BorderLayout.NORTH);

        JScrollPane consoleScroll = new JScrollPane(console); consoleScroll.setBorder(BorderFactory.createTitledBorder("Compiler and program output"));
        JPanel info = new JPanel(new GridLayout(0, 1)); info.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8)); info.add(stats); info.add(status);
        JPanel south = new JPanel(new BorderLayout()); south.add(consoleScroll, BorderLayout.CENTER); south.add(info, BorderLayout.SOUTH);

        banner.setOpaque(true); banner.setBackground(new Color(255, 243, 205)); banner.setForeground(new Color(102, 70, 0));
        banner.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8)); banner.setVisible(false); bannerTimer.setRepeats(false);
        JPanel left = new JPanel(new BorderLayout()); left.add(banner, BorderLayout.NORTH); left.add(new JScrollPane(src));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, roll); split.setResizeWeight(0.5);
        add(split); add(east, BorderLayout.EAST); add(south, BorderLayout.SOUTH);
        pack(); setLocationRelativeTo(null);
    }

    private JButton btn(String t, Runnable r) { JButton b = new JButton(t); b.addActionListener(e -> r.run()); return b; }
    private JPanel pair(JComponent a, JComponent b) { JPanel p = new JPanel(new GridLayout(1, 2, 4, 0)); p.add(a); p.add(b); return p; }
    private JPanel labeled(String label, JComponent comp) { JPanel p = new JPanel(new BorderLayout(6, 0)); p.add(new JLabel(label), BorderLayout.WEST); p.add(comp); return p; }
    private JPanel slider(String label, JSlider s, String unit) {
        JLabel v = new JLabel(s.getValue() + unit); s.addChangeListener(e -> v.setText(s.getValue() + unit));
        JPanel p = new JPanel(new BorderLayout(6, 0)); p.add(new JLabel(label), BorderLayout.WEST); p.add(s); p.add(v, BorderLayout.EAST); return p;
    }

    // ---------- language and code ----------
    private void setCode(String text) { src.setText(text); src.setCaretPosition(0); }

    private static int sampleIndexOf(String text, LanguageSupport.Kind kind) {
        for (int i = 0; i < Samples.LABELS.length; i++) if (Samples.get(kind, i).equals(text)) return i;
        return -1;
    }

    /** Changing language swaps a built-in sample for the same sample in the new language; your own code is never touched. */
    private void switchLanguage() {
        LanguageSupport.Lang next = (LanguageSupport.Lang) langBox.getSelectedItem();
        if (next == null || next == lang) return;
        int idx = sampleIndexOf(src.getText(), lang.kind);
        lang = next;
        if (idx >= 0) setCode(Samples.get(next.kind, idx));
        clearMarks(); status.setText("Language: " + next.name);
    }

    private void open() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            try {
                File f = fc.getSelectedFile();
                LanguageSupport.Lang l = LanguageSupport.forFile(f.getName());
                if (l == null) {   // unsupported language: say so for both the compiler and the music, and do not load the file
                    String msg = "\"" + f.getName() + "\" is not a supported file type.\nSupported languages: " + LanguageSupport.supportedList() + ".";
                    console.setText(msg + "\n"); status.setText("Unsupported language - " + f.getName() + " was not opened");
                    JOptionPane.showMessageDialog(this, msg, "Unsupported language", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                String text = Files.readString(f.toPath());
                langBox.setSelectedItem(l);
                setCode(text);
            } catch (Exception ex) { status.setText("Could not read file: " + ex.getMessage()); }
    }

    // ---------- compile and run ----------
    private void checkCode(boolean execute) {
        String code = src.getText(); LanguageSupport.Lang l = lang;
        if (code.trim().isEmpty()) {   // empty code: just a message - no compiling, no sound
            console.setText("Nothing to compile - the editor is empty. Write or open some " + l.name + " code first.\n");
            status.setText("Nothing to compile - write or open some code first."); return;
        }
        setBusy(true); clearMarks();
        console.setText("Working...\n"); status.setText((execute ? "Compiling and running " : "Compiling ") + l.name + "...");
        new SwingWorker<CodeRunner.Report, Void>() {
            protected CodeRunner.Report doInBackground() { return CodeRunner.run(l.kind, code, execute); }
            protected void done() {
                setBusy(false);
                try { showReport(get()); } catch (Exception ex) { console.setText("Error: " + ex + "\n"); status.setText("Could not compile"); }
            }
        }.execute();
    }

    private void setBusy(boolean busy) { compileBtn.setEnabled(!busy); runBtn.setEnabled(!busy); }

    private void showReport(CodeRunner.Report rep) {
        console.setText(rep.format()); console.setCaretPosition(0);
        int first = -1;
        for (CodeRunner.Issue i : rep.issues) if (i.line > 0) { int s = mark(i.line, i.error); if (first < 0 && i.error) first = s; }
        if (first >= 0) src.setCaretPosition(first);
        if (rep.toolMissing) { status.setText(rep.message); return; }
        if (cue.isSelected()) playCue(rep.success());
        int e = rep.count(true);
        status.setText(!rep.compiled ? (e > 0 ? "Found " + e + (e == 1 ? " error" : " errors") + " - see the red line(s) in the editor" : "Compilation failed - see the output below")
            : !rep.success() ? "Compiled, but the program stopped with an error" : rep.ran ? "Compiled and ran without errors" : "Compiled - no errors found");
    }

    private int mark(int line, boolean error) {
        try {
            int s = src.getLineStartOffset(line - 1), e = src.getLineEndOffset(line - 1);
            src.getHighlighter().addHighlight(s, e, error ? errorPaint : warnPaint);
            return s;
        } catch (BadLocationException ex) { return -1; }
    }

    private void clearMarks() { src.getHighlighter().removeAllHighlights(); hideBanner(); }

    private void showBanner(String text) {
        banner.setText("<html><body style='width: 330px'>" + text + "</body></html>");
        banner.setVisible(true); bannerTimer.restart();
    }
    private void hideBanner() { bannerTimer.stop(); banner.setVisible(false); }

    /**
     * Pressing Play never blocks on errors: the music is made from whatever the parser can read. In the background we
     * also check the code, and if it has errors we mark the lines and show a small notice that the music may be incomplete.
     */
    private void warnIfErrors() {
        String code = src.getText(); LanguageSupport.Lang l = lang;
        new SwingWorker<CodeRunner.Report, Void>() {
            protected CodeRunner.Report doInBackground() { return CodeRunner.run(l.kind, code, false); }
            protected void done() {
                try {
                    CodeRunner.Report rep = get();
                    if (rep.toolMissing || !code.equals(src.getText())) return;   // cannot check, or the code was edited meanwhile
                    clearMarks();
                    if (rep.compiled) return;
                    console.setText(rep.format()); console.setCaretPosition(0);
                    for (CodeRunner.Issue i : rep.issues) if (i.line > 0) mark(i.line, i.error);
                    showBanner(ERROR_NOTE);
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    /** A short happy tune when the code is fine, a low sour one when it is not. */
    private void playCue(boolean ok) {
        try {
            Composer.Result res = new Composer.Result(); int t = 0, dur = ok ? 240 : 480;
            for (int p : ok ? new int[]{72, 76, 79, 84} : new int[]{58, 54, 48}) { res.notes.add(new Composer.Note(0, p, t, dur, ok ? 90 : 100)); t += dur; }
            res.ticks = t + 240;
            Composer.Settings s = settings(); s.tempo = 160; s.volume = 100;
            stopPlayback(); startClip(AudioEngine.render(res, s), false);
        } catch (Exception ignored) { }
    }

    // ---------- music ----------
    private Composer.Settings settings() {
        Composer.Settings s = new Composer.Settings();
        s.melody = (Instruments.Inst) melodyBox.getSelectedItem(); s.accomp = (Instruments.Inst) accompBox.getSelectedItem();
        s.scale = scaleBox.getSelectedIndex(); s.tempo = tempo.getValue(); s.transpose = pitch.getValue(); s.volume = vol.getValue();
        return s;
    }

    private boolean compose(Composer.Settings s) {
        if (src.getText().trim().isEmpty()) {   // empty code: no music, just a message
            result = null; roll.set(null); stats.setText(" ");
            status.setText("Nothing to play - the editor is empty. Write or open some " + lang.name + " code first."); return false;
        }
        CodeParser.Node root = CodeParser.parse(src.getText(), lang.kind); int[] c = CodeParser.tally(root);
        result = Composer.compose(root, s); roll.set(result);
        if (result.notes.isEmpty()) { stats.setText(" "); status.setText("No musical elements found - is this really " + lang.name + " code?"); return false; }
        String[] lvl = {"Simple", "Moderate", "Rich", "Intricate"};
        stats.setText(String.format("Classes %d | Methods %d | Variables %d | if/else %d | Loops %d | Returns %d  ->  nesting %d (%s), %d notes, %.1f s%s",
            c[CodeParser.Type.CLASS.ordinal()], c[CodeParser.Type.METHOD.ordinal()], c[CodeParser.Type.VAR.ordinal()],
            c[CodeParser.Type.IF.ordinal()] + c[CodeParser.Type.ELSE.ordinal()], c[CodeParser.Type.FOR.ordinal()] + c[CodeParser.Type.WHILE.ordinal()],
            c[CodeParser.Type.RETURN.ordinal()], result.maxDepth, lvl[Math.min(result.maxDepth, 3)], result.notes.size(),
            result.ticks * 60.0 / (s.tempo * Composer.PPQ), result.truncated ? " (truncated)" : ""));
        return true;
    }

    private void renderAsync(Composer.Settings s, Consumer<byte[]> cb) {
        Composer.Result res = result; status.setText("Rendering audio...");
        new SwingWorker<byte[], Void>() {
            protected byte[] doInBackground() { return AudioEngine.render(res, s); }
            protected void done() { try { cb.accept(get()); } catch (Exception ex) { status.setText("Error: " + ex); } }
        }.execute();
    }

    private void play() {
        stopPlayback(); Composer.Settings s = settings(); if (!compose(s)) return;
        playSecs = result.ticks * 60.0 / (s.tempo * Composer.PPQ);
        s.volume = 100;   // the audio is made at full level; the Volume slider is applied live while it plays
        warnIfErrors();
        renderAsync(s, pcm -> startClip(pcm, true));
    }

    /** Opens the audio in a Clip and starts it. The Volume slider controls the clip's gain, so it also works while playing. */
    private void startClip(byte[] pcm, boolean music) {
        try {
            Clip c = openClip(pcm);
            c.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) SwingUtilities.invokeLater(() -> {
                if (clip != c) return;
                if (music) { timer.stop(); roll.setCursor(0); status.setText("Finished"); } else { clip = null; c.close(); } }); });
            clip = c; applyVolume(); c.start();
            if (music) { timer.start(); status.setText("Playing..."); }
        } catch (Exception ex) { if (music) status.setText("Audio error: " + ex.getMessage()); }
    }

    private Clip openClip(byte[] pcm) throws Exception {
        Clip c = AudioSystem.getClip();
        c.open(new AudioInputStream(new ByteArrayInputStream(pcm), AudioEngine.FMT, pcm.length / 2));
        softwareVolume = !c.isControlSupported(FloatControl.Type.MASTER_GAIN);
        if (softwareVolume) {   // no live volume control on this sound system: bake the volume in instead
            c.close(); c = AudioSystem.getClip();
            byte[] quiet = AudioEngine.scale(pcm, AudioEngine.gain(vol.getValue()));
            c.open(new AudioInputStream(new ByteArrayInputStream(quiet), AudioEngine.FMT, quiet.length / 2));
        }
        return c;
    }

    private void applyVolume() {
        Clip c = clip; if (c == null) return;
        if (softwareVolume) { if (c.isRunning()) status.setText("Your sound system has no live volume control - the new volume applies on the next Play"); return; }
        try {
            FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
            double lin = AudioEngine.gain(vol.getValue());
            g.setValue(lin < 0.0001 ? g.getMinimum() : (float) Math.max(g.getMinimum(), Math.min(g.getMaximum(), 20 * Math.log10(lin))));
        } catch (Exception ignored) { }
    }

    private void tick() {
        if (clip != null && clip.isRunning() && playSecs > 0) roll.setCursor(Math.min(1, clip.getMicrosecondPosition() / 1e6 / playSecs));
    }

    private void stopPlayback() {
        timer.stop(); if (clip != null) { Clip c = clip; clip = null; c.stop(); c.close(); }
        roll.setCursor(0); status.setText("Stopped");
    }

    private void exportMidi() {
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File("composition.mid"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Composer.Settings s = settings(); if (!compose(s)) return;
        try { AudioEngine.writeMidi(fc.getSelectedFile(), result, s); status.setText("Saved " + fc.getSelectedFile().getName()); }
        catch (Exception ex) { status.setText("MIDI export failed: " + ex.getMessage()); }
    }

    private void exportWav() {
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File("composition.wav"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Composer.Settings s = settings(); if (!compose(s)) return;
        File f = fc.getSelectedFile();
        renderAsync(s, pcm -> {
            try { AudioEngine.writeWav(f, pcm); status.setText("Saved " + f.getName()); }
            catch (Exception ex) { status.setText("WAV export failed: " + ex.getMessage()); }
        });
    }

    static class PianoRoll extends JPanel {
        private Composer.Result r; private double cur;
        PianoRoll() { setBackground(new Color(24, 26, 32)); setPreferredSize(new Dimension(420, 300)); }
        void set(Composer.Result r) { this.r = r; cur = 0; repaint(); }
        void setCursor(double c) { cur = c; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (r == null || r.notes.isEmpty()) return;
            int lo = 127, hi = 0; for (Composer.Note n : r.notes) { lo = Math.min(lo, n.pitch); hi = Math.max(hi, n.pitch); }
            double rows = hi - lo + 1, w = getWidth(), h = getHeight();
            Color[] col = {new Color(90, 170, 255), new Color(110, 210, 130), new Color(250, 170, 80)};
            for (Composer.Note n : r.notes) {
                g.setColor(col[n.ch]);
                g.fillRect((int) (n.start / (double) r.ticks * w), (int) ((hi - n.pitch) / rows * h),
                    Math.max(2, (int) (n.dur / (double) r.ticks * w)), Math.max(2, (int) (h / rows) - 1));
            }
            g.setColor(Color.WHITE); int cx = (int) (cur * w); g.drawLine(cx, 0, cx, (int) h);
        }
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new CodeToMusicApp().setVisible(true)); }
}
