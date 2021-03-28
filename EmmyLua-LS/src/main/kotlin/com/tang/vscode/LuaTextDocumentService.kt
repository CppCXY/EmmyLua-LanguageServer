package com.tang.vscode

import com.google.gson.JsonPrimitive
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Consumer
import com.intellij.util.Processor
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.editor.completion.CompletionService
import com.tang.intellij.lua.editor.completion.asCompletionItem
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.reference.ReferencesSearch
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.ITyFunction
import com.tang.intellij.lua.ty.findPerfectSignature
import com.tang.intellij.lua.ty.process
import com.tang.lsp.ILuaFile
import com.tang.lsp.getRangeInFile
import com.tang.lsp.nameRange
import com.tang.lsp.toRange
import com.tang.vscode.api.impl.LuaFile
import com.tang.vscode.documentation.LuaDocumentationProvider
import com.tang.vscode.utils.TargetElementUtil
import com.tang.vscode.utils.computeAsync
import com.tang.vscode.utils.getDocumentSymbols
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.TextDocumentService
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * tangzx
 * Created by Client on 2018/3/20.
 */
class LuaTextDocumentService(private val workspace: LuaWorkspaceService) : TextDocumentService {
    private var client: LuaLanguageClient? = null
    private val documentProvider = LuaDocumentationProvider()

    fun connect(client: LuaLanguageClient) {
        this.client = client
    }

    @Suppress("unused")
    @JsonRequest("emmy/annotator")
    fun updateAnnotators(ann: AnnotatorParams): CompletableFuture<List<Annotator>> {
        return computeAsync {
            val file = workspace.findFile(ann.uri) as? ILuaFile
            if (file != null)
                findAnnotators(file)
            else
                emptyList()
        }
    }

    private fun findAnnotators(file: ILuaFile): List<Annotator> {
        val params = mutableListOf<TextRange>()
        val globals = mutableListOf<TextRange>()
        val docTypeNames = mutableListOf<TextRange>()
        val upValues = mutableListOf<TextRange>()
        val notUse = mutableListOf<TextRange>()
        val paramHints = mutableListOf<RenderRange>()
        val localHints = mutableListOf<RenderRange>()

        file.psi?.acceptChildren(object : LuaRecursiveVisitor() {
            override fun visitParamNameDef(o: LuaParamNameDef) {
                if (ReferencesSearch.search(o).findFirst() != null) {
                    params.add(o.textRange)
                } else {
                    notUse.add(o.textRange)
                }
            }

            override fun visitFuncDef(o: LuaFuncDef) {
                val name = o.nameIdentifier
                if (name != null && o.forwardDeclaration == null) {
                    globals.add(name.textRange)
                }
                super.visitFuncDef(o)
            }

            override fun visitNameExpr(o: LuaNameExpr) {
                if (o.parent is LuaExprStat) // non-complete stat
                    return

                val context = SearchContext.get(o.project)
                when (resolveInFile(o.name, o, context)) {
                    is LuaParamNameDef -> params.add(o.textRange)
                    is LuaFuncDef -> globals.add(o.textRange)
                    is LuaNameDef -> {
                    } //local
                    is LuaLocalFuncDef -> {
                    } //local
                    else -> {
                        if (o.firstChild.textMatches(Constants.WORD_SELF)) {
                            // SELF
                        } else
                            globals.add(o.textRange)
                    }
                }

                if (isUpValue(o, context))
                    upValues.add(o.textRange)
            }

            override fun visitLocalDef(o: LuaLocalDef) {
                if (o.parent is LuaExprStat) // non-complete stat
                    return

                val nameList = o.nameList
                o.exprList?.exprList.let { _ ->
                    nameList?.nameDefList?.forEach {
                        it.nameRange?.let { nameRange ->
                            // 这个类型联合的名字太长对大多数情况都不是必要的，将进行必要的裁剪
                            val gussType = it.guessType(SearchContext.get(o.project))
                            val displayName = gussType.displayName
                            when {
                                displayName.startsWith("fun") -> {
                                    localHints.add(RenderRange(nameRange.toRange(file), "function"))
                                }
                                displayName.startsWith('[') -> {
                                    // ignore
                                }
                                else -> {
                                    val unexpectedNameIndex = displayName.indexOf("|[")
                                    when (unexpectedNameIndex) {
                                        -1 -> {
                                            localHints.add(RenderRange(nameRange.toRange(file), displayName))
                                        }
                                        else -> {
                                            localHints.add(
                                                RenderRange(
                                                    nameRange.toRange(file),
                                                    displayName.substring(0, unexpectedNameIndex)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            if (ReferencesSearch.search(it).findFirst() == null) {
                                notUse.add(nameRange)
                            }
                        }
                    }
                }
                o.acceptChildren(this)
            }

            override fun visitElement(element: PsiElement) {
                if (element is LuaComment) {
                    element.acceptChildren(object : LuaDocVisitor() {

                        override fun visitTagClass(o: LuaDocTagClass) {
                            val identifier = o.nameIdentifier
                            docTypeNames.add(identifier.textRange)
                            super.visitTagClass(o)
                        }

                        override fun visitClassNameRef(o: LuaDocClassNameRef) {
                            docTypeNames.add(o.textRange)
                        }

                        override fun visitElement(element: PsiElement) {
                            element.acceptChildren(this)
                        }

                        override fun visitTagAlias(o: LuaDocTagAlias) {
                            val identifier = o.nameIdentifier
                            if (identifier != null)
                                docTypeNames.add(identifier.textRange)
                            super.visitTagAlias(o)
                        }
                    })
                } else if (element is LuaCallExpr) {
                    val callExpr = element
                    var activeParameter = 0
                    var nCommas = 0
                    val literalMap = mutableMapOf<Int, Int>()
                    callExpr.args.firstChild?.let { firstChild ->
                        var child: PsiElement? = firstChild
                        while (child != null) {
                            if (child.node.elementType == LuaTypes.COMMA) {
                                activeParameter++
                                nCommas++
                            } else if (child.node.elementType == LuaTypes.LITERAL_EXPR
                                || child.node.elementType == LuaTypes.TABLE_EXPR
                                || child.node.elementType == LuaTypes.CLOSURE_EXPR
                            ) {
                                paramHints.add(RenderRange(child.textRange.toRange(file), null))
                                literalMap[activeParameter] = paramHints.size - 1;
                            }

                            child = child.nextSibling
                        }
                    }

                    callExpr.guessParentType(SearchContext.get(callExpr.project)).let { parentType ->
                        parentType.each { ty ->
                            if (ty is ITyFunction) {
                                val active = ty.findPerfectSignature(nCommas + 1)
                                ty.process(Processor { sig ->
                                    if (sig == active) {
                                        var index = 0;

                                        if (sig.colonCall && callExpr.isMethodDotCall) {
                                            literalMap[index]?.let {
                                                paramHints[it].hint = "self"
                                            }
                                            index++
                                        }
                                        var skipSelf = false
                                        sig.params.forEach { pi ->
                                            if (index == 0 && !skipSelf && !sig.colonCall && callExpr.isMethodColonCall) {
                                                skipSelf = true
                                            } else {
                                                literalMap[index]?.let {
                                                    paramHints[it].hint = pi.name
                                                }
                                                index++
                                            }
                                        }
                                    }

                                    true
                                })
                            }
                        }
                    }
                    element.acceptChildren(this)
                } else
                    super.visitElement(element)
            }
        })
        val all = mutableListOf<Annotator>()
        val uri = file.uri.toString()
        if (params.isNotEmpty())
            all.add(Annotator(uri, params.map { RenderRange(it.toRange(file), null) }, AnnotatorType.Param))
        if (globals.isNotEmpty())
            all.add(
                Annotator(
                    uri,
                    // 未完成的定义会让全局代码变成global染色,导致闪屏
                    globals.filter {
                        // startOffset 不为 0 时 startLines的 firs和second依然可以为0
                        val startLines = file.getLine(it.startOffset)
                        if (it.startOffset != 0 && startLines.first == 0 && startLines.second == 0) {
                            return@filter false
                        }
                        true
                    }.map { RenderRange(it.toRange(file), null) },
                    AnnotatorType.Global
                )
            )
        if (docTypeNames.isNotEmpty())
            all.add(Annotator(uri, docTypeNames.map { RenderRange(it.toRange(file), null) }, AnnotatorType.DocName))
        if (upValues.isNotEmpty())
            all.add(Annotator(uri, upValues.map { RenderRange(it.toRange(file), null) }, AnnotatorType.Upvalue))
        if (notUse.isNotEmpty()) {
            all.add(Annotator(uri, notUse.map { RenderRange(it.toRange(file), null) }, AnnotatorType.NotUse))
        }
        if (paramHints.isNotEmpty()) {
            all.add(Annotator(uri, paramHints, AnnotatorType.ParamHint))
        }
        if (localHints.isNotEmpty()) {
            all.add(Annotator(uri, localHints, AnnotatorType.LocalHint))
        }
        return all
    }

    override fun resolveCompletionItem(item: CompletionItem): CompletableFuture<CompletionItem> {
        return computeAsync {
            val data = item.data
            if (data is JsonPrimitive) {
                val arr = data.asString.split("|")
                if (arr.size >= 2) {
                    workspace.findLuaFile(arr[0])?.let { file ->
                        val position = arr[1].toInt()
                        file.psi?.findElementAt(position)?.let { psi ->
                            PsiTreeUtil.getParentOfType(psi, LuaClassMember::class.java)?.let { member ->
                                val doc = documentProvider.generateDoc(member, member)
                                val content = MarkupContent()
                                content.kind = "markdown"
                                content.value = doc
                                item.documentation = Either.forRight(content)
                            }
                        }
                    }
                }
            }
            item
        }
    }

    override fun hover(position: TextDocumentPositionParams): CompletableFuture<Hover?> {
        return computeAsync {
            var hover: Hover? = null

            val file = workspace.findFile(position.textDocument.uri)
            if (file is ILuaFile) {
                val pos = file.getPosition(position.position.line, position.position.character)
                val element = TargetElementUtil.findTarget(file.psi, pos)
                if (element != null) {
                    val ref = element.reference?.resolve() ?: element
                    val doc = documentProvider.generateDoc(ref, element)
                    if (doc != null)
                        hover = Hover(listOf(Either.forLeft(doc)))
                }
            }

            hover
        }
    }

    override fun documentHighlight(position: TextDocumentPositionParams): CompletableFuture<MutableList<out DocumentHighlight>> {
        return computeAsync {
            val list = mutableListOf<DocumentHighlight>()
            withPsiFile(position) { file, psiFile, i ->
                val target = TargetElementUtil.findTarget(psiFile, i)
                if (target != null) {
                    val def = target.reference?.resolve() ?: target

                    // self highlight
                    if (def.containingFile == psiFile) {
                        def.nameRange?.let { range -> list.add(DocumentHighlight(range.toRange(file))) }
                    }

                    // references highlight
                    val search = ReferencesSearch.search(def, GlobalSearchScope.fileScope(psiFile))
                    search.forEach { reference ->
                        list.add(DocumentHighlight(reference.getRangeInFile(file)))
                    }
                }
            }
            list
        }
    }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        TODO()
    }

    override fun definition(position: TextDocumentPositionParams): CompletableFuture<MutableList<out Location>> {
        return computeAsync {
            val list = mutableListOf<Location>()

            withPsiFile(position) { _, psiFile, i ->
                val target = TargetElementUtil.findTarget(psiFile, i)
                val resolve = target?.reference?.resolve()
                if (resolve != null) {
                    val sourceFile = resolve.containingFile?.virtualFile as? LuaFile
                    val range = resolve.nameRange
                    if (range != null && sourceFile != null)
                        list.add(Location(sourceFile.uri.toString(), range.toRange(sourceFile)))
                }
            }

            list
        }
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        TODO()
    }

    override fun codeLens(params: CodeLensParams): CompletableFuture<MutableList<out CodeLens>> {
        return computeAsync { cc ->
            val list = mutableListOf<CodeLens>()
            if (VSCodeSettings.showCodeLens) {
                workspace.findFile(params.textDocument.uri)?.let {
                    val luaFile = it as? ILuaFile
                    luaFile?.psi?.acceptChildren(object : LuaVisitor() {
                        override fun visitClassMethod(o: LuaClassMethod) {
                            cc.checkCanceled()
                            o.nameIdentifier?.let { id ->
                                val range = id.textRange.toRange(luaFile)
                                list.add(CodeLens(range, null, params.textDocument.uri))
                            }
                        }

                        override fun visitLocalFuncDef(o: LuaLocalFuncDef) {
                            cc.checkCanceled()
                            o.nameIdentifier?.let { id ->
                                val range = id.textRange.toRange(luaFile)
                                list.add(CodeLens(range, null, params.textDocument.uri))
                            }
                            super.visitLocalFuncDef(o)
                        }
                    })
                }
            }
            list
        }
    }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
        return computeAsync {
            val data = unresolved.data as? JsonPrimitive
            val command = Command("References:0", null)
            val uri = data?.asString
            if (uri != null) {
                workspace.findFile(uri)?.let { file ->
                    if (file is ILuaFile) {
                        val pos = file.getPosition(unresolved.range.start.line, unresolved.range.start.character)
                        val target = TargetElementUtil.findTarget(file.psi, pos)
                        if (target != null) {
                            val search = ReferencesSearch.search(target)
                            val findAll = search.findAll()
                            command.title = "References:${findAll.size}"
                            if (findAll.isNotEmpty()) {
                                command.command = "emmy.showReferences"
                                command.arguments = listOf(uri, unresolved.range.start)
                            }
                        }
                    }
                }
            }
            unresolved.command = command
            unresolved
        }
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        return computeAsync {
            val changes = mutableListOf<TextDocumentEdit>()
            withPsiFile(params.textDocument, params.position) { _, psiFile, i ->
                val target = TargetElementUtil.findTarget(psiFile, i) ?: return@withPsiFile

                val map = mutableMapOf<String, MutableList<TextEdit>>()
                val def = target.reference?.resolve() ?: target

                def.nameRange?.let { range ->
                    val refFile = def.containingFile.virtualFile as LuaFile
                    val uri = refFile.uri.toString()
                    val list = map.getOrPut(uri) { mutableListOf() }
                    list.add(TextEdit(range.toRange(refFile), params.newName))
                }

                // references
                val search = ReferencesSearch.search(def)
                search.forEach { reference ->
                    val refFile = reference.element.containingFile.virtualFile as LuaFile
                    val uri = refFile.uri.toString()
                    val list = map.getOrPut(uri) { mutableListOf() }
                    list.add(TextEdit(reference.getRangeInFile(refFile), params.newName))
                }

                map.forEach { (t, u) ->
                    val documentIdentifier = VersionedTextDocumentIdentifier()
                    documentIdentifier.uri = t
                    changes.add(TextDocumentEdit(documentIdentifier, u))
                }
            }
            val edit = WorkspaceEdit(changes)
            edit
        }
    }

    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        return computeAsync { checker ->
            val list = CompletionList()
            list.items = mutableListOf()
            val file = workspace.findFile(position.textDocument.uri)
            if (file is ILuaFile) {
                val pos = file.getPosition(position.position.line, position.position.character)
                val psi = file.psi
                if (psi != null) {
                    CompletionService.collectCompletion(psi, pos, Consumer {
                        checker.checkCanceled()
                        list.items.add(it.asCompletionItem)
                    })
                }
            }
            Either.forRight<MutableList<CompletionItem>, CompletionList>(list)
        }
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        return computeAsync {
            val list = mutableListOf<Either<SymbolInformation, DocumentSymbol>>()
            val file = workspace.findFile(params.textDocument.uri)
            if (file is ILuaFile) {
                val psi = file.psi
                if (psi is LuaPsiFile) {
                    val symbols = getDocumentSymbols(psi, file)
                    symbols.forEach { symbol -> list.add(Either.forRight(symbol)) }
                }
            }
            list
        }
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        var file = workspace.findFile(uri)
        if (file == null) {
            val u = URI(uri)
            file = workspace.addFile(File(u.path), params.textDocument.text, true)
        }
        if (file is LuaFile) {
            val diagnosticsParams = PublishDiagnosticsParams(params.textDocument.uri, file.diagnostics)
            client?.publishDiagnostics(diagnosticsParams)
        }
    }

    override fun didSave(params: DidSaveTextDocumentParams) {

    }

    override fun signatureHelp(position: TextDocumentPositionParams): CompletableFuture<SignatureHelp> {
        return computeAsync {
            val list = mutableListOf<SignatureInformation>()
            var activeParameter = 0
            var activeSig = 0
            withPsiFile(position) { _, psiFile, i ->
                val callExpr = PsiTreeUtil.findElementOfClassAtOffset(psiFile, i, LuaCallExpr::class.java, false)
                var nCommas = 0
                callExpr?.args?.firstChild?.let { firstChild ->
                    var child: PsiElement? = firstChild
                    while (child != null) {
                        if (child.node.elementType == LuaTypes.COMMA) {
                            activeParameter++
                            nCommas++
                        }
                        child = child.nextSibling
                    }
                }

                callExpr?.guessParentType(SearchContext.get(psiFile.project))?.let { parentType ->
                    parentType.each { ty ->
                        if (ty is ITyFunction) {
                            val active = ty.findPerfectSignature(nCommas + 1)
                            var idx = 0
                            ty.process(Processor { sig ->
                                val information = SignatureInformation()
                                information.parameters = mutableListOf()
                                sig.params.forEach { pi ->
                                    val paramInfo =
                                        ParameterInformation("${pi.name}:${pi.ty.displayName}", pi.ty.displayName)
                                    information.parameters.add(paramInfo)
                                }
                                information.label = sig.displayName
                                list.add(information)

                                if (sig == active) {
                                    activeSig = idx
                                }
                                idx++
                                true
                            })
                        }
                    }
                }
            }
            SignatureHelp(list, activeSig, activeParameter)
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        workspace.removeFileIfNeeded(params.textDocument.uri)
    }

    override fun formatting(params: DocumentFormattingParams?): CompletableFuture<MutableList<out TextEdit>> {
        TODO()
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val file = workspace.findFile(params.textDocument.uri)
        if (file is ILuaFile) {
            file.didChange(params)
            val diagnosticsParams = PublishDiagnosticsParams(params.textDocument.uri, file.diagnostics)
            client?.publishDiagnostics(diagnosticsParams)
        }
    }

    override fun references(params: ReferenceParams): CompletableFuture<MutableList<out Location>> {
        val list = mutableListOf<Location>()
        withPsiFile(params) { _, psiFile, pos ->
            val element = TargetElementUtil.findTarget(psiFile, pos)
            if (element != null) {
                val target = element.reference?.resolve() ?: element
                val query = ReferencesSearch.search(target)
                query.forEach { ref ->
                    val luaFile = ref.element.containingFile.virtualFile as LuaFile
                    list.add(Location(luaFile.uri.toString(), ref.getRangeInFile(luaFile)))
                }
            }
        }
        return CompletableFuture.completedFuture(list)
    }

    override fun foldingRange(params: FoldingRangeRequestParams?): CompletableFuture<MutableList<FoldingRange>> {
        return computeAsync {
            val file = params?.textDocument?.let { it -> workspace.findFile(it.uri) }
            val foldingRanges = mutableListOf<FoldingRange>()
            // 用于region
            var regionStartLine = -1
            // 用于require
            var requireStartLine = -1
            var requireLastLine = -1

            if (file is ILuaFile) {
                file.psi?.acceptChildren(object : LuaRecursiveVisitor() {
                    override fun visitComment(comment: PsiComment?) {
                        comment?.let {
                            // 在require 语句中不支持--region
                            if (it.tokenType.toString() == "REGION") {
                                regionStartLine = file.getLine(it.textRange.startOffset).first
                            } else if (it.tokenType.toString() == "ENDREGION") {
                                if (regionStartLine != -1) {
                                    val endLine = file.getLine(it.textRange.startOffset).first
                                    val foldRange = FoldingRange(regionStartLine, endLine)
                                    foldRange.kind = "region"
                                    foldingRanges.add(foldRange)
                                    regionStartLine = -1
                                }
                            }
                        }
                    }

                    override fun visitElement(element: PsiElement) {
                        var callExpr = element
                        if (element is LuaStatement) {
                            element.acceptChildren(object : LuaRecursiveVisitor() {
                                override fun visitCallExpr(o: LuaCallExpr) {
                                    callExpr = o
                                }
                            })
                        }


                        if (callExpr is LuaCallExpr) {
                            if (callExpr.firstChild.text == "require") {
                                val lines = file.getLine(callExpr.textOffset)
                                if (requireStartLine == -1) {
                                    requireStartLine = lines.first
                                }
                                requireLastLine = lines.first
                                return
                            }
                        }

                        if (requireStartLine != -1) {
                            val sameLines = file.getLine(callExpr.textOffset)
                            if (sameLines.first > requireLastLine) {
                                val foldRange = FoldingRange(requireStartLine, requireLastLine)
                                foldRange.kind = "imports"
                                foldingRanges.add(foldRange)
                                requireStartLine = -1
                                requireLastLine = -1
                            }
                        }
                    }
                })

                if (requireStartLine != -1) {
                    val foldRange = FoldingRange(requireStartLine, requireLastLine)
                    foldRange.kind = "imports"
                    foldingRanges.add(foldRange)
                }
            }
            foldingRanges
        }
    }

    private fun withPsiFile(position: TextDocumentPositionParams, code: (ILuaFile, LuaPsiFile, Int) -> Unit) {
        withPsiFile(position.textDocument, position.position, code)
    }

    private fun withPsiFile(
        textDocument: TextDocumentIdentifier,
        position: Position,
        code: (ILuaFile, LuaPsiFile, Int) -> Unit
    ) {
        val file = workspace.findFile(textDocument.uri)
        if (file is ILuaFile) {
            val psi = file.psi
            if (psi is LuaPsiFile) {
                val pos = file.getPosition(position.line, position.character)
                code(file, psi, pos)
            }
        }
    }
}
