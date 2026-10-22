package com.workreport.app.domain.model

/**
 * 一条敏感词规则。
 *
 * @param word   敏感词原文，例如「纪委」
 * @param pinyin 替换成的缩写，例如「jw」（小写拼音首字母）
 * @param auto   缩写是否由规则自动生成（自动生成时，改词会同步重算缩写）
 */
data class SensitiveWord(
    val word: String,
    val pinyin: String,
    val auto: Boolean = true,
)
