package com.webclass.plus.util

import com.webclass.plus.data.models.*
import org.jsoup.Jsoup
import java.net.URL

object WebClassHtmlParser {
    fun parseHomeData(html: String, baseUrl: String = "https://webclass.kosen-k.go.jp"): HomeDataResult {
        val doc = Jsoup.parse(html, baseUrl)
        val pageType = "HOME"

        // 1. Extract courses & timetable
        val courses = mutableListOf<Course>()
        val timetable = mutableListOf<TimetableEntry>()
        val table = doc.selectFirst("#schedule-table")
        if (table != null) {
            val links = table.select("td a[href*=\"/course.php/\"]")
            val dueSoonText = "締切が近い課題があります。"
            for (a in links) {
                val rawText = a.text().trim()
                if (rawText.contains("開講期間外")) continue
                
                val isDueSoon = rawText.contains(dueSoonText)
                var messageCount = 0
                val msgRegex = """新着メッセージ\((\d+)\)""".toRegex()
                val msgMatch = msgRegex.find(rawText)
                if (msgMatch != null) {
                    messageCount = msgMatch.groupValues[1].toIntOrNull() ?: 0
                }
                
                val cleanTitle = rawText
                    .replace(dueSoonText, "")
                    .replace("""新着メッセージ\(\d+\)""".toRegex(), "")
                    .replace("""[（\(]新着メッセージ\d+[）\)]""".toRegex(), "")
                    .replace("""^»\s*""".toRegex(), "")
                    .trim()
                
                var unreadCount = 0
                val row = a.closest("tr")
                if (row != null) {
                    val badges = row.select(".badge, .label-danger")
                    for (b in badges) {
                        val num = b.text().trim().toIntOrNull()
                        if (num != null) {
                            unreadCount += num
                        }
                    }
                }
                
                val href = a.attr("href")
                val absoluteUrl = try {
                    URL(URL(baseUrl), href).toString()
                } catch (e: Exception) {
                    href
                }
                
                val codeRegex = """course\.php/([^/]+)""".toRegex()
                val codeMatch = codeRegex.find(href)
                val code = codeMatch?.groupValues?.get(1) ?: ""
                
                courses.add(Course(
                    title = cleanTitle,
                    href = absoluteUrl,
                    code = code,
                    isDueSoon = isDueSoon,
                    unreadCount = unreadCount,
                    messageCount = messageCount
                ))
            }

            // Parse timetable
            val mobileSchedule = doc.selectFirst(".schedule-list")
            if (mobileSchedule != null) {
                var currentDay = ""
                var currentPeriod = 0
                for (element in mobileSchedule.children()) {
                    when {
                        element.hasClass("list-group-label-item") -> {
                            val dayText = element.text().trim()
                            currentDay = listOf("月", "火", "水", "木", "金", "土", "日")
                                .firstOrNull { dayText.contains(it) }
                                .orEmpty()
                            currentPeriod = 0
                        }
                        element.hasClass("class-order") -> {
                            currentPeriod = element.text()
                                .replace("""\D+""".toRegex(), "")
                                .toIntOrNull() ?: 0
                        }
                        element.`is`("a.course") && currentDay.isNotEmpty() && currentPeriod > 0 -> {
                            val rawText = element.text().trim()
                            if (rawText.contains("開講期間外")) continue
                            val cleanTitle = rawText
                                .replace(dueSoonText, "")
                                .replace("""新着メッセージ\(\d+\)""".toRegex(), "")
                                .replace("""[（\(]新着メッセージ\d+[）\)]""".toRegex(), "")
                                .replace("""^»\s*""".toRegex(), "")
                                .trim()
                            val href = element.attr("href")
                            val absoluteUrl = try {
                                URL(URL(baseUrl), href).toString()
                            } catch (e: Exception) {
                                href
                            }
                            val code = """course\.php/([^/]+)""".toRegex()
                                .find(href)?.groupValues?.get(1).orEmpty()
                            timetable.add(
                                TimetableEntry(currentDay, currentPeriod, cleanTitle, code, absoluteUrl)
                            )
                        }
                    }
                }
            }

            if (timetable.isEmpty()) {
                val headerRow = table.selectFirst("thead tr") ?: table.selectFirst("tr")
                val dayMap = mutableMapOf<Int, String>()
                if (headerRow != null) {
                    headerRow.children().forEachIndexed { colIndex, cell ->
                        val text = cell.text().trim()
                        if (text.contains("月") || text.contains("Mon")) dayMap[colIndex] = "月"
                        else if (text.contains("火") || text.contains("Tue")) dayMap[colIndex] = "火"
                        else if (text.contains("水") || text.contains("Wed")) dayMap[colIndex] = "水"
                        else if (text.contains("木") || text.contains("Thu")) dayMap[colIndex] = "木"
                        else if (text.contains("金") || text.contains("Fri")) dayMap[colIndex] = "金"
                        else if (text.contains("土") || text.contains("Sat")) dayMap[colIndex] = "土"
                        else if (text.contains("日") || text.contains("Sun")) dayMap[colIndex] = "日"
                    }
                }

                val rows = table.select("tbody tr").ifEmpty { table.select("tr") }
                for (row in rows) {
                    if (row == headerRow) continue
                    val cells = row.children()
                    if (cells.isEmpty()) continue
                    val periodText = cells[0].text().trim()
                    val rawPeriod = periodText.replace("""\D+""".toRegex(), "").toIntOrNull() ?: continue
                    val periodNum = (rawPeriod + 1) / 2

                    cells.forEachIndexed { colIndex, cell ->
                        if (colIndex == 0) return@forEachIndexed
                        val dayName = dayMap[colIndex] ?: return@forEachIndexed
                        val cellLinks = cell.select("a[href*=\"/course.php/\"]")
                        for (a in cellLinks) {
                            val rawText = a.text().trim()
                            if (rawText.contains("開講期間外")) continue
                        
                        val cleanTitle = rawText
                            .replace(dueSoonText, "")
                            .replace("""新着メッセージ\(\d+\)""".toRegex(), "")
                            .replace("""[（\(]新着メッセージ\d+[）\)]""".toRegex(), "")
                            .replace("""^»\s*""".toRegex(), "")
                            .trim()
                        
                        val href = a.attr("href")
                        val absoluteUrl = try {
                            URL(URL(baseUrl), href).toString()
                        } catch (e: Exception) {
                            href
                        }
                        
                        val codeRegex = """course\.php/([^/]+)""".toRegex()
                        val codeMatch = codeRegex.find(href)
                        val code = codeMatch?.groupValues?.get(1) ?: ""
                        
                            timetable.add(TimetableEntry(
                                day = dayName,
                                period = periodNum,
                                courseTitle = cleanTitle,
                                courseCode = code,
                                url = absoluteUrl
                            ))
                        }
                    }
                }
            }
        }

        // 2. Extract User Name
        var userName = "WebClass ユーザー"
        val dropdowns = doc.select("#menu li.dropdown")
        for (li in dropdowns) {
            val hasAccountLink = li.selectFirst("a[href*=\"user.php/config\"]")
            val toggle = li.selectFirst("> a.dropdown-toggle")
            if (hasAccountLink != null && toggle != null) {
                val text = toggle.ownText().trim()
                if (text.isNotEmpty()) {
                    userName = text
                    break
                }
            }
        }
        if (userName == "WebClass ユーザー") {
            val mobileToggles = doc.select("#menu .visible-xs-block li.dropdown > a.dropdown-toggle")
            for (a in mobileToggles) {
                val text = a.ownText().trim()
                if (text.isNotEmpty() && !text.contains("言語") && !text.contains("Language", ignoreCase = true)) {
                    userName = text
                    break
                }
            }
        }
        if (userName == "WebClass ユーザー") {
            val generic = doc.selectFirst(".user_name, .header_user_name")
            if (generic != null && generic.text().trim().isNotEmpty()) {
                userName = generic.text().trim()
            }
        }

        // 3. Extract Schedule Settings
        val years = mutableListOf<ScheduleOption>()
        var currentYear = ""
        val yearSelect = doc.selectFirst("select[name=set_year]") ?: doc.selectFirst("select[name=year]")
        if (yearSelect != null) {
            currentYear = yearSelect.`val`()
            for (option in yearSelect.select("option")) {
                years.add(ScheduleOption(option.text().trim(), option.`val`()))
            }
        }

        val semesters = mutableListOf<ScheduleOption>()
        var currentSemester = ""
        val semSelect = doc.selectFirst("select[name=set_semester]") ?: doc.selectFirst("select[name=semester]")
        if (semSelect != null) {
            currentSemester = semSelect.`val`()
            for (option in semSelect.select("option")) {
                semesters.add(ScheduleOption(option.text().trim(), option.`val`()))
            }
        }
        
        val scheduleSettings = ScheduleSettings(
            years = years,
            semesters = semesters,
            currentYear = currentYear,
            currentSemester = currentSemester
        )

        // 4. Extract Dashboard Info
        val announcements = mutableListOf<AdminAnnouncement>()
        val userTopInfo = doc.selectFirst("#UserTopInfo")
        if (userTopInfo != null) {
            val header = userTopInfo.selectFirst(".page-header")
            if (header != null && header.text().contains("管理者からのお知らせ")) {
                val listItems = userTopInfo.select("ul.info-list > li")
                for (li in listItems) {
                    if (li.hasClass("head")) continue
                    val a = li.selectFirst("a.title.hidden-xs") ?: li.selectFirst("a.title.visible-xs") ?: li.selectFirst("a.title")
                    if (a != null) {
                        val title = a.text().trim()
                        val href = a.attr("href")
                        val absoluteUrl = try {
                            URL(URL(baseUrl), href).toString()
                        } catch (e: Exception) {
                            href
                        }
                        
                        val match = """[?&]id=(\d+)""".toRegex().find(href)
                        val messageId = match?.groupValues?.get(1)
                        
                        var sender = ""
                        var date = ""
                        val infoSpan = li.selectFirst(".exhibitionInfo")
                        if (infoSpan != null) {
                            val text = infoSpan.text().trim()
                            val parts = text.split("-")
                            if (parts.size >= 2) {
                                date = parts.last().trim()
                                sender = parts.dropLast(1).joinToString("-").trim()
                            } else {
                                sender = text
                            }
                        }
                        
                        announcements.add(AdminAnnouncement(
                            title = title,
                            url = absoluteUrl,
                            date = date,
                            sender = sender,
                            messageId = messageId
                        ))
                    }
                }
            }
        }

        val surveys = mutableListOf<SurveyBlock>()
        val sideBlocks = doc.select(".side-block-outer")
        val seenUrls = mutableSetOf<String>()
        for (block in sideBlocks) {
            val titleEl = block.selectFirst(".side-block-title")
            if (titleEl != null && titleEl.text().contains("アンケート")) {
                val links = block.select("ul.menugroup > li > a")
                for (a in links) {
                    val rawText = a.text().trim()
                    var count = 0
                    val countRegex = """\((\d+)\)\s*$""".toRegex()
                    val countMatch = countRegex.find(rawText)
                    if (countMatch != null) {
                        count = countMatch.groupValues[1].toIntOrNull() ?: 0
                    }
                    val title = rawText.replace("""\(\d+\)\s*$""".toRegex(), "").replace("""^»\s*""".toRegex(), "").trim()
                    val href = a.attr("href")
                    val absoluteUrl = try {
                        URL(URL(baseUrl), href).toString()
                    } catch (e: Exception) {
                        href
                    }
                    
                    if (!seenUrls.contains(absoluteUrl)) {
                        seenUrls.add(absoluteUrl)
                        surveys.add(SurveyBlock(
                            title = title,
                            url = absoluteUrl,
                            count = count
                        ))
                    }
                }
            }
        }

        var unreadMessageCount = 0
        val unreadBadge = doc.selectFirst("#js-unread-message-count") ?: doc.selectFirst(".hd-notification_badge")
        if (unreadBadge != null) {
            val badgeText = unreadBadge.text().trim()
            val parsedVal = badgeText.toIntOrNull()
            if (parsedVal != null) {
                unreadMessageCount = parsedVal
            }
        }

        var portfolioUrl: String? = null
        val portfolioA = doc.selectFirst("a[href*=\"/plugin/shugaku_carte/carte/mypage\"]")
        if (portfolioA != null) {
            val href = portfolioA.attr("href")
            portfolioUrl = try {
                URL(URL(baseUrl), href).toString()
            } catch (e: Exception) {
                href
            }
        }

        val dashboardInfo = DashboardInfo(
            announcements = announcements,
            surveys = surveys,
            unreadMessageCount = unreadMessageCount,
            portfolioUrl = portfolioUrl
        )

        return HomeDataResult(
            pageType = pageType,
            courses = courses,
            userName = userName,
            scheduleSettings = scheduleSettings,
            dashboardInfo = dashboardInfo,
            timetable = timetable
        )
    }

    fun parseCourseFolders(html: String, baseUrl: String = "https://webclass.kosen-k.go.jp"): List<CourseFolder> {
        val doc = Jsoup.parse(html, baseUrl)
        val folders = mutableListOf<CourseFolder>()
        val folderEls = doc.select(".cl-contentsList_folder")
        
        folderEls.forEachIndexed { index, folderEl ->
            val id = folderEl.id().ifBlank { "folder-$index" }
            val titleEl = folderEl.selectFirst(".panel-title")
            val folderTitle = titleEl?.text()?.trim() ?: "(無題セクション)"
            
            val items = mutableListOf<CourseItem>()
            val itemEls = folderEl.select(".cl-contentsList_listGroupItem")
            
            itemEls.forEachIndexed { idx, itemEl ->
                val info = itemEl.selectFirst(".cl-contentsList_contentInfo")
                val nameRoot = info?.selectFirst(".cm-contentsList_contentName")
                val link = nameRoot?.selectFirst("a")
                val isNew = nameRoot?.selectFirst(".cl-contentsList_new") != null
                val categoryEl = info?.selectFirst(".cl-contentsList_categoryLabel")
                val category = categoryEl?.text()?.trim() ?: ""
                
                val title = when {
                    link != null -> link.text().trim()
                    nameRoot != null -> {
                        val clone = nameRoot.clone()
                        clone.select(".cl-contentsList_new").remove()
                        clone.text().trim()
                    }
                    else -> "(無題コンテンツ)"
                }
                
                var isUnread = false
                val listStatusEl = itemEl.selectFirst(".cl-contentsList_listStatus")
                if (listStatusEl != null) {
                    val statusBadge = listStatusEl.selectFirst(".label")
                    if (statusBadge != null) {
                        val badgeText = statusBadge.text().trim()
                        if (badgeText == "未読" || badgeText == "未実施" || badgeText.contains("未")) {
                            isUnread = true
                        }
                    }
                }
                
                val isDisabled = link == null
                var url = ""
                if (link != null && link.attr("href").isNotEmpty()) {
                    val href = link.attr("href")
                    url = try {
                        URL(URL(baseUrl), href).toString()
                    } catch (e: Exception) {
                        href
                    }
                }
                
                var detailUrl: String? = null
                var historyUrl: String? = null
                var usageCount: Int? = null
                var availablePeriod: String? = null
                
                val detailLinks = itemEl.select(".cl-contentsList_contentDetailListItemData a")
                for (a in detailLinks) {
                    val href = a.attr("href")
                    if (href.isEmpty()) continue
                    
                    if (href.contains("history")) {
                        historyUrl = try {
                            URL(URL(baseUrl), href).toString()
                        } catch (e: Exception) {
                            href
                        }
                        val text = a.text()
                        val m = """利用回数\s*(\d+)""".toRegex().find(text)
                        if (m != null) {
                            usageCount = m.groupValues[1].toIntOrNull()
                        }
                    } else if (href.contains("/contents/")) {
                        detailUrl = try {
                            URL(URL(baseUrl), href).toString()
                        } catch (e: Exception) {
                            href
                        }
                    }
                }
                
                val hasSubmitted = usageCount != null && usageCount > 0
                
                val periodBlocks = itemEl.select(".cm-contentsList_contentDetailListItem")
                for (block in periodBlocks) {
                    val labelEl = block.selectFirst(".cm-contentsList_contentDetailListItemLabel")
                    val dataEl = block.selectFirst(".cm-contentsList_contentDetailListItemData")
                    if (labelEl == null || dataEl == null) continue
                    if (labelEl.text().contains("利用可能期間")) {
                        availablePeriod = dataEl.text().trim()
                    }
                }
                
                items.add(CourseItem(
                    id = "$id-item-$idx",
                    title = title,
                    url = url,
                    category = category,
                    isNew = isNew,
                    isDisabled = isDisabled,
                    detailUrl = detailUrl,
                    historyUrl = historyUrl,
                    usageCount = usageCount,
                    availablePeriod = availablePeriod,
                    hasSubmitted = hasSubmitted,
                    isUnread = isUnread
                ))
            }
            
            folders.add(CourseFolder(
                id = id,
                title = folderTitle,
                items = items
            ))
        }
        return folders
    }

    fun parseSyllabusDepartments(html: String, baseUrl: String = "https://syllabus.kosen-k.go.jp"): List<SyllabusDepartment> {
        val doc = Jsoup.parse(html, baseUrl)
        val departments = mutableListOf<SyllabusDepartment>()
        val headings = doc.select("h4.list-group-item-heading")
        for (h4 in headings) {
            val name = h4.text().trim()
            val container = h4.closest(".list-group-item") ?: h4.parent()
            var links = container?.select("a.btn") ?: org.jsoup.select.Elements()
            if (links.isEmpty()) {
                var nextSib = h4.parent()
                while (nextSib != null) {
                    links = nextSib.select("a.btn, a[href*=\"PublicSubjects\"]")
                    if (links.isNotEmpty()) break
                    nextSib = nextSib.nextElementSibling()
                }
            }
            if (links.isEmpty()) {
                val parent = h4.closest(".row") ?: h4.closest(".panel") ?: h4.closest(".list-group-item") ?: h4.parent()?.parent()
                if (parent != null) {
                    links = parent.select("a[href*=\"PublicSubjects\"]")
                }
            }
            
            var subjectsUrl = ""
            var departmentId = ""
            for (link in links) {
                val text = link.text().trim()
                val href = link.attr("href")
                if (text.contains("本年度の開講科目一覧") || href.contains("PublicSubjects")) {
                    try {
                        val fullUrl = URL(URL(baseUrl), href).toString()
                        val uri = android.net.Uri.parse(fullUrl)
                        departmentId = uri.getQueryParameter("department_id") ?: ""
                        subjectsUrl = fullUrl
                    } catch (e: Exception) {
                        subjectsUrl = href
                    }
                    break
                }
            }
            
            if (name.isNotEmpty() && subjectsUrl.isNotEmpty()) {
                departments.add(SyllabusDepartment(name, departmentId, subjectsUrl))
            }
        }
        return departments
    }

    fun parseSyllabusSubjects(html: String, baseUrl: String = "https://syllabus.kosen-k.go.jp"): List<SyllabusSubject> {
        val doc = Jsoup.parse(html, baseUrl)
        val subjects = mutableListOf<SyllabusSubject>()
        
        val rows = doc.select("tr[data-course-value]")
        if (rows.isNotEmpty()) {
            for (tr in rows) {
                val tds = tr.select("td")
                if (tds.size < 4) continue
                
                val subjectItem = tr.selectFirst(".subject-item")
                var subjectName = ""
                var syllabusUrl = ""
                
                if (subjectItem != null) {
                    val a = subjectItem.selectFirst("a.mcc-show")
                    if (a != null) {
                        subjectName = a.text().trim()
                        syllabusUrl = a.attr("href")
                    } else {
                        val span = subjectItem.selectFirst("span.mcc-hide")
                        if (span != null) subjectName = span.text().trim()
                    }
                }
                
                var category = ""
                var requirement = ""
                var code = ""
                var creditType = ""
                var credits = ""
                var teachers = ""
                var targetYear = ""
                
                for (i in 0 until tds.size) {
                    val cls = tds[i].className()
                    val txt = tds[i].text().trim()
                    if (cls.contains("c1") && !cls.contains("c1m")) {
                        if (category.isEmpty()) category = txt
                    }
                }
                
                if (category.isEmpty() && tds.isNotEmpty()) category = tds[0].text().trim()
                if (requirement.isEmpty() && tds.size > 1) requirement = tds[1].text().trim()
                
                if (syllabusUrl.isNotEmpty()) {
                    try {
                        val fullUrl = URL(URL(baseUrl), syllabusUrl).toString()
                        val uri = android.net.Uri.parse(fullUrl)
                        code = uri.getQueryParameter("subject_code") ?: ""
                        syllabusUrl = fullUrl
                    } catch (e: Exception) {}
                }
                
                for (j in 0 until tds.size) {
                    val t = tds[j].text().trim()
                    if (t == "履修単位" || t == "学修単位") {
                        creditType = t
                        if (j + 1 < tds.size) {
                            credits = tds[j+1].text().trim()
                        }
                        break
                    }
                }
                
                for (k in tds.size - 1 downTo 0) {
                    val tt = tds[k].text().trim()
                    if (tt.isNotEmpty() && tt.length > 1 && !tt.matches("^[0-9]+$".toRegex()) && !tds[k].className().contains("m")) {
                        if (tt.any { it in '\u3040'..'\u309f' || it in '\u30a0'..'\u30ff' || it in '\u4e00'..'\u9faf' }) {
                            teachers = tt
                            break
                        }
                    }
                }
                
                for (y in 1..5) {
                    val cCells = tr.select(".c${y}m")
                    var hasNumber = false
                    for (cell in cCells) {
                        val valStr = cell.text().trim()
                        val num = valStr.toIntOrNull()
                        if (num != null && num > 0) {
                            hasNumber = true
                            break
                        }
                    }
                    if (hasNumber) {
                        targetYear = "${y}年"
                        break
                    }
                }
                
                if (subjectName.isNotEmpty()) {
                    subjects.add(SyllabusSubject(
                        category = category,
                        requirement = requirement,
                        name = subjectName,
                        code = code,
                        creditType = creditType,
                        credits = credits,
                        teachers = teachers,
                        syllabusUrl = syllabusUrl,
                        targetYear = targetYear
                    ))
                }
            }
        } else {
            val links = doc.select("a[href*=\"PublicSyllabus\"]")
            for (a in links) {
                val name = a.text().trim()
                var href = a.attr("href")
                if (name.isEmpty() || name == "全て") continue
                var code = ""
                try {
                    val fullUrl = URL(URL(baseUrl), href).toString()
                    val uri = android.net.Uri.parse(fullUrl)
                    code = uri.getQueryParameter("subject_code") ?: ""
                    href = fullUrl
                } catch (e: Exception) {}
                
                subjects.add(SyllabusSubject(
                    name = name,
                    code = code,
                    syllabusUrl = href
                ))
            }
        }
        return subjects
    }

    fun parseSyllabusDetail(html: String, baseUrl: String = "https://syllabus.kosen-k.go.jp"): SyllabusDetail {
        val doc = Jsoup.parse(html, baseUrl)
        val root = doc.selectFirst("#MainContent_SubjectSyllabus_UpdatePanelSyllabus") ?: doc
        
        fun brToNewline(el: org.jsoup.nodes.Element?): String {
            if (el == null) return ""
            val clone = el.clone()
            clone.select("br").append("\\n")
            val raw = clone.text()
            return raw.replace("\\n", "\n")
                .split("\n")
                .map { it.replace("""\s+""".toRegex(), " ").trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        }

        fun findTableAfterHeading(headingText: String): org.jsoup.nodes.Element? {
            val h3s = root.select("h3")
            for (h3 in h3s) {
                if (h3.text().contains(headingText)) {
                    var next = h3.nextElementSibling()
                    while (next != null) {
                        if (next.tagName() == "table") return next
                        val tbl = next.selectFirst("table")
                        if (tbl != null) return tbl
                        next = next.nextElementSibling()
                    }
                }
            }
            return null
        }

        fun findDivAfterHeading(headingText: String): org.jsoup.nodes.Element? {
            val h3s = root.select("h3")
            for (h3 in h3s) {
                if (h3.text().contains(headingText)) {
                    var next = h3.nextElementSibling()
                    while (next != null) {
                        if (next.tagName() == "div") return next
                        next = next.nextElementSibling()
                    }
                }
            }
            return null
        }

        fun getThValue(table: org.jsoup.nodes.Element?, label: String): String {
            if (table == null) return ""
            val rows = table.select("tr")
            for (row in rows) {
                val ths = row.select("th")
                for (th in ths) {
                    if (th.text().trim() == label) {
                        var td = th.nextElementSibling()
                        while (td != null && td.tagName() != "td") {
                            td = td.nextElementSibling()
                        }
                        if (td != null) return td.text().trim()
                    }
                }
            }
            return ""
        }

        val basicTable = findTableAfterHeading("科目基礎情報")
        var creditType = ""
        var credits = ""
        val creditRaw = getThValue(basicTable, "単位の種別と単位数")
        if (creditRaw.isNotEmpty()) {
            val parts = creditRaw.split(":")
            if (parts.size >= 2) {
                creditType = parts[0].trim()
                credits = parts[1].trim()
            } else {
                creditType = creditRaw
            }
        }
        
        val teachers = root.selectFirst("#Teachers")?.text()?.trim() ?: getThValue(basicTable, "担当教員")

        val basicInfo = SyllabusBasicInfo(
            school = getThValue(basicTable, "学校"),
            year = getThValue(basicTable, "開講年度"),
            subjectName = getThValue(basicTable, "授業科目"),
            subjectCode = getThValue(basicTable, "科目番号"),
            category = getThValue(basicTable, "科目区分"),
            teachingFormat = getThValue(basicTable, "授業形態"),
            creditType = creditType,
            credits = credits,
            department = getThValue(basicTable, "開設学科"),
            targetYear = getThValue(basicTable, "対象学年"),
            semester = getThValue(basicTable, "開設期"),
            weeklyHours = getThValue(basicTable, "週時間数"),
            textbook = getThValue(basicTable, "教科書/教材"),
            teachers = teachers
        )

        val goalsDiv = findDivAfterHeading("到達目標")
        val goals = brToNewline(goalsDiv)

        val rubric = mutableListOf<RubricItem>()
        val rubricTable = root.selectFirst("#MainContent_SubjectSyllabus_hyouka") ?: findTableAfterHeading("ルーブリック")
        if (rubricTable != null) {
            val rRows = rubricTable.select("tr[id*=\"rubricItemRow\"]")
            val targetRows = if (rRows.isNotEmpty()) rRows else rubricTable.select("tr")
            for (ri in 1 until targetRows.size) {
                val rCells = targetRows[ri].select("td, th")
                if (rCells.size >= 4) {
                    rubric.add(RubricItem(
                        item = rCells[0].text().trim(),
                        ideal = rCells[1].text().trim(),
                        standard = rCells[2].text().trim(),
                        notReached = rCells[3].text().trim()
                    ))
                }
            }
        }

        val departmentGoals = mutableListOf<String>()
        val deptGoalsDiv = findDivAfterHeading("学科の到達目標項目との関係")
        if (deptGoalsDiv != null) {
            val divs = deptGoalsDiv.select("div")
            for (d in divs) {
                val t = d.text().trim()
                if (t.isNotEmpty()) departmentGoals.add(t)
            }
            if (departmentGoals.isEmpty()) {
                val t = brToNewline(deptGoalsDiv)
                if (t.isNotEmpty()) departmentGoals.add(t)
            }
        }

        var overview = ""
        var methodology = ""
        var notes = ""
        val eduDiv = findDivAfterHeading("教育方法等")
        if (eduDiv != null) {
            val bolds = eduDiv.select("b, strong")
            for (b in bolds) {
                val label = b.text().trim()
                val contentEl = b.nextElementSibling()
                var content = ""
                if (contentEl != null) {
                    content = brToNewline(contentEl)
                } else {
                    var node = b.nextSibling()
                    while (node != null) {
                        if (node is org.jsoup.nodes.TextNode) {
                            content += node.text()
                        } else if (node is org.jsoup.nodes.Element && node.tagName() == "br") {
                            content += "\n"
                        } else if (node is org.jsoup.nodes.Element && (node.tagName() == "b" || node.tagName() == "strong")) {
                            break
                        } else if (node is org.jsoup.nodes.Element) {
                            content += node.text()
                        }
                        node = node.nextSibling()
                    }
                    content = content.trim()
                }
                
                if (label.contains("概要")) overview = content
                else if (label.contains("授業の進め方")) methodology = content
                else if (label.contains("注意点")) notes = content
            }
        }

        val activeAttributes = mutableMapOf<String, Boolean>()
        val attrDiv = findDivAfterHeading("授業の属性")
        if (attrDiv != null) {
            val checkboxes = attrDiv.select("input[type=checkbox]")
            for (cb in checkboxes) {
                val id = cb.id()
                var label = ""
                val labelEl = attrDiv.selectFirst("label[for=\"$id\"]")
                if (labelEl != null) label = labelEl.text().trim()
                if (label.isEmpty()) {
                    val next = cb.nextSibling()
                    if (next is org.jsoup.nodes.TextNode) {
                        label = next.text().trim()
                    }
                }
                if (label.isNotEmpty()) {
                    activeAttributes[label] = cb.hasAttr("checked") || cb.attr("checked").isNotEmpty()
                }
            }
        }

        val lessonPlan = mutableListOf<LessonPlanItem>()
        val lessonsTable = root.selectFirst("#lessonsTable") ?: findTableAfterHeading("授業計画")
        if (lessonsTable != null) {
            val lRows = lessonsTable.select("tr")
            for (row in lRows) {
                val weekTh = row.selectFirst("th.week_number")
                if (weekTh != null) {
                    val lTds = row.select("td")
                    val content = if (lTds.isNotEmpty()) brToNewline(lTds[0]) else ""
                    val target = if (lTds.size > 1) brToNewline(lTds[1]) else ""
                    lessonPlan.add(LessonPlanItem(
                        week = weekTh.text().trim(),
                        content = content,
                        target = target
                    ))
                }
            }
        }

        val evaluation = mutableListOf<EvaluationItem>()
        val evalTable = root.selectFirst("#MainContent_SubjectSyllabus_wariaiTable") ?: findTableAfterHeading("評価割合")
        if (evalTable != null) {
            val eRows = evalTable.select("tr")
            val columnNames = mutableListOf<String>()
            var headerRowIndex = -1
            for (hi in 0 until eRows.size) {
                if (eRows[hi].hasClass("bg-success")) {
                    headerRowIndex = hi
                    val headerCells = eRows[hi].select("td, th")
                    for (cell in headerCells) {
                        val colName = cell.text().trim()
                        if (colName.isNotEmpty()) columnNames.add(colName)
                    }
                    break
                }
            }
            if (headerRowIndex == -1) {
                headerRowIndex = 0
                val headerCells = eRows[0].select("td, th")
                for (cell in headerCells) {
                    val colName = cell.text().trim()
                    if (colName.isNotEmpty()) columnNames.add(colName)
                }
            }
            
            for (ei in headerRowIndex + 1 until eRows.size) {
                val eThs = eRows[ei].select("th")
                val eTds = eRows[ei].select("td")
                if (eThs.isEmpty() && eTds.isEmpty()) continue
                
                val eName = if (eThs.isNotEmpty()) eThs[0].text().trim() else ""
                if (eName.isEmpty()) continue
                
                val components = mutableMapOf<String, Int>()
                var total = 0
                
                for (ej in 0 until eTds.size) {
                    if (ej >= columnNames.size) break
                    val rawVal = eTds[ej].text().trim().replace("%", "")
                    val numVal = rawVal.toIntOrNull() ?: 0
                    val colName = columnNames[ej]
                    if (colName == "合計") {
                        total = numVal
                    } else if (colName.isNotEmpty() && numVal >= 0) {
                        components[colName] = numVal
                    }
                }
                
                evaluation.add(EvaluationItem(
                    name = eName,
                    components = components,
                    total = total
                ))
            }
        }

        return SyllabusDetail(
            basicInfo = basicInfo,
            goals = goals,
            rubric = rubric,
            departmentGoals = departmentGoals,
            overview = overview,
            methodology = methodology,
            notes = notes,
            activeAttributes = activeAttributes,
            lessonPlan = lessonPlan,
            evaluation = evaluation
        )
    }
}
