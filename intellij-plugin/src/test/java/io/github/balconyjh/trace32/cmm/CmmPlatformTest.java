package io.github.balconyjh.trace32.cmm;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext;
import com.intellij.util.indexing.FindSymbolParameters;
import io.github.balconyjh.trace32.cmm.core.CmmScanner;
import io.github.balconyjh.trace32.cmm.lsp.CmmLspClientDescriptor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Real platform fixtures exercise plugin registration, editors, PSI edits, and file indexes. */
public final class CmmPlatformTest extends BasePlatformTestCase {
    private final TempDirTestFixture physicalTempDir = new TempDirTestFixtureImpl();
    private boolean previousAutocomplete;

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
        var roots = ModuleRootManager.getInstance(getModule()).getContentRoots();
        assertEquals(1, roots.length);
        assertEquals(myFixture.getTempDirFixture().getFile(""), roots[0]);
        assertEquals("file", roots[0].getFileSystem().getProtocol());
        previousAutocomplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
        CmmSettings.getInstance(getProject()).loadState(new CmmSettings.Data());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = previousAutocomplete;
            CmmSettings.getInstance(getProject()).loadState(new CmmSettings.Data());
        } finally {
            super.tearDown();
        }
    }

    public void testDescriptorAndFileAssociations() {
        var plugin = PluginManagerCore.getPlugin(PluginId.getId("io.github.balconyjh.trace32"));
        assertNotNull("The plugin descriptor must be loaded by the platform", plugin);
        assertEquals("Toolkit for Lauterbach TRACE32", plugin.getName());
        for (String name : List.of("script.cmm", "SCRIPT.CMM", "template.cmmt", "TEMPLATE.CMMT")) {
            assertSame(name, CmmFileType.INSTANCE, FileTypeRegistry.getInstance().getFileTypeByFileName(name));
        }
        assertEquals("//", LanguageCommenters.INSTANCE.forLanguage(CmmLanguage.INSTANCE).getLineCommentPrefix());
    }

    public void testRegisteredHighlightingAndDiagnostics() {
        var file = configure("PRIVATE &value\nPRINT \"&value\"\nGOTO <caret>missing\n");
        var highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(CmmLanguage.INSTANCE, getProject(), file.getVirtualFile());
        assertInstanceOf(highlighter, CmmSyntaxHighlighter.class);
        for (var kind : List.of(CmmScanner.Kind.COMMENT, CmmScanner.Kind.MACRO, CmmScanner.Kind.STRING,
                CmmScanner.Kind.COMMAND, CmmScanner.Kind.KEYWORD, CmmScanner.Kind.LABEL)) {
            assertTrue(kind.name(), highlighter.getTokenHighlights(CmmTypes.of(kind)).length > 0);
        }
        assertTrue("The registered annotator should report the unresolved local target",
                myFixture.doHighlighting(HighlightSeverity.WARNING).stream()
                        .anyMatch(info -> info.getDescription() != null && info.getDescription().contains("missing")));
    }

    public void testLosslessPsiAndLocalReferences() {
        String text = "PRIVATE &directory &value\n&directory=OS.PPD()\n&value=1\n"
                + "ON ERROR GOTO failed\nIF &value\n(\n PRINT \"&(value) &value\"\n)\n"
                + "DO \"&directory/child.cmm\"\nfailed:\nENDDO\n";
        myFixture.addFileToProject("child.cmm", "ENDDO\n");
        CmmFile file = configure(text);
        assertEquals(text, file.getText());
        assertReference(file, text.indexOf("GOTO failed") + 5, text.indexOf("failed:"));
        assertReference(file, text.indexOf("IF &value") + 4, text.indexOf("&value"));
        assertReference(file, text.indexOf("&(value)") + 2, text.indexOf("&value"));
        PsiReference reference = file.findReferenceAt(text.indexOf("child.cmm") + 2);
        assertNotNull(reference);
        assertEquals("child.cmm", assertInstanceOf(reference.resolve(), PsiFile.class).getName());
    }

    public void testCommandAliasesAndCaseInsensitiveCompletion() {
        assertCompletion("SYStem.C<caret>", "SYStem.CPU", true);
        assertCompletion("system.c<caret>", "SYStem.CPU", true);
        assertCompletion("SYS.C<caret>", "SYStem.CPU", true);
        assertCompletion("<caret>\n", "SYStem.CPU", true);
    }

    public void testAutoPopupHandlerLeavesOtherInputAndLanguagesToThePlatform() {
        var handler = TypedHandlerDelegate.EP_NAME.getExtensionList().stream()
                .filter(CmmAutoPopupHandler.class::isInstance).findFirst().orElseThrow();
        CmmFile practice = configure("PRINT <caret>");
        for (char typed : new char[]{'a', ' ', ':', '('}) {
            assertSame(TypedHandlerDelegate.Result.CONTINUE,
                    handler.checkAutoPopup(typed, getProject(), myFixture.getEditor(), practice));
        }
        PsiFile plainText = myFixture.configureByText("plain.txt", "text<caret>");
        for (char typed : new char[]{'.', '&', '/', '\\'}) {
            assertSame(TypedHandlerDelegate.Result.CONTINUE,
                    handler.checkAutoPopup(typed, getProject(), myFixture.getEditor(), plainText));
        }
    }

    public void testMacroAndLabelCompletionContexts() {
        assertCompletion("PRIVATE &value\nPRINT &va<caret>", "&value", true);
        assertCompletion("PRIVATE &value\nPRINT \"&va<caret>", "&value", true);
        assertCompletion("PRIVATE &value\nPRINT &(va<caret>", "value", true);
        assertCompletion("PRIVATE &Value\nPRINT &va<caret>", "&Value", false);
        assertCompletion("PRIVATE &Value\nPRINT &Va<caret>", "&Value", true);
        assertCompletion("failed:\nON ERROR GOTO fa<caret>", "failed", true);
        assertCompletion("Failed:\nGOTO fa<caret>", "Failed", false);
        assertCompletion("Failed:\nGOTO Fa<caret>", "Failed", true);
        assertCompletion("IF TRUE()\n(\n PRIVATE &inner\n)\nPRINT &in<caret>", "&inner", false);
        assertCompletion("PRIVATE &first \\\n &second\nPRINT &se<caret>", "&second", true);
    }

    public void testCompletionExcludesCommentsAndUnrelatedStrings() {
        assertCompletion("// SYStem.C<caret>", "SYStem.CPU", false);
        assertCompletion("PRINT \"SYStem.C<caret>", "SYStem.CPU", false);
        myFixture.addFileToProject("child.cmm", "ENDDO\n");
        assertCompletion("PRINT DO chi<caret>", "child.cmm", false);
        assertCompletion("PRINT \"please DO chi<caret>", "child.cmm", false);
    }

    public void testOptionsAndScriptPathCompletion() {
        myFixture.addFileToProject("child.cmm", "ENDDO\n");
        assertCompletion("Data.Load.Elf file.elf /NoC<caret>", "/NoCODE", true);
        assertCompletion("DO \"chi<caret>", "child.cmm", true);
        assertCompletion("ON ERROR DO chi<caret>", "child.cmm", true);
        assertCompletion("DO \\\n  chi<caret>", "child.cmm", true);
    }

    public void testSpacedPathCompletionInsertsQuotesInRealEditor() {
        myFixture.addFileToProject("file name.cmm", "ENDDO\n");
        assertCompletion("DO fi<caret>", "file name.cmm", true);
        var lookup = myFixture.getLookup();
        assertNotNull(lookup);
        var item = lookup.getItems().stream()
                .filter(candidate -> candidate.getLookupString().equals("file name.cmm")).findFirst().orElseThrow();
        lookup.setCurrentItem(item);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        myFixture.checkResult("DO \"file name.cmm\"<caret>");
    }

    public void testFoldingAndStructureProviders() {
        CmmFile file = configure("PRIVATE &value\nIF TRUE()\n(\n PRINT &value\n)\ndone:\nENDDO\n");
        var folding = LanguageFolding.INSTANCE.forLanguage(CmmLanguage.INSTANCE);
        assertNotNull(folding);
        assertEquals(1, folding.buildFoldRegions(file.getNode(), myFixture.getEditor().getDocument()).length);
        assertEquals(2, new CmmStructureViewFactory.Node(file, null).getChildren().length);
        assertNotNull(new CmmStructureViewFactory().getStructureViewBuilder(file));
    }

    public void testRegisteredFormatterPreservesStatementContents() {
        CmmFile file = configure("IF TRUE()\n(\n\nPRINT \"a  b\"\n)\n\nlabel:\n  PRINT 1\n");
        WriteCommandAction.runWriteCommandAction(getProject(),
                () -> { CodeStyleManager.getInstance(getProject()).reformat(file); });
        assertEquals("IF TRUE()\n(\n\n    PRINT \"a  b\"\n)\n\nlabel:\nPRINT 1\n", file.getText());
    }

    public void testRegisteredLineCommentActionToggles() {
        CmmFile file = configure("<caret>PRINT 1\n");
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertTrue(file.getText().startsWith("//"));
        myFixture.getEditor().getCaretModel().moveToOffset(0);
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEquals("PRINT 1\n", file.getText());
    }

    public void testParameterHintsUseEditorCaretAndPsi() {
        configure("PRINT STRing.MID(\"a,b\", 1., <caret>");
        var handler = new CmmParameterInfoHandler();
        var create = new MockCreateParameterInfoContext(myFixture.getEditor(), myFixture.getFile());
        PsiElement owner = handler.findElementForParameterInfo(create);
        assertNotNull(owner);
        assertEquals("STRing.MID", owner.getText());
        assertEquals(1, create.getItemsToShow().length);
        var update = new MockUpdateParameterInfoContext(myFixture.getEditor(), myFixture.getFile(), create.getItemsToShow());
        update.setParameterOwner(owner);
        handler.updateParameterInfo(owner, update);
        assertEquals(2, update.getCurrentParameter());
    }

    public void testRegisteredFindUsagesIncludesBothQuotedMacroForms() {
        CmmFile file = configure("PRIVATE &name\nPRINT \"&(name) &name\"\n");
        PsiElement declaration = declaration(file, "&name");
        assertTrue(new CmmFindUsagesProvider().canFindUsagesFor(declaration));
        assertEquals(2, ReferencesSearch.search(declaration).findAll().size());
        assertEquals(2, myFixture.findUsages(declaration).size());
    }

    public void testPlatformRenameUpdatesRepeatedMacrosAndSupportsUndo() {
        String original = "PRIVATE &old\n&old=1.\nPRINT \"&old &(old)\" &old\n";
        CmmFile file = configure(original);
        myFixture.renameElement(declaration(file, "&old"), "longerName");
        String renamed = "PRIVATE &longerName\n&longerName=1.\nPRINT \"&longerName &(longerName)\" &longerName\n";
        assertEquals(renamed, file.getText());
        assertReference(file, renamed.indexOf("&(longerName)") + 2, renamed.indexOf("&longerName"));
        myFixture.performEditorAction(IdeActions.ACTION_UNDO);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEquals(original, file.getText());
        myFixture.performEditorAction(IdeActions.ACTION_REDO);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEquals(renamed, file.getText());
    }

    public void testPlatformRenameRejectsCollisionWithoutMutation() {
        String original = "PRIVATE &first &second\nPRINT &first &second\n";
        CmmFile file = configure(original);
        org.junit.Assert.assertThrows(BaseRefactoringProcessor.ConflictsInTestsException.class,
                () -> myFixture.renameElement(declaration(file, "&first"), "second"));
        assertEquals(original, file.getText());
    }

    public void testPlatformRenamePreservesLabelSyntaxAndForwardReferences() {
        CmmFile file = configure("GOTO before\nbefore:\nENDDO\n");
        myFixture.renameElement(declaration(file, "before:"), "after");
        assertEquals("GOTO after\nafter:\nENDDO\n", file.getText());
        assertReference(file, file.getText().indexOf("after"), file.getText().indexOf("after:"));
    }

    public void testConfiguredScriptRootsAndCompletion() {
        var child = myFixture.addFileToProject("sdk/child.cmm", "ENDDO\n");
        var settings = CmmSettings.getInstance(getProject());
        settings.setSystemDirectory(child.getVirtualFile().getParent().getPath());
        settings.setTemporaryDirectory(child.getVirtualFile().getParent().getPath());
        CmmFile file = configure("DO \"~~/child.cmm\"\n");
        assertEquals(child.getVirtualFile(), CmmReferences.resolvePath(file, "~~/child.cmm").getVirtualFile());
        assertEquals(child.getVirtualFile(), CmmReferences.resolvePath(file, "~~~/child.cmm").getVirtualFile());
        myFixture.addFileToProject("local.cmm", "ENDDO\n");
        assertEquals("local.cmm", CmmReferences.resolvePath(file, "~~~~/local.cmm").getName());
        assertCompletion("DO \"~~/chi<caret>", "child.cmm", true);
    }

    public void testNavigateSymbolReadsRealFileIndex() {
        var file = myFixture.addFileToProject("symbols.cmm", "PRIVATE &indexed\nentry:\nENDDO\n");
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        var contributor = new CmmSymbolContributor();
        var scope = GlobalSearchScope.projectScope(getProject());
        var names = new HashSet<String>();
        contributor.processNames(name -> { names.add(name); return true; }, scope, null);
        assertTrue(names.contains("&indexed"));
        assertTrue(names.contains("entry"));
        var items = new ArrayList<NavigationItem>();
        contributor.processElementsWithName("&indexed", item -> { items.add(item); return true; },
                FindSymbolParameters.wrap("&indexed", scope));
        assertEquals(1, items.size());
        assertEquals("&indexed", items.getFirst().getName());
        assertTrue(items.getFirst().canNavigate());
        assertTrue(items.getFirst().getPresentation().getLocationString().contains(file.getName()));

        myFixture.addFileToProject("more-symbols.cmm", "PRIVATE &indexed\nENDDO\n");
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        items.clear();
        contributor.processElementsWithName("&indexed", item -> { items.add(item); return true; },
                FindSymbolParameters.wrap("&indexed", scope));
        assertEquals(2, items.size());
        items.clear();
        contributor.processElementsWithName("&indexed", item -> { items.add(item); return false; },
                FindSymbolParameters.wrap("&indexed", scope));
        assertEquals("Navigate Symbol must stop when its consumer rejects further results", 1, items.size());
    }

    public void testCallHierarchyUsesRealIndexAndHonorsScope() {
        CmmFile child = (CmmFile) myFixture.addFileToProject("child.cmm", "DO \"parent.cmm\"\nENDDO\n");
        CmmFile parent = (CmmFile) myFixture.addFileToProject("parent.cmm", "DO \"child.cmm\"\nDO &runtime\nENDDO\n");
        CmmFile other = (CmmFile) myFixture.addFileToProject("other.cmm", "ON ERROR DO \"child.cmm\"\nENDDO\n");
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        var callees = CmmCallHierarchyQueries.callees(parent);
        assertEquals(1, callees.size());
        assertEquals(child.getVirtualFile(), callees.getFirst().getFile());
        assertEquals(parent.getVirtualFile(), CmmCallHierarchyQueries.callees(child).getFirst().getFile());
        var callers = CmmCallHierarchyQueries.callers(child);
        assertEquals(2, callers.size());
        assertEquals(1, CmmCallHierarchyQueries.callers(child,
                GlobalSearchScope.fileScope(getProject(), other.getVirtualFile())).size());
        assertEquals(parent.getVirtualFile(), CmmCallHierarchyQueries.callers(child,
                GlobalSearchScope.fileScope(getProject(), parent.getVirtualFile())).getFirst().getFile());
    }

    public void testSettingsAndLspCommandDescriptor() {
        var settings = CmmSettings.getInstance(getProject());
        assertFalse(settings.getLspEnabled());
        var file = configure("ENDDO\n");
        String directory = file.getVirtualFile().getParent().getPath();
        settings.setServerExecutable(directory + "/server with spaces/t32ls.exe");
        settings.setSystemDirectory(directory + "/system");
        settings.setTemporaryDirectory(directory + "/temporary");
        var descriptor = new CmmLspClientDescriptor(getProject());
        var command = descriptor.createCommandLine();
        assertEquals(settings.getServerExecutable(), command.getExePath());
        assertTrue(command.getParametersList().getList().contains("--t32SystemDir=" + settings.getSystemDirectory()));
        assertTrue(command.getParametersList().getList().contains("--t32TempDir=" + settings.getTemporaryDirectory()));
        assertEquals("practice", descriptor.getLanguageId(file.getVirtualFile()));
        assertTrue(descriptor.isSupportedFile(new LightVirtualFile("script.CMMT")));
        assertFalse(descriptor.isSupportedFile(new LightVirtualFile("unrelated.txt")));
        var restored = new CmmSettings();
        restored.loadState(settings.getState());
        assertEquals(settings.getState(), restored.getState());
        settings.setSystemDirectory("changed");
        assertFalse(settings.getSystemDirectory().equals(restored.getSystemDirectory()));
    }

    public void testLiveTemplatesArePackaged() throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var stream = CmmFile.class.getResourceAsStream("/liveTemplates/CMM.xml")) {
            assertNotNull(stream);
            assertEquals(6, factory.newDocumentBuilder().parse(stream).getElementsByTagName("template").getLength());
        }
    }

    private CmmFile configure(String text) {
        return assertInstanceOf(myFixture.configureByText("test.cmm", text), CmmFile.class);
    }

    private void assertCompletion(String text, String expected, boolean present) {
        configure(text);
        myFixture.complete(CompletionType.BASIC, 2);
        List<String> variants = myFixture.getLookupElementStrings();
        assertEquals("Completion variants for " + text + ": " + variants,
                present, variants != null && variants.contains(expected));
    }

    private static PsiElement declaration(CmmFile file, String text) {
        var leaf = file.findElementAt(file.getText().indexOf(text));
        assertNotNull(leaf);
        return assertInstanceOf(leaf.getParent(), CmmParserDefinition.Atom.class);
    }

    private static void assertReference(CmmFile file, int offset, int declarationOffset) {
        PsiReference reference = file.findReferenceAt(offset);
        assertNotNull(reference);
        PsiElement resolved = reference.resolve();
        assertNotNull(resolved);
        assertEquals(declarationOffset, resolved.getTextOffset());
    }
}
