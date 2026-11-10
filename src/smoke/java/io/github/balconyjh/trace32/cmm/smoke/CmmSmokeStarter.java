package io.github.balconyjh.trace32.cmm.smoke;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import io.github.balconyjh.trace32.cmm.*;
import io.github.balconyjh.trace32.cmm.core.CmmScanner;
import java.nio.file.*;
import java.util.*;

/** IntelliJ core integration tests. Never included in the distributable plugin. */
public final class CmmSmokeStarter {
    private static final List<LookupElement> lastCompletions = new ArrayList<>();
    private final List<String> passed = new ArrayList<>();
    private CoreApplicationEnvironment environment;
    private Project project;
    public static void main(String[] args) { new CmmSmokeStarter().run(); }
    private void run() {
        Path output = Path.of(System.getProperty("cmm.smoke.output"));
        var disposable = Disposer.newDisposable();
        try {
            environment = new CoreApplicationEnvironment(disposable);
            // Initialize its low-memory listener while the mock application is alive, before disposal.
            Class.forName("com.intellij.psi.impl.smartPointers.SmartPointerTracker");
            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                    com.intellij.psi.impl.source.tree.TreeCopyHandler.EP_NAME, com.intellij.psi.impl.source.tree.TreeCopyHandler.class);
            environment.registerFileType(CmmFileType.INSTANCE, "cmm");
            environment.registerFileType(CmmFileType.INSTANCE, "CMM");
            environment.registerFileType(CmmFileType.INSTANCE, "cmmt");
            environment.registerFileType(CmmFileType.INSTANCE, "CMMT");
            environment.registerParserDefinition(new CmmParserDefinition());
            environment.addExplicitExtension(LanguageCommenters.INSTANCE, CmmLanguage.INSTANCE, new CmmCommenter());
            environment.addExplicitExtension(SyntaxHighlighterFactory.LANGUAGE_FACTORY, CmmLanguage.INSTANCE, new CmmSyntaxHighlighter.Factory());
            environment.addExplicitExtension(LanguageFolding.INSTANCE, CmmLanguage.INSTANCE, new CmmFoldingBuilder());
            var projectEnvironment = new CoreProjectEnvironment(disposable, environment);
            project = projectEnvironment.getProject();
            projectEnvironment.getProject().registerService(com.intellij.pom.tree.TreeAspect.class, new com.intellij.pom.tree.TreeAspect());
            projectEnvironment.getProject().registerService(com.intellij.pom.PomModel.class, new com.intellij.pom.core.impl.PomModelImpl(project));
            projectEnvironment.getProject().registerService(CmmSettings.class, new CmmSettings());
            projectEnvironment.getProject().registerService(com.intellij.openapi.project.BaseProjectDirectories.class,
                    new com.intellij.openapi.project.BaseProjectDirectories(project) {
                        @Override public boolean isProcessing() { return false; }
                        @Override protected Set<com.intellij.openapi.vfs.VirtualFile> getBaseDirectories() { return Set.of(); }
                        @Override public com.intellij.openapi.vfs.VirtualFile getBaseDirectoryFor(com.intellij.openapi.vfs.VirtualFile file) { return null; }
                    });
            javax.swing.SwingUtilities.invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> runChecks(output.getParent())));
            Files.writeString(output, "PASS " + passed.size() + " platform checks\n" + String.join("\n", passed) + "\n");
            Disposer.dispose(disposable);
            System.exit(0);
        } catch (Throwable error) {
            try {
                var writer = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(writer));
                Files.writeString(output, "FAIL\n" + String.join("\n", passed) + "\n" + writer);
            } catch (Exception ignored) { }
            error.printStackTrace();
            System.exit(1);
        }
    }
    private void runChecks(Path directory) {
        check(FileTypeRegistry.getInstance().getFileTypeByFileName("init.cmm") == CmmFileType.INSTANCE, "cmm association");
        check(FileTypeRegistry.getInstance().getFileTypeByFileName("INIT.CMM") == CmmFileType.INSTANCE, "CMM association");
        check(FileTypeRegistry.getInstance().getFileTypeByFileName("template.cmmt") == CmmFileType.INSTANCE, "cmmt association");
        check(LanguageCommenters.INSTANCE.forLanguage(CmmLanguage.INSTANCE).getLineCommentPrefix().equals("//"), "line commenter registration");
        var highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(CmmLanguage.INSTANCE, project, null);
        check(highlighter instanceof CmmSyntaxHighlighter, "highlighter registration");
        for (var kind : List.of(CmmScanner.Kind.COMMENT, CmmScanner.Kind.MACRO, CmmScanner.Kind.STRING,
                CmmScanner.Kind.COMMAND, CmmScanner.Kind.KEYWORD, CmmScanner.Kind.LABEL))
            check(highlighter.getTokenHighlights(CmmTypes.of(kind)).length > 0, "highlight " + kind);
        String source = "PRIVATE &directory &value\n&directory=OS.PPD()\n&value=1\nON ERROR GOTO failed\nIF &value\n(\n PRINT \"&value\"\n)\nDO \"&directory/child.cmm\"\nfailed:\nENDDO\n";
        CmmFile file = fixture(project, directory.resolve("parent.cmm"), source);
        fixture(project, directory.resolve("child.cmm"), "ENDDO\n");
        check(file.getText().equals(source), "lossless PSI parser");
        assertReference(file, source.indexOf("GOTO failed") + 5, source.indexOf("failed:"), "label navigation");
        assertReference(file, source.indexOf("IF &value") + 3, source.indexOf("&value"), "macro navigation");
        assertReference(file, source.indexOf("PRINT \"&value") + 7, source.indexOf("&value"), "macro navigation inside string");
        PsiReference script = file.findReferenceAt(source.indexOf("child.cmm") + 2);
        check(script != null && script.resolve() instanceof PsiFile target && target.getName().equals("child.cmm"), "OS.PPD script navigation");
        var document = new DocumentImpl(source);
        var folding = LanguageFolding.INSTANCE.forLanguage(CmmLanguage.INSTANCE);
        check(folding != null && folding.buildFoldRegions(file.getNode(), document).length == 1, "block folding registration");
        complete(project, directory, "SYStem.C", "SYStem.CPU", true, "dotted command completion");
        complete(project, directory, "system.c", "SYStem.CPU", true, "case insensitive command completion");
        complete(project, directory, "PRIVATE &value\nPRINT &va", "&value", true, "macro completion");
        complete(project, directory, "PRIVATE &Value\nPRINT &va", "&Value", false, "case sensitive macro completion");
        complete(project, directory, "failed:\nON ERROR GOTO fa", "failed", true, "label completion");
        complete(project, directory, "// SYStem.C", "SYStem.CPU", false, "no completion in comment");
        complete(project, directory, "PRINT \"SYStem.C", "SYStem.CPU", false, "no commands in string");
        complete(project, directory, "PRIVATE &value\nPRINT \"&va", "&value", true, "macro completion in string");
        completeAt(project, directory, "\n", 0, "SYStem.CPU", true, "completion at start of blank first line");
        complete(project, directory, "IF TRUE()\n(\n PRIVATE &inner\n)\nPRINT &in", "&inner", false, "inner macro not suggested outside its block");
        complete(project, directory, "PRIVATE &first \\\n &second\nPRINT &se", "&second", true, "continued declaration completion");
        runFeatureChecks(directory);
    }
    private void runFeatureChecks(Path directory) {
        complete(project, directory, "SYS.C", "SYStem.CPU", true, "official command alias completion");
        complete(project, directory, "PRIVATE &value\nPRINT &(va", "value", true, "parenthesized macro completion");
        complete(project, directory, "Data.Load.Elf file.elf /NoC", "/NoCODE", true, "command option completion");
        complete(project, directory, "DO \"chi", "child.cmm", true, "quoted script path completion");
        complete(project, directory, "PRINT DO chi", "child.cmm", false, "no script paths in PRINT arguments");
        complete(project, directory, "PRINT \"please DO chi", "child.cmm", false, "no script paths in arbitrary string text");
        complete(project, directory, "ON ERROR DO chi", "child.cmm", true, "event script path completion");
        complete(project, directory, "DO \\\n  chi", "child.cmm", true, "continued script path completion");
        spacedPathInsertion(directory);
        String source = "PRIVATE &name\nPRINT \"&(name) &name\"\nGOTO done\ndone:\nENDDO\n";
        CmmFile file = fixture(project, directory.resolve("features.cmm"), source);
        int definition = source.indexOf("&name");
        assertReference(file, source.indexOf("&(name)") + 2, definition, "parenthesized string macro navigation");
        var atom = (CmmParserDefinition.Atom) file.findElementAt(definition).getParent();
        check(atom.getName().equals("&name"), "PSI named declaration");
        check(new CmmFindUsagesProvider().canFindUsagesFor(atom), "find usages declaration support");
        var usages = new ArrayList<PsiReference>();
        new CmmReferenceSearch().execute(new com.intellij.psi.search.searches.ReferencesSearch.SearchParameters(
                atom, new com.intellij.psi.search.LocalSearchScope(file), true), reference -> { usages.add(reference); return true; });
        check(usages.size() == 2, "find usages includes both quoted macro forms");
        var root = new CmmStructureViewFactory.Node(file, null);
        check(root.getChildren().length == 2, "structure view contains macro and label");
        check(new CmmStructureViewFactory().getStructureViewBuilder(file) != null, "structure provider accepts CMM");
        check(CmmDocumentation.html("SYS.CPU").contains("&lt;cpu&gt;"), "quick documentation resolves official aliases");
        check(new CmmNamesValidator().isIdentifier("&value", project), "rename identifier validation");
        check(!new CmmNamesValidator().isIdentifier("value name", project), "rename rejects invalid identifiers");
        var settings = CmmSettings.getInstance(project);
        check(!settings.getLspEnabled(), "LSP defaults disabled for unconfigured projects");
        settings.setSystemDirectory(directory.toString());
        settings.setTemporaryDirectory(directory.toString());
        check(CmmReferences.resolvePath(file, "~~/child.cmm").getName().equals("child.cmm"), "configured system root navigation");
        check(CmmReferences.resolvePath(file, "~~~/child.cmm").getName().equals("child.cmm"), "configured temporary root navigation");
        check(CmmReferences.resolvePath(file, "~~~~/child.cmm").getName().equals("child.cmm"), "script root navigation");
        complete(project, directory, "DO \"~~/chi", "child.cmm", true, "configured root script completion");
        CmmFile caller = fixture(project, directory.resolve("caller.cmm"), "DO \"~~/child.cmm\"\nDO &runtime\n");
        var callees = CmmCallHierarchyQueries.callees(caller);
        check(callees.size() == 1 && callees.getFirst().getFile().getName().equals("child.cmm"), "script hierarchy resolves configured static calls only");
        parameterHints(directory);
        settings.setServerExecutable(directory.resolve("server with spaces/t32ls.exe").toString());
        var descriptor = new io.github.balconyjh.trace32.cmm.lsp.CmmLspClientDescriptor(project);
        var command = descriptor.createCommandLine();
        check(command.getExePath().equals(settings.getServerExecutable()), "LSP executable path preserved without shell parsing");
        check(command.getParametersList().getList().contains("--t32SystemDir=" + settings.getSystemDirectory()), "LSP system directory argument");
        check(command.getParametersList().getList().contains("--t32TempDir=" + settings.getTemporaryDirectory()), "LSP temporary directory argument");
        check(descriptor.getLanguageId(file.getVirtualFile()).equals("practice"), "LSP language identifier");
        check(descriptor.isSupportedFile(new com.intellij.testFramework.LightVirtualFile("template.CMMT")), "LSP supports uppercase template extension");
        check(!descriptor.isSupportedFile(new com.intellij.testFramework.LightVirtualFile("other.txt")), "LSP excludes unrelated file types");
        var restored = new CmmSettings();
        restored.loadState(settings.getState());
        check(restored.getServerExecutable().equals(settings.getServerExecutable()) && restored.getSystemDirectory().equals(settings.getSystemDirectory()), "settings persistence roundtrip");
        settings.setSystemDirectory(""); settings.setTemporaryDirectory(""); settings.setServerExecutable("");
        CmmFile renameFile = (CmmFile) PsiFileFactory.getInstance(project).createFileFromText(
                "rename.cmm", CmmFileType.INSTANCE, "PRIVATE &old\nPRINT \"&(old)\"\n");
        renameFile.findReferenceAt(renameFile.getText().indexOf("&(old)") + 2).handleElementRename("newName");
        check(renameFile.getText().contains("\"&(newName)\""), "PSI rename preserves parenthesized string macro");
        ((CmmParserDefinition.Atom) renameFile.findElementAt(renameFile.getText().indexOf("&old")).getParent()).setName("newName");
        check(renameFile.getText().contains("PRIVATE &newName"), "PSI declaration rename preserves ampersand");
        assertReference(renameFile, renameFile.getText().indexOf("&(newName)") + 2, renameFile.getText().indexOf("&newName"), "renamed macro remains navigable");
        CmmFile conflict = (CmmFile) PsiFileFactory.getInstance(project).createFileFromText(
                "collision.cmm", CmmFileType.INSTANCE, "PRIVATE &a &b\nPRINT &a &b\n");
        String beforeRename = conflict.getText();
        try {
            ((CmmParserDefinition.Atom) conflict.findElementAt(beforeRename.indexOf("&a")).getParent()).setName("b");
            throw new AssertionError("Rename must reject existing macro in the same scope");
        } catch (com.intellij.util.IncorrectOperationException expected) {
            check(conflict.getText().equals(beforeRename), "colliding macro rename rejected without mutation");
        }
        renameProcessorChecks();
        validateDescriptors();
    }
    private void renameProcessorChecks() {
        String source = "PRIVATE &old\n&old=1.\nPRINT \"&old &(old)\" &old\n";
        CmmFile file = (CmmFile) PsiFileFactory.getInstance(project).createFileFromText("rename-flow.cmm", CmmFileType.INSTANCE, source);
        var declaration = (CmmParserDefinition.Atom) file.findElementAt(source.indexOf("&old")).getParent();
        var processor = new CmmRenameProcessor();
        check(processor.canProcessElement(declaration), "rename processor accepts native declarations");
        var usages = io.github.balconyjh.trace32.cmm.core.CmmModel.parse(source).references().stream()
                .map(reference -> file.findReferenceAt(reference.start() + 1))
                .filter(Objects::nonNull)
                .map(reference -> new com.intellij.refactoring.util.MoveRenameUsageInfo(reference, declaration))
                .toArray(com.intellij.usageView.UsageInfo[]::new);
        check(usages.length == 4, "rename collects assignments and repeated references within and outside a string");
        processor.renameElement(declaration, "longerName", usages, null);
        check(file.getText().equals("PRIVATE &longerName\n&longerName=1.\nPRINT \"&longerName &(longerName)\" &longerName\n"),
                "rename processor updates all references and declaration: " + file.getText());
        CmmFile collision = (CmmFile) PsiFileFactory.getInstance(project).createFileFromText("rename-conflict.cmm", CmmFileType.INSTANCE, "PRIVATE &a &b\nPRINT &a\n");
        var target = collision.findElementAt(collision.getText().indexOf("&a")).getParent();
        var conflicts = new com.intellij.util.containers.MultiMap<PsiElement, String>();
        processor.findExistingNameConflicts(target, "b", conflicts);
        check(!conflicts.isEmpty(), "rename processor reports conflicts before applying usages");
        String labels = "GOTO before\nbefore:\nENDDO\n";
        CmmFile labelFile = (CmmFile) PsiFileFactory.getInstance(project).createFileFromText("rename-label.cmm", CmmFileType.INSTANCE, labels);
        var label = labelFile.findElementAt(labels.indexOf("before:")).getParent();
        PsiElement[] notified = {null};
        processor.renameElement(label, "after", new com.intellij.usageView.UsageInfo[]{
                new com.intellij.refactoring.util.MoveRenameUsageInfo(labelFile.findReferenceAt(labels.indexOf("before")), label)
        }, new com.intellij.refactoring.listeners.RefactoringElementListener() {
            @Override public void elementMoved(PsiElement element) { throw new AssertionError("Unexpected move callback"); }
            @Override public void elementRenamed(PsiElement element) { notified[0] = element; }
        });
        check(labelFile.getText().equals("GOTO after\nafter:\nENDDO\n"), "rename preserves label colon and forward branch");
        check(notified[0] != null && notified[0].isValid() && notified[0].getText().equals("after:"), "rename listener receives updated declaration");
    }
    private void spacedPathInsertion(Path directory) {
        fixture(project, directory.resolve("file name.cmm"), "ENDDO\n");
        complete(project, directory, "DO fi", "file name.cmm", true, "spaced script filename candidate");
        var chosen = lastCompletions.stream().filter(item -> item.getLookupString().equals("file name.cmm")).findFirst().orElseThrow();
        var document = new DocumentImpl("DO file name.cmm");
        int[] position = { document.getTextLength() };
        var caret = (com.intellij.openapi.editor.CaretModel) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{com.intellij.openapi.editor.CaretModel.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getOffset" -> position[0];
                    case "moveToOffset" -> { position[0] = (int) args[0]; yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var editor = (Editor) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Editor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDocument" -> document;
                    case "getProject" -> project;
                    case "getCaretModel" -> caret;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var offsets = new OffsetMap(document);
        offsets.addOffset(CompletionInitializationContext.START_OFFSET, 3);
        var context = new InsertionContext(offsets, '\n', new LookupElement[]{chosen},
                fixture(project, directory.resolve("insertion.cmm"), "DO fi"), editor, false);
        com.intellij.openapi.command.CommandProcessor.getInstance().runUndoTransparentAction(() -> chosen.handleInsert(context));
        check(document.getText().equals("DO \"file name.cmm\""), "spaced filename completion inserts quotes");
        check(position[0] == document.getTextLength() && context.getTailOffset() == position[0], "path completion caret remains within document");
        offsets.dispose();
    }
    private void parameterHints(Path directory) {
        String text = "PRINT STRing.MID(\"a,b\", 1., ";
        CmmFile file = fixture(project, directory.resolve("parameters.cmm"), text);
        Object[][] items = { null };
        var create = (com.intellij.lang.parameterInfo.CreateParameterInfoContext) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.intellij.lang.parameterInfo.CreateParameterInfoContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFile" -> file;
                    case "getOffset" -> text.length();
                    case "setItemsToShow" -> { items[0] = (Object[]) args[0]; yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var handler = new CmmParameterInfoHandler();
        var owner = handler.findElementForParameterInfo(create);
        check(owner != null && owner.getText().equals("STRing.MID") && items[0].length == 1, "function parameter hint selects nested function owner");
        int[] parameter = { -1 };
        var update = (com.intellij.lang.parameterInfo.UpdateParameterInfoContext) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.intellij.lang.parameterInfo.UpdateParameterInfoContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFile" -> file;
                    case "getOffset" -> text.length();
                    case "getParameterOwner" -> owner;
                    case "setCurrentParameter" -> { parameter[0] = (int) args[0]; yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        handler.updateParameterInfo(owner, update);
        check(parameter[0] == 2, "parameter hint ignores commas in quoted arguments");
    }
    private void validateDescriptors() {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            int checked = 0;
            for (String resource : List.of("META-INF/plugin.xml", "META-INF/cmm-lsp.xml")) {
                try (var stream = CmmFile.class.getClassLoader().getResourceAsStream(resource)) {
                    if (stream == null) throw new AssertionError("Missing plugin descriptor " + resource);
                    var elements = factory.newDocumentBuilder().parse(stream).getElementsByTagName("*");
                    for (int i = 0; i < elements.getLength(); i++) {
                        var element = (org.w3c.dom.Element) elements.item(i);
                        for (String attribute : List.of("implementation", "implementationClass", "instance", "class")) {
                            String className = element.getAttribute(attribute);
                            if (!className.startsWith("io.github.balconyjh.trace32.cmm.")) continue;
                            Class.forName(className, false, CmmFile.class.getClassLoader()).getDeclaredConstructors();
                            checked++;
                        }
                    }
                }
            }
            check(checked >= 22, "all plugin extension classes load (" + checked + ")");
            try (var stream = CmmFile.class.getClassLoader().getResourceAsStream("liveTemplates/CMM.xml")) {
                check(factory.newDocumentBuilder().parse(stream).getElementsByTagName("template").getLength() == 6, "six packaged live templates");
            }
        } catch (Exception e) { throw new RuntimeException("Plugin descriptor validation", e); }
    }
    private CmmFile fixture(Project project, Path path, String text) {
        try { Files.writeString(path, text); } catch (Exception e) { throw new RuntimeException(e); }
        var vf = environment.getLocalFileSystem().findFileByNioFile(path);
        if (vf == null) throw new AssertionError("VFS file missing: " + path);
        return (CmmFile) PsiManager.getInstance(project).findFile(vf);
    }
    private void assertReference(CmmFile file, int offset, int targetOffset, String name) {
        PsiReference ref = file.findReferenceAt(offset);
        PsiElement target = ref == null ? null : ref.resolve();
        check(target != null && target.getTextOffset() == targetOffset, name);
    }
    private void complete(Project project, Path dir, String text, String expected, boolean present, String name) {
        completeAt(project, dir, text, text.length(), expected, present, name);
    }
    private void completeAt(Project project, Path dir, String text, int offset, String expected, boolean present, String name) {
        CmmFile file = fixture(project, dir.resolve("complete-" + passed.size() + ".cmm"), text);
        var document = new DocumentImpl(text);
        Editor editor = (Editor) java.lang.reflect.Proxy.newProxyInstance(Editor.class.getClassLoader(), new Class<?>[]{Editor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDocument" -> document;
                    case "getProject" -> project;
                    case "toString" -> "CMM test editor";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var parameters = new CompletionParameters(file.findElementAt(Math.max(0, offset - 1)), file,
                CompletionType.BASIC, offset, 1, editor, () -> false);
        var contributor = new CmmCompletionContributor();
        Set<String> variants = new HashSet<>();
        lastCompletions.clear();
        contributor.fillCompletionVariants(parameters, new Results("", false, variants, contributor));
        check(variants.contains(expected) == present, name + " (" + variants.size() + " candidates)");
    }
    private static final class Results extends CompletionResultSet {
        private final String prefix;
        private final boolean insensitive;
        private final Set<String> variants;
        Results(String prefix, boolean insensitive, Set<String> variants, CompletionContributor contributor) {
            super(new PlainPrefixMatcher(prefix), item -> {}, contributor);
            this.prefix = prefix; this.insensitive = insensitive; this.variants = variants;
        }
        @Override public void addElement(LookupElement element) {
            String name = element.getLookupString();
            if (element.getAllLookupStrings().stream().anyMatch(alias -> alias.regionMatches(insensitive, 0, prefix, 0, prefix.length()))) {
                variants.add(name); lastCompletions.add(element);
            }
        }
        @Override public CompletionResultSet withPrefixMatcher(PrefixMatcher matcher) { return withPrefixMatcher(matcher.getPrefix()); }
        @Override public CompletionResultSet withPrefixMatcher(String prefix) { return new Results(prefix, insensitive, variants, contributor); }
        @Override public CompletionResultSet caseInsensitive() { return new Results(prefix, true, variants, contributor); }
        @Override public CompletionResultSet withRelevanceSorter(CompletionSorter sorter) { return this; }
        @Override public void addLookupAdvertisement(String text) { }
        @Override public void restartCompletionOnPrefixChange(com.intellij.patterns.ElementPattern<String> condition) { }
        @Override public void restartCompletionWhenNothingMatches() { }
    }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed.add(message);
    }
}
