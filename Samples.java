import java.util.EnumMap;
import java.util.Map;

/** Built-in example programs: a simple one, a deeply nested one, and one with a deliberate mistake to learn from. */
public final class Samples {
    public static final String[] LABELS = {"Simple code", "Deeply nested code", "Code with an error"};
    private static final Map<LanguageSupport.Kind, String[]> DATA = new EnumMap<>(LanguageSupport.Kind.class);

    private static String join(String... lines) { return String.join("\n", lines); }
    public static String get(LanguageSupport.Kind kind, int index) { return DATA.get(kind)[index]; }

    static {
        DATA.put(LanguageSupport.Kind.JAVA, new String[]{
            join("public class Calc {", "    static int add(int a, int b) {", "        int sum = a + b;", "        return sum;", "    }", "",
                "    public static void main(String[] args) {", "        System.out.println(\"2 + 3 = \" + add(2, 3));", "    }", "}"),
            join("public class PrimeSieve {", "    static int count(int limit) {", "        int found = 0;", "        for (int n = 2; n < limit; n++) {",
                "            boolean prime = true;", "            for (int d = 2; d < n; d++) {", "                while (n % d == 0) {", "                    if (d < n) {",
                "                        prime = false;", "                    } else {", "                        found = found + 1;", "                    }",
                "                    break;", "                }", "            }", "            if (prime) {", "                found++;", "            }", "        }",
                "        return found;", "    }", "", "    public static void main(String[] args) {",
                "        System.out.println(\"Primes below 30: \" + count(30));", "    }", "}"),
            join("public class Oops {", "    public static void main(String[] args) {", "        int total = 5", "        System.out.println(\"Total: \" + totl);", "    }", "}")});

        DATA.put(LanguageSupport.Kind.PYTHON, new String[]{
            join("def add(a, b):", "    total = a + b", "    return total", "", "print(\"2 + 3 =\", add(2, 3))"),
            join("def count(limit):", "    found = 0", "    for n in range(2, limit):", "        prime = True", "        for d in range(2, n):",
                "            while n % d == 0:", "                if d < n:", "                    prime = False", "                else:", "                    found = found + 1",
                "                break", "        if prime:", "            found += 1", "    return found", "", "print(\"Primes below 30:\", count(30))"),
            join("def greet(name)", "    print(\"Hello,\", name)", "", "greet(\"Ada\")")});

        DATA.put(LanguageSupport.Kind.C, new String[]{
            join("#include <stdio.h>", "", "int add(int a, int b) {", "    int sum = a + b;", "    return sum;", "}", "", "int main(void) {",
                "    printf(\"2 + 3 = %d\\n\", add(2, 3));", "    return 0;", "}"),
            join("#include <stdio.h>", "", "int count(int limit) {", "    int found = 0;", "    for (int n = 2; n < limit; n++) {", "        int prime = 1;",
                "        for (int d = 2; d < n; d++) {", "            while (n % d == 0) {", "                if (d < n) {", "                    prime = 0;",
                "                } else {", "                    found = found + 1;", "                }", "                break;", "            }", "        }",
                "        if (prime) {", "            found++;", "        }", "    }", "    return found;", "}", "", "int main(void) {",
                "    printf(\"Primes below 30: %d\\n\", count(30));", "    return 0;", "}"),
            join("#include <stdio.h>", "", "int main(void) {", "    int total = 5", "    printf(\"Total: %d\\n\", totl);", "    return 0;", "}")});

        DATA.put(LanguageSupport.Kind.CPP, new String[]{
            join("#include <iostream>", "", "int add(int a, int b) {", "    int sum = a + b;", "    return sum;", "}", "", "int main() {",
                "    std::cout << \"2 + 3 = \" << add(2, 3) << std::endl;", "    return 0;", "}"),
            join("#include <iostream>", "", "int count(int limit) {", "    int found = 0;", "    for (int n = 2; n < limit; n++) {", "        bool prime = true;",
                "        for (int d = 2; d < n; d++) {", "            while (n % d == 0) {", "                if (d < n) {", "                    prime = false;",
                "                } else {", "                    found = found + 1;", "                }", "                break;", "            }", "        }",
                "        if (prime) {", "            found++;", "        }", "    }", "    return found;", "}", "", "int main() {",
                "    std::cout << \"Primes below 30: \" << count(30) << std::endl;", "    return 0;", "}"),
            join("#include <iostream>", "", "int main() {", "    int total = 5", "    std::cout << \"Total: \" << totl << std::endl;", "    return 0;", "}")});

        DATA.put(LanguageSupport.Kind.JAVASCRIPT, new String[]{
            join("function add(a, b) {", "    let sum = a + b;", "    return sum;", "}", "", "console.log(\"2 + 3 =\", add(2, 3));"),
            join("function count(limit) {", "    let found = 0;", "    for (let n = 2; n < limit; n++) {", "        let prime = true;", "        for (let d = 2; d < n; d++) {",
                "            while (n % d === 0) {", "                if (d < n) {", "                    prime = false;", "                } else {",
                "                    found = found + 1;", "                }", "                break;", "            }", "        }", "        if (prime) {",
                "            found++;", "        }", "    }", "    return found;", "}", "", "console.log(\"Primes below 30:\", count(30));"),
            join("function greet(name) {", "    let message = \"Hello, \" + name", "    console.log(message;", "}", "", "greet(\"Ada\");")});
    }
}
