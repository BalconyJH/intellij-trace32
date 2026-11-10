package io.github.balconyjh.trace32.cmm.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static io.github.balconyjh.trace32.cmm.core.CmmScanner.Kind.*;

/** Run with java -ea; does not require an IntelliJ application or third-party libraries. */
public final class CmmScannerTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        assertTokens("; full line\r\nPRINT \"a;b//c\" // trailing\n", List.of(
                token(COMMENT, "; full line"), token(KEYWORD, "PRINT"),
                token(STRING, "\"a;b//c\""), token(COMMENT, "// trailing")));
        assertTokens("PRINT \"C:\\folder\\\"\nPRINT \"She said \"\"Hi!\"\"\"\n", List.of(
                token(KEYWORD, "PRINT"), token(STRING, "\"C:\\folder\\\""),
                token(KEYWORD, "PRINT"), token(STRING, "\"She said \"\"Hi!\"\"\"")));
        assertTokens("PRINT ' ' ';' \"unterminated\nPRINT 1\n", List.of(
                token(KEYWORD, "PRINT"), token(STRING, "' '"), token(STRING, "';'"),
                token(STRING, "\"unterminated"), token(KEYWORD, "PRINT"), token(NUMBER, "1")));
        assertTokens("PRIVATE &x &X &_value\nIF &x&&&X\n", List.of(
                token(KEYWORD, "PRIVATE"), token(MACRO, "&x"), token(MACRO, "&X"), token(MACRO, "&_value"),
                token(KEYWORD, "IF"), token(MACRO, "&x"), token(OPERATOR, "&&"), token(MACRO, "&X")));
        assertTokens("start:\n GOTO start\n  not_label:\nData.Set D:0x100 %Long 0xFF\n", List.of(
                token(LABEL, "start:"), token(KEYWORD, "GOTO"), token(IDENTIFIER, "start"),
                token(IDENTIFIER, "not_label"), token(OPERATOR, ":"), token(COMMAND, "Data.Set"),
                token(IDENTIFIER, "D"), token(OPERATOR, ":"), token(NUMBER, "0x100"), token(OPERATOR, "%"),
                token(IDENTIFIER, "Long"), token(NUMBER, "0xFF")));
        assertTokens("PRINT 0xFE 0b1010 12. 1.5 1MHz 1s 2e-3 0x0--0xFF\n", List.of(
                token(KEYWORD, "PRINT"), token(NUMBER, "0xFE"), token(NUMBER, "0b1010"), token(NUMBER, "12."),
                token(NUMBER, "1.5"), token(NUMBER, "1MHz"), token(NUMBER, "1s"), token(NUMBER, "2e-3"),
                token(NUMBER, "0x0"), token(OPERATOR, "--"), token(NUMBER, "0xFF")));
        assertTokens("SYStem.CPU CortexA53\nIF (STATE.RUN())\n(\nPRINT OS.PPD()\n)\n", List.of(
                token(COMMAND, "SYStem.CPU"), token(IDENTIFIER, "CortexA53"), token(KEYWORD, "IF"),
                token(LPAREN, "("), token(FUNCTION, "STATE.RUN"), token(LPAREN, "("), token(RPAREN, ")"),
                token(RPAREN, ")"), token(LPAREN, "("), token(KEYWORD, "PRINT"), token(FUNCTION, "OS.PPD"),
                token(LPAREN, "("), token(RPAREN, ")"), token(RPAREN, ")")));
        assertTokens("Var.View flags[3];ast.count;i // comment\r\nPRINT 1 ; normal\n", List.of(
                token(COMMAND, "Var.View"), token(IDENTIFIER, "flags"), token(OPERATOR, "["), token(NUMBER, "3"),
                token(OPERATOR, "]"), token(OPERATOR, ";"), token(IDENTIFIER, "ast.count"), token(OPERATOR, ";"),
                token(IDENTIFIER, "i"), token(COMMENT, "// comment"), token(KEYWORD, "PRINT"),
                token(NUMBER, "1"), token(COMMENT, "; normal")));
        assertTokens("Var.View a \\\r\n ; b\r\nPRINT 1 ; comment", List.of(
                token(COMMAND, "Var.View"), token(IDENTIFIER, "a"), token(OPERATOR, "\\"), token(OPERATOR, ";"),
                token(IDENTIFIER, "b"), token(KEYWORD, "PRINT"), token(NUMBER, "1"), token(COMMENT, "; comment")));
        assertTokens("label: PRINT 42\nD:0x100", List.of(
                token(LABEL, "label:"), token(KEYWORD, "PRINT"), token(NUMBER, "42"),
                token(IDENTIFIER, "D"), token(OPERATOR, ":"), token(NUMBER, "0x100")));
        check(CmmCatalog.isKeyword("pRiVaTe"), "Case-insensitive keywords");
        check(!CmmCatalog.isKeyword("CPU"), "Argument is not a keyword");
        check(CmmCatalog.entries().stream().anyMatch(e -> e.name().equals("OS.PPD") && e.function()), "Function catalog");
        check(CmmCatalog.entries().stream().anyMatch(e -> e.name().equals("SYStem.CPU") && !e.function()), "Command catalog");
        assertTokens("PRINT &(value) 0y10x1 0xFFxx 0x0..0xFF\n", List.of(
                token(KEYWORD, "PRINT"), token(MACRO, "&(value)"), token(NUMBER, "0y10x1"),
                token(NUMBER, "0xFFxx"), token(NUMBER, "0x0"), token(OPERATOR, ".."), token(NUMBER, "0xFF")));
        assertTokens("&&name=\"&source\"\n&&(_name) = 1\n", List.of(
                token(OPERATOR, "&"), token(MACRO, "&name"), token(OPERATOR, "="), token(STRING, "\"&source\""),
                token(OPERATOR, "&"), token(MACRO, "&(_name)"), token(OPERATOR, "="), token(NUMBER, "1")));
        assertTokens("List.Java `<clinit>(); // text`\n", List.of(
                token(COMMAND, "List.Java"), token(STRING, "`<clinit>(); // text`")));
        assertTokens("PRINT 0y0011 0Y1X00 D:0x100..D:0x1FF\n", List.of(
                token(KEYWORD, "PRINT"), token(NUMBER, "0y0011"), token(NUMBER, "0Y1X00"),
                token(IDENTIFIER, "D"), token(OPERATOR, ":"), token(NUMBER, "0x100"), token(OPERATOR, ".."),
                token(IDENTIFIER, "D"), token(OPERATOR, ":"), token(NUMBER, "0x1FF")));
        check(CmmCatalog.isKeyword("rpt"), "Documented repeat alias");
        check(CmmCatalog.canonicalName("sys.cpu").equals("SYStem.CPU"), "Segment alias resolves to canonical command");
        check(CmmCatalog.canonicalName("d.load.e").equals("Data.Load.Elf"), "Documented command segment aliases");
        check(CmmCatalog.find("d.l.e") == null, "Unspecified partial command is not accepted as an alias");
        check(CmmCatalog.entries().size() >= 180, "Expanded documented catalog");

        Random random = new Random(0xC33);
        String alphabet = "abcDEF_012xy. &;/'\"`\\\r\n\t:=()[]+-!%";
        for (int sample = 0; sample < 350; sample++) {
            StringBuilder source = new StringBuilder();
            for (int i = random.nextInt(250); i > 0; i--) source.append(alphabet.charAt(random.nextInt(alphabet.length())));
            assertRestartable(source.toString());
        }
        for (String filename : args) assertRestartable(Files.readString(Path.of(filename)));
        System.out.println("CmmScannerTest passed (" + checks + " assertions; 350 restart fuzz cases; " + args.length + " source files)");
    }

    private record Expected(CmmScanner.Kind kind, String text) {}

    private static Expected token(CmmScanner.Kind kind, String text) {
        return new Expected(kind, text);
    }

    private static void assertTokens(String source, List<Expected> expected) {
        List<Expected> actual = CmmScanner.scan(source).stream().filter(t -> t.kind() != WHITESPACE)
                .map(t -> new Expected(t.kind(), source.substring(t.start(), t.end()))).toList();
        check(expected.equals(actual), "Tokens differ for " + source + "\nExpected: " + expected + "\nActual: " + actual);
        assertRestartable(source);
    }

    private static void assertRestartable(String source) {
        List<CmmScanner.Token> full = CmmScanner.scan(source);
        int end = 0;
        for (CmmScanner.Token token : full) {
            check(token.start() == end && token.end() > token.start(), "Scanner lost text or made no progress");
            end = token.end();
        }
        check(end == source.length(), "Scanner did not reach end of input");
        for (int i = 0; i < full.size(); i++) {
            CmmScanner.Token from = full.get(i);
            CmmScanner restarted = new CmmScanner(source, from.start(), source.length(), from.state());
            List<CmmScanner.Token> tail = new ArrayList<>();
            for (CmmScanner.Token t; (t = restarted.next()) != null;) tail.add(t);
            check(full.subList(i, full.size()).equals(tail), "Incremental restart differs at " + from.start() + " in " + source);
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
