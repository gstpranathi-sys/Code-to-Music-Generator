import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import java.util.stream.Stream;

/**
 * Compiles (or syntax-checks) and runs code in the chosen language, using the compiler or interpreter installed
 * on this computer. Java is compiled inside this program with javax.tools; the other languages call their tools.
 */
public final class CodeRunner {
    /** One problem found in the code: a line number (0 if unknown), a message, and a plain-English hint. */
    public static final class Issue {
        public final int line; public final boolean error; public final String message, hint;
        public boolean runtime;   // true when the program compiled but then crashed while running
        Issue(int line, boolean error, String message, String hint) { this.line = line; this.error = error; this.message = message; this.hint = hint; }
    }

    public static final class Report {
        public final List<Issue> issues = new ArrayList<>();
        public boolean toolMissing, compiled, ran, timedOut;
        public int exitCode;
        public String message = "", output = "";

        public int count(boolean errors) { int n = 0; for (Issue i : issues) if (i.error == errors) n++; return n; }
        public boolean success() { return compiled && (!ran || (exitCode == 0 && !timedOut)); }

        /** The text shown in the output console. */
        public String format() {
            if (toolMissing) return message + "\n";
            StringBuilder sb = new StringBuilder();
            for (Issue i : issues) if (!i.runtime) append(sb, i);
            int e = count(true), w = count(false);
            if (!compiled) {
                sb.append(issues.isEmpty() ? "" : "\n");
                if (e > 0) sb.append("Compilation failed with ").append(e).append(e == 1 ? " error" : " errors").append(". Fix the highlighted line(s) and try again.\n");
                else sb.append("Compilation failed.\n").append(message.isEmpty() ? "" : message + "\n");
            } else sb.append(issues.isEmpty() ? "" : "\n").append("Compiled successfully").append(w > 0 ? " with " + w + (w == 1 ? " warning" : " warnings") : "").append(".\n");
            if (ran) {
                sb.append("\n--- Program output ---\n").append(output.isEmpty() ? "(no output)\n" : output.endsWith("\n") ? output : output + "\n");
                sb.append(timedOut ? "Stopped: the program ran for more than " + RUN_SECONDS + " seconds.\n" : "Program finished with exit code " + exitCode + ".\n");
                for (Issue i : issues) if (i.runtime) { sb.append('\n'); append(sb, i); }
            }
            return sb.toString();
        }

        private static void append(StringBuilder sb, Issue i) {
            sb.append(i.error ? "ERROR" : "Warning");
            if (i.line > 0) sb.append(" on line ").append(i.line);
            String[] parts = i.message.split("\\R", 2);
            sb.append(": ").append(parts[0].trim()).append('\n');
            if (parts.length > 1) for (String extra : parts[1].split("\\R")) if (!extra.trim().isEmpty()) sb.append("    ").append(extra.trim()).append('\n');
            if (!i.hint.isEmpty()) sb.append("    Hint: ").append(i.hint).append('\n');
        }
    }

    private static final int MAX_OUT = 20_000, RUN_SECONDS = 10, COMPILE_SECONDS = 30;
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static File emptyInput;

    /** Checks the code, and also runs it when {@code execute} is true. Never throws; problems go into the report. */
    public static Report run(LanguageSupport.Kind kind, String src, boolean execute) {
        Report r = new Report(); Path dir = null;
        try {
            dir = Files.createTempDirectory("code2music");
            switch (kind) {
                case JAVA: java(src, dir, execute, r); break;
                case PYTHON: python(src, dir, execute, r); break;
                case C: nativeCode(src, dir, execute, r, false); break;
                case CPP: nativeCode(src, dir, execute, r, true); break;
                default: javascript(src, dir, execute, r);
            }
        } catch (Exception e) {
            r.message = "Could not check the code: " + e;
        } finally {
            if (dir != null) deleteTree(dir);
        }
        return r;
    }

    // ---------- Java ----------
    private static void java(String src, Path dir, boolean execute, Report r) throws Exception {
        String publicName = firstGroup(Pattern.compile("public\\s+(?:(?:final|abstract|strictfp)\\s+)*(?:class|interface|enum|record)\\s+(\\w+)"), src);
        String runName = mainClass(src, publicName);
        String fileName = publicName != null ? publicName : runName;
        String pkg = firstGroup(Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;"), src);
        Path file = dir.resolve(fileName + ".java");
        Files.write(file, src.getBytes(StandardCharsets.UTF_8));

        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) { r.toolMissing = true; r.message = "The Java compiler was not found. Start this app with a JDK (not just a JRE)."; return; }
        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();
        boolean ok;
        try (StandardJavaFileManager fm = jc.getStandardFileManager(dc, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            ok = jc.getTask(null, fm, dc, Arrays.asList("-d", dir.toString(), "-proc:none"), null, fm.getJavaFileObjects(file.toFile())).call();
        }
        for (Diagnostic<? extends JavaFileObject> d : dc.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.NOTE || d.getKind() == Diagnostic.Kind.OTHER) continue;
            String msg = d.getMessage(Locale.ENGLISH);
            r.issues.add(new Issue(d.getLineNumber() > 0 ? (int) d.getLineNumber() : 0, d.getKind() == Diagnostic.Kind.ERROR, msg, hint(msg)));
        }
        r.compiled = ok;
        if (!ok || !execute) return;
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        runProgram(r, Arrays.asList(java, "-cp", dir.toString(), pkg == null ? runName : pkg + "." + runName), dir);
        runtimeIssue(r, LanguageSupport.Kind.JAVA, fileName);
    }

    /** The class to start: the last class declared before "static void main", else the public class, else the first class. */
    private static String mainClass(String src, String publicName) {
        int mainAt = src.indexOf("static void main");
        String best = null, first = null;
        Matcher m = Pattern.compile("\\b(?:class|interface|enum|record)\\s+(\\w+)").matcher(src);
        while (m.find()) {
            if (first == null) first = m.group(1);
            if (mainAt >= 0 && m.start() < mainAt) best = m.group(1);
        }
        if (best != null) return best;
        if (publicName != null) return publicName;
        return first != null ? first : "Main";
    }

    // ---------- Python ----------
    private static void python(String src, Path dir, boolean execute, Report r) throws Exception {
        String py = findTool("python3", "python", "py");
        if (py == null) { r.toolMissing = true; r.message = "Python was not found. Install Python (python.org) and make sure it is on your PATH."; return; }
        Path file = dir.resolve("main.py");
        Files.write(file, src.getBytes(StandardCharsets.UTF_8));
        Exec c = exec(Arrays.asList(py, "-m", "py_compile", file.toString()), dir.toFile(), COMPILE_SECONDS);
        r.compiled = c.exit == 0 && !c.timedOut;
        if (!r.compiled) {
            String text = c.text.trim(), last = lastLine(text);
            String line = firstGroup(Pattern.compile("line (\\d+)"), text);
            if (!last.isEmpty()) r.issues.add(new Issue(line == null ? 0 : Integer.parseInt(line), true, last, hint(last)));
            else r.message = text;
            return;
        }
        if (!execute) return;
        runProgram(r, Arrays.asList(py, file.toString()), dir);
        runtimeIssue(r, LanguageSupport.Kind.PYTHON, "main");
    }

    // ---------- C and C++ ----------
    private static void nativeCode(String src, Path dir, boolean execute, Report r, boolean cpp) throws Exception {
        String cc = cpp ? findTool("g++", "clang++", "c++") : findTool("gcc", "clang", "cc");
        if (cc == null) {
            r.toolMissing = true;
            r.message = (cpp ? "A C++ compiler (g++ or clang++)" : "A C compiler (gcc or clang)") + " was not found. Install one (for example MinGW on Windows) and add it to your PATH.";
            return;
        }
        Path file = dir.resolve(cpp ? "main.cpp" : "main.c"), exe = dir.resolve(WINDOWS ? "program.exe" : "program");
        Files.write(file, src.getBytes(StandardCharsets.UTF_8));
        List<String> cmd = new ArrayList<>(Arrays.asList(cc, "-Wall"));
        if (cpp) cmd.add("-std=c++17");
        cmd.addAll(Arrays.asList("-o", exe.toString(), file.toString(), "-lm"));
        Exec c = exec(cmd, dir.toFile(), COMPILE_SECONDS);
        Matcher m = Pattern.compile("(?m)^(?:.*[\\\\/])?main\\.(?:c|cpp):(\\d+):(?:\\d+:)?\\s*(fatal error|error|warning):\\s*(.*)$").matcher(c.text);
        while (m.find()) {
            String msg = m.group(3).replace('‘', '\'').replace('’', '\'').trim();
            r.issues.add(new Issue(Integer.parseInt(m.group(1)), !m.group(2).equals("warning"), msg, hint(msg)));
        }
        r.compiled = c.exit == 0 && !c.timedOut && Files.exists(exe);
        if (!r.compiled && r.count(true) == 0) r.message = c.text.trim();
        if (!r.compiled || !execute) return;
        runProgram(r, Collections.singletonList(exe.toString()), dir);
    }

    // ---------- JavaScript (Node.js) ----------
    private static void javascript(String src, Path dir, boolean execute, Report r) throws Exception {
        String node = findTool("node");
        if (node == null) { r.toolMissing = true; r.message = "Node.js was not found. Install it from nodejs.org to check and run JavaScript."; return; }
        Path file = dir.resolve("main.js");
        Files.write(file, src.getBytes(StandardCharsets.UTF_8));
        Exec c = exec(Arrays.asList(node, "--check", file.toString()), dir.toFile(), COMPILE_SECONDS);
        r.compiled = c.exit == 0 && !c.timedOut;
        if (!r.compiled) {
            String line = firstGroup(Pattern.compile("main\\.js:(\\d+)"), c.text);
            String msg = firstGroup(Pattern.compile("(?m)^(\\w*Error: .*)$"), c.text);
            if (msg != null) r.issues.add(new Issue(line == null ? 0 : Integer.parseInt(line), true, msg.trim(), hint(msg)));
            else r.message = c.text.trim();
            return;
        }
        if (!execute) return;
        runProgram(r, Arrays.asList(node, file.toString()), dir);
        runtimeIssue(r, LanguageSupport.Kind.JAVASCRIPT, "main");
    }

    // ---------- shared helpers ----------
    private static void runProgram(Report r, List<String> cmd, Path dir) throws Exception {
        Exec e = exec(cmd, dir.toFile(), RUN_SECONDS);
        r.ran = true; r.exitCode = e.exit; r.timedOut = e.timedOut; r.output = e.text;
    }

    /** When a program stops with an error, find the line and message so the editor can point at it. */
    private static void runtimeIssue(Report r, LanguageSupport.Kind kind, String fileName) {
        if (r.exitCode == 0 || r.timedOut) return;
        String out = r.output, msg = null; int line = 0;
        if (kind == LanguageSupport.Kind.JAVA) {
            msg = firstGroup(Pattern.compile("(?m)^(?:Exception in thread \"[^\"]*\" )?(.*(?:Exception|Error).*)$"), out);
            String ln = firstGroup(Pattern.compile("\\(" + Pattern.quote(fileName) + "\\.java:(\\d+)\\)"), out);
            if (ln != null) line = Integer.parseInt(ln);
        } else if (kind == LanguageSupport.Kind.PYTHON) {
            if (out.contains("Traceback")) {
                msg = lastLine(out.trim());
                String ln = lastGroup(Pattern.compile("File \".*main\\.py\", line (\\d+)"), out);
                if (ln != null) line = Integer.parseInt(ln);
            }
        } else if (kind == LanguageSupport.Kind.JAVASCRIPT) {
            msg = firstGroup(Pattern.compile("(?m)^(\\w*Error: .*)$"), out);
            String ln = firstGroup(Pattern.compile("main\\.js:(\\d+)"), out);
            if (ln != null) line = Integer.parseInt(ln);
        }
        if (msg != null) { Issue i = new Issue(line, true, "Runtime error: " + msg.trim(), hint(msg)); i.runtime = true; r.issues.add(i); }
    }

    private static final String[][] HINTS = {
        {"';' expected", "A statement is missing its semicolon (;). Check the end of this line or the line just above."},
        {"expected ';'", "A semicolon (;) is missing, usually at the end of the line above."},
        {"expected ',' or ';'", "A semicolon (;) is missing, usually at the end of the line above."},
        {"')' expected", "A closing bracket ) is missing. Count the ( and ) on this line."},
        {"'(' expected", "An opening bracket ( is missing here."},
        {"missing ) after argument list", "A closing bracket ) is missing in a function call."},
        {"reached end of file while parsing", "A closing brace } is missing at the end of the file."},
        {"unexpected end of input", "A closing } or ) is missing."},
        {"class, interface, enum, or record expected", "There may be an extra } or code placed outside a class."},
        {"expected declaration or statement", "There may be an extra } here, or a missing one earlier."},
        {"cannot find symbol", "Java does not know this name. Check the spelling, or declare the variable or method first."},
        {"undeclared", "This name has not been declared. Check the spelling or declare it first."},
        {"was not declared", "This name has not been declared. Check the spelling or declare it first."},
        {"implicit declaration", "A function is used before it is declared. Add the right #include or declare it first."},
        {"is not defined", "This name is not known. Check the spelling, or give it a value first."},
        {"nameerror", "Python does not know this name. Check the spelling, or give it a value first."},
        {"incompatible types", "The value does not match the type, for example text put into an int."},
        {"missing return statement", "A method that returns a value must return something on every path."},
        {"control reaches end", "A function that returns a value must return something on every path."},
        {"might not have been initialized", "Give the variable a value before you use it."},
        {"not a statement", "This is not a complete instruction. Look for a typo or a missing operator."},
        {"illegal start of expression", "Something is out of place. Often a } or ) is missing on an earlier line."},
        {"unclosed string literal", "A string was opened with a quote but never closed."},
        {"unterminated string", "A string was opened with a quote but never closed."},
        {"already defined", "This name is used twice. Give one of the variables a different name."},
        {"cannot be applied to", "The method was called with the wrong number or type of arguments."},
        {"too few arguments", "The function was called with the wrong number of arguments."},
        {"too many arguments", "The function was called with the wrong number of arguments."},
        {"no such file", "The #include file was not found. Check its name."},
        {"undefined reference", "The linker cannot find a function. Is it defined, and is the name right?"},
        {"expected ':'", "Lines starting with if, for, while, def or class must end with a colon (:)."},
        {"indent", "Python groups code by the spaces at the start of a line. Check the indentation."},
        {"was never closed", "A bracket was opened but never closed."},
        {"invalid syntax", "Check this line and the one above for a typo or a missing bracket, quote or colon."},
        {"unexpected token", "Something is out of place here: a typo, an extra symbol or a missing bracket."},
        {"invalid or unexpected token", "Something is out of place here: a typo, an extra symbol or a missing quote."},
        {"is not a function", "You called something that is not a function. Check the name."},
        {"cannot read propert", "A value that is empty (undefined or null) was used."},
        {"assignment to constant", "A const cannot be changed. Use let instead."},
        {"main method not found", "Add public static void main(String[] args) so Java knows where to start."},
        {"no main method", "Add public static void main(String[] args) so Java knows where to start."},
        {"arrayindexoutofbounds", "An index outside the array was used. Indexes start at 0."},
        {"nullpointerexception", "A variable that holds nothing (null) was used."},
        {"by zero", "You cannot divide by zero."},
        {"typeerror", "A value of the wrong type was used, for example adding text to a number."},
    };

    private static String hint(String message) {
        String m = message.replace('‘', '\'').replace('’', '\'').toLowerCase();
        for (String[] h : HINTS) if (m.contains(h[0])) return h[1];
        return "";
    }

    private static final class Exec { int exit; boolean timedOut; String text = ""; }

    private static Exec exec(List<String> cmd, File dir, int seconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(emptyInput()));
        if (dir != null) pb.directory(dir);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (Reader in = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buf = new char[4096]; int n;
                while ((n = in.read(buf)) > 0) synchronized (sb) { if (sb.length() < MAX_OUT) sb.append(buf, 0, n); }
            } catch (IOException ignored) { }
        });
        reader.setDaemon(true); reader.start();
        Exec e = new Exec();
        if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
            e.timedOut = true;
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly(); p.waitFor();
        }
        reader.join(2000);
        e.exit = p.exitValue();
        synchronized (sb) { e.text = sb.length() > MAX_OUT ? sb.substring(0, MAX_OUT) + "\n... (output cut)" : sb.toString(); }
        return e;
    }

    /** Programs that read the keyboard get an empty input instead of waiting forever. */
    private static synchronized File emptyInput() throws IOException {
        if (emptyInput == null || !emptyInput.exists()) { emptyInput = File.createTempFile("code2music-stdin", ".txt"); emptyInput.deleteOnExit(); }
        return emptyInput;
    }

    private static String findTool(String... names) {
        for (String n : names) {
            try { if (exec(Arrays.asList(n, "--version"), null, 5).exit == 0) return n; } catch (Exception ignored) { }
        }
        return null;
    }

    private static String firstGroup(Pattern p, String s) { Matcher m = p.matcher(s); return m.find() ? m.group(1) : null; }
    private static String lastGroup(Pattern p, String s) { Matcher m = p.matcher(s); String g = null; while (m.find()) g = m.group(1); return g; }
    private static String lastLine(String s) {
        String[] lines = s.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) if (!lines[i].trim().isEmpty()) return lines[i].trim();
        return "";
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) { s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (IOException ignored) { }
    }
}
