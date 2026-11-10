package io.github.balconyjh.trace32.cmm.navigation

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import io.github.balconyjh.trace32.cmm.CmmFileType

class CmmNavigationIndex : FileBasedIndexExtension<String, List<CmmNavigationData.Entry>>() {
    override fun getName() = NAME
    override fun getVersion() = 1
    override fun getKeyDescriptor() = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer() = CmmNavigationData.EXTERNALIZER
    override fun getInputFilter() = FileBasedIndex.InputFilter { it.fileType == CmmFileType.INSTANCE }
    override fun dependsOnFileContent() = true
    override fun getIndexer() = DataIndexer<String, List<CmmNavigationData.Entry>, FileContent> { content ->
        ProgressManager.checkCanceled()
        CmmNavigationData.indexText(content.contentAsText.toString()).also { ProgressManager.checkCanceled() }
    }
    companion object {
        @JvmField val NAME: ID<String, List<CmmNavigationData.Entry>> = ID.create("io.github.balconyjh.trace32.cmm.navigation")
    }
}
