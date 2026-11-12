package com.webclass.plus.util

import android.net.Uri
import com.webclass.plus.data.models.PageType

/**
 * JavaScript utilities for DOM parsing and data extraction from WebClass pages.
 * Each function returns a JavaScript snippet that extracts data and returns JSON.
 */
object JavaScriptBridge {

    fun extractMessageInboxUrl(): String = """
        (function() {
            const links = Array.from(document.querySelectorAll('a[href]'));
            const candidates = links.map(function(link) {
                const href = link.getAttribute('href') || '';
                let score = 0;
                if (href.includes('/mbl.php/messages')) {
                    score = /[?&](course_id|course|courseid)=/i.test(href) ? 140 : 300;
                }
                else if (href.includes('msg_editor.php') && href.includes('msgappmode=inbox')) score = 250;
                else if (href.includes('msg_editor.php') && !href.includes('course_id')) score = 100;
                return { href: href, score: score };
            }).filter(function(item) { return item.score > 0; })
              .sort(function(a, b) { return b.score - a.score; });
            if (!candidates.length) return '';
            try {
                return new URL(candidates[0].href, window.location.origin).href;
            } catch (_) {
                return candidates[0].href;
            }
        })();
    """.trimIndent()

    fun toMobileContentUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val targetPath = when {
            uri.path?.endsWith("/qstn_frame.php") == true -> "/webclass/mbl.php/questions/"
            uri.path?.endsWith("/show_frame.php") == true -> "/webclass/show_info.php"
            else -> return url
        }
        val builder = uri.buildUpon().path(targetPath).clearQuery()
        uri.queryParameterNames.forEach { name ->
            if (name !in setOf("content_mode", "mbl", "start_from_show_info")) {
                uri.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
            }
        }
        builder.appendQueryParameter("mbl", "1")
        builder.appendQueryParameter("start_from_show_info", "true")
        builder.appendQueryParameter("content_mode", if (targetPath.contains("questions")) "q" else "i")
        return builder.build().toString()
    }

    fun isNativeQuestionUrl(url: String): Boolean =
        url.contains("/mbl.php/questions/") || url.contains("/qstn_frame.php")

    fun isCustomContentUrl(url: String): Boolean =
        isNativeQuestionUrl(url) || url.contains("/show_info.php") || url.contains("/show_frame.php")

    /** Finds a quiz/test URL embedded by WebClass inside a wrapper or frameset page. */
    fun detectCustomContentTarget(): String = """
        (function() {
            const matches = function(value) {
                return /\/(qstn_frame\.php|show_frame\.php|show_info\.php|mbl\.php\/questions)/.test(value || '');
            };
            const absolute = function(value) {
                try { return new URL(value, window.location.href).href; } catch (_) { return value || ''; }
            };
            if (matches(window.location.href)) return window.location.href;
            const frames = Array.from(document.querySelectorAll('iframe, frame'));
            for (const frame of frames) {
                const source = absolute(frame.getAttribute('src') || '');
                if (matches(source)) return source;
                try {
                    const nested = frame.contentWindow?.location?.href || '';
                    if (matches(nested)) return nested;
                } catch (_) {}
            }
            const links = Array.from(document.querySelectorAll('a[href], form[action]'));
            for (const element of links) {
                const target = absolute(element.getAttribute('href') || element.getAttribute('action') || '');
                if (matches(target)) return target;
            }
            return '';
        })();
    """.trimIndent()
    
    /**
     * Extracts unified Home screen data (pageType, courses, userName, scheduleSettings, dashboardInfo)
     * in a single WebView Javascript execution to avoid multiple IPC round-trips.
     */
    fun extractHomeData(): String = """
        (function() {
            const data = {};
            try {
                const path = window.location.pathname.replace(/\/+$/, '');
                let pageType = 'OTHER';
                if (path.includes('login.php')) {
                    pageType = 'LOGIN';
                } else if (path === '/webclass' || path === '/webclass/index.php') {
                    pageType = 'HOME';
                } else if (path.startsWith('/webclass/course.php') && !path.includes('/contents/')) {
                    pageType = 'COURSE';
                } else if (path.startsWith('/webclass/course.php') && path.includes('/contents/')) {
                    if (path.includes('/history')) {
                        pageType = 'HISTORY';
                    } else if (path.includes('/results')) {
                        pageType = 'RESULTS';
                    } else {
                        pageType = 'DETAIL';
                    }
                } else if (path.startsWith('/webclass/show_info.php')) {
                    pageType = 'PRE_START';
                }
                data.pageType = pageType;
            } catch(e) {
                data.pageType = 'UNKNOWN';
            }
            
            if (data.pageType === 'HOME' || data.pageType === 'COURSE_LIST') {
                // 1. Extract courses
                const courses = [];
                try {
                    const table = document.querySelector('#schedule-table');
                    if (table) {
                        const links = table.querySelectorAll('td a[href*="/course.php/"]');
                        const dueSoonText = '締切が近い課題があります。';
                        
                        links.forEach(a => {
                            const rawText = (a.textContent || '').trim();
                            if (rawText.includes('開講期間外')) {
                                return;
                            }
                            const isDueSoon = rawText.includes(dueSoonText);
                            let messageCount = 0;
                            const msgMatch = rawText.match(/新着メッセージ\((\d+)\)/);
                            if (msgMatch) {
                                messageCount = parseInt(msgMatch[1], 10);
                            }
                            let cleanTitle = rawText
                                .replace(dueSoonText, '')
                                .replace(/新着メッセージ\((\d+)\)/g, '')
                                .replace(/[（\(]新着メッセージ\d+[）\)]/g, '')
                                .replace(/^»\s*/g, '')
                                .trim();
                            let unreadCount = 0;
                            const row = a.closest('tr');
                            if (row) {
                                const badges = row.querySelectorAll('.badge, .label-danger');
                                badges.forEach(b => {
                                    const num = parseInt(b.textContent.trim(), 10);
                                    if (!isNaN(num)) unreadCount += num;
                                });
                            }
                            const href = a.getAttribute('href') || '';
                            let url = href;
                            try {
                                url = new URL(href, window.location.origin).href;
                            } catch (e) {
                                url = href;
                            }
                            const match = href.match(/course\.php\/([^/]+)/);
                            const code = match ? match[1] : '';
                            
                            courses.push({
                                title: cleanTitle,
                                href: url,
                                code: code,
                                isDueSoon: isDueSoon,
                                unreadCount: unreadCount,
                                messageCount: messageCount
                            });
                        });
                    }
                } catch(e) {}
                data.courses = courses;

                // 1b. Extract timetable
                const timetable = [];
                try {
                    const mobileSchedule = document.querySelector('.schedule-list');
                    if (mobileSchedule) {
                        let currentDay = '';
                        let currentPeriod = 0;
                        Array.from(mobileSchedule.children).forEach(element => {
                            if (element.classList.contains('list-group-label-item')) {
                                const dayText = (element.textContent || '').trim();
                                currentDay = ['月', '火', '水', '木', '金', '土', '日']
                                    .find(day => dayText.includes(day)) || '';
                                currentPeriod = 0;
                                return;
                            }
                            if (element.classList.contains('class-order')) {
                                currentPeriod = parseInt((element.textContent || '').replace(/\D+/g, ''), 10) || 0;
                                return;
                            }
                            if (!element.matches('a.course') || !currentDay || currentPeriod <= 0) return;

                            const rawText = (element.textContent || '').trim();
                            if (rawText.includes('開講期間外')) return;
                            const cleanTitle = rawText
                                .replace('締切が近い課題があります。', '')
                                .replace(/新着メッセージ\((\d+)\)/g, '')
                                .replace(/[（\(]新着メッセージ\d+[）\)]/g, '')
                                .replace(/^»\s*/g, '')
                                .trim();
                            const href = element.getAttribute('href') || '';
                            let url = href;
                            try {
                                url = new URL(href, window.location.origin).href;
                            } catch (e) {
                                url = href;
                            }
                            const match = href.match(/course\.php\/([^/]+)/);
                            const code = match ? match[1] : '';
                            timetable.push({
                                day: currentDay,
                                period: currentPeriod,
                                courseTitle: cleanTitle,
                                courseCode: code,
                                url: url
                            });
                        });
                    }

                    const table = document.querySelector('#schedule-table');
                    if (timetable.length === 0 && table) {
                        const headerRow = table.querySelector('thead tr') || table.querySelector('tr');
                        const dayMap = {};
                        if (headerRow) {
                            const cells = Array.from(headerRow.children);
                            cells.forEach((cell, colIndex) => {
                                const text = cell.textContent.trim();
                                if (text.includes('月') || text.includes('Mon')) dayMap[colIndex] = '月';
                                else if (text.includes('火') || text.includes('Tue')) dayMap[colIndex] = '火';
                                else if (text.includes('水') || text.includes('Wed')) dayMap[colIndex] = '水';
                                else if (text.includes('木') || text.includes('Thu')) dayMap[colIndex] = '木';
                                else if (text.includes('金') || text.includes('Fri')) dayMap[colIndex] = '金';
                                else if (text.includes('土') || text.includes('Sat')) dayMap[colIndex] = '土';
                                else if (text.includes('日') || text.includes('Sun')) dayMap[colIndex] = '日';
                            });
                        }

                        const rows = Array.from(table.querySelectorAll('tbody tr') || table.querySelectorAll('tr'));
                        rows.forEach(row => {
                            if (row === headerRow) return;
                            const cells = Array.from(row.children);
                            if (cells.length === 0) return;
                            const periodText = cells[0].textContent.trim();
                            const rawPeriod = parseInt(periodText.replace(/\D+/g, ''), 10);
                            if (isNaN(rawPeriod)) return;
                            const periodNum = Math.floor((rawPeriod + 1) / 2);

                            cells.forEach((cell, colIndex) => {
                                if (colIndex === 0) return;
                                const dayName = dayMap[colIndex];
                                if (!dayName) return;

                                const cellLinks = cell.querySelectorAll('a[href*="/course.php/"]');
                                cellLinks.forEach(a => {
                                    const rawText = (a.textContent || '').trim();
                                    if (rawText.includes('開講期間外')) return;
                                    
                                    const dueSoonText = '締切が近い課題があります。';
                                    let cleanTitle = rawText
                                        .replace(dueSoonText, '')
                                        .replace(/新着メッセージ\((\d+)\)/g, '')
                                        .replace(/[（\(]新着メッセージ\d+[）\)]/g, '')
                                        .replace(/^»\s*/g, '')
                                        .trim();
                                    
                                    const href = a.getAttribute('href') || '';
                                    let url = href;
                                    try {
                                        url = new URL(href, window.location.origin).href;
                                    } catch (e) {
                                        url = href;
                                    }
                                    const match = href.match(/course\.php\/([^/]+)/);
                                    const code = match ? match[1] : '';

                                    timetable.push({
                                        day: dayName,
                                        period: periodNum,
                                        courseTitle: cleanTitle,
                                        courseCode: code,
                                        url: url
                                    });
                                });
                            });
                        });
                    }
                } catch(e) {}
                data.timetable = timetable;

                // 2. Extract user name
                let userName = 'WebClass ユーザー';
                try {
                    const dropdowns = Array.from(document.querySelectorAll('#menu li.dropdown'));
                    for (const li of dropdowns) {
                        const hasAccountLink = li.querySelector('a[href*="user.php/config"]');
                        const toggle = li.querySelector(':scope > a.dropdown-toggle');
                        if (hasAccountLink && toggle) {
                            const text = (toggle.childNodes[0]?.textContent || toggle.textContent || '').trim();
                            if (text) {
                                userName = text;
                                break;
                            }
                        }
                    }
                    if (userName === 'WebClass ユーザー') {
                        const mobileToggles = Array.from(
                            document.querySelectorAll('#menu .visible-xs-block li.dropdown > a.dropdown-toggle')
                        );
                        for (const a of mobileToggles) {
                            const text = (a.childNodes[0]?.textContent || a.textContent || '').trim();
                            if (text && !text.includes('言語') && !text.toLowerCase().includes('language')) {
                                userName = text;
                                break;
                            }
                        }
                    }
                    if (userName === 'WebClass ユーザー') {
                        const generic = document.querySelector('.user_name, .header_user_name');
                        if (generic && generic.textContent.trim()) {
                            userName = generic.textContent.trim();
                        }
                    }
                } catch(e) {}
                data.userName = userName;

                // 3. Extract schedule settings
                const scheduleSettings = { years: [], semesters: [], currentYear: '', currentSemester: '' };
                try {
                    const yearSelect = document.querySelector('select[name="set_year"]') || document.querySelector('select[name="year"]');
                    if (yearSelect) {
                        scheduleSettings.currentYear = yearSelect.value;
                        scheduleSettings.years = Array.from(yearSelect.options).map(o => ({ label: o.text.trim(), value: o.value }));
                    }
                    const semSelect = document.querySelector('select[name="set_semester"]') || document.querySelector('select[name="semester"]');
                    if (semSelect) {
                        scheduleSettings.currentSemester = semSelect.value;
                        scheduleSettings.semesters = Array.from(semSelect.options).map(o => ({ label: o.text.trim(), value: o.value }));
                    }
                } catch(e) {}
                data.scheduleSettings = scheduleSettings;

                // 4. Extract dashboard info
                const dashboardInfo = { announcements: [], surveys: [], unreadMessageCount: 0, messageUrl: null, portfolioUrl: null };
                try {
                    const userTopInfo = document.querySelector('#UserTopInfo');
                    if (userTopInfo) {
                        const header = userTopInfo.querySelector('.page-header');
                        if (header && header.textContent.trim().includes('管理者からのお知らせ')) {
                            const listItems = userTopInfo.querySelectorAll('ul.info-list > li');
                            listItems.forEach(li => {
                                if (li.classList.contains('head')) return;
                                let a = li.querySelector('a.title.hidden-xs') || li.querySelector('a.title.visible-xs') || li.querySelector('a.title');
                                if (a) {
                                    const title = a.textContent.trim();
                                    const href = a.getAttribute('href') || '';
                                    let url = href;
                                    try {
                                        url = new URL(href, window.location.origin).href;
                                    } catch (e) {
                                        url = href;
                                    }
                                    let messageId = null;
                                    const match = href.match(/[?&]id=(\d+)/);
                                    if (match) {
                                        messageId = match[1];
                                    }
                                    let sender = '';
                                    let date = '';
                                    const infoSpan = li.querySelector('.exhibitionInfo');
                                    if (infoSpan) {
                                        const text = infoSpan.textContent.trim();
                                        const parts = text.split('-');
                                        if (parts.length >= 2) {
                                             date = parts[parts.length - 1].trim();
                                             sender = parts.slice(0, parts.length - 1).join('-').trim();
                                        } else {
                                             sender = text;
                                        }
                                    }
                                    dashboardInfo.announcements.push({
                                        title: title,
                                        url: url,
                                        date: date,
                                        sender: sender,
                                        messageId: messageId
                                    });
                                }
                            });
                        }
                    }
                    const sideBlocks = document.querySelectorAll('.side-block-outer');
                    const seenUrls = new Set();
                    sideBlocks.forEach(block => {
                        const titleEl = block.querySelector('.side-block-title');
                        if (titleEl && titleEl.textContent.trim().includes('アンケート')) {
                            const links = block.querySelectorAll('ul.menugroup > li > a');
                            links.forEach(a => {
                                const rawText = a.textContent.trim();
                                let count = 0;
                                const countMatch = rawText.match(/\((\d+)\)\s*${'$'}/);
                                if (countMatch) {
                                    count = parseInt(countMatch[1], 10);
                                }
                                const title = rawText.replace(/\(\d+\)\s*${'$'}/, '').replace(/^»\s*/, '').trim();
                                const href = a.getAttribute('href') || '';
                                let url = href;
                                try {
                                    url = new URL(href, window.location.origin).href;
                                } catch (e) {
                                    url = href;
                                }
                                if (!seenUrls.has(url)) {
                                    seenUrls.add(url);
                                    dashboardInfo.surveys.push({
                                        title: title,
                                        url: url,
                                        count: count
                                    });
                                }
                            });
                        }
                    });
                    const unreadBadge = document.querySelector('#js-unread-message-count') || document.querySelector('.hd-notification_badge');
                    if (unreadBadge) {
                        const badgeText = unreadBadge.textContent.trim();
                        const parsedVal = parseInt(badgeText, 10);
                        if (!isNaN(parsedVal)) {
                            dashboardInfo.unreadMessageCount = parsedVal;
                        }
                    }
                    const messageA = document.querySelector('a[href*="msg_editor.php"], a[href*="mbl.php/messages"]');
                    if (messageA) {
                        const href = messageA.getAttribute('href') || '';
                        try {
                            dashboardInfo.messageUrl = new URL(href, window.location.origin).href;
                        } catch (e) {
                            dashboardInfo.messageUrl = href;
                        }
                    }
                    const portfolioA = document.querySelector('a[href*="/plugin/shugaku_carte/carte/mypage"]');
                    if (portfolioA) {
                        const href = portfolioA.getAttribute('href') || '';
                        try {
                            dashboardInfo.portfolioUrl = new URL(href, window.location.origin).href;
                        } catch (e) {
                            dashboardInfo.portfolioUrl = href;
                        }
                    }
                } catch(e) {}
                data.dashboardInfo = dashboardInfo;
            }
            return JSON.stringify(data);
        })();
    """.trimIndent()

    /**
     * Detects the current page type based on URL pattern
     */
    fun detectPageType(): String = """
        (function() {
            const path = window.location.pathname.replace(/\/+$/, '');
            
            if (path.includes('login.php')) {
                return 'LOGIN';
            } else if (path === '/webclass' || path === '/webclass/index.php') {
                return 'HOME';
            } else if (path.startsWith('/webclass/course.php') && !path.includes('/contents/')) {
                return 'COURSE';
            } else if (path.startsWith('/webclass/course.php') && path.includes('/contents/')) {
                if (path.includes('/history')) {
                    return 'HISTORY';
                } else if (path.includes('/results')) {
                    return 'RESULTS';
                } else {
                    return 'DETAIL';
                }
            } else if (path.startsWith('/webclass/show_info.php')) {
                return 'PRE_START';
            }
            
            return 'OTHER';
        })();
    """.trimIndent()
    
    /**
     * Extracts course list from home page (#schedule-table)
     */
    fun extractCourses(): String = """
        (function() {
            const result = [];
            const table = document.querySelector('#schedule-table');
            
            if (!table) {
                return JSON.stringify(result);
            }
            
            const links = table.querySelectorAll('td a[href*="/course.php/"]');
            const dueSoonText = '締切が近い課題があります。';
            
            links.forEach(a => {
                const rawText = (a.textContent || '').trim();
                // Filter out 'Outside Course Period' courses
                if (rawText.includes('開講期間外')) {
                    return;
                }

                const isDueSoon = rawText.includes(dueSoonText);
                
                // Extract message count: (新着メッセージ(1))
                let messageCount = 0;
                const msgMatch = rawText.match(/新着メッセージ\((\d+)\)/);
                if (msgMatch) {
                    messageCount = parseInt(msgMatch[1], 10);
                }

                // Clean title: remove due soon text, message count text, and arrows
                let cleanTitle = rawText
                    .replace(dueSoonText, '')
                    .replace(/新着メッセージ\((\d+)\)/g, '')
                    .replace(/[（\(]新着メッセージ\d+[）\)]/g, '') // Also catch with parentheses
                    .replace(/^»\s*/g, '')
                    .trim();
                
                // Check for unread info (often in a sibling element or inside the row)
                let unreadCount = 0;
                const row = a.closest('tr');
                if (row) {
                    // Try to find unread badges in the same row
                    const badges = row.querySelectorAll('.badge, .label-danger');
                    badges.forEach(b => {
                        const num = parseInt(b.textContent.trim(), 10);
                        if (!isNaN(num)) unreadCount += num;
                    });
                }

                const href = a.getAttribute('href') || '';
                let url;
                try {
                    url = new URL(href, window.location.origin).href;
                } catch (e) {
                    url = href;
                }
                
                const match = href.match(/course\.php\/([^/]+)/);
                const code = match ? match[1] : '';
                
                result.push({
                    title: cleanTitle,
                    href: url,
                    code: code,
                    isDueSoon: isDueSoon,
                    unreadCount: unreadCount,
                    messageCount: messageCount
                });
            });
            
            return JSON.stringify(result);
        })();
    """.trimIndent()
    
    /**
     * Extracts course folders and items from course page
     */
    fun extractCourseFolders(): String = """
        (function() {
            const folders = [];
            const folderEls = document.querySelectorAll('.cl-contentsList_folder');
            
            if (!folderEls.length) {
                return JSON.stringify(folders);
            }
            
            folderEls.forEach((folderEl, index) => {
                const id = folderEl.id || 'folder-' + index;
                const titleEl = folderEl.querySelector('.panel-title');
                const folderTitle = titleEl ? titleEl.textContent.trim() : '(無題セクション)';
                
                const items = [];
                const itemEls = folderEl.querySelectorAll('.cl-contentsList_listGroupItem');
                
                itemEls.forEach((itemEl, idx) => {
                    const info = itemEl.querySelector('.cl-contentsList_contentInfo');
                    const nameRoot = info ? info.querySelector('.cm-contentsList_contentName') : null;
                    const link = nameRoot ? nameRoot.querySelector('a') : null;
                    const isNew = !!(nameRoot && nameRoot.querySelector('.cl-contentsList_new'));
                    const categoryEl = info ? info.querySelector('.cl-contentsList_categoryLabel') : null;
                    const category = categoryEl ? categoryEl.textContent.trim() : '';
                    
                    let title = '';
                    if (link) {
                        title = (link.textContent || '').trim();
                    } else if (nameRoot) {
                        const clone = nameRoot.cloneNode(true);
                        const newBadge = clone.querySelector('.cl-contentsList_new');
                        if (newBadge) newBadge.remove();
                        title = (clone.textContent || '').trim();
                    } else {
                        title = '(無題コンテンツ)';
                    }
                    
                    let isUnread = false;
                    
                    const listStatusEl = itemEl.querySelector('.cl-contentsList_listStatus');
                    if (listStatusEl) {
                        const statusBadge = listStatusEl.querySelector('.label');
                        if (statusBadge) {
                            const badgeText = statusBadge.textContent.trim();
                            if (badgeText === '未読' || badgeText === '未実施' || badgeText.includes('未')) {
                                isUnread = true;
                            }
                        }
                    }
                    
                    const isDisabled = !link;
                    let url = '';
                    if (link && link.getAttribute('href')) {
                        try {
                            url = new URL(link.getAttribute('href'), window.location.origin).href;
                        } catch (e) {
                            url = link.getAttribute('href');
                        }
                    }
                    
                    let detailUrl = null;
                    let historyUrl = null;
                    let usageCount = null;
                    let availablePeriod = null;
                    
                    const detailLinks = itemEl.querySelectorAll('.cl-contentsList_contentDetailListItemData a');
                    detailLinks.forEach(a => {
                        const href = a.getAttribute('href') || '';
                        if (!href) return;
                        
                        if (href.includes('history')) {
                            try {
                                historyUrl = new URL(href, window.location.origin).href;
                            } catch (e) {
                                historyUrl = href;
                            }
                            const text = a.textContent || '';
                            const m = text.match(/利用回数\s*(\d+)/);
                            if (m) {
                                const n = parseInt(m[1], 10);
                                if (!isNaN(n)) usageCount = n;
                            }
                        } else if (href.includes('/contents/')) {
                            try {
                                detailUrl = new URL(href, window.location.origin).href;
                            } catch (e) {
                                detailUrl = href;
                            }
                        }
                    });
                    
                    // Use usageCount to determine submission status
                    // If usageCount is null or 0, it's not submitted
                    let hasSubmitted = (usageCount !== null && usageCount > 0);
                    
                    const periodBlocks = itemEl.querySelectorAll('.cm-contentsList_contentDetailListItem');
                    periodBlocks.forEach(block => {
                        const labelEl = block.querySelector('.cm-contentsList_contentDetailListItemLabel');
                        const dataEl = block.querySelector('.cm-contentsList_contentDetailListItemData');
                        if (!labelEl || !dataEl) return;
                        const labelText = (labelEl.textContent || '').trim();
                        if (labelText.includes('利用可能期間')) {
                            availablePeriod = (dataEl.textContent || '').trim();
                        }
                    });
                    
                    items.push({
                        id: id + '-item-' + idx,
                        title: title,
                        url: url,
                        category: category,
                        isNew: isNew,
                        isDisabled: isDisabled,
                        detailUrl: detailUrl,
                        historyUrl: historyUrl,
                        usageCount: usageCount,
                        availablePeriod: availablePeriod,
                        hasSubmitted: hasSubmitted,
                        isUnread: isUnread
                    });
                });
                
                folders.push({
                    id: id,
                    title: folderTitle,
                    items: items
                });
            });
            
            return JSON.stringify(folders);
        })();
    """.trimIndent()
    
    /**
     * Extracts content detail page information
     */
    fun extractContentDetail(): String = """
        (function() {
            const data = {
                title: '',
                type: '',
                description: '',
                execStatusText: '',
                isCurrentlyAvailable: true,
                infoItems: [],
                publicQrUrl: null,
                execUrl: null,
                tabs: []
            };
            
            const titleEl = document.querySelector('h3.page-header');
            if (titleEl) {
                data.title = titleEl.textContent.trim();
                
                let p = titleEl.nextElementSibling;
                while (p && p.tagName !== 'P') {
                    p = p.nextElementSibling;
                }
                if (p) {
                    const raw = p.textContent.trim();
                    const afterColon = raw.split(':')[1] || raw.split('：')[1];
                    data.type = afterColon ? afterColon.trim() : raw;
                }
            }
            
            const tabUl = document.querySelector('ul.nav.nav-tabs');
            if (tabUl) {
                const liList = tabUl.querySelectorAll('li');
                liList.forEach(li => {
                    const a = li.querySelector('a');
                    if (!a) return;
                    const label = (a.textContent || '').trim();
                    const href = a.getAttribute('href') || '';
                    let url = href;
                    try {
                        url = new URL(href, window.location.origin).href;
                    } catch (e) {}
                    const isActive = li.classList.contains('active');
                    data.tabs.push({
                        label: label,
                        href: url,
                        isActive: isActive
                    });
                });
            }
            
            const sectionTitles = document.querySelectorAll('h4.contentsInfo-sectionTitle');
            sectionTitles.forEach(h4 => {
                const label = h4.textContent.trim();
                if (label.includes('説明')) {
                    let p = h4.nextElementSibling;
                    while (p && p.tagName !== 'P') {
                        p = p.nextElementSibling;
                    }
                    if (p) {
                        data.description = p.textContent.trim();
                    }
                } else if (label.includes('教材実行')) {
                    let div = h4.nextElementSibling;
                    while (div && div.tagName !== 'DIV') {
                        div = div.nextElementSibling;
                    }
                    if (div) {
                        const text = div.textContent.trim();
                        data.execStatusText = text;
                        if (text.includes('現在はこの教材は利用できません')) {
                            data.isCurrentlyAvailable = false;
                        }
                    }
                } else if (label.includes('公開URL')) {
                    let wrap = h4.nextElementSibling;
                    while (wrap && !wrap.classList.contains('contentsInfoListData')) {
                        wrap = wrap.nextElementSibling;
                    }
                    if (wrap) {
                        const img = wrap.querySelector('img');
                        if (img && img.getAttribute('src')) {
                            try {
                                data.publicQrUrl = new URL(img.getAttribute('src'), window.location.origin).href;
                            } catch (e) {
                                data.publicQrUrl = img.getAttribute('src');
                            }
                        }
                        const span = wrap.querySelector('#execUrlTarget') || wrap.querySelector('span');
                        if (span) {
                            data.execUrl = span.textContent.trim();
                        }
                    }
                }
            });
            
            const infoItems = document.querySelectorAll('.contentsInfoList .contentsInfoListItem');
            infoItems.forEach(item => {
                const labelEl = item.querySelector('.contentsInfoListLabel');
                const dataEl = item.querySelector('.contentsInfoListData');
                const label = labelEl ? labelEl.textContent.trim() : '';
                const value = dataEl ? dataEl.textContent.trim() : '';
                if (!label && !value) return;
                data.infoItems.push({ label: label, value: value });
            });
            
            return JSON.stringify(data);
        })();
    """.trimIndent()
    
    /**
     * Extracts content history page
     */
    fun extractContentHistory(): String = """
        (function() {
            const data = {
                title: '',
                type: '',
                tabs: [],
                usageSummary: '',
                columns: [],
                rows: []
            };
            
            const titleEl = document.querySelector('h3.page-header');
            if (titleEl) {
                data.title = titleEl.textContent.trim();
                
                let p = titleEl.nextElementSibling;
                while (p && p.tagName !== 'P') {
                    p = p.nextElementSibling;
                }
                if (p) {
                    const raw = p.textContent.trim();
                    const afterColon = raw.split(':')[1] || raw.split('：')[1];
                    data.type = afterColon ? afterColon.trim() : raw;
                }
            }
            
            const tabUl = document.querySelector('ul.nav.nav-tabs');
            if (tabUl) {
                const liList = tabUl.querySelectorAll('li');
                liList.forEach(li => {
                    const a = li.querySelector('a');
                    if (!a) return;
                    const label = (a.textContent || '').trim();
                    const href = a.getAttribute('href') || '';
                    let url = href;
                    try {
                        url = new URL(href, window.location.origin).href;
                    } catch (e) {}
                    const isActive = li.classList.contains('active');
                    data.tabs.push({
                        label: label,
                        href: url,
                        isActive: isActive
                    });
                });
            }
            
            const pList = document.querySelectorAll('p');
            for (const p of pList) {
                const text = p.textContent.trim();
                if (text.includes('利用回数')) {
                    data.usageSummary = text;
                    break;
                }
            }
            
            const tableWrapper = document.querySelector('.table-responsive');
            if (tableWrapper) {
                const table = tableWrapper.querySelector('table');
                if (table) {
                    const headCells = table.querySelectorAll('thead tr th');
                    data.columns = Array.from(headCells).map(th => th.textContent.trim());
                    
                    const bodyRows = table.querySelectorAll('tbody tr');
                    data.rows = Array.from(bodyRows).map(tr => {
                        const cells = tr.querySelectorAll('td,th');
                        return Array.from(cells).map(td => td.textContent.trim());
                    });
                }
            }
            
            return JSON.stringify(data);
        })();
    """.trimIndent()
    
    /**
     * Extracts content results page
     */
    fun extractContentResults(): String = """
        (function() {
            const data = {
                title: '',
                type: '',
                tabs: [],
                columns: [],
                rows: []
            };
            
            const titleEl = document.querySelector('h3.page-header');
            if (titleEl) {
                data.title = titleEl.textContent.trim();
                
                let p = titleEl.nextElementSibling;
                while (p && p.tagName !== 'P') {
                    p = p.nextElementSibling;
                }
                if (p) {
                    const raw = p.textContent.trim();
                    const afterColon = raw.split(':')[1] || raw.split('：')[1];
                    data.type = afterColon ? afterColon.trim() : raw;
                }
            }
            
            const tabUl = document.querySelector('ul.nav.nav-tabs');
            if (tabUl) {
                const liList = tabUl.querySelectorAll('li');
                liList.forEach(li => {
                    const a = li.querySelector('a');
                    if (!a) return;
                    const label = (a.textContent || '').trim();
                    const href = a.getAttribute('href') || '';
                    let url = href;
                    try {
                        url = new URL(href, window.location.origin).href;
                    } catch (e) {}
                    const isActive = li.classList.contains('active');
                    data.tabs.push({
                        label: label,
                        href: url,
                        isActive: isActive
                    });
                });
            }
            
            const tableWrapper = document.querySelector('.table-responsive');
            if (tableWrapper) {
                const table = tableWrapper.querySelector('table');
                if (table) {
                    const headCells = table.querySelectorAll('thead tr th');
                    data.columns = Array.from(headCells).map(th => th.textContent.trim());
                    
                    const bodyRows = table.querySelectorAll('tbody tr');
                    data.rows = Array.from(bodyRows).map(tr => {
                        const cells = tr.querySelectorAll('td,th');
                        return Array.from(cells).map(td => {
                            const link = td.querySelector('a');
                            if (link) {
                                const text = (link.textContent || '').trim();
                                const href = link.getAttribute('href') || '';
                                let abs;
                                try {
                                    abs = new URL(href, window.location.origin).href;
                                } catch (e) {
                                    abs = href;
                                }
                                return { text: text, href: abs };
                            } else {
                                return { text: td.textContent.trim() };
                            }
                        });
                    });
                }
            }
            
            return JSON.stringify(data);
        })();
    """.trimIndent()
    
    /**
     * Extracts pre-start page information
     */
    fun extractPreStartInfo(): String = """
        (function() {
            const data = {
                type: '',
                title: '',
                restrictTerms: [],
                absoluteTerms: [],
                hasStart: false,
                hasEnd: false
            };
            
            const typeEl = document.querySelector('.ui-header .ui-title');
            if (typeEl) data.type = typeEl.textContent.trim();
            
            const titleEl = document.querySelector('.wcl_pageMainTitle');
            if (titleEl) data.title = titleEl.textContent.trim();
            
            const table = document.querySelector('#ContentInfo');
            if (table) {
                const restrict = table.querySelectorAll('ul.restrictterms li');
                restrict.forEach(li => {
                    const t = li.textContent.replace(/^¤\s*/, '').trim();
                    if (t) data.restrictTerms.push(t);
                });
                
                const absolute = table.querySelectorAll('ul.absoluteterms li');
                absolute.forEach(li => {
                    const t = li.textContent.replace(/^¤\s*/, '').trim();
                    if (t) data.absoluteTerms.push(t);
                });
            }
            
            if (document.querySelector('.ui-btn-up-b')) data.hasStart = true;
            if (document.querySelector('.ui-btn-up-c')) data.hasEnd = true;
            
            return JSON.stringify(data);
        })();
    """.trimIndent()

    /** Extracts the mobile question DOM into a stable model for the Compose UI. */
    fun extractNativeQuestionPage(): String = """
        (function() {
            const clean = function(value) {
                return (value || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
            };
            const activePages = Array.from(document.querySelectorAll('.ui-page-active'));
            const root = activePages[activePages.length - 1] || document.getElementById('app') || document;
            const data = {
                title: clean(root.querySelector('.wcl_pageMainTitle')?.textContent),
                sectionTitle: clean(root.querySelector('.wcl_sectionTitle')?.textContent),
                prompt: '', deadline: '', point: '', page: '', mode: 'question', warning: '',
                inputs: [], indexItems: [], canPrevious: false, canNext: false,
                canShowIndex: false, canFinish: false
            };

            data.deadline = clean(root.querySelector('#DeadLineDispBox')?.textContent);
            data.point = clean(root.querySelector('.point')?.textContent);
            data.page = root.querySelector('[name="QstnSession[page]"]')?.value || '';
            data.warning = clean(root.querySelector('.wcl_sectionTitle .tomato_moji')?.textContent);

            const indexLinks = Array.from(root.querySelectorAll('a[href*="QuestionClient.movePage"]'));
            data.indexItems = indexLinks.map(function(a) {
                const href = a.getAttribute('href') || '';
                const match = href.match(/movePage\(['\"]([^'\"]+)['\"]\)/);
                return { page: match ? match[1] : '', label: clean(a.textContent) };
            }).filter(function(item) { return item.page; });

            const fields = Array.from(root.querySelectorAll('fieldset.question'));
            if (data.warning) data.mode = 'confirm';
            else if (!fields.length && data.indexItems.length) data.mode = 'index';

            const contents = Array.from(new Set(fields.map(function(field) { return field.closest('.content'); }).filter(Boolean)));
            const promptParts = [];
            contents.forEach(function(content) {
                const clone = content.cloneNode(true);
                clone.querySelectorAll('fieldset.question, .point, a[href*="QuestionClient."], script, style, input, textarea, select, button')
                    .forEach(function(node) { node.remove(); });
                const rendered = clean(clone.textContent);
                if (rendered) promptParts.push(rendered);
                content.querySelectorAll('.previewPlace[data-initial-source]').forEach(function(node) {
                    if (node.closest('fieldset.question')) return;
                    const source = node.getAttribute('data-initial-source') || '';
                    if (!source) return;
                    const decoded = clean(new DOMParser().parseFromString(source, 'text/html').body.textContent);
                    if (decoded) promptParts.push(decoded);
                });
            });
            data.prompt = Array.from(new Set(promptParts)).join('\n');

            fields.forEach(function(field, fieldIndex) {
                const named = Array.from(field.querySelectorAll('input[name], textarea[name], select[name]'))
                    .filter(function(el) { return !el.name.endsWith('[style]'); });
                const names = Array.from(new Set(named.map(function(el) { return el.name; })));
                names.forEach(function(name) {
                    const elements = named.filter(function(el) { return el.name === name; });
                    if (!elements.length) return;
                    const first = elements[0];
                    let type = (first.type || first.tagName || 'text').toLowerCase();
                    if (first.tagName === 'TEXTAREA') type = 'textarea';
                    if (first.tagName === 'SELECT') type = 'select';
                    const input = { name: name, type: type, label: '', value: first.value || '', options: [] };
                    if (type === 'radio' || type === 'checkbox') {
                        input.options = elements.map(function(el) {
                            const label = el.id ? field.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]') : null;
                            return { value: el.value || '', label: clean(label?.textContent || el.value), selected: !!el.checked };
                        });
                    } else if (type === 'select') {
                        input.options = Array.from(first.options || []).map(function(option) {
                            return { value: option.value || '', label: clean(option.textContent), selected: !!option.selected };
                        });
                    } else {
                        const label = first.id ? field.querySelector('label[for="' + first.id.replace(/"/g, '\\"') + '"]') : null;
                        input.label = clean(label?.textContent || field.querySelector('legend')?.textContent || '回答');
                    }
                    data.inputs.push(input);
                });
            });

            const actionLinks = Array.from(root.querySelectorAll('a[href*="QuestionClient."]'));
            const enabled = function(fragment) {
                return actionLinks.some(function(a) {
                    return (a.getAttribute('href') || '').includes(fragment) &&
                        !a.classList.contains('ui-disabled') && !a.closest('.ui-disabled');
                });
            };
            data.canPrevious = enabled('prevPage');
            data.canNext = enabled('nextPage');
            data.canShowIndex = enabled('index');
            data.canFinish = enabled('finish');
            return JSON.stringify(data);
        })();
    """.trimIndent()

    /** Writes native answers back to the mobile form, then invokes its own navigation API. */
    fun runNativeQuestionAction(answersJson: String, action: String, targetPage: String? = null): String = """
        (function() {
            const answers = $answersJson;
            const activePages = Array.from(document.querySelectorAll('.ui-page-active'));
            const root = activePages[activePages.length - 1] || document.getElementById('app') || document;
            answers.forEach(function(answer) {
                const elements = Array.from(root.querySelectorAll('[name]')).filter(function(el) {
                    return el.name === answer.name;
                });
                elements.forEach(function(el) {
                    const type = (el.type || el.tagName || '').toLowerCase();
                    if (type === 'radio' || type === 'checkbox') {
                        el.checked = (answer.values || []).includes(el.value);
                    } else {
                        el.value = (answer.values || [''])[0] || '';
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                });
            });
            if (!window.QuestionClient) return 'ERROR: QuestionClient not found';
            const action = ${org.json.JSONObject.quote(action)};
            const target = ${org.json.JSONObject.quote(targetPage ?: "")};
            if (action === 'next') QuestionClient.nextPage();
            else if (action === 'previous') QuestionClient.prevPage();
            else if (action === 'index') QuestionClient.index();
            else if (action === 'finish') QuestionClient.finish();
            else if (action === 'restart') QuestionClient.restart();
            else if (action === 'move') QuestionClient.movePage(target);
            return 'OK';
        })();
    """.trimIndent()
    
    /**
     * JavaScript to trigger "Start" button on pre-start page
     */
    fun triggerStart(): String = """
        (function() {
            const startDiv = document.querySelector('.ui-btn-hover-b');
            if (startDiv) {
                const btn = startDiv.querySelector('input[type="submit"], button, .ui-btn-text');
                if (btn && typeof btn.click === 'function') {
                    btn.click();
                    return 'OK';
                }
            }
            
            const form = document.querySelector('form[action*="show_info.php"]');
            if (form) {
                const input = form.querySelector('input[name="next"]');
                if (input && typeof input.click === 'function') {
                    input.click();
                    return 'OK';
                }
                form.submit();
                return 'OK';
            }
            
            return 'ERROR: Start button not found';
        })();
    """.trimIndent()
    
    /**
     * JavaScript to trigger "End" button on pre-start page
     */
    fun triggerEnd(): String = """
        (function() {
            const endBtn = document.querySelector('[name="giveup"]');
            if (endBtn && typeof endBtn.click === 'function') {
                endBtn.click();
                return 'OK';
            }
            return 'ERROR: End button not found';
        })();
    """.trimIndent()
    
    /**
     * Extracts user name from header
     */
    fun extractUserName(): String = """
        (function() {
            try {
                // 1. 「アカウント情報の変更」を含む dropdown を探す
                const dropdowns = Array.from(document.querySelectorAll('#menu li.dropdown'));
                for (const li of dropdowns) {
                    const hasAccountLink = li.querySelector('a[href*="user.php/config"]');
                    const toggle = li.querySelector(':scope > a.dropdown-toggle');
                    if (hasAccountLink && toggle) {
                        const text = (toggle.childNodes[0]?.textContent || toggle.textContent || '').trim();
                        if (text) return text;
                    }
                }
                
                // 2. モバイル版で 「言語」以外の dropdown-toggle を探す
                const mobileToggles = Array.from(
                    document.querySelectorAll('#menu .visible-xs-block li.dropdown > a.dropdown-toggle')
                );
                for (const a of mobileToggles) {
                    const text = (a.childNodes[0]?.textContent || a.textContent || '').trim();
                    // 「言語」または「Language」という単語が含まれていないものを優先
                    if (text && !text.includes('言語') && !text.toLowerCase().includes('language')) {
                        return text;
                    }
                }
                
                // 3. 汎用フォールバック
                const generic = document.querySelector('.user_name, .header_user_name');
                if (generic && generic.textContent.trim()) {
                    return generic.textContent.trim();
                }
            } catch (e) {
                console.warn('[WebClass] Failed to extract user name', e);
            }
            
            return 'WebClass ユーザー';
        })();
    """.trimIndent()
    /**
     * Extracts schedule settings (Year/Semester selectors)
     */
    fun extractScheduleSettings(): String = """
        (function() {
            const data = { years: [], semesters: [], currentYear: '', currentSemester: '' };
            
            // Look for year selector
            const yearSelect = document.querySelector('select[name="set_year"]') || document.querySelector('select[name="year"]');
            if (yearSelect) {
                data.currentYear = yearSelect.value;
                data.years = Array.from(yearSelect.options).map(o => ({ label: o.text.trim(), value: o.value }));
            }
            
            // Look for semester selector
            const semSelect = document.querySelector('select[name="set_semester"]') || document.querySelector('select[name="semester"]');
            if (semSelect) {
                data.currentSemester = semSelect.value;
                data.semesters = Array.from(semSelect.options).map(o => ({ label: o.text.trim(), value: o.value }));
            }
            
            return JSON.stringify(data);
        })();
    """.trimIndent()

    /**
     * Sets schedule settings and submits form
     */
    fun setScheduleSettings(year: String, semester: String): String = """
        (function() {
            const yearSelect = document.querySelector('select[name="set_year"]') || document.querySelector('select[name="year"]');
            const semSelect = document.querySelector('select[name="set_semester"]') || document.querySelector('select[name="semester"]');
            
            let changed = false;
            if (yearSelect && year && yearSelect.value !== year) {
                yearSelect.value = year;
                changed = true;
            }
            if (semSelect && semester && semSelect.value !== semester) {
                semSelect.value = semester;
                changed = true;
            }
            
            if (changed || true) { // Always submit if requested
                 const form = yearSelect ? yearSelect.form : (semSelect ? semSelect.form : null);
                 if (form) {
                     form.submit();
                     return 'SUBMITTED';
                 }
            }
            return 'FORM_NOT_FOUND';
        })();
    """.trimIndent()

    /**
     * Executes cleanup script when closing content (giveup or TextbookClient.finish)
     */
    fun cleanupSession(): String = """
        (function() {
          const btn = document.querySelector('input[name="giveup"][type="submit"]');
          if (btn) {
            btn.click();
            return 'GIVEUP_CLICKED';
          }
          const tc = window.TextbookClient;
          if (tc && typeof tc.finish === 'function') {
            tc.finish();
            return 'TEXTBOOK_FINISHED';
          }
          return 'NO_CLEANUP_REQUIRED';
        })();
    """.trimIndent()

    /**
     * Detects if there is a video in the page (including frames) and returns the src.
     */
    fun detectVideo(): String = """
        (function() {
            function findVideoInDocument(doc) {
                if (!doc) return null;
                
                // 1. Check <video> and <source>
                const video = doc.querySelector('video');
                if (video) {
                    const src = video.getAttribute('src') || (video.querySelector('source') ? video.querySelector('source').getAttribute('src') : null);
                    if (src) return src;
                }
                
                // 2. Check <frame> and <iframe>
                const frames = doc.querySelectorAll('frame, iframe');
                for (let i = 0; i < frames.length; i++) {
                    try {
                        const frame = frames[i];
                        const subDoc = frame.contentDocument || (frame.contentWindow ? frame.contentWindow.document : null);
                        if (subDoc) {
                            const result = findVideoInDocument(subDoc);
                            if (result) return result;
                        }
                    } catch (e) {
                        // Cross-origin access might fail, ignore
                    }
                }
                return null;
            }

            let url = findVideoInDocument(document);
            if (url && !url.match(/^https?:\/\//)) {
                try {
                    url = new URL(url, window.location.href).href;
                } catch(e) {}
            }
            return url ? url : 'NOT_FOUND';
        })();
    """.trimIndent()

    /**
     * Extracts Dashboard Info (Admin announcements, surveys, message count, portfolioUrl)
     */
    fun extractDashboardInfo(): String = """
        (function() {
            const data = {
                announcements: [],
                surveys: [],
                unreadMessageCount: 0,
                messageUrl: null,
                portfolioUrl: null
            };
            
            // 1. Admin Announcements
            const userTopInfo = document.querySelector('#UserTopInfo');
            if (userTopInfo) {
                const header = userTopInfo.querySelector('.page-header');
                if (header && header.textContent.trim().includes('管理者からのお知らせ')) {
                    const listItems = userTopInfo.querySelectorAll('ul.info-list > li');
                    listItems.forEach(li => {
                        if (li.classList.contains('head')) return;
                        let a = li.querySelector('a.title.hidden-xs') || li.querySelector('a.title.visible-xs') || li.querySelector('a.title');
                        if (a) {
                            const title = a.textContent.trim();
                            const href = a.getAttribute('href') || '';
                            let url = href;
                            try {
                                url = new URL(href, window.location.origin).href;
                            } catch (e) {
                                url = href;
                            }
                            let messageId = null;
                            const match = href.match(/[?&]id=(\d+)/);
                            if (match) {
                                messageId = match[1];
                            }
                            let sender = '';
                            let date = '';
                            const infoSpan = li.querySelector('.exhibitionInfo');
                            if (infoSpan) {
                                const text = infoSpan.textContent.trim();
                                const parts = text.split('-');
                                if (parts.length >= 2) {
                                     date = parts[parts.length - 1].trim();
                                     sender = parts.slice(0, parts.length - 1).join('-').trim();
                                } else {
                                     sender = text;
                                }
                            }
                            data.announcements.push({
                                title: title,
                                url: url,
                                date: date,
                                sender: sender,
                                messageId: messageId
                            });
                        }
                    });
                }
            }
            
            // 2. Surveys
            const sideBlocks = document.querySelectorAll('.side-block-outer');
            const seenUrls = new Set();
            sideBlocks.forEach(block => {
                const titleEl = block.querySelector('.side-block-title');
                if (titleEl && titleEl.textContent.trim().includes('アンケート')) {
                    const links = block.querySelectorAll('ul.menugroup > li > a');
                    links.forEach(a => {
                        const rawText = a.textContent.trim();
                        let count = 0;
                        const countMatch = rawText.match(/\((\d+)\)\s*${'$'}/);
                        if (countMatch) {
                            count = parseInt(countMatch[1], 10);
                        }
                        const title = rawText.replace(/\(\d+\)\s*${'$'}/, '').replace(/^»\s*/, '').trim();
                        const href = a.getAttribute('href') || '';
                        let url = href;
                        try {
                            url = new URL(href, window.location.origin).href;
                        } catch (e) {
                            url = href;
                        }
                        if (!seenUrls.has(url)) {
                            seenUrls.add(url);
                            data.surveys.push({
                                title: title,
                                url: url,
                                count: count
                            });
                        }
                    });
                }
            });

            // 3. Unread Message Count
            const unreadBadge = document.querySelector('#js-unread-message-count') || document.querySelector('.hd-notification_badge');
            if (unreadBadge) {
                const badgeText = unreadBadge.textContent.trim();
                const parsedVal = parseInt(badgeText, 10);
                if (!isNaN(parsedVal)) {
                    data.unreadMessageCount = parsedVal;
                }
            }

            const messageA = document.querySelector('a[href*="msg_editor.php"], a[href*="mbl.php/messages"]');
            if (messageA) {
                const href = messageA.getAttribute('href') || '';
                try {
                    data.messageUrl = new URL(href, window.location.origin).href;
                } catch (e) {
                    data.messageUrl = href;
                }
            }

            // 4. Portfolio URL Extraction
            const portfolioA = document.querySelector('a[href*="/plugin/shugaku_carte/carte/mypage"]');
            if (portfolioA) {
                const href = portfolioA.getAttribute('href') || '';
                try {
                    data.portfolioUrl = new URL(href, window.location.origin).href;
                } catch (e) {
                    data.portfolioUrl = href;
                }
            }
            
            return JSON.stringify(data);
        })();
    """.trimIndent()

    // Extracts course scores from the PersonalScoreSheet page
    fun extractCourseScores(): String {
        return """
            (function() {
                try {
                    var table = document.querySelector('table#PersonalScoreSheet');
                    if (!table) {
                        var tables = document.querySelectorAll('table');
                        for (var t = 0; t < tables.length; t++) {
                            if (tables[t].querySelector('th.contents-title') || tables[t].querySelector('tr.unittitle')) {
                                table = tables[t];
                                break;
                            }
                        }
                    }
                    if (!table) return null; // Not found or not logged in

                    var result = {
                        page: {
                            courseId: null,      
                            summaryTitle: null,
                            dateRange: { start: null, end: null },
                            showdata: null
                        },
                        categories: []
                    };

                    // Extract page meta
                    var form = document.querySelector('form#PersonalScoreSheetSearch');
                    if (form) {
                        var startInput = form.querySelector('input[name="summaryOption[dateRangeStart]"]');
                        if (startInput) result.page.dateRange.start = startInput.value;

                        var endInput = form.querySelector('input[name="summaryOption[dateRangeEnd]"]');
                        if (endInput) result.page.dateRange.end = endInput.value;

                        var showDataInput = form.querySelector('input[name="showdata"]:checked');
                        if (showDataInput) result.page.showdata = showDataInput.value;
                    }
                    
                    var currentCategory = null;
                    var rows = table.querySelectorAll('tr');
                    
                    for (var i = 0; i < rows.length; i++) {
                        var tr = rows[i];
                        
                        if (tr.classList.contains('unittitle')) {
                            // Category Row
                            var th = tr.querySelector('th');
                            var catName = th ? th.textContent.trim() : "Unknown";
                            currentCategory = {
                                name: catName,
                                items: [],
                                total: null
                            };
                            result.categories.push(currentCategory);
                            
                        } else if (tr.classList.contains('foot')) {
                            // Total Row
                            var tds = tr.querySelectorAll('td');
                            if (tds.length >= 2 && currentCategory) {
                                var myTotal = parseFloat(tds[0].textContent.trim());
                                var courseTotal = parseFloat(tds[1].textContent.trim());
                                currentCategory.total = {
                                    my: isNaN(myTotal) ? null : myTotal,
                                    course: isNaN(courseTotal) ? null : courseTotal
                                };
                            }
                            
                        } else {
                            // Item Row (likely content)
                            var titleTh = tr.querySelector('th.contents-title');
                            if (!titleTh) continue;

                            if (!currentCategory) {
                                currentCategory = {
                                    name: "成績一覧",
                                    items: [],
                                    total: null
                                };
                                result.categories.push(currentCategory);
                            }
                            
                            var titleAnchor = titleTh.querySelector('a');
                            var title = titleAnchor ? titleAnchor.textContent.trim() : titleTh.textContent.trim();
                            var contentUrl = titleAnchor ? titleAnchor.getAttribute('href') : null;
                            
                            var tds = tr.querySelectorAll('td');
                            if (tds.length < 2) continue;
                            
                            var scoreTd = tds[0];
                            var avgTd = tds[1];
                            
                            var scoreAnchor = scoreTd.querySelector('a');
                            var scoreTextRaw = scoreAnchor ? scoreAnchor.textContent.trim() : scoreTd.textContent.trim();
                            scoreTextRaw = scoreTextRaw.replace(/\s+/g, ' ');
                            var scoreUrl = scoreAnchor ? scoreAnchor.getAttribute('href') : null;
                            
                            var score = null;
                            var match = scoreTextRaw.match(/[0-9]+(\.[0-9]+)?/);
                            if (match) {
                                score = parseFloat(match[0]);
                            }
                            
                            var courseAvgText = avgTd.textContent.trim();
                            var courseAvg = parseFloat(courseAvgText);
                            
                            currentCategory.items.push({
                                title: title,
                                contentUrl: contentUrl,
                                score: isNaN(score) ? null : score,
                                scoreTextRaw: scoreTextRaw,
                                scoreUrl: scoreUrl,
                                courseAvg: isNaN(courseAvg) ? null : courseAvg
                            });
                        }
                    }
                    
                    return JSON.stringify(result);
                } catch(e) {
                    return JSON.stringify({ error: e.toString() });
                }
            })();
        """.trimIndent()
    }

    /**
     * JS snippet to switch the score display type (showdata) and submit the form.
     */
    fun switchScoreType(type: String): String = """
        (function() {
            var form = document.getElementById('PersonalScoreSheetSearch');
            if (form) {
                var radio = form.querySelector('input[name="showdata"][value="$type"]');
                if (radio) {
                    radio.checked = true;
                    radio.click();
                    form.submit();
                    return "SUBMITTED";
                }
            }
            return "FORM_NOT_FOUND";
        })();
    """.trimIndent()

    /**
     * JS snippet to switch the score date range and submit the form.
     */
    fun switchScoreDateRange(start: String, end: String): String = """
        (function() {
            var form = document.getElementById('PersonalScoreSheetSearch') || document.querySelector('form#PersonalScoreSheetSearch');
            if (form) {
                var startInput = form.querySelector('input[name="summaryOption[dateRangeStart]"]');
                var endInput = form.querySelector('input[name="summaryOption[dateRangeEnd]"]');
                var changed = false;
                if (startInput) {
                    startInput.value = "$start";
                    changed = true;
                }
                if (endInput) {
                    endInput.value = "$end";
                    changed = true;
                }
                if (changed) {
                    form.submit();
                    return "SUBMITTED";
                }
            }
            return "FORM_NOT_FOUND";
        })();
    """.trimIndent()


    /**
     * Extracts reports list from My Reports page
     */
    fun extractMyReports(): String = """
        (function() {
            try {
                var table = document.querySelector('div.table-responsive table.table');
                if (!table) return null;
                
                var result = [];
                var rows = table.querySelectorAll('tbody tr');
                for (var i = 0; i < rows.length; i++) {
                    var tr = rows[i];
                    var tds = tr.querySelectorAll('td');
                    if (tds.length < 6) continue;
                    
                    // 1. Task Name & URL
                    var taskTd = tds[0];
                    var taskAnchor = taskTd.querySelector('a');
                    var taskName = taskAnchor ? taskAnchor.textContent.trim() : taskTd.textContent.trim();
                    var taskUrl = null;
                    if (taskAnchor && taskAnchor.getAttribute('href')) {
                        try {
                            taskUrl = new URL(taskAnchor.getAttribute('href'), window.location.origin).href;
                        } catch(e) {
                            taskUrl = taskAnchor.getAttribute('href');
                        }
                    }
                    
                    // 2. Q.No
                    var questionNo = tds[1].textContent.trim();
                    
                    // 3. Report / Corrected File / Comment (Complex cell)
                    var reportTd = tds[2];
                    
                    var reportText = null;
                    var reportFileName = null;
                    var reportFileUrl = null;
                    var correctedFileName = null;
                    var correctedFileUrl = null;
                    var comment = null;
                    
                    // Parse cell
                    var headings = reportTd.querySelectorAll('h4');
                    if (headings.length > 0) {
                        for (var h = 0; h < headings.length; h++) {
                            var hText = headings[h].textContent.trim();
                            var commentDiv = null;
                            var fileAnchor = null;
                            
                            var current = headings[h].nextElementSibling;
                            while (current && current.tagName !== 'H4') {
                                if (current.tagName === 'A') {
                                    fileAnchor = current;
                                } else if (current.classList.contains('text-report-reader') || current.classList.contains('comment')) {
                                    commentDiv = current;
                                }
                                current = current.nextElementSibling;
                            }
                            
                            if (hText.includes('レポート')) {
                                if (fileAnchor) {
                                    reportFileName = fileAnchor.textContent.trim();
                                    try {
                                        reportFileUrl = new URL(fileAnchor.getAttribute('href'), window.location.origin).href;
                                    } catch(e) {
                                        reportFileUrl = fileAnchor.getAttribute('href');
                                    }
                                } else {
                                    var textReader = reportTd.querySelector('div.text-report-reader:not(.comment)');
                                    if (textReader) {
                                        reportText = textReader.textContent.trim();
                                    }
                                }
                            } else if (hText.includes('コメント')) {
                                var textReader = reportTd.querySelector('div.comment') || reportTd.querySelector('div.text-report-reader.comment') || commentDiv;
                                if (textReader) {
                                    comment = textReader.textContent.trim();
                                }
                            } else if (hText.includes('添削ファイル')) {
                                if (fileAnchor) {
                                    correctedFileName = fileAnchor.textContent.trim();
                                    try {
                                        correctedFileUrl = new URL(fileAnchor.getAttribute('href'), window.location.origin).href;
                                    } catch(e) {
                                        correctedFileUrl = fileAnchor.getAttribute('href');
                                    }
                                }
                            }
                        }
                    } else {
                        var anchor = reportTd.querySelector('a');
                        var textReader = reportTd.querySelector('div.text-report-reader');
                        if (anchor) {
                            reportFileName = anchor.textContent.trim();
                            try {
                                reportFileUrl = new URL(anchor.getAttribute('href'), window.location.origin).href;
                            } catch(e) {
                                reportFileUrl = anchor.getAttribute('href');
                            }
                        } else if (textReader) {
                            reportText = textReader.textContent.trim();
                        } else {
                            reportText = reportTd.textContent.trim();
                        }
                    }
                    
                    if (!comment) {
                        var commentDiv = reportTd.querySelector('.comment, .text-report-reader.comment');
                        if (commentDiv) {
                            comment = commentDiv.textContent.trim();
                        }
                    }
                    
                    // 4. Submission Date
                    var submissionDate = tds[3].textContent.trim();
                    
                    // 5. Result
                    var resultVal = tds[4].textContent.trim();
                    
                    // 6. Score details
                    var scoreTd = tds[5];
                    var scoreAnchor = scoreTd.querySelector('a');
                    var scoreText = scoreAnchor ? scoreAnchor.textContent.trim() : scoreTd.textContent.trim();
                    var scoreUrl = null;
                    if (scoreAnchor && scoreAnchor.getAttribute('href')) {
                        try {
                            scoreUrl = new URL(scoreAnchor.getAttribute('href'), window.location.origin).href;
                        } catch(e) {
                            scoreUrl = scoreAnchor.getAttribute('href');
                        }
                    }
                    
                    result.push({
                        taskName: taskName,
                        taskUrl: taskUrl,
                        questionNo: questionNo,
                        reportText: reportText,
                        reportFileName: reportFileName,
                        reportFileUrl: reportFileUrl,
                        correctedFileName: correctedFileName,
                        correctedFileUrl: correctedFileUrl,
                        comment: comment,
                        submissionDate: submissionDate,
                        result: resultVal,
                        scoreText: scoreText,
                        scoreUrl: scoreUrl
                    });
                }
                return JSON.stringify(result);
            } catch(e) {
                return JSON.stringify({ error: e.toString() });
            }
        })();
    """.trimIndent()

    /**
     * Recursively scans same-origin child frames and checks all frame/embed/link URLs for PDF documents.
     */
    fun detectPdfs(): String = """
        (function() {
            const pdfs = [];
            function checkUrl(url) {
                if (!url) return;
                const lowerUrl = url.toLowerCase();
                // Check if url contains .pdf or has pdf extension or contains application/pdf mime type
                if (lowerUrl.includes('.pdf') || lowerUrl.includes('mimetype=application/pdf') || (lowerUrl.includes('file.php?file=') && lowerUrl.includes('.pdf'))) {
                    if (!pdfs.includes(url)) {
                        pdfs.push(url);
                    }
                }
            }
            
            function scanDocument(doc) {
                if (!doc) return;
                
                // 1. Check current document URL
                checkUrl(doc.URL);
                if (doc.location) checkUrl(doc.location.href);
                
                // 2. Check embed and object elements
                try {
                    doc.querySelectorAll('embed[src], object[data]').forEach(el => {
                        const url = el.src || el.data;
                        if (url) {
                            try {
                                const absoluteUrl = new URL(url, doc.baseURI || doc.location.href).href;
                                checkUrl(absoluteUrl);
                            } catch(e) {
                                checkUrl(url);
                            }
                        }
                    });
                } catch(e) {}

                // 3. Check anchor elements that look like PDFs
                try {
                    doc.querySelectorAll('a[href]').forEach(el => {
                        const href = el.getAttribute('href');
                        if (href) {
                            try {
                                const absoluteUrl = new URL(href, doc.baseURI || doc.location.href).href;
                                const lowerHref = absoluteUrl.toLowerCase();
                                if (lowerHref.includes('.pdf')) {
                                    checkUrl(absoluteUrl);
                                }
                            } catch(e) {}
                        }
                    });
                } catch(e) {}

                // 4. Check frames and iframes
                try {
                    const frames = doc.querySelectorAll('iframe, frame');
                    frames.forEach(frame => {
                        const src = frame.src;
                        if (src) {
                            try {
                                const absoluteUrl = new URL(src, doc.baseURI || doc.location.href).href;
                                checkUrl(absoluteUrl);
                            } catch(e) {
                                checkUrl(src);
                            }
                        }
                        
                        // Try to access same-origin contentDocument recursively
                        try {
                            if (frame.contentDocument) {
                                scanDocument(frame.contentDocument);
                            } else if (frame.contentWindow && frame.contentWindow.document) {
                                scanDocument(frame.contentWindow.document);
                            }
                        } catch(e) {
                            // Cross-origin exception, ignore
                        }
                    });
                } catch(e) {}
            }

            try {
                scanDocument(document);
                if (window.top && window.top !== window) {
                    scanDocument(window.top.document);
                }
            } catch(e) {
                try {
                    scanDocument(document);
                } catch(err) {}
            }

            return JSON.stringify(pdfs);
        })();
    """.trimIndent()
}
