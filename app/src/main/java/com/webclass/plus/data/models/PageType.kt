package com.webclass.plus.data.models

/**
 * Represents the current page type detected in WebView
 */
enum class PageType {
    HOME,           // /webclass or /webclass/index.php
    COURSE,         // /webclass/course.php/* (not /contents/)
    DETAIL,         // /webclass/course.php/*/contents/* (not /history or /results)
    HISTORY,        // /webclass/course.php/*/contents/*/history
    RESULTS,        // /webclass/course.php/*/contents/*/results
    PRE_START,      // /webclass/show_info.php
    OTHER           // Any other page
}
