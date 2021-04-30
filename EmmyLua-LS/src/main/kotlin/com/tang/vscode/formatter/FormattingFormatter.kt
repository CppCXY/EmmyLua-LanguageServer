package com.tang.vscode.formatter

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.psi.LuaElementType
import com.tang.lsp.ILuaFile
import java.lang.StringBuilder

class FormattingFormatter(val file: ILuaFile, val psi: PsiFile) {
    private var fileElement: FormattingElement = FormattingElement(psi, FormattingType.Block, psi.textRange, mutableListOf());
    private val lineSeparator = FormattingOptions.lineSeparator
    private val emptyWhite = FormattingOptions.emptyWhite

    // 上下文变量
    private var ctx: FormattingContext = FormattingContext();

    fun add(element: PsiElement, elementType: FormattingType = FormattingType.General) {
        val formattingElement = FormattingElement(element, elementType, element.textRange, mutableListOf())
        add(formattingElement)
    }

    fun add(formattingElement: FormattingElement) {
        add(fileElement.children, formattingElement)
    }

    fun add(list: MutableList<FormattingElement>, element: FormattingElement) {
        if (list.isEmpty()) {
            list.add(element)
        } else {
            for (index in list.lastIndex downTo 0) {
                if (element.textRange.startOffset >= list[index].textRange.endOffset) {
                    list.add(index + 1, element)
                    return
                } else if (element.textRange.endOffset <= list[index].textRange.endOffset
                        && element.textRange.startOffset >= list[index].textRange.startOffset) {

                    add(list[index].children, element)
                    return
                }
            }
            list.add(0, element)
        }
    }

    fun getFormattingResult(): String {
        ctx = FormattingContext()
        printBlock(fileElement)
        return ctx.getFormattingResult()
    }

    private fun printElement(element: FormattingElement) {
        when (element.type) {
            FormattingType.Comment -> {
                printComment(element)
            }
            FormattingType.Function -> {
                printFunction(element)
            }
            FormattingType.LocalFunction -> {
                printLocalFunction(element)
            }
            FormattingType.Closure -> {
                printClosure(element)
            }
            FormattingType.DoBlock -> {
                printDoBlock(element)
            }
            FormattingType.IfStatement -> {
                printIfStatement(element)
            }
            FormattingType.Statement -> {
                printStatement(element)
            }
            FormattingType.RepeatStatement -> {
                printRepeatStatement(element)
            }
            FormattingType.WhileStatement -> {
                printWhileStatement(element)
            }
            FormattingType.ForAStatement -> {
                printForAStatement(element)
            }
            FormattingType.ForBStatement -> {
                printForBStatement(element)
            }
            FormattingType.LocalStatement -> {
                printLocalStatement(element)
            }
            FormattingType.BreakStatement -> {
                printBreakStatement(element)
            }
            FormattingType.ExprStatement -> {
                printExprStatement(element)
            }
            FormattingType.CallExpr -> {
                printCallExpr(element)
            }
            FormattingType.LiteralExpr -> {
                printLiteralExpr(element)
            }
            FormattingType.BinaryExpr -> {
                printBinaryExpr(element)
            }
            FormattingType.UnaryExpr -> {
                printUnaryExpr(element)
            }
            FormattingType.NamedExpr -> {
                printNameExpr(element)
            }
            FormattingType.IndexExpr -> {
                printIndexExpr(element)
            }
            FormattingType.AssignStatement -> {
                printAssignStatement(element)
            }
            FormattingType.ReturnStatement -> {
                printReturnStatement(element)
            }
            FormattingType.GotoStatement -> {
                printGotoStatement(element)
            }
            FormattingType.LabelStatement -> {
                printLabelStatement(element)
            }
            FormattingType.TableExpr -> {
                printTableExpr(element)
            }
            FormattingType.TableField -> {
                printTableField(element)
            }
            FormattingType.TableFieldSep -> {
                printTableFieldSep(element)
            }
            FormattingType.CallArgs -> {
                printCallArgs(element)
            }
            FormattingType.Expr -> {
                printExpr(element)
            }
            FormattingType.ParentExpr -> {
                printParentExpr(element)
            }
            FormattingType.ExprList -> {
                printExprList(element)
            }
            FormattingType.Operator -> {
                printOperator(element)
            }
            FormattingType.FunctionBody -> {
                printFunctionBody(element)
            }
            FormattingType.Block -> {
                printBlock(element)
            }
            FormattingType.Arg -> {
                printArg(element)
            }
            FormattingType.NameDefList -> {
                printNameDefList(element)
            }
            FormattingType.NameDef -> {
                printNameDef(element)
            }
            FormattingType.BinaryOperator -> {
                printBinaryOperator(element)
            }
            FormattingType.UnaryOperator -> {
                printUnaryOperator(element)
            }
            FormattingType.Id -> {
                printId(element)
            }
            FormattingType.SingleArg -> {
                printSingleArg(element)
            }
            FormattingType.Attribute -> {
                printAttribute(element)
            }
            FormattingType.KeyWorld -> {
                printKeyWorld(element)
            }
            else -> {
                ctx.print(element.psi.text)
            }
        }
    }

    private fun printComment(element: FormattingElement) {
        // 注释的排版最麻烦
        // 它可以穿插到很多地方注释里面还会存在多行注释
        val psi = element.psi
        if (psi.node.elementType.toString() == "BLOCK_COMMENT") {
            ctx.print(element.psi.text, false).print(lineSeparator)
        } else {
            ctx.print(psi.text).print(lineSeparator)
        }
    }

    private fun printFunction(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    val text = it.psi.text
                    when (text) {
                        "function" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "end" -> {
                            ctx.print(text)
                        }
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printLocalFunction(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    val text = it.psi.text
                    when (text) {
                        "local" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "function" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "end" -> {
                            ctx.print(text)
                        }
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printClosure(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    ctx.print(it.psi.text)
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printDoBlock(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    ctx.print(it.psi.text).print(lineSeparator)
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printIfStatement(element: FormattingElement) {

        val ifStartLine = file.getLine(element.textRange.startOffset).first
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    val text = it.psi.text
                    when (text) {
                        "if" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "else" -> {
                            ctx.print(text).print(lineSeparator)
                        }
                        "elseif" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "then" -> {
                            ctx.print(emptyWhite).print(text).print(lineSeparator)
                        }
                        "end" -> {
                            ctx.print(text)
                        }
                    }
                }
                // 这是由于if语句块内的首行注释也被认为是if语句的注释要特别处理
                FormattingType.Comment -> {
                    val startLine = file.getLine(it.textRange.startOffset).first
                    if (startLine - ifStartLine > 0) {
                        ctx.enterBlockEnv()
                        printElement(it)
                        ctx.exitBlockEnv()
                    } else {
                        printElement(it)
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printWhileStatement(element: FormattingElement) {
        val whileStartLine = file.getLine(element.textRange.startOffset).first
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    val text = it.psi.text
                    when (text) {
                        "while" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "do" -> {
                            ctx.print(emptyWhite).print(text).print(lineSeparator)
                        }
                        "end" -> {
                            ctx.print(text)
                        }
                    }
                }
                FormattingType.Comment -> {
                    val startLine = file.getLine(it.textRange.startOffset).first
                    if (startLine - whileStartLine > 0) {
                        ctx.enterBlockEnv()
                        printElement(it)
                        ctx.exitBlockEnv()
                    } else {
                        printElement(it)
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printRepeatStatement(element: FormattingElement) {
        val repeatStartLine = file.getLine(element.textRange.startOffset).first
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    val text = it.psi.text
                    when (text) {
                        "repeat" -> {
                            ctx.print(text).print(lineSeparator)
                        }
                        "until" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                    }
                }
                FormattingType.Comment -> {
                    val startLine = file.getLine(it.textRange.startOffset).first
                    if (startLine - repeatStartLine > 0) {
                        ctx.enterBlockEnv()
                        printElement(it)
                        ctx.exitBlockEnv()
                    } else {
                        printElement(it)
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printForAStatement(element: FormattingElement) {
        val forStartLine = file.getLine(element.textRange.startOffset).first
        element.children.forEach {
            val text = it.psi.text
            when (it.type) {
                FormattingType.KeyWorld -> {
                    when (text) {
                        "for" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "do" -> {
                            ctx.print(emptyWhite).print(text).print(lineSeparator)
                        }
                        "end" -> {
                            ctx.print(text)
                        }
                    }
                }
                FormattingType.Operator -> {
                    when (text) {
                        "," -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "=" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        else -> {
                            printElement(it)
                        }
                    }
                }
                FormattingType.Arg -> {
                    ctx.print(text).print(emptyWhite)
                }
                FormattingType.Comment -> {
                    val startLine = file.getLine(it.textRange.startOffset).first
                    if (startLine - forStartLine > 0) {
                        ctx.enterBlockEnv()
                        printElement(it)
                        ctx.exitBlockEnv()
                    } else {
                        printElement(it)
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printForBStatement(element: FormattingElement) {
        val forStartLine = file.getLine(element.textRange.startOffset).first
        element.children.forEach {
            val text = it.psi.text
            when (it.type) {
                FormattingType.KeyWorld -> {
                    when (text) {
                        "for" -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "in" -> {
                            ctx.print(emptyWhite).print(text).print(emptyWhite)
                        }
                        "do" -> {
                            ctx.print(emptyWhite).print(text).print(lineSeparator)
                        }
                        "end" -> {
                            ctx.print(text)
                        }
                    }
                }
                FormattingType.Operator -> {
                    when (text) {
                        "," -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        else -> {
                            printElement(it)
                        }
                    }
                }
                FormattingType.Comment -> {
                    val startLine = file.getLine(it.textRange.startOffset).first
                    if (startLine - forStartLine > 0) {
                        ctx.enterBlockEnv()
                        printElement(it)
                        ctx.exitBlockEnv()
                    } else {
                        printElement(it)
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printStatement(element: FormattingElement) {
        ctx.print(element.psi.text).print(lineSeparator)
    }

    private fun printLocalStatement(element: FormattingElement) {
        val endLine = file.getLine(element.textRange.endOffset).first
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    ctx.print(it.psi.text).print(emptyWhite)
                }
                FormattingType.Operator -> {
                    ctx.print(it.psi.text).print(emptyWhite)
                }
                FormattingType.NameDefList -> {
                    printElement(it)
                    ctx.print(emptyWhite)
                }
                FormattingType.Comment -> {
                    val commentLine = file.getLine(it.textRange.endOffset).first
                    if (commentLine >= endLine) {
                        ctx.print(emptyWhite).print(it.psi.text)
                    } else {
                        printElement(it)
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printBreakStatement(element: FormattingElement) {
        ctx.print(element.psi.text).print(lineSeparator)
    }

    private fun printAssignStatement(element: FormattingElement) {
        val endLine = file.getLine(element.textRange.endOffset).first
        var leftExpr = true
        element.children.forEach {
            when (it.type) {
                FormattingType.Operator -> {
                    ctx.print(it.psi.text).print(emptyWhite)
                }
                FormattingType.ExprList -> {
                    if (leftExpr) {
                        printElement(it)
                        ctx.print(emptyWhite)
                        leftExpr = false
                    } else {
                        printElement(it)
                    }
                }
                FormattingType.Comment -> {
                    val commentLine = file.getLine(it.textRange.endOffset).first
                    if (commentLine >= endLine) {
                        // 那么这是赋值表达式尾部的注释 只会有一行
                        ctx.print(emptyWhite).print(it.psi.text)
                    } else {
                        // 赋值表达式上边的注释
                        printElement(it)
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printExprStatement(element: FormattingElement) {
        element.children.forEach {
            printElement(it)
        }
        ctx.print(lineSeparator)
    }

    private fun printReturnStatement(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    ctx.print(it.psi.text).print(emptyWhite)
                }
                else -> {
                    printElement(it)
                }
            }

        }
        ctx.print(lineSeparator)
    }

    private fun printGotoStatement(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.KeyWorld -> {
                    ctx.print(it.psi.text).print(emptyWhite)
                }
                else -> {
                    printElement(it)
                }
            }
        }
        ctx.print(lineSeparator)
    }

    private fun printLabelStatement(element: FormattingElement) {
        element.children.forEach {
            printElement(it)
        }
        ctx.print(lineSeparator)
    }

    private fun printFunctionBody(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.Operator -> {
                    val text = it.psi.text
                    when (text) {
                        "," -> {
                            ctx.print(text).print(emptyWhite)
                        }
                        "(" -> {
                            ctx.print(text)
                        }
                        ")" -> {
                            ctx.print(text).print(lineSeparator)
                        }
                    }
                }
                FormattingType.Comment -> {
                    ctx.enterBlockEnv()
                    printElement(it)
                    ctx.exitBlockEnv()
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printBlock(element: FormattingElement) {
        ctx.enterBlockEnv()
        var lastLine = -1
        var lastElement: FormattingElement? = null
        for (index in element.children.indices) {
            val childElement = element.children[index]
            val startLine = file.getLine(childElement.textRange.startOffset).first
            val endLine = file.getLine(childElement.textRange.endOffset).first

            // 用于检查原始布局
            val lineDiff = startLine - lastLine

            // 行布局
            if (index != 0) {
                val type = childElement.type
                // 函数和前文定义之间插入空行
                if (type == FormattingType.Function || type == FormattingType.LocalFunction) {
                    if (lastElement?.type == FormattingType.Comment) {
                        if (lineDiff > 1) {
                            // 保持原始布局
                            ctx.print(lineSeparator.repeat(lineDiff - 1))
                        }
                    } else {
                        ctx.print(lineSeparator.repeat(FormattingOptions.functionSpacing))
                    }
                }
                // 在语句块和前文之间至少一个空格
                else if (type == FormattingType.ForAStatement || type == FormattingType.ForBStatement || type == FormattingType.RepeatStatement
                        || type == FormattingType.WhileStatement) {
                    if (lastElement?.type == FormattingType.Comment) {
                        if (lineDiff > 1) {
                            // 保持原始布局
                            ctx.print(lineSeparator.repeat(lineDiff - 1))
                        }
                    } else {
                        ctx.print(lineSeparator.repeat(FormattingOptions.loopSpacing))
                    }
                }
                // 赋值语句会根据情况空行
                else if (type == FormattingType.LocalStatement || type == FormattingType.AssignStatement || type == FormattingType.ExprStatement || type == FormattingType.Comment) {
                    val lastType = lastElement?.type
                    if (lastType == FormattingType.LocalStatement || lastType == FormattingType.AssignStatement || lastType == FormattingType.ExprStatement || lastType == FormattingType.Comment) {
                        if (lineDiff > 1) {
                            // 保持原始布局
                            ctx.print(lineSeparator.repeat(lineDiff - 1))
                        }
                    } else {
                        ctx.print(lineSeparator)
                    }
                }
            }
            printElement(childElement)
            lastLine = endLine
            lastElement = childElement
        }
        ctx.exitBlockEnv()
    }


    private fun printBinaryExpr(element: FormattingElement) {
        var currentLine = file.getLine(element.textRange.startOffset).first
        var lastElement: FormattingElement? = null
        element.children.forEach {
            val line = file.getLine(it.textRange.startOffset).first
            if (line > currentLine) {
                currentLine = line
                if (lastElement?.type != FormattingType.Comment) {
                    //则换行
                    ctx.print(lineSeparator)
                }
            }

            when (it.type) {
                FormattingType.Comment -> {
                    printElement(it)
                }
                else -> {
                    printElement(it)
                }
            }
            lastElement = it
        }
    }

    private fun printUnaryExpr(element: FormattingElement) {
        element.children.forEach {
            printElement(it)
        }
    }

    private fun printCallExpr(element: FormattingElement) {

        element.children.forEach {
            when (it.type) {
                FormattingType.CallArgs -> {
                    val firstLeftBracketLine = file.getLine(it.textRange.startOffset).first
                    val lastEndLine = file.getLine(it.textRange.endOffset).first

                    if (lastEndLine > firstLeftBracketLine) {
                        val firstArgs = it.children.firstOrNull { it -> it.type != FormattingType.Operator }
                        if (firstArgs != null) {

                            val firstArgsLine = file.getLine(firstArgs.textRange.startOffset).first
                            // 第一个调用的参数已经换行则只做一个缩进
                            if (firstArgsLine > firstLeftBracketLine) {
                                printCallArgsAlignment(it, false)
                            } else {
                                //第一个参数还在原行上，则与第一个参数对齐
                                printCallArgsAlignment(it, true)
                            }
                            return@forEach
                        }
                    }

                    printElement(it)
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printIndexExpr(element: FormattingElement) {
        var lineBreak = false
        //索引表达式的换行行为跟索引运算符有关
        for (index in element.children.indices) {
            val child = element.children[index]
            when (child.type) {
                FormattingType.Operator -> {
                    val text = child.psi.text
                    if (text == "." || text == ":" || text == "[") {
                        if (index > 0) {
                            val childLineInfo = file.getLine(child.textRange.startOffset)
                            val lastChild = element.children[index - 1]
                            val lastChildLineInfo = file.getLine(lastChild.textRange.endOffset)
                            if (childLineInfo.first > lastChildLineInfo.first) {
                                // 才重新换行
                                ctx.print(lineSeparator)
                                //换行之后如何对齐，那就跟以前一样对齐就好了
                                ctx.enterBlockEnv(if (childLineInfo.second < lastChildLineInfo.second)
                                    childLineInfo.second
                                else
                                    lastChildLineInfo.second
                                )
                                lineBreak = true
                            }
                        }
                    }
                    printElement(child)
                }
                else -> {
                    printElement(child)
                }
            }
        }
        if (lineBreak) {
            ctx.exitBlockEnv()
        }
    }

    private fun printNameExpr(element: FormattingElement) {
        ctx.print(element.psi.text)
    }

    private fun printTableExpr(element: FormattingElement) {
        val startLine = file.getLine(element.textRange.startOffset).first
        val endLine = file.getLine(element.textRange.endOffset).first
        var lastFieldOrSepElement: FormattingElement? = null
        if (endLine > startLine || element.textRange.endOffset - element.textRange.startOffset > FormattingOptions.tableLineWidth) {
            //执行换行对齐
            for (index in element.children.indices) {
                val child = element.children[index]
                when (child.type) {
                    FormattingType.Operator -> {
                        val text = child.psi.text
                        when (text) {
                            "{" -> {
                                ctx.print(text).print(lineSeparator)
                                ctx.enterBlockEnv()
                            }
                            "}" -> {
                                val lastUseCharacter = ctx.getCurrentCharacter()
                                ctx.exitBlockEnv()

                                if (lastUseCharacter != 0) {
                                    ctx.print(lineSeparator)
                                }

                                ctx.print(text)
                            }
                            else -> {
                                printElement(child)
                            }
                        }
                    }
                    FormattingType.TableField -> {
                        // TableField认为他的缩进已经被TableExpr打印了
                        printElement(child)
                        lastFieldOrSepElement = child
                    }
                    /**
                     * 在表的表达式上有多种注释风格 可以是field = 123, --注解内容
                     * 也可以是
                     *  --注解内容
                     *  field = 123,
                     *  怎么这么多奇怪的习惯
                     */
                    FormattingType.Comment -> {
                        if (lastFieldOrSepElement != null) {
                            // 还有人会用多行注释？那我只好换行了
                            val commentLine = file.getLine(child.textRange.endOffset).first
                            val lastFieldEndLine = file.getLine(lastFieldOrSepElement.textRange.endOffset).first

                            if (lastFieldEndLine != commentLine) {
                                printElement(child)
                            } else {
                                ctx.print(emptyWhite).print(child.psi.text).print(lineSeparator)
                            }
                        } else {
                            printElement(child)
                        }
                    }
                    FormattingType.TableFieldSep -> {
                        ctx.print(child.psi.text)
                        var isAddLineSeparator = true
                        // 考察一下 下一个child是不是comment
                        if (index + 1 < element.children.size) {
                            val nextChild = element.children[index + 1]
                            if (nextChild.type == FormattingType.Comment) {
                                // 考察一下该注释是否和自己在同一行
                                val sepLine = file.getLine(child.textRange.endOffset).first
                                val commentLine = file.getLine(nextChild.textRange.endOffset).first
                                if (sepLine == commentLine) {
                                    isAddLineSeparator = false
                                }
                            }
                        }
                        if (isAddLineSeparator) {
                            ctx.print(lineSeparator)
                        }
                        lastFieldOrSepElement = child
                    }
                    else -> {
                        printElement(child)
                    }
                }
            }
        } else {
            //执行非换行对齐
            element.children.forEach {
                when (it.type) {
                    FormattingType.TableField -> {
                        printElement(it)
                    }
                    FormattingType.TableFieldSep -> {
                        ctx.print(it.psi.text).print(emptyWhite)
                    }
                    else -> {
                        printElement(it)
                    }
                }
            }
        }
    }

    private fun printExpr(element: FormattingElement) {
        ctx.print(element.psi.text)
    }

    private fun printParentExpr(element: FormattingElement) {
        element.children.forEach {
            printElement(it)
        }
    }

    private fun printLiteralExpr(element: FormattingElement) {
        ctx.print(element.psi.text, false)
    }

    private fun printExprList(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.Operator -> {
                    ctx.print(it.psi.text).print(emptyWhite)
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printArg(element: FormattingElement) {
        ctx.print(element.psi.text)
    }

    private fun printCallArgs(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.Operator -> {
                    val text = it.psi.text
                    when (text) {
                        "," -> {
                            ctx.print(it.psi.text).print(emptyWhite)
                        }
                        else -> {
                            printElement(it)
                        }
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printCallArgsAlignment(element: FormattingElement, alignmentTobracket: Boolean) {
        element.children.forEach {
            when (it.type) {
                FormattingType.Operator -> {
                    val text = it.psi.text
                    when (text) {
                        "," -> {
                            ctx.print(it.psi.text).print(lineSeparator)
                        }
                        "(" -> {
                            ctx.print(text)
                            if (alignmentTobracket) {
                                ctx.enterBlockEnv(ctx.currentLineWidth)
                            } else {
                                ctx.print(lineSeparator)
                                ctx.enterBlockEnv()
                            }
                        }
                        ")" -> {
                            if (alignmentTobracket) {
                                ctx.print(text)
                                ctx.exitBlockEnv()
                            } else {
                                ctx.exitBlockEnv()
                                ctx.print(text)
                            }
                        }
                        else -> {
                            printElement(it)
                        }
                    }
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printAttribute(element: FormattingElement) {
        element.children.forEach {
            printElement(it)
        }
    }

    private fun printNameDefList(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.Operator -> {
                    ctx.print(it.psi.text).print(emptyWhite)
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printNameDef(element: FormattingElement) {
        ctx.print(element.psi.text)
    }

    private fun printBinaryOperator(element: FormattingElement) {

        ctx.print(emptyWhite)
        element.children.forEach {
            printElement(it)
        }
        ctx.print(emptyWhite)
    }

    private fun printUnaryOperator(element: FormattingElement) {
        element.children.forEach {
            val text = it.psi.text
            when (text) {
                "not" -> {
                    ctx.print(text).print(emptyWhite)
                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printOperator(element: FormattingElement) {
        ctx.print(element.psi.text)
    }

    private fun printId(element: FormattingElement) {
        ctx.print(element.psi.text)
    }

    private fun printTableField(element: FormattingElement) {
        element.children.forEach {
            when (it.type) {
                FormattingType.Operator -> {
                    val text = it.psi.text
                    when (text) {
                        "=" -> {
                            ctx.print(emptyWhite).print(text).print(emptyWhite)
                        }
                        else -> {
                            printElement(it)
                        }
                    }

                }
                else -> {
                    printElement(it)
                }
            }
        }
    }

    private fun printTableFieldSep(element: FormattingElement) {
        ctx.print(element.psi.text)
    }

    private fun printSingleArg(element: FormattingElement) {
        ctx.print(emptyWhite)
        element.children.forEach {
            printElement(it)
        }
    }

    private fun printErrorElement(element: FormattingElement) {
        ctx.print(element.psi.text)
    }

    private fun printKeyWorld(element: FormattingElement) {
        ctx.print(element.psi.text)
    }


}