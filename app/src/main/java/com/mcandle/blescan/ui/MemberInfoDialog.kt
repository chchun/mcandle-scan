package com.mcandle.blescan.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.mcandle.blescan.R
import com.mcandle.blescan.databinding.DialogMemberInfoBinding
import com.mcandle.blescan.MemberInfo

class MemberInfoDialog(private val memberInfo: MemberInfo) : DialogFragment() {

    private var _binding: DialogMemberInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMemberInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 멤버 정보 설정
        binding.tvName.text = "이름: ${memberInfo.name}"
        binding.tvGrade.text = "등급: ${memberInfo.grade}"
        binding.tvPhone.text = "전화번호: ${memberInfo.phone}"
        binding.tvKakao.text = "카카오 사용: ${if (memberInfo.kakao_use) "사용" else "미사용"}"
        binding.tvCardNo.text = "멤버십 카드번호: ${memberInfo.member_card_no}"
        binding.tvCreditCard.text = "신용카드 정보: ${memberInfo.credit_card_name} (${memberInfo.credit_card_no})"

        // 닫기 버튼
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // 배경 투명 처리
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
