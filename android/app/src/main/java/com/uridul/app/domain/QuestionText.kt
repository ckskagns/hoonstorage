package com.uridul.app.domain

import com.uridul.app.data.PartnerAnswer

/** 오늘의 질문 카드·창에 쓰는 안내 글 (화면과 떼어 단위 테스트) */
object QuestionText {

    /** 홈 카드 아래 한 줄 */
    fun cardStatus(ready: Boolean, paired: Boolean, mine: String?, partner: PartnerAnswer, partnerName: String): String = when {
        !ready -> "곧 열려요"
        !paired -> if (mine == null) "눌러서 먼저 답해 두기" else "답했어요 · $partnerName 연결을 기다려요"
        mine == null && partner is PartnerAnswer.Locked -> "🔒 ${partnerName}님은 답했어요 · 내가 답하면 열려요"
        mine == null -> "눌러서 답하기"
        partner is PartnerAnswer.Open -> "💞 둘 다 답했어요 · 눌러서 보기"
        partner is PartnerAnswer.None -> "답했어요 · ${partnerName}님 답을 기다려요"
        else -> "답했어요"
    }

    /** 답하기 창의 상대 칸: 글과 흐리게 보일지 */
    fun partnerLine(paired: Boolean, partner: PartnerAnswer, loading: Boolean): Pair<String, Boolean> = when {
        !paired -> "상대가 연결되면 함께 답할 수 있어요" to true
        partner is PartnerAnswer.Open -> partner.text to false
        partner is PartnerAnswer.Locked -> "🔒 답했어요 · 내가 답하면 열려요" to true
        partner is PartnerAnswer.None -> "💭 아직 답하지 않았어요" to true
        loading -> "불러오는 중…" to true
        else -> "연결되면 확인할 수 있어요" to true
    }
}
