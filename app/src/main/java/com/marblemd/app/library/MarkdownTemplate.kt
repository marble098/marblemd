package com.marblemd.app.library

/** Starter documents offered by "New file". */
enum class MarkdownTemplate(
    val label: String,
    val description: String,
    val suggestedName: String,
    val emoji: String,
    val body: String
) {
    BLANK(
        label = "Blank",
        description = "Empty document, ready for typing",
        suggestedName = "Untitled.md",
        emoji = "📄",
        body = "# Untitled\n\n"
    ),
    NOTE(
        label = "Note",
        description = "Quick note with tasks",
        suggestedName = "Note.md",
        emoji = "🗒️",
        body = """
            # عنوان یادداشت

            > تاریخ: 

            ## نکات مهم

            - 
            - 

            ## کارها

            - [ ] 
            - [ ] 
        """.trimIndent()
    ),
    ARTICLE(
        label = "Article",
        description = "Long form writing with a summary and sections",
        suggestedName = "Article.md",
        emoji = "📝",
        body = """
            # عنوان مقاله

            **نویسنده:** 

            ---

            ## چکیده

            خلاصه‌ای کوتاه از موضوع بنویسید.

            ## مقدمه

            متن مقدمه...

            ## بدنه اصلی

            ### بخش اول

            - نکته اول
            - نکته دوم

            ```kotlin
            // نمونه کد
            fun main() = println("MarbleMD")
            ```

            ## نتیجه‌گیری

            جمع‌بندی نهایی...
        """.trimIndent()
    ),
    README(
        label = "README",
        description = "Project documentation with badges and a feature table",
        suggestedName = "README.md",
        emoji = "📚",
        body = """
            # Project name

            One line that explains what this project does.

            ## ✨ Features

            | Feature | Status |
            | --- | --- |
            | Fast | ✅ |
            | Multilingual | ✅ |

            ## 🚀 Getting started

            1. Install
            2. Configure
            3. Enjoy

            ## 📄 License

            MIT
        """.trimIndent()
    ),
    MEETING(
        label = "Meeting notes",
        description = "Agenda, decisions and action items",
        suggestedName = "Meeting.md",
        emoji = "📅",
        body = """
            # جلسه — 

            **تاریخ:** 
            **حاضرین:** 

            ## دستور جلسه

            1. 
            2. 

            ## تصمیم‌ها

            - 

            ## اقدامات

            | کار | مسئول | مهلت |
            | --- | --- | --- |
            |  |  |  |
        """.trimIndent()
    ),
    CHANGELOG(
        label = "Changelog",
        description = "Keep a changelog in the Keep a Changelog style",
        suggestedName = "CHANGELOG.md",
        emoji = "🧾",
        body = """
            # Changelog

            All notable changes to this project are documented here.

            ## [Unreleased]

            ### Added

            - 

            ### Fixed

            - 

            ## [1.0.0] — today

            ### Added

            - First release
        """.trimIndent()
    );

    companion object {
        val default: MarkdownTemplate = BLANK
    }
}
