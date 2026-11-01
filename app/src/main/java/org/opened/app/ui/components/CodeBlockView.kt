package org.opened.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.opened.app.ui.theme.CodeBackgroundDark
import org.opened.app.ui.theme.CodeCommentColor
import org.opened.app.ui.theme.CodeKeywordColor
import org.opened.app.ui.theme.CodeNumberColor
import org.opened.app.ui.theme.CodeStringColor
import org.opened.app.ui.theme.CodeTypeColor

@Composable
fun CodeBlockView(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    val highlightedText = remember(code, language) {
        highlightSyntax(code, language)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        color = CodeBackgroundDark,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "CODE" }.uppercase(),
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("code", code)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            copied = false
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF34D399) else Color(0xFF8B949E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Code content with horizontal scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = highlightedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

private fun highlightSyntax(code: String, language: String): AnnotatedString {
    val keywords = setOf(
        "fun", "val", "var", "class", "interface", "object", "return", "if", "else",
        "for", "while", "do", "when", "in", "is", "as", "import", "package", "try",
        "catch", "finally", "throw", "null", "true", "false", "this", "super",
        "def", "async", "await", "fn", "let", "mut", "pub", "struct", "enum",
        "impl", "match", "const", "static", "void", "int", "char", "float",
        "double", "bool", "typedef", "sizeof", "include", "define", "pragma"
    )

    val types = setOf(
        "Int", "Long", "Float", "Double", "Boolean", "String", "List", "Map", "Set",
        "Flow", "CoroutineScope", "Result", "Unit", "Any", "Array", "Byte", "Short"
    )

    return buildAnnotatedString {
        val lines = code.lines()
        lines.forEachIndexed { index, line ->
            var i = 0
            val trimmedLine = line.trimStart()

            // Check for line comments
            if (trimmedLine.startsWith("//") || trimmedLine.startsWith("#")) {
                val indent = line.substring(0, line.indexOf(trimmedLine))
                append(indent)
                pushStyle(SpanStyle(color = CodeCommentColor))
                append(trimmedLine)
                pop()
            } else {
                while (i < line.length) {
                    val c = line[i]

                    // Strings
                    if (c == '"' || c == '\'') {
                        val quote = c
                        val strStart = i
                        i++
                        while (i < line.length && line[i] != quote) {
                            if (line[i] == '\\' && i + 1 < line.length) i++
                            i++
                        }
                        if (i < line.length) i++ // include closing quote
                        pushStyle(SpanStyle(color = CodeStringColor))
                        append(line.substring(strStart, i))
                        pop()
                        continue
                    }

                    // Words (Identifiers, keywords, types)
                    if (c.isLetter() || c == '_') {
                        val wordStart = i
                        while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
                            i++
                        }
                        val word = line.substring(wordStart, i)
                        when {
                            word in keywords -> {
                                pushStyle(SpanStyle(color = CodeKeywordColor, fontWeight = FontWeight.SemiBold))
                                append(word)
                                pop()
                            }
                            word in types -> {
                                pushStyle(SpanStyle(color = CodeTypeColor))
                                append(word)
                                pop()
                            }
                            else -> {
                                pushStyle(SpanStyle(color = Color(0xFFE6EDF3)))
                                append(word)
                                pop()
                            }
                        }
                        continue
                    }

                    // Numbers
                    if (c.isDigit()) {
                        val numStart = i
                        while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'f' || line[i] == 'L' || line[i] == 'x')) {
                            i++
                        }
                        pushStyle(SpanStyle(color = CodeNumberColor))
                        append(line.substring(numStart, i))
                        pop()
                        continue
                    }

                    // Other characters
                    pushStyle(SpanStyle(color = Color(0xFFC9D1D9)))
                    append(c.toString())
                    pop()
                    i++
                }
            }

            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}
