package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.FairAdminAssignment;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.AdminAccountListItemResponse;
import com.ms.petopia.api.auth.dto.EmailLoginRequest;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.mapper.FairAdminAssignmentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

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
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    //행사 관리자 계정 생성
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
        try {
            authMapper.insertUser(newAdmin);
        } catch (DuplicateKeyException e) {
            //동시 요청이 겹쳐 email UNIQUE 제약에 걸린 경우 500 대신 409로 응답
            throw new CommonException(ErrorCode.DUPLICATED_EMAIL, e);
        }

        //페어-관리자 연결 테이블에 값 넣기
        FairAdminAssignment fairAdminAssignment = FairAdminAssignment.builder()
                .adminUserId(newAdmin.getUserId())
                .requesterUserId(applicant.getUserId())
                .fairId(fairId)
                .build();
        try {
            fairAdminAssignmentMapper.insertFairAdminAssignment(fairAdminAssignment);
        } catch (DuplicateKeyException e) {
            //동시 승인 요청이 겹쳐 fair_id UNIQUE 제약에 걸린 경우 500 대신 409로 응답
            throw new CommonException(ErrorCode.FAIR_ADMIN_ALREADY_ASSIGNED, e);
        }

        //비밀번호가 담긴 메일 전송
        mailService.sendAdminAccountIssueEmail(managerEmail, tempPassword);

        return newAdmin.getUserId();

    }

    //SUPER_ADMIN 전용 로그인
    public TokenPair adminLogin(EmailLoginRequest request) {
        User user = authMapper.selectUserByEmail(request.getEmail());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CommonException(ErrorCode.INVALID_LOGIN);
        }

        if (!user.getRole().equals("SUPER_ADMIN")) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

        if (user.getStatus().equals("INACTIVE")) {
            throw new CommonException(ErrorCode.ACCOUNT_INACTIVE);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);
        refreshTokenStore.save(refreshTokenHash, user.getUserId(), Duration.ofDays(14));

        return new TokenPair(accessToken, refreshToken);
    }


    //관리자 계정 목록 조회
    public List<AdminAccountListItemResponse> getAdminAccounts() {
        return authMapper.selectAdminAccounts().stream()
                .map(row -> new AdminAccountListItemResponse(
                        row.getUserId(),
                        row.getEmail(),
                        row.getNickname(),
                        row.getStatus(),
                        row.getFairId(),
                        row.getFairName(),
                        row.getOperationStartDate(),
                        row.getOperationEndDate()
                ))
                .toList();
    }

    //관리자 계정 정지/정지 해제
    @Transactional
    public void updateAccountStatus(Long userId, String status) {
        int updated = authMapper.updateUserStatus(userId, status);
        if (updated == 0) {
            //대상 userId의 계정 자체가 없는 경우
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
    }

}
