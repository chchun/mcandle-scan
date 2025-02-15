package com.example.mybleapp.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.mybleapp.MemberInfo
import com.example.mybleapp.R

class MemberInfoDialog(private val memberInfo: MemberInfo) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_member_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvGrade: TextView = view.findViewById(R.id.tvGrade)
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val tvKakao: TextView = view.findViewById(R.id.tvKakao)
        val tvCardNo: TextView = view.findViewById(R.id.tvCardNo)
        val tvCreditCard: TextView = view.findViewById(R.id.tvCreditCard)
        val btnClose: Button = view.findViewById(R.id.btnClose)

        // 멤버 정보 설정
        tvName.text = "이름: ${memberInfo.name}"
        tvGrade.text = "등급: ${memberInfo.grade}"
        tvPhone.text = "전화번호: ${memberInfo.phone}"
        tvKakao.text = "카카오 사용: ${if (memberInfo.kakao_use) "사용" else "미사용"}"
        tvCardNo.text = "멤버십 카드번호: ${memberInfo.member_card_no}"
        tvCreditCard.text = "신용카드 정보: ${memberInfo.credit_card_name} (${memberInfo.credit_card_no})"

        btnClose.setOnClickListener {
            dismiss() // 다이얼로그 닫기
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // 배경 투명 처리
        return dialog
    }
}
