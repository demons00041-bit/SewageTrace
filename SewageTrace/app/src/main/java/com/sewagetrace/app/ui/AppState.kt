package com.sewagetrace.app.ui

/** 本机视觉复核结果：判断工单所指水体是否已恢复 */
data class VerifyResult(
    val cleared: Boolean,
    val index: Int,
    val grade: String,
    val confidencePct: Int,
    val engine: String
)
