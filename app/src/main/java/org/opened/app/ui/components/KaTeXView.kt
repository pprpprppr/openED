package org.opened.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Checks if the given text contains LaTeX math notation, formulas, or delimiters.
 */
fun hasMathContent(text: String): Boolean {
    if (text.isEmpty()) return false
    if (text.contains("$$") || text.contains("\\[") || text.contains("\\(") || text.contains("\\begin{")) {
        return true
    }
    // Check for inline math $...$
    if (text.contains('$')) {
        val first = text.indexOf('$')
        if (first in 0 until text.length - 1) {
            val second = text.indexOf('$', first + 1)
            if (second > first) return true
        }
    }
    // Check for common LaTeX math commands
    if (text.contains("\\frac") || text.contains("\\dfrac") || text.contains("\\tfrac") ||
        text.contains("\\sqrt") || text.contains("\\sum") || text.contains("\\int") ||
        text.contains("\\prod") || text.contains("\\lim") || text.contains("\\partial") ||
        text.contains("\\nabla") || text.contains("\\infty") || text.contains("\\times") ||
        text.contains("\\cdot") || text.contains("\\pm") || text.contains("\\mp") ||
        text.contains("\\leq") || text.contains("\\geq") || text.contains("\\neq") ||
        text.contains("\\approx") || text.contains("\\equiv") || text.contains("\\to") ||
        text.contains("\\rightarrow") || text.contains("\\leftarrow") || text.contains("\\Rightarrow") ||
        text.contains("\\Leftarrow") || text.contains("\\Leftrightarrow") || text.contains("\\vec") ||
        text.contains("\\hat") || text.contains("\\mathbf") || text.contains("\\mathbb") ||
        text.contains("\\mathcal") || text.contains("\\ce{") || text.contains("\\binom") ||
        text.contains("\\matrix") || text.contains("\\pmatrix") || text.contains("\\bmatrix") ||
        text.contains("\\cases")
    ) {
        return true
    }
    return false
}

/**
 * Ensures bare LaTeX expressions (like \frac{1}{2} or \sqrt{3}) are wrapped in $...$
 * so KaTeX auto-render recognizes them.
 */
fun ensureDelimiters(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw
    if (trimmed.startsWith("$") || trimmed.startsWith("\\[") || trimmed.startsWith("\\(") || trimmed.startsWith("\\begin{")) {
        return raw
    }
    // If it's a bare LaTeX command e.g. \frac{a}{b} or \sqrt{x}
    if (trimmed.startsWith("\\") && (trimmed.contains("{") || trimmed.contains(" "))) {
        return "\$$trimmed\$"
    }
    return raw
}

/**
 * Bridge interface exposed to JavaScript in the WebView.
 */
private class KaTeXBridge(
    private val onHeight: (Int) -> Unit,
    private val onLink: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastReported: Int = 0

    @JavascriptInterface
    fun onHeightChanged(heightDp: Int) {
        if (Math.abs(heightDp - lastReported) >= 2) {
            lastReported = heightDp
            mainHandler.post { onHeight(heightDp) }
        }
    }

    @JavascriptInterface
    fun onChapterClicked(title: String) {
        mainHandler.post { onLink(title) }
    }
}

/**
 * High-performance, offline KaTeX Display Math View.
 * Renders block LaTeX formulas with Donald Knuth / Computer Modern typesetting quality.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXMathView(
    latex: String,
    modifier: Modifier = Modifier,
    isDisplayMode: Boolean = true,
    fontSize: TextUnit = 18.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    minHeight: Dp = 48.dp
) {
    val context = LocalContext.current
    val textColorHex = remember(color) {
        String.format("#%06X", 0xFFFFFF and color.toArgb())
    }
    val fontSizePx = remember(fontSize) { fontSize.value.toInt() }

    var reportedHeightDp by remember(latex) { mutableIntStateOf(0) }

    val cleanLatex = remember(latex) {
        var str = latex.trim()
        if (str.startsWith("$$") && str.endsWith("$$") && str.length >= 4) {
            str = str.removePrefix("$$").removeSuffix("$$").trim()
        } else if (str.startsWith("\\[") && str.endsWith("\\]") && str.length >= 4) {
            str = str.removePrefix("\\[").removeSuffix("\\]").trim()
        }
        str
    }

    val htmlContent = remember(cleanLatex, textColorHex, fontSizePx, isDisplayMode) {
        buildKaTeXDisplayHtml(
            latex = cleanLatex,
            textColorHex = textColorHex,
            fontSizePx = fontSizePx,
            isDisplayMode = isDisplayMode
        )
    }

    val heightModifier = if (reportedHeightDp > 0) {
        Modifier.height(reportedHeightDp.dp)
    } else {
        Modifier.heightIn(min = minHeight)
    }

    var webViewRef by remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }

    DisposableEffect(cleanLatex) {
        onDispose {
            try {
                webViewRef?.let { wv ->
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.destroy()
                }
            } catch (_: Exception) {}
            webViewRef = null
        }
    }

    Box(modifier = modifier.then(heightModifier)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            factory = { ctx ->
                try {
                    WebView(ctx).apply {
                        webViewRef = this
                        tag = htmlContent
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        addJavascriptInterface(
                            KaTeXBridge(
                                onHeight = { h -> reportedHeightDp = h },
                                onLink = {}
                            ),
                            "AndroidBridge"
                        )

                        loadDataWithBaseURL(
                            "file:///android_asset/katex/",
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                } catch (_: Exception) {
                    android.view.View(ctx)
                }
            },
            update = { webView ->
                val currentTag = webView.tag as? String
                if (currentTag != htmlContent && webView is WebView) {
                    webView.tag = htmlContent
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/katex/",
                        htmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )
    }
}

/**
 * High-performance, offline KaTeX Rich Text View.
 * Renders paragraphs, option texts, explanations, or questions containing inline or display math
 * mixed with Markdown formatting, identical to Gemini, Claude, and ChatGPT mobile apps.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXRichTextView(
    text: String,
    modifier: Modifier = Modifier,
    onChapterClick: (String) -> Unit = {},
    isClickableCard: Boolean = false,
    fontSize: TextUnit = 16.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    selectable: Boolean = true,
    minHeight: Dp = 24.dp
) {
    val context = LocalContext.current
    val textColorHex = remember(color) {
        String.format("#%06X", 0xFFFFFF and color.toArgb())
    }
    val primaryColorHex = remember(primaryColor) {
        String.format("#%06X", 0xFFFFFF and primaryColor.toArgb())
    }
    val fontSizePx = remember(fontSize) { fontSize.value.toInt() }

    var reportedHeightDp by remember(text) { mutableIntStateOf(0) }

    val formattedContent = remember(text) {
        ensureDelimiters(text)
    }

    val htmlContent = remember(formattedContent, textColorHex, primaryColorHex, fontSizePx, isClickableCard) {
        buildKaTeXRichTextHtml(
            markdown = formattedContent,
            textColorHex = textColorHex,
            primaryColorHex = primaryColorHex,
            fontSizePx = fontSizePx,
            isClickableCard = isClickableCard,
            selectable = selectable && !isClickableCard
        )
    }

    val heightModifier = if (reportedHeightDp > 0) {
        Modifier.height(reportedHeightDp.dp)
    } else {
        Modifier.heightIn(min = minHeight)
    }

    var webViewRef by remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }

    DisposableEffect(formattedContent) {
        onDispose {
            try {
                webViewRef?.let { wv ->
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.destroy()
                }
            } catch (_: Exception) {}
            webViewRef = null
        }
    }

    Box(modifier = modifier.then(heightModifier)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            factory = { ctx ->
                try {
                    WebView(ctx).apply {
                        webViewRef = this
                        tag = htmlContent
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith("opened://chapter/")) {
                                    val title = Uri.decode(url.removePrefix("opened://chapter/"))
                                    onChapterClick(title)
                                    return true
                                }
                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                        }

                        addJavascriptInterface(
                            KaTeXBridge(
                                onHeight = { h -> reportedHeightDp = h },
                                onLink = { title -> onChapterClick(title) }
                            ),
                            "AndroidBridge"
                        )

                        loadDataWithBaseURL(
                            "file:///android_asset/katex/",
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                } catch (_: Exception) {
                    android.view.View(ctx)
                }
            },
            update = { webView ->
                val currentTag = webView.tag as? String
                if (currentTag != htmlContent && webView is WebView) {
                    webView.tag = htmlContent
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/katex/",
                        htmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )
    }
}

/**
 * Escapes HTML characters while preserving math strings.
 */
private fun escapeHtml(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
        when (ch) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(ch)
        }
    }
    return out.toString()
}

/**
 * Converts basic markdown formatting (bold, italic, code, wiki links) into HTML.
 */
private fun markdownToHtml(raw: String): String {
    // Process [[Wiki Links]] (with or without backticks)
    var result = raw.replace(Regex("""`?\[\[(.*?)\]\]`?""")) { match ->
        val title = match.groupValues[1].trim()
        val encoded = Uri.encode(title)
        """<a href="opened://chapter/$encoded" class="wiki-link">$title</a>"""
    }

    // Unescape common escaped symbols e.g. 80\% -> 80%
    result = result.replace("\\%", "%").replace("\\$", "$")

    // Process [Markdown Links](url)
    result = result.replace(Regex("""\[(.*?)\]\((.*?)\)""")) { match ->
        val label = match.groupValues[1]
        val url = match.groupValues[2]
        """<a href="$url" class="md-link">$label</a>"""
    }

    // Process `code`
    result = result.replace(Regex("""`([^`]+)`""")) { match ->
        val code = escapeHtml(match.groupValues[1])
        """<code>$code</code>"""
    }

    // Process **bold**
    result = result.replace(Regex("""\*\*(.*?)\*\*""")) { match ->
        """<strong>${match.groupValues[1]}</strong>"""
    }

    // Process *italic*
    result = result.replace(Regex("""\*(.*?)\*""")) { match ->
        """<em>${match.groupValues[1]}</em>"""
    }

    // Process double newlines into paragraphs, single into br
    result = result.replace("\n\n", "</p><p>").replace("\n", "<br>")

    return "<p>$result</p>"
}

/**
 * Builds the standalone HTML page for rendering display LaTeX with KaTeX.
 */
private fun buildKaTeXDisplayHtml(
    latex: String,
    textColorHex: String,
    fontSizePx: Int,
    isDisplayMode: Boolean
): String {
    // Escaped string literal for JS
    val escapedLatex = latex
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("\$", "\\$")
        .replace("\r", "")
        .replace("\n", " ")

    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="katex.min.css">
<style>
  * {
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
  }
  html, body {
    margin: 0;
    padding: 0;
    height: auto;
    min-height: 0;
    background-color: transparent;
    color: $textColorHex;
    font-size: ${fontSizePx}px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    overflow: hidden;
  }
  #math-container {
    padding: 2px 2px;
    display: flex;
    justify-content: ${if (isDisplayMode) "center" else "flex-start"};
    align-items: center;
    min-height: 20px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  #math-container::-webkit-scrollbar {
    display: none;
  }
  .katex {
    color: $textColorHex;
    font-size: 1.15em;
  }
  .katex-display {
    margin: 0.15em 0;
  }
</style>
<script src="katex.min.js"></script>
<script src="contrib/mhchem.min.js"></script>
</head>
<body>
<div id="math-container"></div>
<script>
var lastH = 0;
function reportHeight() {
    var elem = document.getElementById("math-container");
    if (!elem) return;
    var h = Math.ceil(elem.getBoundingClientRect().height || elem.offsetHeight || elem.scrollHeight);
    if (h > 0 && Math.abs(h - lastH) >= 2) {
        lastH = h;
        if (window.AndroidBridge && window.AndroidBridge.onHeightChanged) {
            window.AndroidBridge.onHeightChanged(h);
        }
    }
}

function render() {
    try {
        katex.render(`$escapedLatex`, document.getElementById("math-container"), {
            displayMode: $isDisplayMode,
            throwOnError: false
        });
    } catch(e) {
        document.getElementById("math-container").innerText = `$escapedLatex`;
    }
    reportHeight();
    setTimeout(reportHeight, 50);
}

window.addEventListener("load", render);
document.addEventListener("DOMContentLoaded", render);
</script>
</body>
</html>
""".trimIndent()
}

/**
 * Builds the HTML page for rendering mixed text, markdown, and inline/display math.
 */
private fun buildKaTeXRichTextHtml(
    markdown: String,
    textColorHex: String,
    primaryColorHex: String,
    fontSizePx: Int,
    isClickableCard: Boolean,
    selectable: Boolean
): String {
    val bodyHtml = markdownToHtml(markdown)

    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="katex.min.css">
<style>
  * {
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
  }
  html, body {
    margin: 0;
    padding: 0;
    background-color: transparent;
    color: $textColorHex;
    font-size: ${fontSizePx}px;
    line-height: 1.55;
    word-break: break-word;
    overflow-wrap: break-word;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    ${if (isClickableCard) "pointer-events: none; user-select: none; -webkit-user-select: none;" else if (selectable) "user-select: text; -webkit-user-select: text;" else "user-select: none; -webkit-user-select: none;"}
  }
  p {
    margin: 0 0 0.5em 0;
  }
  p:last-child {
    margin-bottom: 0;
  }
  .katex {
    color: $textColorHex;
    font-size: 1.1em;
  }
  .katex-display {
    margin: 0.5em 0;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 4px 0;
    text-align: center;
    -webkit-overflow-scrolling: touch;
  }
  .katex-display::-webkit-scrollbar {
    display: none;
  }
  a.wiki-link {
    color: $primaryColorHex;
    text-decoration: underline;
    font-weight: 600;
  }
  a.md-link {
    color: $primaryColorHex;
    text-decoration: underline;
  }
  code {
    font-family: monospace;
    background: rgba(128, 128, 128, 0.15);
    padding: 2px 4px;
    border-radius: 4px;
    font-size: 0.9em;
  }
</style>
<script src="katex.min.js"></script>
<script src="contrib/mhchem.min.js"></script>
<script src="contrib/auto-render.min.js"></script>
</head>
<body>
<div id="content">$bodyHtml</div>
<script>
var lastH = 0;
function reportHeight() {
    var elem = document.getElementById("content");
    if (!elem) return;
    var h = Math.ceil(elem.getBoundingClientRect().height || elem.offsetHeight || elem.scrollHeight);
    if (h > 0 && Math.abs(h - lastH) >= 2) {
        lastH = h;
        if (window.AndroidBridge && window.AndroidBridge.onHeightChanged) {
            window.AndroidBridge.onHeightChanged(h);
        }
    }
}

function render() {
    try {
        renderMathInElement(document.getElementById("content"), {
            delimiters: [
                {left: "$$", right: "$$", display: true},
                {left: "\\[", right: "\\]", display: true},
                {left: "\\begin{equation}", right: "\\end{equation}", display: true},
                {left: "\\begin{align}", right: "\\end{align}", display: true},
                {left: "\\begin{aligned}", right: "\\end{aligned}", display: true},
                {left: "\\begin{gather}", right: "\\end{gather}", display: true},
                {left: "\\begin{matrix}", right: "\\end{matrix}", display: true},
                {left: "\\begin{pmatrix}", right: "\\end{pmatrix}", display: true},
                {left: "\\begin{bmatrix}", right: "\\end{bmatrix}", display: true},
                {left: "\\begin{cases}", right: "\\end{cases}", display: true},
                {left: "$", right: "$", display: false},
                {left: "\\(", right: "\\)", display: false}
            ],
            throwOnError: false
        });
    } catch(e) {
        console.error("KaTeX auto-render error:", e);
    }
    reportHeight();
    setTimeout(reportHeight, 50);
}

window.addEventListener("load", render);
document.addEventListener("DOMContentLoaded", render);
</script>
</body>
</html>
""".trimIndent()
}
