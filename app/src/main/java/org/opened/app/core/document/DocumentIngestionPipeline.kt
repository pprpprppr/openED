package org.opened.app.core.document

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

data class ParsedDocument(
    val title: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val summary: String,
    val chunks: List<DocumentChunkData>
)

data class DocumentChunkData(
    val chunkIndex: Int,
    val pageNumber: Int,
    val sectionTitle: String,
    val rawText: String,
    val tokenEstimate: Int,
    val keywords: String
)

object DocumentIngestionPipeline {

    private const val TARGET_CHUNK_WORDS = 350
    private const val CHUNK_OVERLAP_WORDS = 50

    /**
     * Ingest and parse PDF streams into structured page chunks.
     */
    fun parsePdf(inputStream: InputStream, fileName: String, fileSizeBytes: Long = 0L): ParsedDocument {
        val bytes = inputStream.readBytes()
        val textBuilder = StringBuilder()
        var pageCount = 1

        // Extract text streams from PDF literal strings & Tj / TJ operators
        val pdfString = String(bytes, Charsets.ISO_8859_1)
        val pageMatches = Regex("/Type\\s*/Page\\b").findAll(pdfString).count()
        if (pageMatches > 0) {
            pageCount = pageMatches
        }

        // Extract literal text blocks within parentheses e.g. (Hello World) Tj or [(Hello) 10 (World)] TJ
        val tjRegex = Regex("\\(([^\\\\\\)]*(?:\\\\.[^\\\\\\)]*)*)\\)\\s*Tj")
        val tjMatches = tjRegex.findAll(pdfString)
        val extractedLines = mutableListOf<String>()

        for (match in tjMatches) {
            val raw = match.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\(", "(")
                .replace("\\)", ")")
                .replace("\\\\", "\\")
            if (raw.isNotBlank()) {
                extractedLines.add(raw.trim())
            }
        }

        val fullText = if (extractedLines.isNotEmpty()) {
            extractedLines.joinToString(" ")
        } else {
            // Clean fallback: extract printable ASCII/UTF-8 character sequences
            val printable = pdfString.filter { it in ' '..'~' || it == '\n' || it == '\t' }
                .replace(Regex("[^a-zA-Z0-9.,;:?!'\"()\\s-]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            printable.ifBlank { "PDF content loaded from $fileName" }
        }

        val chunks = chunkText(fullText, pageCount = pageCount, title = fileName)
        val title = fileName.removeSuffix(".pdf").replace("[_-]".toRegex(), " ")

        return ParsedDocument(
            title = title,
            fileName = fileName,
            mimeType = "application/pdf",
            fileSizeBytes = if (fileSizeBytes > 0L) fileSizeBytes else bytes.size.toLong(),
            pageCount = pageCount,
            summary = fullText.take(250).trim() + "...",
            chunks = chunks
        )
    }

    /**
     * Ingest and parse EPUB files:
     * Unpacks ZIP, inspects META-INF/container.xml and content.opf,
     * extracts XHTML chapter flow in spine order.
     */
    fun parseEpub(inputStream: InputStream, fileName: String, fileSizeBytes: Long = 0L): ParsedDocument {
        val bytes = inputStream.readBytes()
        var title = fileName.removeSuffix(".epub").replace("[_-]".toRegex(), " ")
        val chapterTexts = mutableListOf<Pair<String, String>>() // (chapterTitle, text)

        try {
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }

            // 1. Find OPF package file
            var opfPath: String? = null
            val containerBytes = entries["META-INF/container.xml"]
            if (containerBytes != null) {
                val containerXml = String(containerBytes, Charsets.UTF_8)
                val fullPathMatch = Regex("full-path=\"([^\"]+)\"").find(containerXml)
                opfPath = fullPathMatch?.groupValues?.get(1)
            }

            val opfDir = if (opfPath != null && opfPath.contains("/")) {
                opfPath.substringBeforeLast("/") + "/"
            } else ""

            val opfContent = opfPath?.let { entries[it] }?.let { String(it, Charsets.UTF_8) }
            if (opfContent != null) {
                // Try extract book title from metadata
                val titleMatch = Regex("<dc:title[^>]*>([^<]+)</dc:title>", RegexOption.IGNORE_CASE).find(opfContent)
                titleMatch?.groupValues?.get(1)?.let {
                    if (it.isNotBlank()) title = it.trim()
                }

                // Extract manifest items
                val manifestItems = mutableMapOf<String, String>() // id -> href
                val itemRegex = Regex("<item[^>]+id=\"([^\"]+)\"[^>]+href=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                for (match in itemRegex.findAll(opfContent)) {
                    manifestItems[match.groupValues[1]] = match.groupValues[2]
                }
                // Check reverse attribute order href then id
                val itemRegexAlt = Regex("<item[^>]+href=\"([^\"]+)\"[^>]+id=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                for (match in itemRegexAlt.findAll(opfContent)) {
                    manifestItems[match.groupValues[2]] = match.groupValues[1]
                }

                // Extract spine items in order
                val spineRegex = Regex("<itemref[^>]+idref=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                for (match in spineRegex.findAll(opfContent)) {
                    val idref = match.groupValues[1]
                    val href = manifestItems[idref] ?: continue
                    val resolvedPath = if (opfDir.isNotEmpty() && !href.startsWith("/")) opfDir + href else href
                    val htmlBytes = entries[resolvedPath] ?: entries[href]
                    if (htmlBytes != null) {
                        val html = String(htmlBytes, Charsets.UTF_8)
                        val (chapTitle, text) = extractCleanHtmlText(html, defaultTitle = "Section ${chapterTexts.size + 1}")
                        if (text.isNotBlank()) {
                            chapterTexts.add(chapTitle to text)
                        }
                    }
                }
            }

            // Fallback: extract all html/xhtml files found if spine parsing was empty
            if (chapterTexts.isEmpty()) {
                entries.filter { it.key.endsWith(".xhtml") || it.key.endsWith(".html") }
                    .forEach { (name, b) ->
                        val html = String(b, Charsets.UTF_8)
                        val (chapTitle, text) = extractCleanHtmlText(html, defaultTitle = name.substringAfterLast("/"))
                        if (text.isNotBlank()) {
                            chapterTexts.add(chapTitle to text)
                        }
                    }
            }
        } catch (_: Exception) {
            // Graceful fallback
        }

        val allChunks = mutableListOf<DocumentChunkData>()
        var globalIndex = 0

        chapterTexts.forEachIndexed { chapIdx, (chapTitle, text) ->
            val pageNum = chapIdx + 1
            val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.size <= TARGET_CHUNK_WORDS) {
                allChunks.add(
                    DocumentChunkData(
                        chunkIndex = globalIndex++,
                        pageNumber = pageNum,
                        sectionTitle = chapTitle,
                        rawText = text,
                        tokenEstimate = words.size,
                        keywords = extractKeywords(text)
                    )
                )
            } else {
                var start = 0
                while (start < words.size) {
                    val end = min(start + TARGET_CHUNK_WORDS, words.size)
                    val chunkWords = words.subList(start, end)
                    val chunkText = chunkWords.joinToString(" ")
                    allChunks.add(
                        DocumentChunkData(
                            chunkIndex = globalIndex++,
                            pageNumber = pageNum,
                            sectionTitle = chapTitle,
                            rawText = chunkText,
                            tokenEstimate = chunkWords.size,
                            keywords = extractKeywords(chunkText)
                        )
                    )
                    if (end >= words.size) break
                    start += (TARGET_CHUNK_WORDS - CHUNK_OVERLAP_WORDS)
                }
            }
        }

        val summary = chapterTexts.firstOrNull()?.second?.take(250)?.trim().orEmpty() + "..."

        return ParsedDocument(
            title = title,
            fileName = fileName,
            mimeType = "application/epub+zip",
            fileSizeBytes = if (fileSizeBytes > 0L) fileSizeBytes else bytes.size.toLong(),
            pageCount = max(1, chapterTexts.size),
            summary = summary,
            chunks = allChunks
        )
    }

    /**
     * Ingest and parse Markdown files into structured section chunks.
     */
    fun parseMarkdown(text: String, fileName: String): ParsedDocument {
        val lines = text.lines()
        val titleLine = lines.firstOrNull { it.startsWith("# ") }
        val title = titleLine?.removePrefix("# ")?.trim()
            ?: fileName.removeSuffix(".md").removeSuffix(".markdown").replace("[_-]".toRegex(), " ")

        val sections = mutableListOf<Pair<String, String>>()
        var currentHeading = title
        val currentContent = StringBuilder()

        for (line in lines) {
            if (line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ")) {
                if (currentContent.isNotBlank()) {
                    sections.add(currentHeading to currentContent.toString().trim())
                    currentContent.clear()
                }
                currentHeading = line.trimStart('#').trim()
            } else {
                currentContent.appendLine(line)
            }
        }
        if (currentContent.isNotBlank()) {
            sections.add(currentHeading to currentContent.toString().trim())
        }

        val allChunks = mutableListOf<DocumentChunkData>()
        var globalIndex = 0

        sections.forEachIndexed { secIdx, (heading, secText) ->
            val pageNum = secIdx + 1
            val words = secText.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.size <= TARGET_CHUNK_WORDS) {
                allChunks.add(
                    DocumentChunkData(
                        chunkIndex = globalIndex++,
                        pageNumber = pageNum,
                        sectionTitle = heading,
                        rawText = secText,
                        tokenEstimate = words.size,
                        keywords = extractKeywords(secText)
                    )
                )
            } else {
                var start = 0
                while (start < words.size) {
                    val end = min(start + TARGET_CHUNK_WORDS, words.size)
                    val chunkWords = words.subList(start, end)
                    val chunkText = chunkWords.joinToString(" ")
                    allChunks.add(
                        DocumentChunkData(
                            chunkIndex = globalIndex++,
                            pageNumber = pageNum,
                            sectionTitle = heading,
                            rawText = chunkText,
                            tokenEstimate = chunkWords.size,
                            keywords = extractKeywords(chunkText)
                        )
                    )
                    if (end >= words.size) break
                    start += (TARGET_CHUNK_WORDS - CHUNK_OVERLAP_WORDS)
                }
            }
        }

        return ParsedDocument(
            title = title,
            fileName = fileName,
            mimeType = "text/markdown",
            fileSizeBytes = text.toByteArray().size.toLong(),
            pageCount = max(1, sections.size),
            summary = text.take(250).trim() + "...",
            chunks = allChunks
        )
    }

    /**
     * Ingest and parse plain text / user notes.
     */
    fun parsePlainText(text: String, fileName: String): ParsedDocument {
        val title = fileName.removeSuffix(".txt").replace("[_-]".toRegex(), " ")
        val paragraphs = text.split("\n\n+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }
        val chunks = mutableListOf<DocumentChunkData>()
        var globalIndex = 0
        var currentChunkWords = mutableListOf<String>()
        var pageNum = 1

        for (p in paragraphs) {
            val words = p.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (currentChunkWords.size + words.size > TARGET_CHUNK_WORDS && currentChunkWords.isNotEmpty()) {
                val chunkText = currentChunkWords.joinToString(" ")
                chunks.add(
                    DocumentChunkData(
                        chunkIndex = globalIndex++,
                        pageNumber = pageNum++,
                        sectionTitle = "Page $pageNum",
                        rawText = chunkText,
                        tokenEstimate = currentChunkWords.size,
                        keywords = extractKeywords(chunkText)
                    )
                )
                currentChunkWords = words.toMutableList()
            } else {
                currentChunkWords.addAll(words)
            }
        }

        if (currentChunkWords.isNotEmpty()) {
            val chunkText = currentChunkWords.joinToString(" ")
            chunks.add(
                DocumentChunkData(
                    chunkIndex = globalIndex++,
                    pageNumber = pageNum,
                    sectionTitle = "Page $pageNum",
                    rawText = chunkText,
                    tokenEstimate = currentChunkWords.size,
                    keywords = extractKeywords(chunkText)
                )
            )
        }

        return ParsedDocument(
            title = title,
            fileName = fileName,
            mimeType = "text/plain",
            fileSizeBytes = text.toByteArray().size.toLong(),
            pageCount = max(1, pageNum),
            summary = text.take(250).trim() + "...",
            chunks = chunks
        )
    }

    private fun chunkText(fullText: String, pageCount: Int, title: String): List<DocumentChunkData> {
        val words = fullText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val chunks = mutableListOf<DocumentChunkData>()
        var start = 0
        var chunkIndex = 0

        while (start < words.size) {
            val end = min(start + TARGET_CHUNK_WORDS, words.size)
            val chunkWords = words.subList(start, end)
            val chunkText = chunkWords.joinToString(" ")
            val pageNum = if (pageCount > 1) {
                ((start.toDouble() / words.size.toDouble()) * pageCount).toInt() + 1
            } else 1

            chunks.add(
                DocumentChunkData(
                    chunkIndex = chunkIndex++,
                    pageNumber = pageNum,
                    sectionTitle = "$title (Page $pageNum)",
                    rawText = chunkText,
                    tokenEstimate = chunkWords.size,
                    keywords = extractKeywords(chunkText)
                )
            )

            if (end >= words.size) break
            start += (TARGET_CHUNK_WORDS - CHUNK_OVERLAP_WORDS)
        }
        return chunks
    }

    private fun extractCleanHtmlText(html: String, defaultTitle: String): Pair<String, String> {
        var clean = html
        // Remove style & script tags
        clean = clean.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        clean = clean.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")

        // Extract heading if available
        val h1Match = Regex("<h[1-2][^>]*>([^<]+)</h[1-2]>", RegexOption.IGNORE_CASE).find(clean)
        val extractedTitle = h1Match?.groupValues?.get(1)?.trim() ?: defaultTitle

        // Replace block tags with newline
        clean = clean.replace(Regex("</?(p|div|h[1-6]|li|blockquote|br)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        // Remove all other HTML tags
        clean = clean.replace(Regex("<[^>]+>"), " ")
        // Unescape standard entities
        clean = clean.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        // Collapse multiple blank lines
        val text = clean.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        return extractedTitle to text
    }

    private fun extractKeywords(text: String): String {
        val stopwords = setOf(
            "the", "and", "that", "this", "with", "from", "for", "have", "with", "were",
            "they", "will", "would", "about", "there", "their", "which", "could", "should"
        )
        val words = text.lowercase().split("\\W+".toRegex())
            .filter { it.length > 3 && it !in stopwords }
        val freq = mutableMapOf<String, Int>()
        for (w in words) {
            freq[w] = (freq[w] ?: 0) + 1
        }
        return freq.entries.sortedByDescending { it.value }.take(10).joinToString(", ") { it.key }
    }
}
