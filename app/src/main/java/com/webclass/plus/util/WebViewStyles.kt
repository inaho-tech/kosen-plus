package com.webclass.plus.util

import com.webclass.plus.ui.theme.AppTheme

object WebViewStyles {
    
    private const val FONT_IMPORTS = """
@import url("https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0");
@import url("https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap");
"""
    
    private val lightThemeRoot = """
:root {
  --wcx-nav-height: 68px;
  --wcx-color-bg: #ffffff;
  --wcx-color-bg-elevated: #fafbfd;
  --wcx-color-surface: #ffffff;
  --wcx-color-border: #e8f4f8;
  --wcx-color-border-dark: #b8dae8;
  --wcx-color-primary: #4fc3f7;
  --wcx-color-primary-variant: #29b6f6;
  --wcx-color-on-primary: #ffffff;
  --wcx-color-secondary: #81d4fa;
  --wcx-color-text-primary: #0a1e3d;
  --wcx-color-text-secondary: #5f7d98;
  --wcx-color-text-disabled: #b0c5d9;
  --wcx-color-accent: #4fc3f7;
  --wcx-radius-sm: 12px;
  --wcx-radius-md: 16px;
  --wcx-radius-lg: 20px;
  --wcx-radius-xl: 24px;
  --wcx-shadow-sm: 0 2px 8px rgba(79, 195, 247, 0.08);
  --wcx-shadow-md: 0 4px 16px rgba(79, 195, 247, 0.12);
  --wcx-shadow-lg: 0 8px 24px rgba(79, 195, 247, 0.15);
  --wcx-shadow-xl: 0 12px 40px rgba(79, 195, 247, 0.2);
  --wcx-color-due-bg: #f0f9ff;
  --wcx-color-due-border: var(--wcx-color-secondary);
}
""".trimIndent()
    
    private val darkThemeRoot = """
:root {
  --wcx-nav-height: 68px;
  --wcx-color-bg: #0a0e1a;
  --wcx-color-bg-elevated: #131824;
  --wcx-color-surface: #1a1f2e;
  --wcx-color-border: #2a3040;
  --wcx-color-border-dark: #3a4050;
  --wcx-color-primary: #00d4ff;
  --wcx-color-primary-variant: #00b8e6;
  --wcx-color-on-primary: #0a0e1a;
  --wcx-color-secondary: #4dd0e1;
  --wcx-color-text-primary: #e8f2f7;
  --wcx-color-text-secondary: #8fa3b0;
  --wcx-color-text-disabled: #4a5568;
  --wcx-color-accent: #00d4ff;
  --wcx-radius-sm: 12px;
  --wcx-radius-md: 16px;
  --wcx-radius-lg: 20px;
  --wcx-radius-xl: 24px;
  --wcx-shadow-sm: 0 2px 8px rgba(0, 212, 255, 0.15);
  --wcx-shadow-md: 0 4px 16px rgba(0, 212, 255, 0.2);
  --wcx-shadow-lg: 0 8px 24px rgba(0, 212, 255, 0.25);
  --wcx-shadow-xl: 0 12px 40px rgba(0, 212, 255, 0.3);
  --wcx-color-due-bg: #1a2838;
  --wcx-color-due-border: var(--wcx-color-secondary);
}
""".trimIndent()
    
    private val pastelThemeRoot = """
:root {
  --wcx-nav-height: 68px;
  --wcx-color-bg: #fef5e7;
  --wcx-color-bg-elevated: #fff9f0;
  --wcx-color-surface: #fffdf7;
  --wcx-color-border: #f5e6d3;
  --wcx-color-border-dark: #e8d4bd;
  --wcx-color-primary: #a5d6a7;
  --wcx-color-primary-variant: #81c784;
  --wcx-color-on-primary: #2e5233;
  --wcx-color-secondary: #c8e6c9;
  --wcx-color-text-primary: #3e4a3a;
  --wcx-color-text-secondary: #6d8066;
  --wcx-color-text-disabled: #b8c5b3;
  --wcx-color-accent: #a5d6a7;
  --wcx-radius-sm: 12px;
  --wcx-radius-md: 16px;
  --wcx-radius-lg: 20px;
  --wcx-radius-xl: 24px;
  --wcx-shadow-sm: 0 2px 8px rgba(129, 199, 132, 0.12);
  --wcx-shadow-md: 0 4px 16px rgba(129, 199, 132, 0.16);
  --wcx-shadow-lg: 0 8px 24px rgba(129, 199, 132, 0.2);
  --wcx-shadow-xl: 0 12px 40px rgba(129, 199, 132, 0.25);
  --wcx-color-due-bg: #f1f8e9;
  --wcx-color-due-border: var(--wcx-color-secondary);
}
""".trimIndent()

    private val countryThemeRoot = """
:root {
  --wcx-nav-height: 68px;
  --wcx-color-bg: #fffefa;
  --wcx-color-bg-elevated: #fffefa;
  --wcx-color-surface: #fffefa;
  --wcx-color-border: #d7ccc8;
  --wcx-color-border-dark: #bcaaa4;
  --wcx-color-primary: #5d4037;
  --wcx-color-primary-variant: #4e342e;
  --wcx-color-on-primary: #ffffff;
  --wcx-color-secondary: #5d4037;
  --wcx-color-text-primary: #3e2723;
  --wcx-color-text-secondary: #6d4c41;
  --wcx-color-text-disabled: #a1887f;
  --wcx-color-accent: #5d4037;
  --wcx-radius-sm: 12px;
  --wcx-radius-md: 16px;
  --wcx-radius-lg: 20px;
  --wcx-radius-xl: 24px;
  --wcx-shadow-sm: 0 2px 8px rgba(93, 64, 55, 0.08);
  --wcx-shadow-md: 0 4px 16px rgba(93, 64, 55, 0.12);
  --wcx-shadow-lg: 0 8px 24px rgba(93, 64, 55, 0.15);
  --wcx-shadow-xl: 0 12px 40px rgba(93, 64, 55, 0.2);
  --wcx-color-due-bg: #efebe9;
  --wcx-color-due-border: var(--wcx-color-secondary);
}
""".trimIndent()
    
    // User's complete CSS styles (keeping the rest of the CSS)
    private val customStyles = """
/*content-hide*/
""".trimIndent()
    
    fun getThemeCss(theme: AppTheme, customCss: String = ""): String {
        val rootCss = when (theme) {
            AppTheme.LIGHT -> lightThemeRoot
            AppTheme.DARK -> darkThemeRoot
            AppTheme.PASTEL -> pastelThemeRoot
            AppTheme.CUSTOM -> lightThemeRoot
            AppTheme.SYSTEM -> lightThemeRoot // Resolved at Compose layer
            AppTheme.COUNTRY -> countryThemeRoot
        }
        
        return """
$FONT_IMPORTS

$rootCss

$customStyles

$customCss
        """.trimIndent()
    }
    
    fun injectCss(css: String): String {
        val escapedCss = css
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
        
        return """
            (function() {
                var style = document.getElementById('webclass-custom-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'webclass-custom-style';
                    style.type = 'text/css';
                    document.head.appendChild(style);
                }
                style.textContent = `$escapedCss`;
            })();
        """.trimIndent()
    }
}
