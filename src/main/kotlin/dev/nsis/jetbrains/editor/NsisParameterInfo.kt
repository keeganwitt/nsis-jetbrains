package dev.nsis.jetbrains.editor

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.project.DumbAware
import dev.nsis.jetbrains.NsisFile
import dev.nsis.jetbrains.model.NsisCommandInfo
import dev.nsis.jetbrains.psi.NsisStatement

class NsisParameterInfoHandler : ParameterInfoHandler<NsisStatement, NsisCommandInfo>, DumbAware {
    override fun isWhitespaceSensitive() = true

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): NsisStatement? {
        val file = context.file as? NsisFile ?: return null
        val argument = NsisArgumentContext.at(file, context.offset) ?: return null
        val info = argument.info ?: return null
        context.itemsToShow = arrayOf(info)
        return argument.line.element
    }

    override fun showParameterInfo(element: NsisStatement, context: CreateParameterInfoContext) {
        context.showHint(element, element.textOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): NsisStatement? {
        val file = context.file as? NsisFile ?: return null
        val argument = NsisArgumentContext.at(file, context.offset) ?: return null
        if (argument.info == null || argument.line.element != context.parameterOwner) return null
        return argument.line.element
    }

    override fun updateParameterInfo(element: NsisStatement, context: UpdateParameterInfoContext) {
        val file = context.file as? NsisFile
        val argument = file?.let { NsisArgumentContext.at(it, context.offset) }
        if (argument?.line?.element != element || argument.info == null || context.objectsToView.firstOrNull() != argument.info) {
            context.removeHint()
            return
        }
        context.parameterOwner = element
        context.setCurrentParameter(argument.signatureIndex)
    }

    override fun updateUI(info: NsisCommandInfo, context: ParameterInfoUIContext) {
        val index = context.currentParameterIndex
        val parts = info.signatureParts
        val start = if (index in parts.indices) info.name.length + 1 + parts.take(index).sumOf { it.length + 1 } else -1
        val end = if (start >= 0) start + parts[index].length else -1
        context.setupUIComponentPresentation(info.signature, start, end, false, false, false, context.defaultParameterColor)
    }
}
