package com.uridul.app.domain

import com.uridul.app.data.DateReview

/**
 * 일기장 탭의 새 소식 점: 내가 일기장을 마지막으로 본 뒤에 상대가 후기를 남겼거나 사진을 올렸으면.
 * (내가 쓴 것은 점을 띄우지 않는다)
 */
object DiaryNews {
    fun hasNew(reviews: Collection<DateReview>, myUid: String, seenAt: Long, partnerPhotoAt: Long?): Boolean =
        reviews.any { it.entries.isNotEmpty() && it.updatedBy.isNotEmpty() && it.updatedBy != myUid && it.updatedAtMillis > seenAt } ||
            (partnerPhotoAt ?: 0L) > seenAt
}
