package io.github.balconyjh.trace32.cmm;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Type through the real editor pipeline and wait for the platform's completion popup. */
public final class CmmAutoPopupTest extends CompletionAutoPopupTestCase {
    private final TempDirTestFixture physicalTempDir = new TempDirTestFixtureImpl();

    @Override
    protected TempDirTestFixture createTempDirTestFixture() {
        return physicalTempDir;
    }

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        // Install the physical root before project startup activities can inspect module roots.
        return new LightProjectDescriptor() {
            @Override
            public VirtualFile createDirForSources(Module module) {
                try {
                    return physicalTempDir.findOrCreateDir("");
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
        };
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        EdtTestUtil.runInEdtAndWait(() -> {
            var roots = ModuleRootManager.getInstance(getModule()).getContentRoots();
            assertEquals(1, roots.length);
            assertEquals(myFixture.getTempDirFixture().getFile(""), roots[0]);
            assertEquals("file", roots[0].getFileSystem().getProtocol());
            CmmSettings.getInstance(getProject()).loadState(new CmmSettings.Data());
        });
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
