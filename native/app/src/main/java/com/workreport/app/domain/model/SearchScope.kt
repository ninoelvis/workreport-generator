package com.workreport.app.domain.model

/**
 * 历史报告的搜索范围。
 *
 * v2.0.0 起，搜索不再只匹配文件名：
 * 用户真正想找的往往是「上周写了什么」，而他记得的是**内容**，
 * 不是「张三-2026年10月第3周工作报告」这种文件名。
 *
 * 但搜全部内容时结果会很杂，所以让用户自己选，默认维持原来的「仅文件名」。
 */
enum class SearchScope(val storageValue: String, val label: String) {

    /** 只搜文件名与展示名（v1.x 的原有行为） */
    FILE_NAME("file_name", "文件名"),

    /** 搜文件名 + 标题 + 时间范围 + 本周完成 + 下周计划（**原始条目**，不是脱敏后的） */
    ALL_CONTENT("all_content", "全部内容");

    companion object {
        fun from(value: String?): SearchScope =
            entries.firstOrNull { it.storageValue == value } ?: FILE_NAME
    }
}
