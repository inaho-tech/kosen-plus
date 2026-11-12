package com.webclass.plus.data.models

data class NativeQuestionOption(
    val value: String = "",
    val label: String = "",
    val selected: Boolean = false
)

data class NativeQuestionInput(
    val name: String = "",
    val type: String = "text",
    val label: String = "",
    val value: String = "",
    val options: List<NativeQuestionOption> = emptyList()
)

data class NativeQuestionIndexItem(
    val page: String = "",
    val label: String = ""
)

data class NativeQuestionPage(
    val title: String = "",
    val sectionTitle: String = "",
    val prompt: String = "",
    val deadline: String = "",
    val point: String = "",
    val page: String = "",
    val mode: String = "question",
    val warning: String = "",
    val inputs: List<NativeQuestionInput> = emptyList(),
    val indexItems: List<NativeQuestionIndexItem> = emptyList(),
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val canShowIndex: Boolean = false,
    val canFinish: Boolean = false
)
