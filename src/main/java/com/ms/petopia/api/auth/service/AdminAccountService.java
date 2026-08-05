package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.FairAdminAssignment;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.mapper.FairAdminAssignmentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/*
    행사 신청이 승인될 때 관리자 계정을 새로 발급해 주는 service
    행사 신청 시의(행사 테이블의) name, email, phone의 값을 가지고 users 테이블에 새 계정을 만든다.
    users.email과 행사의 email은 같을 수 없다(이미 가입된 메일도 안 됨)
 */

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AuthMapper authMapper;
    private final FairAdminAssignmentMapper fairAdminAssignmentMapper;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long issueEventAdminAccount(Long fairId, Long applicantUserId,
                                       String managerName, String managerEmail, String managerPhone) {

        if(authMapper.selectUserByEmail(managerEmail) != null) {
            throw new CommonException(ErrorCode.DUPLICATED_EMAIL);
        }

        //생일, 동의 약관 등 부가 정보는 신청자 정보에서 빼와서 넣는다.
        User applicant = authMapper.selectUserById(applicantUserId);

        //행사에서 phone은 null 가능이라 null 이면 신청자 정보에서 뺴와서 넣는다.
        String phone = (managerPhone != null && !managerPhone.isBlank()) ? managerPhone : applicant.getPhone();

        String tempPassword = TokenHashUtil.generateRawToken();
        String passwordHash = passwordEncoder.encode(tempPassword);

        User newAdmin = User.builder()
                .email(managerEmail)
                .passwordHash(passwordHash)
                .nickname(managerName)
                .birthDate(applicant.getBirthDate())
                .phone(phone)
                .gender(applicant.getGender())
                .address(applicant.getAddress())
                .agreedTerms(applicant.isAgreedTerms())
                .agreedPrivacy(applicant.isAgreedPrivacy())
                .emailVerified(true)
                .role("EVENT_ADMIN")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
        authMapper.insertUser(newAdmin);

        //페어-관리자 연결 테이블에 값 넣기
        FairAdminAssignment fairAdminAssignment = FairAdminAssignment.builder()
                .adminUserId(newAdmin.getUserId())
                .requesterUserId(applicant.getUserId())
                .fairId(fairId)
                .build();
        fairAdminAssignmentMapper.insertFairAdminAssignment(fairAdminAssignment);

        //비밀번호가 담긴 메일 전송
        mailService.sendAdminAccountIssueEmail(managerEmail, tempPassword);

        return newAdmin.getUserId();

    }
}
