package com.tang.vscode.diagnostics.inspections

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*
import com.tang.lsp.ILuaFile
import com.tang.lsp.toRange
import com.tang.vscode.diagnostics.DiagnosticsOptions
import com.tang.vscode.diagnostics.InspectionsLevel
import org.eclipse.lsp4j.Diagnostic

object FunctionInspection {
    fun callExprInspections(callExpr: LuaCallExpr, file: ILuaFile, diagnostics: MutableList<Diagnostic>) {
        if (DiagnosticsOptions.parameterValidation != InspectionsLevel.None) {
            var nCommas = 0
            val paramMap = mutableMapOf<Int, LuaTypeGuessable>()
            callExpr.args.firstChild?.let { firstChild ->
                var child: PsiElement? = firstChild
                while (child != null) {
                    if (child.node.elementType == LuaTypes.COMMA) {
                        nCommas++
                    } else {
                        if (child is LuaTypeGuessable) {
                            paramMap[nCommas] = child
                        }
                    }

                    child = child.nextSibling
                }
            }
            val context = SearchContext.get(callExpr.project)
            callExpr.guessParentType(context).let { parentType ->
                parentType.each { ty ->
                    if (ty is ITyFunction) {
                        val sig = ty.findPerfectSignature(nCommas + 1)

                        var index = 0;

                        var skipFirstParam = false

                        if (sig.colonCall && callExpr.isMethodDotCall) {
                            index++;
                        } else if (!sig.colonCall && callExpr.isMethodColonCall) {
                            skipFirstParam = true
                        }

                        sig.params.forEach { pi ->
                            if (skipFirstParam) {
                                skipFirstParam = false
                                return@forEach
                            }

                            val param = paramMap[index]
                            if (param != null) {
                                val paramType = param.guessType(context)
                                if (!paramTypeCheck(pi, param, context)) {
                                    val diagnostic = Diagnostic()
                                    diagnostic.message =
                                        "Type mismatch '${paramType.displayName}' not match type '${pi.ty.displayName}'"
                                    diagnostic.severity = Severity.makeSeverity(DiagnosticsOptions.parameterValidation)
                                    diagnostic.range = param.textRange.toRange(file)
                                    diagnostics.add(diagnostic)
                                }
                            } else if (!pi.nullable) {
                                val diagnostic = Diagnostic()
                                diagnostic.message =
                                    "Too few arguments to function call"
                                diagnostic.severity = Severity.makeSeverity(DiagnosticsOptions.parameterValidation)
                                val endOffset = callExpr.textRange.endOffset
                                diagnostic.range = TextRange(endOffset, endOffset).toRange(file)
                                diagnostics.add(diagnostic)
                                return@each
                            }
                            ++index;
                        }
                        //可变参数暂时不做验证
                    }
                }
            }
        }
    }

    private fun paramTypeCheck(param: LuaParamInfo, variable: LuaTypeGuessable, context: SearchContext): Boolean {
        val variableType = variable.guessType(context)
        val defineType = param.ty

        if (DiagnosticsOptions.defineTypeCanReceiveNilType && variableType.kind == TyKind.Nil) {
            return true
        }

        if (!param.nullable && variableType.kind == TyKind.Nil) {
            return false
        }

        // 由于没有接口 interface
        // 那么将匿名表传递给具有特定类型的定义类型也都被认为是合理的
        // 暂时不做field检查
        if (variable is LuaTableExpr &&
            (defineType.kind == TyKind.Class || defineType.kind == TyKind.Array || defineType.kind == TyKind.Tuple)
        ) {
            return true
        }

        // 类似于回调函数的写法，不写传参是非常普遍的，所以只需要认为定义类型是个函数就通过
        if (variable is LuaClosureExpr && defineType.kind == TyKind.Function) {
            return true
        }

        return typeCheck(defineType, variableType, context)
    }

    private fun typeCheck(defineType: ITy, variableType: ITy, context: SearchContext): Boolean {
        if (DiagnosticsOptions.anyTypeCanAssignToAnyDefineType && variableType is TyUnknown) {
            return true
        }

        if (DiagnosticsOptions.defineAnyTypeCanBeAssignedByAnyVariable && defineType is TyUnknown) {
            return true
        }

        if (defineType is TyUnion) {
            var isUnionCheckPass = false
            defineType.each {
                if (typeCheck(it, variableType, context)) {
                    isUnionCheckPass = true
                    return@each
                }
            }

            if (isUnionCheckPass) {
                return true
            }
        }

        return variableType.subTypeOf(defineType, context, true)
    }
}