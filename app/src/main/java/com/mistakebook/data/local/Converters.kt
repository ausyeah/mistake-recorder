package com.mistakebook.data.local

import androidx.room.TypeConverter
import com.mistakebook.domain.AttachmentKind
import com.mistakebook.domain.AttachmentStatus
import com.mistakebook.domain.ChatRole
import com.mistakebook.domain.ErrorReason
import com.mistakebook.domain.MessageStatus
import com.mistakebook.domain.MasteryStatus
import com.mistakebook.domain.ReviewResult
import com.mistakebook.domain.TaskStatus

/**
 * Room 枚举转换：入库一律用 name 字符串，便于跨版本迁移与排查。
 */
class Converters {

    @TypeConverter
    fun errorReasonToString(value: ErrorReason): String = value.name

    @TypeConverter
    fun stringToErrorReason(value: String): ErrorReason =
        runCatching { ErrorReason.valueOf(value) }.getOrDefault(ErrorReason.OTHER)

    @TypeConverter
    fun masteryStatusToString(value: MasteryStatus): String = value.name

    @TypeConverter
    fun stringToMasteryStatus(value: String): MasteryStatus =
        runCatching { MasteryStatus.valueOf(value) }.getOrDefault(MasteryStatus.ACTIVE)

    @TypeConverter
    fun taskStatusToString(value: TaskStatus): String = value.name

    @TypeConverter
    fun stringToTaskStatus(value: String): TaskStatus =
        runCatching { TaskStatus.valueOf(value) }.getOrDefault(TaskStatus.PENDING)

    @TypeConverter
    fun reviewResultToString(value: ReviewResult): String = value.name

    @TypeConverter
    fun stringToReviewResult(value: String): ReviewResult =
        runCatching { ReviewResult.valueOf(value) }.getOrDefault(ReviewResult.CORRECT)

    @TypeConverter
    fun chatRoleToString(value: ChatRole): String = value.name

    @TypeConverter
    fun stringToChatRole(value: String): ChatRole =
        runCatching { ChatRole.valueOf(value) }.getOrDefault(ChatRole.USER)

    @TypeConverter
    fun messageStatusToString(value: MessageStatus): String = value.name

    /**
     * 读不出来时兜底 [MessageStatus.FAILED]，而不是 [DONE]。
     *
     * 理由：拿到一条状态未知的消息，**宁可显示成失败让用户重试**，
     * 也不能当成正常完成——后者会让用户以为 AI 答完了，
     * 而实际内容可能是空的或半截的。
     */
    @TypeConverter
    fun stringToMessageStatus(value: String): MessageStatus =
        runCatching { MessageStatus.valueOf(value) }.getOrDefault(MessageStatus.FAILED)

    @TypeConverter
    fun attachmentKindToString(value: AttachmentKind): String = value.name

    @TypeConverter
    fun stringToAttachmentKind(value: String): AttachmentKind =
        runCatching { AttachmentKind.valueOf(value) }.getOrDefault(AttachmentKind.OTHER)

    @TypeConverter
    fun attachmentStatusToString(value: AttachmentStatus): String = value.name

    /**
     * 兜底 [AttachmentStatus.FAILED] 而非 [AttachmentStatus.READY]：
     * 状态读不出来却当成可用，等于把一个坏附件发给模型，
     * 模型会对着不存在的内容编答案。
     */
    @TypeConverter
    fun stringToAttachmentStatus(value: String): AttachmentStatus =
        runCatching { AttachmentStatus.valueOf(value) }.getOrDefault(AttachmentStatus.FAILED)
}
