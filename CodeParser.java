import java.util.*;
import java.util.regex.Pattern;

/**
 * Lightweight tokenizer + structure parser: turns source into a tree of musical "events".
 * Java, C, C++ and JavaScript share the brace-based parser; Python uses an indentation-based one.
 */
public final class CodeParser {
    public enum Type { FILE, CLASS, METHOD, VAR, IF, ELSE, FOR, WHILE, RETURN, BLOCK }

    public static final class Node {
        public final Type type; public final String name, arg; public int count = 2;
        public final List<Node> kids = new ArrayList<>();
        Node(Type type, String name, String arg) { this.type = type; this.name = name; this.arg = arg; }
        Node(Type type, String name) { this(type, name, ""); }
    }

    private static final Set<String> PRIM = Set.of("int", "long", "short", "byte", "char", "float", "double", "boolean");
    private static final Set<String> KW = Set.of("abstract", "assert", "break", "case", "catch", "class", "continue",
        "default", "do", "else", "enum", "extends", "final", "finally", "for", "if", "implements", "import", "instanceof",
        "interface", "native", "new", "package", "private", "protected", "public", "return", "static", "strictfp", "super",
        "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
        "true", "false", "null");

    static boolean isName(String s) { return Character.isJavaIdentifierStart(s.charAt(0)) && !KW.contains(s) && !PRIM.contains(s); }
    static boolean typeish(String s) {
        return (Character.isJavaIdentifierStart(s.charAt(0)) && !KW.contains(s)) || s.equals(">") || s.equals("]");
    }

    static List<String> lex(String s) {
        List<String> t = new ArrayList<>(); int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) i++;
            else if (s.startsWith("//", i)) { while (i < n && s.charAt(i) != '\n') i++; }
            else if (s.startsWith("/*", i)) { int e = s.indexOf("*/", i + 2); i = e < 0 ? n : e + 2; }
            else if (s.startsWith("\"\"\"", i)) { int e = s.indexOf("\"\"\"", i + 3); i = e < 0 ? n : e + 3; t.add("\"S\""); }
            else if (c == '"' || c == '\'') {
                i++;
                while (i < n && s.charAt(i) != c) { if (s.charAt(i) == '\\') i++; i++; }
                i++; t.add("\"S\"");
            }
            else if (Character.isJavaIdentifierStart(c)) {
                int j = i; while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                t.add(s.substring(i, j)); i = j;
            }
            else if (Character.isDigit(c)) {
                int j = i; while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.' || s.charAt(j) == '_')) j++;
                t.add(s.substring(i, j)); i = j;
            }
            else { t.add(String.valueOf(c)); i++; }
        }
        return t;
    }

    private static int match(List<String> t, int open) {
        int d = 0;
        for (int i = open; i < t.size(); i++) {
            String s = t.get(i);
            if (s.equals("(")) d++; else if (s.equals(")") && --d == 0) return i;
        }
        return t.size() - 1;
    }

    private static int literal(List<String> t, int from, int to) {
        int best = 2;
        for (int i = from; i < to; i++) if (t.get(i).matches("\\d{1,4}")) best = Math.max(best, Integer.parseInt(t.get(i)));
        return Math.min(4, best);
    }

    private static boolean isDecl(List<String> t, int k, String a, String b) {
        return k > 0 && k + 1 < t.size() && isName(t.get(k)) && typeish(t.get(k - 1))
            && (t.get(k + 1).equals(a) || t.get(k + 1).equals(b));
    }

    private static void push(Deque<Node> st, Node nd) { st.peek().kids.add(nd); st.push(nd); }

    public static Node parse(String src) {
        List<String> t = lex(src); int n = t.size();
        Node root = new Node(Type.FILE, "file");
        Deque<Node> st = new ArrayDeque<>(); st.push(root);
        for (int i = 0; i < n; i++) {
            String w = t.get(i); Node top = st.peek();
            if (w.equals("import") || w.equals("package")) { while (i < n && !t.get(i).equals(";")) i++; continue; }
            if (w.equals("{")) { push(st, new Node(Type.BLOCK, "")); continue; }
            if (w.equals("}")) { if (st.size() > 1) st.pop(); continue; }

            boolean rec = w.equals("record") && i + 2 < n && isName(t.get(i + 1)) && (t.get(i + 2).equals("(") || t.get(i + 2).equals("<"));
            if (w.equals("class") || w.equals("interface") || w.equals("enum") || rec) {
                if (i + 1 < n && isName(t.get(i + 1))) {
                    int j = i + 1; while (j < n && !t.get(j).equals("{") && !t.get(j).equals(";")) j++;
                    if (j < n && t.get(j).equals("{")) { push(st, new Node(Type.CLASS, t.get(i + 1))); i = j; }
                }
                continue;
            }
            if ((w.equals("if") || w.equals("for") || w.equals("while")) && i + 1 < n && t.get(i + 1).equals("(")) {
                int p = match(t, i + 1); String after = p + 1 < n ? t.get(p + 1) : "";
                Type ty = w.equals("if") ? Type.IF : w.equals("for") ? Type.FOR : Type.WHILE;
                if (ty == Type.WHILE && after.equals(";")) { i = p; continue; }   // end of do-while
                Node nd = new Node(ty, w);
                if (ty != Type.IF) nd.count = literal(t, i + 2, p);
                if (after.equals("{")) { push(st, nd); i = p + 1; } else { top.kids.add(nd); i = p; }
                continue;
            }
            if (w.equals("else")) {
                if (i + 1 < n && t.get(i + 1).equals("{")) { push(st, new Node(Type.ELSE, "else")); i++; }
                else if (!(i + 1 < n && t.get(i + 1).equals("if"))) top.kids.add(new Node(Type.ELSE, "else"));
                continue;
            }
            if (w.equals("do") && i + 1 < n && t.get(i + 1).equals("{")) { push(st, new Node(Type.WHILE, "do")); i++; continue; }
            if (w.equals("return")) { top.kids.add(new Node(Type.RETURN, "return")); continue; }

            if (isName(w) && i + 1 < n && t.get(i + 1).equals("(") && (top.type == Type.CLASS || top.type == Type.FILE)) {
                int p = match(t, i + 1), q = p + 1;
                if (q < n && t.get(q).equals("throws")) while (q < n && !t.get(q).equals("{") && !t.get(q).equals(";")) q++;
                if (q < n && t.get(q).equals("{")) {
                    Node m = new Node(Type.METHOD, w); push(st, m);
                    for (int k = i + 3; k < p; k++) if (isDecl(t, k, ",", ")")) m.kids.add(new Node(Type.VAR, t.get(k), t.get(k - 1)));
                    i = q; continue;
                }
            }
            if (isDecl(t, i, "=", ";")) top.kids.add(new Node(Type.VAR, w, t.get(i - 1)));
        }
        return root;
    }

    /** Picks the right parser for the chosen language. */
    public static Node parse(String src, LanguageSupport.Kind kind) {
        return kind == LanguageSupport.Kind.PYTHON ? parsePython(src) : parse(src);
    }

    private static final Pattern PY_ASSIGN = Pattern.compile("^([A-Za-z_]\\w*)\\s*=(?!=)\\s*(.*)$");

    /** Python has no braces, so the tree is built from how far each line is indented. */
    public static Node parsePython(String src) {
        Node root = new Node(Type.FILE, "file");
        Deque<Node> nodes = new ArrayDeque<>(); Deque<Integer> indents = new ArrayDeque<>();
        nodes.push(root); indents.push(-1);
        Set<String> seen = new HashSet<>(); String docEnd = null;
        for (String raw : src.split("\\R")) {
            String t0 = raw.trim();
            if (docEnd != null) { if (t0.contains(docEnd)) docEnd = null; continue; }          // inside a """docstring"""
            if (t0.startsWith("\"\"\"") || t0.startsWith("'''")) { String d = t0.substring(0, 3); if (t0.indexOf(d, 3) < 0) docEnd = d; continue; }
            String line = stripPyComment(raw), t = line.trim();
            if (t.isEmpty()) continue;
            int ind = 0;
            for (char ch : line.toCharArray()) { if (ch == ' ') ind++; else if (ch == '\t') ind += 4; else break; }
            while (nodes.size() > 1 && indents.peek() >= ind) { nodes.pop(); indents.pop(); }
            Node parent = nodes.peek();
            if (t.startsWith("async ")) t = t.substring(6).trim();
            String head = t.split("[\\s(:]", 2)[0]; boolean block = t.endsWith(":");
            Node nd = null;
            switch (head) {
                case "class": nd = new Node(Type.CLASS, wordAfter(t, "class")); break;
                case "def": {
                    nd = new Node(Type.METHOD, wordAfter(t, "def")); seen.clear();
                    int a = t.indexOf('('), b = t.lastIndexOf(')');
                    if (a >= 0 && b > a) for (String p : t.substring(a + 1, b).split(",")) {
                        String name = p.trim().replaceFirst("^\\*+", "").split("[:=\\s]", 2)[0];
                        if (!name.isEmpty() && !name.equals("self") && !name.equals("cls") && seen.add(name)) nd.kids.add(new Node(Type.VAR, name, ""));
                    }
                    break;
                }
                case "if": case "elif": nd = new Node(Type.IF, "if"); break;
                case "else": nd = new Node(Type.ELSE, "else"); break;
                case "for": case "while":
                    nd = new Node(head.equals("for") ? Type.FOR : Type.WHILE, head);
                    List<String> tok = lex(t); nd.count = literal(tok, 0, tok.size()); break;
                case "return": parent.kids.add(new Node(Type.RETURN, "return")); continue;
                default:
                    if (block) { nd = new Node(Type.BLOCK, ""); break; }                           // try:, with ...:, except ...:
                    java.util.regex.Matcher m = PY_ASSIGN.matcher(t);
                    if (m.matches() && seen.add(m.group(1))) parent.kids.add(new Node(Type.VAR, m.group(1), m.group(2).matches("-?\\d+(\\.\\d+)?") ? "int" : ""));
                    continue;
            }
            parent.kids.add(nd);
            if (block) { nodes.push(nd); indents.push(ind); }
        }
        return root;
    }

    private static String wordAfter(String t, String kw) {
        String rest = t.substring(kw.length()).trim();
        int e = 0; while (e < rest.length() && Character.isJavaIdentifierPart(rest.charAt(e))) e++;
        return rest.substring(0, e);
    }

    private static String stripPyComment(String s) {
        char q = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q != 0) { if (c == '\\') i++; else if (c == q) q = 0; }
            else if (c == '"' || c == '\'') q = c;
            else if (c == '#') return s.substring(0, i);
        }
        return s;
    }

    public static int[] tally(Node root) { int[] c = new int[Type.values().length]; walk(root, c); return c; }
    private static void walk(Node n, int[] c) { c[n.type.ordinal()]++; for (Node k : n.kids) walk(k, c); }
}
