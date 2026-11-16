package com.example

import com.example.core.parsing.CourseDetailParser
import com.example.core.parsing.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseDetailParserTest {

  private val parser = CourseDetailParser()

  @Test
  fun `parses course metadata, grade components with nullable scores, and safe BTU resource links`() {
    val html = """
      <html>
        <body>
          <nav class="navbar"><a href="/ge/login/logout">Logout</a></nav>
          <main>
            <h1 class="course-title">ალგორითმები და მონაცემთა სტრუქტურები</h1>
            <table>
              <tr><th>კოდი</th><td>CS301</td></tr>
              <tr><th>ლექტორი</th><td>დოქტ. ნინო კაპანაძე</td></tr>
              <tr><th>სემესტრი</th><td>2026 შემოდგომა</td></tr>
              <tr><th>კრედიტი</th><td>6</td></tr>
              <tr><th>ჯამური ქულა</th><td>74.5</td></tr>
            </table>
            <div class="course-syllabus">სილაბუსი მოიცავს გრაფებისა და დინამიკური პროგრამირების ალგორითმებს.</div>
            <table>
              <thead>
                <tr><th>კომპონენტი</th><th>მაქს. ქულა</th><th>ქულა</th><th>შენიშვნა</th></tr>
              </thead>
              <tbody>
                <tr><td>შუალედური გამოცდა</td><td>30</td><td>26.5</td><td>ჩაბარებულია</td></tr>
                <tr><td>პრაქტიკული დავალებები</td><td>30</td><td>28</td><td></td></tr>
                <tr><td>ფინალური გამოცდა</td><td>40</td><td>—</td><td>დაგეგმილია</td></tr>
              </tbody>
            </table>
            <div class="course-resources">
              <a href="/files/syllabus_cs301.pdf">კურსის სილაბუსი (PDF)</a>
              <a href="https://malicious.example.com/phish.pdf">External Phishing Link</a>
              <a href="javascript:void(0)">Ignore JS</a>
            </div>
          </main>
        </body>
      </html>
    """.trimIndent()

    val result = parser.parse(html)
    assertTrue(result is ParseResult.Success)
    val detail = (result as ParseResult.Success).data

    assertEquals("ალგორითმები და მონაცემთა სტრუქტურები", detail.title)
    assertEquals("CS301", detail.code)
    assertEquals("დოქტ. ნინო კაპანაძე", detail.lecturer)
    assertEquals("2026 შემოდგომა", detail.semesterName)
    assertEquals(6.0, detail.credits)
    assertEquals(74.5, detail.totalScore)
    assertEquals(3, detail.gradeComponents.size)
    assertEquals(26.5, detail.gradeComponents[0].score)
    assertNull(detail.gradeComponents[2].score)
    assertEquals(40.0, detail.gradeComponents[2].maxScore)

    // External non-BTU and javascript links are strictly stripped
    assertEquals(1, detail.resources.size)
    assertEquals("https://classroom.btu.edu.ge/files/syllabus_cs301.pdf", detail.resources[0].url)
  }

  @Test
  fun `returns SessionExpired on login HTML and UnsupportedStructure on unrecognized page`() {
    val loginHtml = "<html><body><a href='/ge/login/index'>Login</a></body></html>"
    assertEquals(ParseResult.SessionExpired, parser.parse(loginHtml))

    val unrecognized = "<html><body><span>hello</span></body></html>"
    assertTrue(parser.parse(unrecognized) is ParseResult.UnsupportedStructure)
  }
}
