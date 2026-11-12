package com.webclass.plus.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.ui.graphics.vector.ImageVector

fun getCourseIcon(title: String): ImageVector {
    val t = title.lowercase()
    return when {
        // "国語", "日本", "中国", "英語", "ドイツ", "英会話", "国文学" -> Language
        t.contains("国語") || t.contains("日本") || t.contains("中国") || t.contains("英語") || t.contains("ドイツ") || t.contains("英会話") || t.contains("国文学") -> Icons.Outlined.Language
        
        // "数学", "線形代数", "微分", "積分" -> Calculate/Functions
        t.contains("数学") || t.contains("線形代数") || t.contains("微分") || t.contains("積分") -> Icons.Outlined.Calculate
        
        // "統計", "統計学" -> BarChart
        t.contains("統計") -> Icons.Outlined.BarChart
        
        // "保険", "保健" -> HealthAndSafety
        t.contains("保険") || t.contains("保健") -> Icons.Outlined.HealthAndSafety
        
        // "体育" -> FitnessCenter
        t.contains("体育") -> Icons.Outlined.FitnessCenter
        
        // "地理", "社会", "経済学", "法学" -> Public, "歴史" -> HistoryEdu
        t.contains("地理") || t.contains("社会") || t.contains("経済学") || t.contains("法学") -> Icons.Outlined.Public
        t.contains("歴史") -> Icons.Outlined.HistoryEdu
        
        // "物理", "化学", "科学", "サイエンス" -> Science
        t.contains("物理") || t.contains("化学") || t.contains("科学") || t.contains("サイエンス") -> Icons.Outlined.Science
        
        // "哲学", "心理学" -> Psychology
        t.contains("哲学") || t.contains("心理学") -> Icons.Outlined.Psychology
        
        // "課題研究", "研究" -> Lightbulb
        t.contains("課題研究") || t.contains("研究") -> Icons.Outlined.Lightbulb
        
        // "技術", "実習" -> Build
        t.contains("技術") || t.contains("実習") -> Icons.Outlined.Build

        // "機械" -> PrecisionManufacturing
        t.contains("機械") -> Icons.Outlined.PrecisionManufacturing
        
        // "設計" -> DesignServices
        t.contains("設計") -> Icons.Outlined.DesignServices
        
        // "情報", "計算機", "コンピュータ", "システム", "プログラミング" -> Computer / Memory
        t.contains("情報") || t.contains("計算機") || t.contains("コンピュータ") || t.contains("プログラミング") -> Icons.Outlined.Computer
        t.contains("電子工学") || t.contains("システム") -> Icons.Outlined.Memory
        
        // "力学" -> Engineering
        t.contains("力学") -> Icons.Outlined.Engineering
        
        // "ロボット" -> SmartToy
        t.contains("ロボット") -> Icons.Outlined.SmartToy
        
        // "電磁気", "電気回路", "電子回路" -> ElectricalServices / FlashOn
        t.contains("電磁気") -> Icons.Outlined.FlashOn
        t.contains("電気回路") || t.contains("電子回路") -> Icons.Outlined.ElectricalServices
        
        // "計測工学", "製図" -> Architecture
        t.contains("計測工学") || t.contains("製図") -> Icons.Outlined.Architecture
        
        // "制御" -> Tune
        t.contains("制御") -> Icons.Outlined.Tune
        
        // "セミナー" -> Groups
        t.contains("セミナー") -> Icons.Outlined.Groups
        
        // "芸術", "美術", "音楽" -> Palette
        t.contains("芸術") || t.contains("美術") || t.contains("音楽") -> Icons.Outlined.Palette

        // "HR", "ホームルーム" -> MeetingRoom
        t.contains("hr") || t.contains("ホームルーム") -> Icons.Outlined.MeetingRoom
        
        else -> Icons.Outlined.School
    }
}

fun getAssignmentIcon(category: String): ImageVector {
    val cat = category.lowercase()
    return when {
        cat.contains("テスト") || cat.contains("試験") -> Icons.Outlined.Quiz
        cat.contains("アンケート") -> Icons.Outlined.Poll
        cat.contains("レポート") || cat.contains("課題") -> Icons.Outlined.Description
        cat.contains("教材") || cat.contains("資料") -> Icons.Outlined.MenuBook
        else -> Icons.Outlined.Assignment
    }
}
