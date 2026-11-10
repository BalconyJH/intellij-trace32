package io.github.balconyjh.trace32.cmm.core;

import java.util.List;

/** Dependency-free semantic regression checks; invoke this class with java. */
public final class CmmModelTest {
    private static int checks;

    public static void main(String[] args) {
        labelsAndEvents();
        eventActionSlots();
        macrosAndScopes();
        folding();
        scriptPaths();
        noInventedSymbols();
        malformedInput();
        continuedCommands();
        parenthesizedAndRecursiveMacros();
        commentFolds();
        System.out.println("CmmModelTest: " + checks + " checks passed");
    }

    private static void labelsAndEvents() {
        String text = "ON ERROR GOTO failed\r\nGOSUB initMem\r\nGOTO missing\r\n"
                + "SUBROUTINE initMem\r\n(\r\n RETURN\r\n)\r\nfailed:\r\nENDDO\r\n";
        CmmModel model = CmmModel.parse(text);
        check(model.symbols().stream().filter(s -> !s.macro()).count() == 2, "label and SUBROUTINE definitions");
        check(resolve(model, "failed", false, 0).start() == text.indexOf("failed:"), "ON ERROR target resolves forward");
        check(resolve(model, "initMem", false, 0).start() == text.indexOf("initMem", text.indexOf("SUBROUTINE")), "GOSUB subroutine resolves");
        check(resolve(model, "missing", false, 0) == null, "unknown label remains unresolved");
        check(CmmModel.parse("GOTO 102.\nGOTO &target\n").references().stream().noneMatch(r -> !r.macro()), "line-number and dynamic targets are not labels");
        check(CmmModel.parse("PRINT GOTO target\n").references().isEmpty(), "argument named GOTO is not a branch");
    }

    private static void macrosAndScopes() {
        String text = "PRIVATE &value &Value\n&value=1\nPRINT &value &Value &missing\n"
                + "IF TRUE()\n(\n PRIVATE &value\n PRINT &value\n)\nPRINT &value\n"
                + "&implicit=1\n&implicit=2\nPRINT \"&implicit &Value\"\n";
        CmmModel model = CmmModel.parse(text);
        CmmModel.Symbol value = resolve(model, "&value", true, 0);
        check(value != null && value.start() == text.indexOf("&value"), "assignment resolves to explicit declaration");
        CmmModel.Symbol upper = resolve(model, "&Value", true, 0);
        check(upper != null && upper.start() == text.indexOf("&Value"), "case-distinct macro resolves separately");
        check(resolve(model, "&missing", true, 0) == null, "unknown macro remains unresolved");
        int inner = text.indexOf("&value", text.indexOf(" PRIVATE"));
        CmmModel.Reference innerReference = model.references().stream().filter(r -> r.name().equals("&value") && r.start() > inner).findFirst().orElseThrow();
        check(model.resolve(innerReference).start() == inner, "nested lexical declaration shadows outer");
        CmmModel.Reference outerReference = model.references().stream().filter(r -> r.name().equals("&value") && r.start() > text.indexOf(")\nPRINT")).findFirst().orElseThrow();
        check(model.resolve(outerReference).start() == value.start(), "inner scope does not leak");
        check(model.symbols().stream().filter(s -> s.name().equals("&implicit")).count() == 1, "reassignments are not duplicate definitions");
        check(model.references().stream().anyMatch(r -> r.name().equals("&implicit") && text.charAt(r.start() - 1) == '\"'), "string interpolation has macro reference");
        check(CmmModel.parse("PRINT &future\nPRIVATE &future\n").resolve(new CmmModel.Reference(6, 13, "&future", true)) == null, "future declaration is not visible before execution");
    }

    private static void eventActionSlots() {
        String source = "ON ERROR DO recover.cmm\nGLOBALON CMD custom DO custom.cmm\n"
                + "ON TIME 0.2s DO delayed.cmm\nGLOBALON TIME &delay DO timed.cmm\n"
                + "start: ON ERROR \\\n DO continued.cmm\n"
                + "ON ERROR GOTO failed\nON CMD DO GOSUB handler\nON TIME 1.s JUMPTO failed\n"
                + "failed:\nSUBROUTINE handler\n";
        CmmModel model = CmmModel.parse(source);
        check(model.scriptPaths().stream().map(CmmModel.ScriptPath::path).toList()
                        .equals(List.of("recover.cmm", "custom.cmm", "delayed.cmm", "timed.cmm", "continued.cmm")),
                "documented event action slots, timed events, inline labels and continuation");
        check(model.references().stream().filter(r -> !r.macro()).count() == 3,
                "only actual event branch actions introduce label references");
        check(resolve(model, "handler", false, 0).start() == source.lastIndexOf("handler"),
                "CMD event argument named DO is not mistaken for an action");
        for (CmmModel.ScriptPath path : model.scriptPaths())
            check(source.substring(path.start(), path.end()).equals(path.path()), "event path source offsets");

        for (String fake : List.of("ON ERROR PRINT DO fake.cmm", "ON ERROR PRINT GOTO fake",
                "GLOBALON CMD custom EXECute PRINT DO fake.cmm", "GLOBALON CMD custom EXECute PRINT GOTO fake",
                "ON ERROR PRINT \"DO fake.cmm\"", "GLOBALON CMD custom EXECute PRINT \"GOTO fake\"",
                "ON CMD DO", "ON TIME", "ON ERROR", "GLOBALON CMD custom EXECute DO fake.cmm")) {
            CmmModel ignored = CmmModel.parse(fake + "\n");
            check(ignored.scriptPaths().isEmpty(), "non-action DO cannot introduce a script path: " + fake);
            check(ignored.references().stream().noneMatch(r -> !r.macro()),
                    "non-action GOTO cannot introduce a label reference: " + fake);
        }

        String aliases = "ChDir.DO first.cmm\nCD.DO second.cmm\ncd.do third.cmm\n"
                + "ChDir.RUN unsupported.cmm\nCD.RUN unsupported.cmm\nChDir.PEDIT edit.cmm\n";
        check(CmmCatalog.canonicalName("CD.DO").equalsIgnoreCase("ChDir.DO"), "CD.DO is the catalog's documented alias");
        check(CmmModel.parse(aliases).scriptPaths().stream().map(CmmModel.ScriptPath::path).toList()
                        .equals(List.of("first.cmm", "second.cmm", "third.cmm")),
                "only documented ChDir.DO/CD.DO script-call aliases are accepted");
    }

    private static void folding() {
        String text = "IF (STATE.RUN())\n( // block\n PRINT \"( )\"\n WHILE TRUE()\n (\n  PRINT 1. ; )\n )\n)\nPRINT FUNC(1.)\n";
        CmmModel model = CmmModel.parse(text);
        check(model.folds().size() == 2, "only nested multiline statement blocks fold");
        check(model.folds().get(0).start() == text.indexOf("( //"), "outer block starts at statement parenthesis");
        for (CmmModel.Fold fold : model.folds()) {
            check(text.charAt(fold.start()) == '(' && text.charAt(fold.end() - 1) == ')', "fold includes matching delimiters");
        }
        var commentsOnly = CmmModel.parse("PRINT \"(\"\n; (\n// )\nPRINT ')'; )\n");
        check(commentsOnly.folds().size() == 1, "comment run folds without interpreting its parentheses");
        check(CmmModel.parse("REPEAT\n(\n PRINT 1.\n) WHILE TRUE()\n").folds().size() == 1, "REPEAT close with condition folds");
    }

    private static void scriptPaths() {
        String text = "PRIVATE &directory\n&directory=OS.PPD()\nDO \"&directory/prepare.cmm\"\n"
                + "RUN \"sub folder/launch.cmm\" arg\nDO C:\\project\\script.cmm\n"
                + "ON ERROR DO recover.cmm\nGLOBALON CMD custom DO \"~~/tools/reset.cmm\"\n"
                + "&directory=OS.PWD()\nDO \"&directory/later.cmm\"\nDO &runtime\nDO noextension\n";
        CmmModel model = CmmModel.parse(text);
        List<CmmModel.ScriptPath> paths = model.scriptPaths();
        check(paths.size() == 8, "script commands and event actions have paths");
        check(paths.get(0).path().equals("./prepare.cmm"), "OS.PPD assignment resolves directory alias");
        check(text.substring(paths.get(0).start(), paths.get(0).end()).equals("&directory/prepare.cmm"), "path range preserves source without quotes");
        check(paths.get(1).path().equals("sub folder/launch.cmm"), "spaces inside quoted path preserved");
        check(paths.get(2).path().equals("C:\\project\\script.cmm"), "Windows unquoted path preserved");
        check(paths.get(3).path().equals("recover.cmm"), "event script path extracted");
        check(paths.get(4).path().equals("~~/tools/reset.cmm"), "Lauterbach TRACE32® root is not guessed");
        check(paths.get(5).path().equals("&directory/later.cmm"), "reassignment invalidates directory alias");
        check(paths.get(6).path().equals("&runtime"), "runtime-only macro path retained unresolved");
        check(paths.get(7).path().equals("noextension"), "missing extension retained for IDE resolver");
        String scoped = "PRIVATE &dir\n&dir=OS.PPD()\nIF TRUE()\n(\n PRIVATE &dir\n DO \"&dir/x.cmm\"\n)\nDO \"&dir/y.cmm\"\n";
        check(CmmModel.parse(scoped).scriptPaths().get(0).path().equals("&dir/x.cmm"), "shadowed directory alias is not inherited");
        check(CmmModel.parse(scoped).scriptPaths().get(1).path().equals("./y.cmm"), "outer directory alias restored after block");
        String conditionalMutation = "PRIVATE &dir\n&dir=OS.PPD()\nIF TRUE()\n(\n &dir=OS.PWD()\n)\nDO \"&dir/x.cmm\"\n";
        check(CmmModel.parse(conditionalMutation).scriptPaths().get(0).path().equals("&dir/x.cmm"), "conditional mutation invalidates a previously static directory alias");
        String returnedDirectory = "PRIVATE &dir\n&dir=OS.PPD()\nRETURNVALUES &dir\nDO \"&dir/x.cmm\"\n";
        check(CmmModel.parse(returnedDirectory).scriptPaths().get(0).path().equals("&dir/x.cmm"), "returned values invalidate directory alias");
    }

    private static void noInventedSymbols() {
        String text = "; PRIVATE &comment\n// fake:\nPRINT \"GOTO fake; PRIVATE &text\"\nPRINT \"DO false.cmm\"\n"
                + "Var.View source;field\nDO real.cmm ; GOTO absent\n";
        CmmModel model = CmmModel.parse(text);
        check(model.symbols().isEmpty(), "comments and strings do not declare symbols");
        check(model.references().size() == 1 && model.references().get(0).name().equals("&text"), "only string macro interpolation is a reference");
        check(model.scriptPaths().size() == 1 && model.scriptPaths().get(0).path().equals("real.cmm"), "comments and strings do not introduce script paths");
    }

    private static void malformedInput() {
        check(CmmModel.parse("").symbols().isEmpty(), "empty file");
        check(CmmModel.parse("GOTO\nDO\nPRIVATE\n(\n").references().isEmpty(), "incomplete edit is tolerated");
        check(CmmModel.parse("DO // comment\nDO ; comment\n").scriptPaths().isEmpty(), "missing path before comment is tolerated");
        check(CmmModel.parse("DO \"unfinished.cmm\n").scriptPaths().size() == 1, "unfinished quoted path remains navigable");
    }

    private static void continuedCommands() {
        String text = "PRIVATE &first \\\r\n &second\r\nPRINT &second\r\nGOTO \\\n done\nDO \\\n \"prepare.cmm\"\ndone:\nENDDO\n";
        CmmModel model = CmmModel.parse(text);
        check(model.symbols().stream().anyMatch(s -> s.name().equals("&second")), "continued declaration");
        check(resolve(model, "&second", true, 0) != null, "continued macro navigation");
        check(resolve(model, "done", false, 0).start() == text.indexOf("done:"), "continued branch target");
        check(model.scriptPaths().get(0).path().equals("prepare.cmm"), "continued script path");
        check(text.substring(model.scriptPaths().get(0).start(), model.scriptPaths().get(0).end()).equals("prepare.cmm"), "continued path source offsets");
    }

    private static void parenthesizedAndRecursiveMacros() {
        String text = "PRIVATE &name &directory\n&&name=\"expanded\"\nPRINT &(name) \"&(name)\"\n"
                + "&(directory)=OS.PPD()\nCD.DO \"&(directory)/prepare.cmm\"\n"
                + "&&implicit=1\nPRINT &(implicit)\n";
        CmmModel model = CmmModel.parse(text);
        check(model.symbols().stream().filter(s -> s.name().equals("&name")).count() == 1, "recursive assignment does not redeclare macro");
        for (CmmModel.Reference ref : model.references()) {
            if (ref.name().equals("&name")) check(model.resolve(ref).start() == text.indexOf("&name"), "both macro spellings resolve to one declaration");
        }
        check(model.references().stream().anyMatch(r -> text.substring(r.start(), r.end()).equals("&(name)")), "parenthesized macro source range remains complete");
        check(model.scriptPaths().get(0).path().equals("./prepare.cmm"), "parenthesized directory macro and CD.DO alias resolve");
        check(resolve(model, "&implicit", true, 0) != null, "recursive assignment can define an implicit macro");
        check(CmmModel.parse("PRIVATE &(value)\nPRINT &value\n").symbols().get(0).name().equals("&value"), "declaration spelling normalizes symbol name");
        check(CmmModel.parse("List.Java `fake: ()`\n").symbols().isEmpty(), "quoted source symbol cannot declare PRACTICE label");
    }

    private static void commentFolds() {
        String text = "// first\r\n  // second\r\nPRINT 1 // trailing\r\n// alone\r\n\r\n; next\r\n; last\r\n";
        var folds = CmmModel.parse(text).folds();
        check(folds.size() == 2, "only adjacent standalone comment lines form folds");
        check(text.substring(folds.get(0).start(), folds.get(0).end()).equals("// first\r\n  // second"), "comment fold exact range");
        check(text.substring(folds.get(1).start(), folds.get(1).end()).equals("; next\r\n; last"), "blank line separates comment runs");
    }

    private static CmmModel.Symbol resolve(CmmModel model, String name, boolean macro, int occurrence) {
        CmmModel.Reference reference = model.references().stream().filter(r -> r.macro() == macro && r.name().equals(name))
                .skip(occurrence).findFirst().orElseThrow(() -> new AssertionError("Missing reference " + name));
        return model.resolve(reference);
    }

    private static void check(boolean condition, String detail) {
        checks++;
        if (!condition) throw new AssertionError(detail);
    }
}
