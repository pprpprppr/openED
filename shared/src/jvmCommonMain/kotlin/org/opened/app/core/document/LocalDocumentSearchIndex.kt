package org.opened.app.core.document

import org.opened.app.core.database.entity.DocumentChunkEntity
import kotlin.math.ln
import kotlin.math.max

data class RankedDocumentChunk(
    val chunk: DocumentChunkEntity,
    val score: Float,
    val highlightedSnippet: String
)

object LocalDocumentSearchIndex {

    private const val K1 = 1.5
    private const val B = 0.75

    /**
     * Rank chunks using BM25 relevance scoring.
     */
    fun search(
        query: String,
        chunks: List<DocumentChunkEntity>,
        topK: Int = 5
    ): List<RankedDocumentChunk> {
        if (chunks.isEmpty() || query.isBlank()) return emptyList()

        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        val n = chunks.size.toDouble()
        val tokenizedChunks = chunks.map { tokenize(it.rawText) }
        val avgdl = tokenizedChunks.map { it.size }.average().coerceAtLeast(1.0)

        // Compute document frequency (DF) for each query term
        val df = mutableMapOf<String, Int>()
        for (term in queryTerms) {
            val count = tokenizedChunks.count { it.contains(term) }
            df[term] = count
        }

        val ranked = chunks.mapIndexed { index, chunk ->
            val docTokens = tokenizedChunks[index]
            val docLength = docTokens.size.toDouble()
            val termFreqs = mutableMapOf<String, Int>()
            for (t in docTokens) {
                termFreqs[t] = (termFreqs[t] ?: 0) + 1
            }

            var bm25Score = 0.0
            for (term in queryTerms) {
                val docFreq = df[term] ?: 0
                if (docFreq == 0) continue

                // Robertson-Spärck Jones IDF
                val idf = ln(max(0.0, (n - docFreq + 0.5) / (docFreq + 0.5)) + 1.0)
                val tf = (termFreqs[term] ?: 0).toDouble()
                val numerator = tf * (K1 + 1.0)
                val denominator = tf + K1 * (1.0 - B + B * (docLength / avgdl))

                bm25Score += idf * (numerator / denominator)
            }

            val snippet = generateSnippet(chunk.rawText, queryTerms)
            RankedDocumentChunk(
                chunk = chunk,
                score = bm25Score.toFloat(),
                highlightedSnippet = snippet
            )
        }
            .filter { it.score > 0.05f }
            .sortedByDescending { it.score }
            .take(topK)

        return ranked
    }

    private fun generateSnippet(text: String, queryTerms: List<String>): String {
        val lowerText = text.lowercase()
        var bestIndex = -1
        for (term in queryTerms) {
            val idx = lowerText.indexOf(term)
            if (idx != -1) {
                bestIndex = idx
                break
            }
        }

        if (bestIndex == -1) {
            return text.take(200).trim() + "..."
        }

        val start = max(0, bestIndex - 60)
        val end = kotlin.math.min(text.length, bestIndex + 140)
        val snippet = text.substring(start, end).trim()
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        return "$prefix$snippet$suffix"
    }

    private fun tokenize(text: String): List<String> {
        val stopwords = setOf(
            "the", "and", "that", "this", "with", "from", "for", "have", "with",
            "they", "will", "would", "about", "there", "their", "which", "could",
            "should", "what", "how", "why", "when", "where", "who", "whom"
        )
        return text.lowercase()
            .split("\\W+".toRegex())
            .filter { it.length > 2 && it !in stopwords }
    }
}
