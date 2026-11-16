package com.example.core.parsing

import com.example.core.network.BtuRoutes
import com.example.core.network.ResponseClassifier
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Jsoup contract parser for BTU Classroom course detail pages (/ge/student/me/course/index/{id}).
 * Preserves actual grading category names, keeps missing scores nullable, and validates external links
 * strictly against the approved BTU origin.
 */
class CourseDetailParser : BtuPageParser<ParsedCourseDetail> {

  override fun parse(html: String): ParseResult<ParsedCourseDetail> {
    if (html.isBlank()) {
      return ParseResult.UnsupportedStructure("Empty course detail HTML")
    }
    if (ResponseClassifier.looksLikeLoginPage(html)) {
      return ParseResult.SessionExpired
    }
    val lower = html.lowercase()
    if (lower.contains("<title>just a moment...</title>") ||
      (lower.contains("id=\"challenge-form\"") && lower.contains("cf_chl_opt"))
    ) {
      return ParseResult.TemporarilyUnavailable
    }

    val doc = Jsoup.parse(html)
    val bodyText = doc.body()?.text().orEmpty()
    if (bodyText.contains("დროებით მიუწვდომელია", ignoreCase = true) ||
      bodyText.contains("temporarily unavailable", ignoreCase = true) ||
      bodyText.contains("under maintenance", ignoreCase = true)
    ) {
      return ParseResult.TemporarilyUnavailable
    }

    val title = extractCourseTitle(doc)
    val kvMetadata = extractKeyValueMetadata(doc)
    val gradeComponents = extractGradeComponents(doc)
    val resources = extractSafeResources(doc)
    val syllabus = doc.selectFirst(".course-syllabus, .syllabus-summary, [data-syllabus], .course-description")
      ?.text()?.trim()?.ifBlank { null }

    // Require clear course context (title or recognized grade/metadata structure)
    val hasCourseContext = !title.isNullOrBlank() ||
      kvMetadata.isNotEmpty() ||
      gradeComponents.isNotEmpty()

    if (!hasCourseContext) {
      return ParseResult.UnsupportedStructure("Course detail headings, metadata, and grade components not recognized")
    }

    val finalTitle = title ?: kvMetadata["course"] ?: kvMetadata["კურსის დასახელება"]
      ?: return ParseResult.UnsupportedStructure("Missing course title in detail markup")

    val code = kvMetadata.findByKeys("კოდი", "course code", "code")
    val lecturer = kvMetadata.findByKeys("ლექტორი", "პედაგოგი", "lecturer", "instructor")
    val semester = kvMetadata.findByKeys("სემესტრი", "semester", "term")
    val credits = ParsingNumberUtils.parseNullableDecimal(kvMetadata.findByKeys("კრედიტი", "კრედიტები", "credits", "credit"))
    val minCredit = ParsingNumberUtils.parseNullableDecimal(kvMetadata.findByKeys("მინ. კრედიტი", "min credit", "min"))
    val totalScore = ParsingNumberUtils.parseNullableDecimal(
      kvMetadata.findByKeys("ჯამური ქულა", "ქულა", "სულ", "total score", "total", "score"),
    )

    return ParseResult.Success(
      ParsedCourseDetail(
        title = finalTitle,
        code = code,
        semesterName = semester,
        lecturer = lecturer,
        credits = credits,
        minCredit = minCredit,
        totalScore = totalScore,
        syllabusSummary = syllabus,
        gradeComponents = gradeComponents,
        resources = resources,
      ),
    )
  }

  private fun extractCourseTitle(doc: Document): String? {
    val candidates = listOf(
      ".course-title",
      "[data-course-title]",
      ".page-header h1",
      ".page-title",
      "main h1",
      "main h2",
      "h1",
      "h2",
    )
    for (selector in candidates) {
      val text = doc.selectFirst(selector)?.text()?.trim()?.ifBlank { null }
      if (text != null && !text.equals("BTU Classroom", ignoreCase = true)) {
        return text
      }
    }
    return null
  }

  private fun extractKeyValueMetadata(doc: Document): Map<String, String> {
    val result = linkedMapOf<String, String>()

    // 1. Definition lists <dt>/<dd>
    val dts = doc.select("dl dt")
    for (dt in dts) {
      val dd = dt.nextElementSibling()?.takeIf { it.tagName().equals("dd", ignoreCase = true) } ?: continue
      val k = dt.text().trim().trimEnd(':').lowercase()
      val v = dd.text().trim()
      if (k.isNotBlank() && v.isNotBlank()) {
        result[k] = v
      }
    }

    // 2. Two-column metadata tables (<th>key</th><td>val</td> or <td>key</td><td>val</td>)
    for (table in doc.select("table")) {
      if (isGradeBreakdownTable(table)) continue
      for (row in table.select("tr")) {
        val cells = row.select("> th, > td")
        if (cells.size == 2) {
          val k = cells[0].text().trim().trimEnd(':').lowercase()
          val v = cells[1].text().trim()
          if (k.isNotBlank() && v.isNotBlank() && k.length <= 48) {
            result.putIfAbsent(k, v)
          }
        }
      }
    }

    // 3. Explicit data attributes or labeled spans
    for (item in doc.select("[data-meta-label]")) {
      val k = item.attr("data-meta-label").trim().lowercase()
      val v = item.text().trim()
      if (k.isNotBlank() && v.isNotBlank()) {
        result.putIfAbsent(k, v)
      }
    }

    return result
  }

  private fun isGradeBreakdownTable(table: Element): Boolean {
    val headerText = table.select("thead th, tr:first-child th").joinToString(" ") { it.text().lowercase() }
    return (headerText.contains("კომპონენტ") || headerText.contains("component") || headerText.contains("შეფასებ") || headerText.contains("activity")) &&
      (headerText.contains("ქულ") || headerText.contains("score") || headerText.contains("grade") || headerText.contains("max") || headerText.contains("მაქს"))
  }

  private fun extractGradeComponents(doc: Document): List<ParsedGradeComponent> {
    val components = mutableListOf<ParsedGradeComponent>()
    for (table in doc.select("table")) {
      if (!isGradeBreakdownTable(table)) continue
      val headerCells = table.select("thead tr").lastOrNull()?.select("th, td")
        ?: table.selectFirst("tr")?.select("th")
        ?: continue

      var nameIdx: Int? = null
      var scoreIdx: Int? = null
      var maxIdx: Int? = null
      var noteIdx: Int? = null

      headerCells.forEachIndexed { idx, cell ->
        val h = cell.text().trim().lowercase()
        when {
          nameIdx == null && (h.contains("კომპონენტ") || h.contains("დასახელებ") || h.contains("აქტივობ") || h.contains("component") || h.contains("category") || h.contains("name") || h.contains("activity")) ->
            nameIdx = idx
          maxIdx == null && (h.contains("მაქს") || h.contains("max") || h.contains("limit")) ->
            maxIdx = idx
          scoreIdx == null && (h.contains("ქულ") || h.contains("შეფასებ") || h.contains("score") || h.contains("earned") || h.contains("grade")) ->
            scoreIdx = idx
          noteIdx == null && (h.contains("შენიშვნ") || h.contains("კომენტარ") || h.contains("note") || h.contains("comment")) ->
            noteIdx = idx
        }
      }

      if (nameIdx == null) continue

      val rows = table.select("tbody tr").ifEmpty { table.select("tr").drop(1) }
      for (row in rows) {
        val cells = row.select("> td, > th")
        val name = cells.getOrNull(nameIdx!!)?.text()?.trim()?.ifBlank { null } ?: continue
        val earned = scoreIdx?.let { ParsingNumberUtils.parseNullableDecimal(cells.getOrNull(it)?.text()) }
        val max = maxIdx?.let { ParsingNumberUtils.parseNullableDecimal(cells.getOrNull(it)?.text()) }
        val notes = noteIdx?.let { cells.getOrNull(it)?.text()?.trim()?.ifBlank { null } }
        components.add(
          ParsedGradeComponent(
            categoryName = name,
            score = earned,
            maxScore = max,
            notes = notes,
          ),
        )
      }
    }
    return components
  }

  private fun extractSafeResources(doc: Document): List<ParsedCourseResource> {
    val container = doc.selectFirst(".course-resources, .resources-list, [data-resources], main") ?: doc.body()
      ?: return emptyList()

    val seenUrls = mutableSetOf<String>()
    val resources = mutableListOf<ParsedCourseResource>()
    for (anchor in container.select("a[href]")) {
      if (anchor.parents().any { it.tagName().equals("nav", ignoreCase = true) || it.hasClass("navbar") }) {
        continue
      }
      val rawHref = anchor.attr("href")
      val lowerHref = rawHref.lowercase()
      if (lowerHref.contains("/ge/login") || lowerHref.contains("/ge/student/me/courses")) {
        continue
      }
      val safeUrl = BtuRoutes.resolveSafeBtuUrl(rawHref) ?: continue
      val title = anchor.text().trim().ifBlank { null } ?: continue
      if (seenUrls.add(safeUrl)) {
        resources.add(ParsedCourseResource(title = title, url = safeUrl))
      }
    }
    return resources
  }

  private fun Map<String, String>.findByKeys(vararg candidates: String): String? {
    for (candidate in candidates) {
      this[candidate.lowercase()]?.let { return it }
    }
    for ((k, v) in this) {
      if (candidates.any { c -> k.contains(c, ignoreCase = true) }) {
        return v
      }
    }
    return null
  }
}
