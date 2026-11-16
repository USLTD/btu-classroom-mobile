package com.example

import com.example.core.parsing.ParseResult
import com.example.core.parsing.SelectedCoursesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedCoursesParserTest {

  private val parser = SelectedCoursesParser()

  @Test
  fun `parses Georgian selected courses table with nullable grades and long titles`() {
    val html = """
      <html>
        <body>
          <div class="student-name">გიორგი ბერიძე</div>
          <select id="semester">
            <option value="2026_fall" selected="selected">2026 შემოდგომა</option>
          </select>
          <table class="table">
            <thead>
              <tr>
                <th>კოდი</th>
                <th>კურსის დასახელება</th>
                <th>კრედიტი</th>
                <th>მინ. კრედიტი</th>
                <th>ქულა</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>CS301</td>
                <td><a href="/ge/student/me/course/index/501">ალგორითმები და მონაცემთა სტრუქტურები გაღრმავებული კურსი</a></td>
                <td>6</td>
                <td>51</td>
                <td>92.5</td>
              </tr>
              <tr>
                <td>CS302</td>
                <td><a href="/ge/student/me/course/index/502">მობილური აპლიკაციების დეველოპმენტი (Kotlin &amp; Compose)</a></td>
                <td>5</td>
                <td>51</td>
                <td>-</td>
              </tr>
              <tr>
                <td>CS303</td>
                <td><a href="/ge/student/me/course/index/503">კიბერუსაფრთხოების საფუძვლები</a></td>
                <td>5</td>
                <td>51</td>
                <td></td>
              </tr>
            </tbody>
          </table>
        </body>
      </html>
    """.trimIndent()

    val result = parser.parse(html)
    assertTrue(result is ParseResult.Success)
    val data = (result as ParseResult.Success).data

    assertEquals("გიორგი ბერიძე", data.studentSummary?.displayName)
    assertEquals(1, data.semesters.size)
    assertEquals("2026 შემოდგომა", data.semesters[0].title)
    assertEquals(3, data.courses.size)

    val first = data.courses[0]
    assertEquals("501", first.courseId)
    assertEquals("CS301", first.code)
    assertEquals("ალგორითმები და მონაცემთა სტრუქტურები გაღრმავებული კურსი", first.title)
    assertEquals(6.0, first.credits)
    assertEquals(51.0, first.minCredit)
    assertEquals(92.5, first.score)

    // Missing grades MUST remain null and never convert to 0.0
    assertNull(data.courses[1].score)
    assertNull(data.courses[2].score)
  }

  @Test
  fun `handles reordered columns and English layout`() {
    val html = """
      <html><body>
        <table>
          <thead>
            <tr>
              <th>Score</th>
              <th>Course Name</th>
              <th>Semester</th>
              <th>Credits</th>
              <th>Code</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>88</td>
              <td><a href="/ge/student/me/course/index/777">Operating Systems</a></td>
              <td>Spring 2026</td>
              <td>6</td>
              <td>SYS201</td>
            </tr>
          </tbody>
        </table>
      </body></html>
    """.trimIndent()

    val result = parser.parse(html) as ParseResult.Success
    val item = result.data.courses.single()
    assertEquals("777", item.courseId)
    assertEquals("SYS201", item.code)
    assertEquals("Operating Systems", item.title)
    assertEquals(88.0, item.score)
    assertEquals(6.0, item.credits)
    assertEquals("Spring 2026", item.semesterName)
  }

  @Test
  fun `distinguishes valid empty table from unsupported structure, maintenance, and login page`() {
    val emptyTableHtml = """
      <html><body>
        <table>
          <thead><tr><th>კოდი</th><th>კურსის დასახელება</th><th>ქულა</th></tr></thead>
          <tbody></tbody>
        </table>
      </body></html>
    """.trimIndent()
    assertEquals(ParseResult.Empty, parser.parse(emptyTableHtml))

    val maintenanceHtml = "<html><body><div class='alert'>მიმდინარეობს ტექნიკური სამუშაოები</div></body></html>"
    assertEquals(ParseResult.TemporarilyUnavailable, parser.parse(maintenanceHtml))

    val loginHtml = "<html><body><a href='/ge/login/oauth/'>Sign in with Google</a></body></html>"
    assertEquals(ParseResult.SessionExpired, parser.parse(loginHtml))

    val brokenHtml = "<html><body><div>Completely changed portal layout</div></body></html>"
    assertTrue(parser.parse(brokenHtml) is ParseResult.UnsupportedStructure)
  }
}
