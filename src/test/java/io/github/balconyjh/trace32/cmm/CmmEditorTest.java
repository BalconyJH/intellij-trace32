package io.github.balconyjh.trace32.cmm;

import com.intellij.psi.tree.IElementType;
import io.github.balconyjh.trace32.cmm.core.CmmScanner;
import java.util.ArrayList;
import java.util.List;

/** Editing regressions that do not require a live IntelliJ application. */
public final class CmmEditorTest {
    private static int checks;
    private record Token(IElementType type, int start, int end, int state) { }
    public static void main(String[] args) {
        String source = "PRIVATE &name\nPRINT \"Hello &(name) and &name\" // comment\nPRINT `symbol`\n";
        List<Token> tokens = lex(source, 0, 0);
        check(tokens.stream().filter(t -> t.type() == CmmTypes.of(CmmScanner.Kind.MACRO)).count() == 3,
                "macros are individually colored inside quoted strings");
        check(tokens.stream().anyMatch(t -> source.substring(t.start(), t.end()).equals("&(name)")), "parenthesized macro span preserved");
        int cursor = 0;
        for (Token token : tokens) {
            check(cursor == token.start(), "highlight tokens losslessly cover input");
            cursor = token.end();
            check(lex(source, token.start(), token.state()).equals(tokens.subList(tokens.indexOf(token), tokens.size())),
                    "highlight restarts reconstruct quote context");
        }
        check(cursor == source.length(), "highlight covers final newline");
        String quotes = "PRINT \"Hello &name\"";
        check(CmmQuoteHandler.quoteRole(quotes, 6) == 1, "opening quote classification");
        check(CmmQuoteHandler.quoteRole(quotes, quotes.length() - 1) == -1, "closing quote after macro is not mistaken for a new literal");
        check(CmmQuoteHandler.quoteRole("PRINT \"a\"\"b\"", 8) == 0, "doubled quote is an escape");
        check(CmmQuoteHandler.quoteRole("PRINT \"a\"\"", 9) == 0, "trailing doubled quote does not close a literal");
        check(CmmScanner.scan(source).stream().filter(t -> t.kind() == CmmScanner.Kind.STRING).anyMatch(t -> source.substring(t.start(), t.end()).contains("&(name)")),
                "parser string tokens remain whole");
        check(CmmDocumentation.signature("DO").contains("<script>"), "script signature");
        check(CmmDocumentation.signature("SYS.CPU").contains("<cpu>"), "official alias documentation");
        check(CmmDocumentation.html("PRIVATE").contains("&lt;macro&gt;"), "documentation escapes signature HTML");
        check(CmmDocumentation.html("Vendor.Unknown") == null, "unknown command not invented");
        check(CmmDocumentation.options("D.Load.E").contains("/NoCODE"), "options use validated official aliases");
        check(CmmAnnotator.inspect("PRIVATE &value\nIF &value\n(\n PRINT \"ok\"\n)\n").isEmpty(), "valid macro block has no diagnostic");
        check(CmmAnnotator.inspect("PRINT \"unterminated\n").stream().anyMatch(i -> i.getMessage().contains("Unterminated")), "unterminated string diagnostic");
        check(CmmAnnotator.inspect("PRINT \"a\"\"b\"\n").isEmpty(), "doubled quote is not an unterminated literal");
        check(CmmAnnotator.inspect("PRINT unknown()\nVendor.Command &external\n").isEmpty(), "unknown runtime constructs remain accepted");
        check(CmmAnnotator.inspect("GOTO missing\n").size() == 1, "static missing local target diagnostic");
        check(CmmAnnotator.inspect("GOTO prefix&suffix\nJUMPTO external\n").isEmpty(), "dynamic and stack targets are not falsely diagnosed");
        check(CmmAnnotator.inspect("same:\nPRINT 1\nsame:\n").stream().anyMatch(i -> i.getMessage().contains("Duplicate")), "duplicate label diagnostic");
        check(CmmAnnotator.inspect("IF TRUE()\n(\n").stream().anyMatch(i -> i.getMessage().contains("opening")), "unclosed block diagnostic");
        check(CmmAnnotator.inspect(")\n").stream().anyMatch(i -> i.getMessage().contains("closing")), "extra closing block diagnostic");
        String formatting = "IF TRUE()\n(\nPRINT \"a  b\"\n(\nDO \"my file.cmm\"\n)\n)\nlabel:\n  PRINT 1\n";
        var lines = CmmFormattingModelBuilder.lines(formatting, 4);
        check(lines.stream().map(CmmFormattingModelBuilder.Line::getSpaces).toList().equals(List.of(0, 0, 4, 4, 8, 4, 0, 0, 0)), "nested block indentation and column-zero labels");
        check(lines.get(2).getTextRange().substring(formatting).equals("PRINT \"a  b\""), "formatter line blocks preserve internal whitespace");
        String continued = "DO \\\n       \"child.cmm\"\n";
        check(CmmFormattingModelBuilder.lines(continued, 4).get(1).getSpaces() == 7, "continuation indentation preserved");
        String comments = "// one\n// two\nPRINT 1\n";
        check(CmmFormattingModelBuilder.lines(comments, 4).stream().allMatch(l -> l.getSpaces() == 0), "comment folds do not affect block indentation");
        System.out.println("PASS " + checks + " editor regression checks");
    }
    private static List<Token> lex(String text, int start, int state) {
        var lexer = new CmmHighlightingLexer();
        lexer.start(text, start, text.length(), state);
        var result = new ArrayList<Token>();
        while (lexer.getTokenType() != null) {
            result.add(new Token(lexer.getTokenType(), lexer.getTokenStart(), lexer.getTokenEnd(), lexer.getState()));
            lexer.advance();
        }
        return result;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
