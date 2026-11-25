/** Languages the app can turn into music and (with the tools installed on your computer) compile and run. */
public final class LanguageSupport {
    public enum Kind { JAVA, PYTHON, C, CPP, JAVASCRIPT }

    public static final class Lang {
        public final Kind kind; public final String name, ext;
        Lang(Kind kind, String name, String ext) { this.kind = kind; this.name = name; this.ext = ext; }
        @Override public String toString() { return name; }
    }

    public static final Lang[] ALL = {
        new Lang(Kind.JAVA, "Java", ".java"), new Lang(Kind.PYTHON, "Python", ".py"), new Lang(Kind.C, "C", ".c"),
        new Lang(Kind.CPP, "C++", ".cpp"), new Lang(Kind.JAVASCRIPT, "JavaScript", ".js")
    };

    /** "Java, Python, C, C++ and JavaScript" - used in messages. */
    public static String supportedList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ALL.length; i++) sb.append(i == 0 ? "" : i == ALL.length - 1 ? " and " : ", ").append(ALL[i].name);
        return sb.toString();
    }

    /** Picks a language from a file name, or returns null when the extension is not known. */
    public static Lang forFile(String fileName) {
        String n = fileName.toLowerCase();
        for (Lang l : ALL) if (n.endsWith(l.ext)) return l;
        if (n.endsWith(".cc") || n.endsWith(".cxx")) return ALL[3];
        return null;
    }
}
