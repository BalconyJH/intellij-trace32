package io.github.balconyjh.trace32.cmm;

import io.github.balconyjh.trace32.cmm.navigation.CmmNavigationData;
import java.io.*;
import java.util.List;

/** Content-index and static script resolution tests; does not require a running IDE. */
public final class CmmNavigationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        String source = "PRIVATE &name &directory\n&directory=OS.PPD()\n"
                + "IF TRUE()\n(\n PRIVATE &(name)\n)\n"
                + "SUBROUTINE initialize\n(\n RETURN\n)\n"
                + "DO \"&directory/child.cmm\"\nRUN static\nDO &runtime\nDO \"prefix&suffix.cmm\"\n"
                + "ON ERROR DO \"~~/recover.cmm\"\nfailed:\nENDDO\n";
        var data = CmmNavigationData.indexText(source);
        check(data.get("symbol:&name").size() == 2, "project index preserves scoped duplicate macro names");
        check(data.get("symbol:&name").get(0).getOffset() == source.indexOf("&name"), "symbol offsets are source offsets");
        check(data.get("symbol:&name").get(1).getValue().equals("&name"), "parenthesized macro is indexed canonically");
        check(data.get("symbol:&name").stream().allMatch(CmmNavigationData.Entry::getMacro), "macro symbol type retained");
        check(data.containsKey("symbol:initialize"), "subroutine project symbol");
        check(data.containsKey("symbol:failed"), "label project symbol");
        var calls = data.get(CmmNavigationData.CALLS_KEY);
        check(calls.size() == 3, "dynamic macro calls excluded from static graph");
        check(calls.stream().anyMatch(e -> e.getValue().equals("./child.cmm")), "known OS.PPD macro becomes static call");
        check(calls.stream().anyMatch(e -> e.getValue().equals("static")), "extensionless call preserved for resolver");
        check(calls.stream().anyMatch(e -> e.getValue().equals("~~/recover.cmm")), "configured root stays symbolic in content index");
        check(CmmNavigationData.indexText("PRINT 1\n").isEmpty(), "non-navigation file creates no index keys");
        for (var entries : data.values()) {
            var buffer = new ByteArrayOutputStream();
            CmmNavigationData.EXTERNALIZER.save(new DataOutputStream(buffer), entries);
            check(CmmNavigationData.EXTERNALIZER.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()))).equals(entries),
                    "index serialization preserves data");
        }
        var unicode = List.of(new CmmNavigationData.Entry("\u811a\u672c/" + "a".repeat(70_000) + ".cmm", 123, false));
        var buffer = new ByteArrayOutputStream();
        CmmNavigationData.EXTERNALIZER.save(new DataOutputStream(buffer), unicode);
        check(CmmNavigationData.EXTERNALIZER.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()))).equals(unicode),
                "index serialization supports Unicode and long values without modified-UTF limits");
        check(expand("child.cmm").equals("C:/project/scripts/child.cmm"), "relative script path root");
        check(expand("~~~~/child.cmm").equals("C:/project/scripts/child.cmm"), "present script directory root");
        check(expand("~~/demo/child.cmm").equals("C:/T32/demo/child.cmm"), "system root resolution");
        check(expand("~~~/generated.cmm").equals("C:/Temp/generated.cmm"), "temporary root resolution");
        check(expand("D:\\scripts\\child.cmm").equals("D:/scripts/child.cmm"), "absolute Windows path");
        check(expand("//server/share/child.cmm").equals("//server/share/child.cmm"), "UNC path retained");
        check(expand("&runtime/child.cmm") == null, "runtime macro root not guessed");
        check(expand("~/child.cmm") == null, "unknown tilde root not guessed");
        check(CmmNavigationData.expandedPath("C:/project", "~~/child.cmm", "", "") == null, "unconfigured system root remains unresolved");
        check(CmmNavigationData.expandedPath("C:/project", "~~~/child.cmm", "", "") == null, "unconfigured temporary root remains unresolved");
        check(CmmNavigationData.expandedPath("/", "child.cmm", "", "").equals("/child.cmm"), "filesystem root preserved");
        System.out.println("PASS " + checks + " navigation regression checks");
    }
    private static String expand(String path) {
        return CmmNavigationData.expandedPath("C:\\project\\scripts", path, "C:\\T32", "C:\\Temp");
    }
    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
}
