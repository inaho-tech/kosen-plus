package com.webclass.plus.util

/**
 * JavaScript utilities for DOM parsing and data extraction from Syllabus pages.
 * Target: https://syllabus.kosen-k.go.jp
 */
object SyllabusJsBridge {

    // Text normalization helper (shared across all extractors)
    private const val NORMALIZE_TEXT_FN = """
        function normalizeText(str) {
            if (!str) return '';
            return str.replace(/\u00a0/g, ' ')
                       .replace(/\s+/g, ' ')
                       .trim();
        }
        function normalizeMultiLineText(str) {
            if (!str) return '';
            // normalize spaces within lines, but preserve newlines
            return str.replace(/\u00a0/g, ' ')
                      .split('\n')
                      .map(function(line) { 
                          return line.replace(/\s+/g, ' ').trim(); 
                      })
                      .filter(function(line) { return line.length > 0; })
                      .join('\n');
        }
        function brToNewline(el) {
            if (!el) return '';
            var clone = el.cloneNode(true);
            var brs = clone.querySelectorAll('br');
            for (var i = 0; i < brs.length; i++) {
                brs[i].replaceWith('\n');
            }
            return normalizeMultiLineText(clone.textContent);
        }
    """

    /**
     * Detects page type based on DOM features (not URL).
     * Returns JSON: { "pageType": "departments"|"subjects"|"syllabus"|"unknown" }
     */
    fun detectSyllabusPageType(): String = """
        (function() {
            $NORMALIZE_TEXT_FN
            if (document.querySelector('#MainContent_SubjectSyllabus_UpdatePanelSyllabus')) {
                return JSON.stringify({ pageType: 'syllabus' });
            }
            var subjectItems = document.querySelectorAll('.subject-item');
            if (subjectItems.length > 0) {
                return JSON.stringify({ pageType: 'subjects' });
            }
            var headings = document.querySelectorAll('h4.list-group-item-heading');
            if (headings.length > 1) {
                return JSON.stringify({ pageType: 'departments' });
            }
            return JSON.stringify({ pageType: 'unknown' });
        })();
    """.trimIndent()

    /**
     * Extracts department list from PublicDepartments page.
     * Returns JSON: { departments: [{ name, departmentId, publicSubjectsUrl }] }
     */
    fun extractDepartments(): String = """
        (function() {
            $NORMALIZE_TEXT_FN
            var data = { departments: [] };
            
            // Each department is in a panel/list-group structure
            var headings = document.querySelectorAll('h4.list-group-item-heading');
            headings.forEach(function(h4) {
                var name = normalizeText(h4.textContent);
                
                // Find the sibling/parent container with buttons
                var container = h4.closest('.list-group-item') || h4.parentElement;
                var links = container.querySelectorAll('a.btn');
                if (links.length === 0) {
                    var nextSib = h4.parentElement;
                    while (nextSib) {
                        links = nextSib.querySelectorAll('a.btn, a[href*="PublicSubjects"]');
                        if (links.length > 0) break;
                        nextSib = nextSib.nextElementSibling;
                    }
                }
                // Also try: all links near this heading
                if (links.length === 0) {
                    var parent = h4.closest('.row') || h4.closest('.panel') || h4.closest('.list-group-item') || h4.parentElement.parentElement;
                    if (parent) {
                        links = parent.querySelectorAll('a[href*="PublicSubjects"]');
                    }
                }
                
                var subjectsUrl = '';
                var departmentId = '';
                for (var i = 0; i < links.length; i++) {
                    var link = links[i];
                    var text = normalizeText(link.textContent);
                    var href = link.getAttribute('href') || '';
                    if (text.includes('本年度の開講科目一覧') || href.includes('PublicSubjects')) {
                        try {
                            var fullUrl = new URL(href, window.location.origin).href;
                            var urlParams = new URL(fullUrl).searchParams;
                            departmentId = urlParams.get('department_id') || '';
                            subjectsUrl = fullUrl;
                        } catch(e) {
                            subjectsUrl = href;
                        }
                        break;
                    }
                }
                
                if (name && subjectsUrl) {
                    data.departments.push({
                        name: name,
                        departmentId: departmentId,
                        publicSubjectsUrl: subjectsUrl
                    });
                }
            });
            
            return JSON.stringify(data);
        })();
    """.trimIndent()

    /**
     * Extracts subject list from PublicSubjects page.
     * Returns JSON: { subjects: [{ category, requirement, name, code, creditType, credits, teachers, syllabusUrl, targetYear }] }
     */
    fun extractSubjects(): String = """
        (function() {
            $NORMALIZE_TEXT_FN
            var data = { subjects: [] };
            
            // Method 1: table rows with data-course-value
            var rows = document.querySelectorAll('tr[data-course-value]');
            if (rows.length > 0) {
                rows.forEach(function(tr) {
                    var tds = tr.querySelectorAll('td');
                    if (tds.length < 4) return;
                    
                    // Find subject name and URL
                    var subjectItem = tr.querySelector('.subject-item');
                    var subjectName = '';
                    var syllabusUrl = '';
                    
                    if (subjectItem) {
                        var a = subjectItem.querySelector('a.mcc-show');
                        if (a) {
                            subjectName = normalizeText(a.textContent);
                            syllabusUrl = a.getAttribute('href') || '';
                        } else {
                            var span = subjectItem.querySelector('span.mcc-hide');
                            if (span) subjectName = normalizeText(span.textContent);
                        }
                    }
                    
                    // Parse other columns
                    var category = '', requirement = '', code = '', creditType = '', credits = '', teachers = '', targetYear = '';
                    
                    // Iterate through tds
                    for (var i = 0; i < tds.length; i++) {
                        var cls = tds[i].className;
                        var txt = normalizeText(tds[i].textContent);
                        
                        if (cls.includes('c1') && !cls.includes('c1m')) {
                            if (!category) category = txt;
                        }
                    }
                    
                    // Try positional extraction: common order is category, requirement, name, code, creditType, credits...teachers
                    if (!category && tds.length > 0) category = normalizeText(tds[0].textContent);
                    if (!requirement && tds.length > 1) requirement = normalizeText(tds[1].textContent);
                    
                    // Code from URL or column
                    if (syllabusUrl) {
                        try {
                            var urlObj = new URL(syllabusUrl, window.location.origin);
                            code = urlObj.searchParams.get('subject_code') || '';
                            syllabusUrl = urlObj.href;
                        } catch(e) {}
                    }
                    
                    // Find creditType and credits
                    for (var j = 0; j < tds.length; j++) {
                        var t = normalizeText(tds[j].textContent);
                        if (t === '履修単位' || t === '学修単位') { creditType = t; if (j+1 < tds.length) credits = normalizeText(tds[j+1].textContent); break; }
                    }
                    
                    // Teachers: usually last meaningful td
                    for (var k = tds.length - 1; k >= 0; k--) {
                        var tt = normalizeText(tds[k].textContent);
                        // Skip empty or just-number cells
                        if (tt && isNaN(tt) && tt.length > 1 && !tt.match(/^[0-9]+${'$'}/) && tds[k].className.indexOf('m') === -1) {
                            // Check if it looks like a teacher list (contains Japanese characters)
                            if (tt.match(/[\u3040-\u309f\u30a0-\u30ff\u4e00-\u9faf]/)) {
                                teachers = tt;
                                break;
                            }
                        }
                    }
                    
                    // Determine school year (学年) from c1m, c2m, c3m, c4m, c5m
                    for (var y = 1; y <= 5; y++) {
                        var cCells = tr.querySelectorAll('.c' + y + 'm');
                        var hasNumber = false;
                        for (var cIdx = 0; cIdx < cCells.length; cIdx++) {
                            var val = normalizeText(cCells[cIdx].textContent);
                            if (val && !isNaN(val) && parseInt(val) > 0) {
                                hasNumber = true;
                                break;
                            }
                        }
                        if (hasNumber) {
                            targetYear = y.toString() + '年';
                            break;
                        }
                    }
                    
                    if (subjectName) {
                        data.subjects.push({
                            category: category,
                            requirement: requirement,
                            name: subjectName,
                            code: code,
                            creditType: creditType,
                            credits: credits,
                            teachers: teachers,
                            syllabusUrl: syllabusUrl,
                            targetYear: targetYear
                        });
                    }
                });
            } else {
                // Method 2: links to PublicSyllabus
                var links = document.querySelectorAll('a[href*="PublicSyllabus"]');
                links.forEach(function(a) {
                    var name = normalizeText(a.textContent);
                    var href = a.getAttribute('href') || '';
                    if (!name || name === '全て') return;
                    var code = '';
                    try {
                        var url = new URL(href, window.location.origin);
                        code = url.searchParams.get('subject_code') || '';
                        href = url.href;
                    } catch(e) {}
                    data.subjects.push({
                        category: '',
                        requirement: '',
                        name: name,
                        code: code,
                        creditType: '',
                        credits: '',
                        teachers: '',
                        syllabusUrl: href,
                        targetYear: ''
                    });
                });
            }
            
            return JSON.stringify(data);
        })();
    """.trimIndent()

    /**
     * Extracts syllabus detail from PublicSyllabus page.
     * Returns JSON with all sections.
     */
    fun extractSyllabusDetail(): String = """
        (function() {
            $NORMALIZE_TEXT_FN
            var data = {
                basicInfo: {},
                goals: '',
                rubric: [],
                departmentGoals: [],
                overview: '',
                methodology: '',
                notes: '',
                activeAttributes: {},
                lessonPlan: [],
                evaluation: []
            };
            
            var root = document.querySelector('#MainContent_SubjectSyllabus_UpdatePanelSyllabus') || document;
            
            // ===== 1. 科目基礎情報 =====
            function findTableAfterHeading(headingText) {
                var h3s = root.querySelectorAll('h3');
                for (var i = 0; i < h3s.length; i++) {
                    if (normalizeText(h3s[i].textContent).includes(headingText)) {
                        var next = h3s[i].nextElementSibling;
                        while (next) {
                            if (next.tagName === 'TABLE') return next;
                            if (next.querySelector && next.querySelector('table')) return next.querySelector('table');
                            next = next.nextElementSibling;
                        }
                    }
                }
                return null;
            }
            
            function findDivAfterHeading(headingText) {
                var h3s = root.querySelectorAll('h3');
                for (var i = 0; i < h3s.length; i++) {
                    if (normalizeText(h3s[i].textContent).includes(headingText)) {
                        var next = h3s[i].nextElementSibling;
                        while (next) {
                            if (next.tagName === 'DIV') return next;
                            next = next.nextElementSibling;
                        }
                    }
                }
                return null;
            }
            
            function getThValue(table, label) {
                if (!table) return '';
                var rows = table.querySelectorAll('tr');
                for (var i = 0; i < rows.length; i++) {
                    var ths = rows[i].querySelectorAll('th');
                    for (var j = 0; j < ths.length; j++) {
                        if (normalizeText(ths[j].textContent) === label) {
                            var td = ths[j].nextElementSibling;
                            while (td && td.tagName !== 'TD') td = td.nextElementSibling;
                            if (td) return normalizeText(td.textContent);
                        }
                    }
                }
                return '';
            }
            
            var basicTable = findTableAfterHeading('科目基礎情報');
            data.basicInfo = {
                school: getThValue(basicTable, '学校'),
                year: getThValue(basicTable, '開講年度'),
                subjectName: getThValue(basicTable, '授業科目'),
                subjectCode: getThValue(basicTable, '科目番号'),
                category: getThValue(basicTable, '科目区分'),
                teachingFormat: getThValue(basicTable, '授業形態'),
                creditType: '',
                credits: '',
                department: getThValue(basicTable, '開設学科'),
                targetYear: getThValue(basicTable, '対象学年'),
                semester: getThValue(basicTable, '開設期'),
                weeklyHours: getThValue(basicTable, '週時間数'),
                textbook: getThValue(basicTable, '教科書/教材'),
                teachers: ''
            };
            
            // Parse creditType and credits
            var creditRaw = getThValue(basicTable, '単位の種別と単位数');
            if (creditRaw) {
                var parts = creditRaw.split(':');
                if (parts.length >= 2) {
                    data.basicInfo.creditType = normalizeText(parts[0]);
                    data.basicInfo.credits = normalizeText(parts[1]);
                } else {
                    data.basicInfo.creditType = creditRaw;
                }
            }
            
            // Teachers
            var teachersEl = root.querySelector('#Teachers');
            if (teachersEl) {
                data.basicInfo.teachers = normalizeText(teachersEl.textContent);
            } else {
                data.basicInfo.teachers = getThValue(basicTable, '担当教員');
            }
            
            // ===== 2. 到達目標 =====
            var goalsDiv = findDivAfterHeading('到達目標');
            if (goalsDiv) data.goals = brToNewline(goalsDiv);
            
            // ===== 3. ルーブリック =====
            var rubricTable = root.querySelector('#MainContent_SubjectSyllabus_hyouka');
            if (!rubricTable) rubricTable = findTableAfterHeading('ルーブリック');
            if (rubricTable) {
                var rRows = rubricTable.querySelectorAll('tr[id*="rubricItemRow"]');
                if (rRows.length === 0) rRows = rubricTable.querySelectorAll('tr');
                for (var ri = 1; ri < rRows.length; ri++) {
                    var rCells = rRows[ri].querySelectorAll('td, th');
                    if (rCells.length >= 4) {
                        data.rubric.push({
                            item: normalizeText(rCells[0].textContent),
                            ideal: normalizeText(rCells[1].textContent),
                            standard: normalizeText(rCells[2].textContent),
                            notReached: normalizeText(rCells[3].textContent)
                        });
                    }
                }
            }
            
            // ===== 4. 学科の到達目標項目との関係 =====
            var deptGoalsDiv = findDivAfterHeading('学科の到達目標項目との関係');
            if (deptGoalsDiv) {
                var divs = deptGoalsDiv.querySelectorAll('div');
                divs.forEach(function(d) {
                    var t = normalizeText(d.textContent);
                    if (t) data.departmentGoals.push(t);
                });
                if (data.departmentGoals.length === 0) {
                    var t = brToNewline(deptGoalsDiv);
                    if (t) data.departmentGoals.push(t);
                }
            }
            
            // ===== 5. 教育方法等 =====
            var eduDiv = findDivAfterHeading('教育方法等');
            if (eduDiv) {
                var bolds = eduDiv.querySelectorAll('b, strong');
                bolds.forEach(function(b) {
                    var label = normalizeText(b.textContent);
                    var contentEl = b.nextElementSibling;
                    var content = '';
                    if (contentEl) {
                        content = brToNewline(contentEl);
                    } else {
                        // Try next text node
                        var node = b.nextSibling;
                        while (node) {
                            if (node.nodeType === 3) content += node.textContent;
                            else if (node.tagName === 'BR') content += '\n';
                            else if (node.tagName === 'B' || node.tagName === 'STRONG') break;
                            else content += node.textContent || '';
                            node = node.nextSibling;
                        }
                        content = normalizeText(content);
                    }
                    
                    if (label.includes('概要')) data.overview = content;
                    else if (label.includes('授業の進め方')) data.methodology = content;
                    else if (label.includes('注意点')) data.notes = content;
                });
            }
            
            // ===== 6. 授業の属性・履修上の区分 =====
            var attrDiv = findDivAfterHeading('授業の属性');
            if (attrDiv) {
                var checkboxes = attrDiv.querySelectorAll('input[type=checkbox]');
                checkboxes.forEach(function(cb) {
                    var id = cb.id || '';
                    var label = '';
                    var labelEl = attrDiv.querySelector('label[for="' + id + '"]');
                    if (labelEl) label = normalizeText(labelEl.textContent);
                    if (!label) {
                        var next = cb.nextSibling;
                        if (next) label = normalizeText(next.textContent);
                    }
                    if (label) {
                        data.activeAttributes[label] = cb.checked;
                    }
                });
            }
            
            // ===== 7. 授業計画 =====
            var lessonsTable = root.querySelector('#lessonsTable');
            if (!lessonsTable) lessonsTable = findTableAfterHeading('授業計画');
            if (lessonsTable) {
                var lRows = lessonsTable.querySelectorAll('tr');
                for (var li = 0; li < lRows.length; li++) {
                    var weekTh = lRows[li].querySelector('th.week_number');
                    if (weekTh) {
                        var lTds = lRows[li].querySelectorAll('td');
                        var content = lTds.length > 0 ? brToNewline(lTds[0]) : '';
                        var target = lTds.length > 1 ? brToNewline(lTds[1]) : '';
                        data.lessonPlan.push({
                            week: normalizeText(weekTh.textContent),
                            content: content,
                            target: target
                        });
                    }
                }
            }
            
            // ===== 8. 評価割合 =====
            var evalTable = root.querySelector('#MainContent_SubjectSyllabus_wariaiTable');
            if (!evalTable) evalTable = findTableAfterHeading('評価割合');
            if (evalTable) {
                var eRows = evalTable.querySelectorAll('tr');
                // Find header row and extract column names
                var columnNames = [];
                var headerRowIndex = -1;
                for (var hi = 0; hi < eRows.length; hi++) {
                    if (eRows[hi].classList.contains('bg-success')) {
                        headerRowIndex = hi;
                        var headerCells = eRows[hi].querySelectorAll('td, th');
                        for (var hj = 0; hj < headerCells.length; hj++) {
                            var colName = normalizeText(headerCells[hj].textContent);
                            if (colName !== '') {
                                columnNames.push(colName);
                            }
                        }
                        break;
                    }
                }
                // If no bg-success header found, fall back to first row
                if (headerRowIndex === -1) {
                    headerRowIndex = 0;
                    var headerCells = eRows[0].querySelectorAll('td, th');
                    for (var hj = 0; hj < headerCells.length; hj++) {
                        var colName = normalizeText(headerCells[hj].textContent);
                        if (colName !== '') {
                            columnNames.push(colName);
                        }
                    }
                }
                
                // Process data rows (all rows after header)
                for (var ei = headerRowIndex + 1; ei < eRows.length; ei++) {
                    var eThs = eRows[ei].querySelectorAll('th');
                    var eTds = eRows[ei].querySelectorAll('td');
                    if (eThs.length === 0 && eTds.length === 0) continue;
                    
                    // Row label comes from <th>, values from <td>
                    var eName = eThs.length > 0 ? normalizeText(eThs[0].textContent) : '';
                    if (eName === '') continue;
                    
                    var evalItem = {
                        name: eName,
                        components: {},
                        total: 0
                    };
                    
                    // Map each td value using actual column names
                    for (var ej = 0; ej < eTds.length && ej < columnNames.length; ej++) {
                        var rawVal = normalizeText(eTds[ej].textContent).replace('%', '');
                        var numVal = parseInt(rawVal) || 0;
                        var colName = columnNames[ej];
                        if (colName === '合計') {
                            evalItem.total = numVal;
                        } else if (colName !== '' && numVal >= 0) {
                            evalItem.components[colName] = numVal;
                        }
                    }
                    
                    data.evaluation.push(evalItem);
                }
            }
            
            return JSON.stringify(data);
        })();
    """.trimIndent()
}
