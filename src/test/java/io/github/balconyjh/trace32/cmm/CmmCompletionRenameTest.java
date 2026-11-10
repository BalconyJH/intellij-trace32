package io.github.balconyjh.trace32.cmm;

/** Command-position completion and binding-preserving rename regressions. */
public final class CmmCompletionRenameTest {
    private static int checks;
    public static void main(String[] args) {
        path("DO chi", "chi", false);
        path("DO ", "", false);
        path("DO \"sub dir/chi", "sub dir/chi", true);
        path("DO 'sub dir/chi", "sub dir/chi", true);
        path("RUN C:\\scripts\\chi", "C:/scripts/chi", false);
        path("CD.DO chi", "chi", false);
        path("ChDir.DO chi", "chi", false);
        noPath("ChDir.RUN chi");
        noPath("CD.RUN chi");
        path("ON ERROR DO chi", "chi", false);
        path("GLOBALON CMD custom DO chi", "chi", false);
        path("ON CMD DO DO chi", "chi", false);
        path("ON TIME 5.0s DO chi", "chi", false);
        path("again: DO chi", "chi", false);
        path("DO \\\n    chi", "chi", false);
        path("ON ERROR \\\n DO chi", "chi", false);
        noPath("PRINT DO chi");
        noPath("PRINT \"please DO chi");
        noPath("ON ERROR PRINT DO chi");
        noPath("GLOBALON CMD custom EXECute PRINT DO chi");
        noPath("// DO chi");
        noPath("DO chi // comment");
        noPath("DO \"child.cmm\" argument");
        noPath("DO 'child.cmm' argument");
        noPath("DO child.cmm argument");
        noPath("PRINT 1. \\\n DO chi");
        noPath("DO");

        conflict("PRIVATE &a &b\nPRINT &a &b\n", "&a", "b", true, "same scope declaration collision");
        conflict("first:\nGOTO first\nsecond:\n", "first", "second", true, "duplicate label collision");
        conflict("GOTO first\nfirst:\n", "first:", "longer", false, "valid label rename updates forward jump");
        conflict("PRIVATE &a &A\nPRINT &a &A\n", "&a", "A", true, "exact-case existing name collides");
        conflict("PRIVATE &a\nPRINT &(a) &a\n", "&a", "fresh", false, "valid quoted and bare macro rename");
        conflict("PRIVATE &(a)\nPRINT \"&(a) &a\"\n&a=1\n", "&(a)", "fresh", false,
                "parenthesized declaration and both string reference forms");
        conflict("PRIVATE &a\nPRINT &a\n", "&a", "a", false, "unchanged name is accepted");
        conflict("PRIVATE &a\nPRINT &b\n", "&a", "b", true, "rename cannot capture an unresolved reference");
        conflict("IF TRUE()\n(\n PRIVATE &a\n PRINT &a\n)\nIF TRUE()\n(\n PRIVATE &b\n PRINT &b\n)\n",
                "&a", "b", false, "disjoint sibling scopes are allowed");
        conflict("PRIVATE &a\nIF TRUE()\n(\n PRIVATE &b\n PRINT &a &b\n)\n", "&a", "b", true,
                "nested declaration cannot capture renamed uses");
        conflict("PRIVATE &b\nIF TRUE()\n(\n PRIVATE &a\n PRINT &b &a\n)\n", "&a", "b", true,
                "rename cannot capture existing outer uses");
        conflict("PRIVATE &A\nPRINT &A\n", "&A", "a", false, "case-only rename allowed without collision");
        String direct = "PRIVATE &a &b\n";
        check(CmmRenameProcessor.conflict(direct, direct.indexOf("&a"), "b", false) != null,
                "declaration-only guard catches duplicate without usage mutation");
        System.out.println("PASS " + checks + " completion/rename checks");
    }
    private static void path(String source, String expected, boolean quoted) {
        var context = CmmCompletionContributor.pathContext(source, source.length());
        check(context != null && context.getTyped().equals(expected) && context.getQuoted() == quoted,
                "script path context: " + source.replace('\n', ' '));
        check(quoted && "\"'".indexOf(source.charAt(context.getStart())) >= 0
                || !quoted && source.substring(context.getStart()).startsWith(expected.replace('/', '\\'))
                || source.substring(context.getStart()).equals(expected), "path insertion range");
    }
    private static void noPath(String source) {
        check(CmmCompletionContributor.pathContext(source, source.length()) == null,
                "no path completion: " + source.replace('\n', ' '));
    }
    private static void conflict(String text, String target, String name, boolean expected, String message) {
        check((CmmRenameProcessor.conflict(text, text.indexOf(target), name) != null) == expected, message);
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
