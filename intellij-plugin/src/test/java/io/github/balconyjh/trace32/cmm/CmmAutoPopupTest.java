package io.github.balconyjh.trace32.cmm;

import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;

/** Type through the real editor pipeline and wait for the platform's completion popup. */
public final class CmmAutoPopupTest extends CompletionAutoPopupTestCase {
    private VirtualFile contentRoot;

    @Override
    protected TempDirTestFixture createTempDirTestFixture() {
        return new TempDirTestFixtureImpl();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        EdtTestUtil.runInEdtAndWait(() -> {
            contentRoot = myFixture.getTempDirFixture().findOrCreateDir("");
            PsiTestUtil.addContentRoot(getModule(), contentRoot);
            for (var root : ModuleRootManager.getInstance(getModule()).getContentRoots()) {
                if (root.getFileSystem().getProtocol().equals("temp")) PsiTestUtil.removeContentEntry(getModule(), root);
            }
            CmmSettings.getInstance(getProject()).loadState(new CmmSettings.Data());
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (contentRoot != null) {
                EdtTestUtil.runInEdtAndWait(() -> PsiTestUtil.removeContentEntry(getModule(), contentRoot));
            }
        } finally {
            super.tearDown();
        }
    }

    public void testDotOpensCommandCompletion() {
        myFixture.configureByText("command.cmm", "SYStem<caret>");
        type(".");
        assertPopupContains("SYStem.CPU");
    }

    public void testAmpersandOpensMacroCompletionInTemplates() {
        myFixture.configureByText("macro.cmmt", "PRIVATE &value\nPRINT <caret>");
        type("&");
        assertPopupContains("&value");
    }

    public void testSlashOpensScriptPathCompletion() {
        myFixture.addFileToProject("scripts/child.cmm", "ENDDO\n");
        myFixture.configureByText("path.cmm", "DO \"scripts<caret>");
        type("/");
        assertPopupContains("child.cmm");
    }

    public void testBackslashOpensScriptPathCompletion() {
        myFixture.addFileToProject("scripts/child.cmm", "ENDDO\n");
        myFixture.configureByText("path.cmm", "DO \"scripts<caret>");
        type("\\");
        assertPopupContains("child.cmm");
    }

    private void assertPopupContains(String expected) {
        EdtTestUtil.runInEdtAndWait(() -> {
            var lookup = myFixture.getLookup();
            assertNotNull("Typing should open completion without an explicit completion action", lookup);
            assertTrue("Missing completion variant: " + expected,
                    lookup.getItems().stream().anyMatch(item -> item.getLookupString().equals(expected)));
        });
    }
}
