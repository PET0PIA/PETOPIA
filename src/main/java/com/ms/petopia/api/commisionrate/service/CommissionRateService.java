package com.ms.petopia.api.commisionrate.service;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.commisionrate.dto.CommissionRateResponse;
import com.ms.petopia.api.commisionrate.dto.CommissionRateRow;
import com.ms.petopia.api.commisionrate.dto.CommissionRateScope;
import com.ms.petopia.api.commisionrate.mapper.CommissionRateMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 수수료율(전역 기본값 + 행사별 override) 조회·설정.
 *
 * <p>UPDATE 대신 매번 새 행을 INSERT하는 이력 보존형 — "현재 요율"은 scope(+fairId)별
 * 최신(updated_at 최대) 행 하나. DB에 GLOBAL 행이 하나도 없으면(운영 초기, 관리자가
 * 아직 설정 전) {@link #BOOTSTRAP_DEFAULT_RATE}를 최후 fallback으로 쓴다 — 예전
 * SettlementService에 하드코딩돼 있던 값과 동일.
 */
@Service
@RequiredArgsConstructor
public class CommissionRateService {

    private static final BigDecimal BOOTSTRAP_DEFAULT_RATE = new BigDecimal("0.0500");

    private final CommissionRateMapper commissionRateMapper;
    private final AuditLogService auditLogService;

    /** SettlementService.calculate()가 정산 생성 시 스냅샷으로 저장할 "지금 적용될" 요율. */
    public BigDecimal resolveEffectiveRate(Long fairId) {
        CommissionRateRow row = resolveEffectiveRow(fairId);
        return row != null ? row.getRate() : BOOTSTRAP_DEFAULT_RATE;
    }

    /** 관리자 조회 API용. fairId 없으면 GLOBAL만 본다. */
    public CommissionRateResponse getEffectiveRate(Long fairId) {
        CommissionRateRow row = resolveEffectiveRow(fairId);
        return row != null
                ? CommissionRateResponse.from(row)
                : CommissionRateResponse.bootstrapDefault(BOOTSTRAP_DEFAULT_RATE);
    }

    /** fairId가 있으면 FAIR override를 먼저 보고, 없으면(또는 override가 없으면) GLOBAL로 떨어진다. */
    private CommissionRateRow resolveEffectiveRow(Long fairId) {
        if (fairId != null) {
            CommissionRateRow fairOverride = commissionRateMapper.selectLatestByFair(fairId);
            if (fairOverride != null) {
                return fairOverride;
            }
        }
        return commissionRateMapper.selectLatestGlobal();
    }

    /**
     * 새 요율을 설정한다(SUPER_ADMIN). 기존 행을 안 건드리고 새 행을 추가한다 — 과거
     * 정산은 각자 계산 시점에 스냅샷해둔 commission_rate를 그대로 쓰므로 영향 없음.
     *
     * @throws CommonException {@link ErrorCode#COMMISSION_RATE_INVALID_SCOPE}
     *         scope=GLOBAL인데 fairId가 있거나, scope=FAIR인데 fairId가 없을 때
     *         (DB의 CK_COMMISSION_SCOPE 제약과 동일 규칙)
     */
    @Transactional
    public CommissionRateResponse setRate(CommissionRateScope scope, Long fairId, BigDecimal rate, Long updatedByUserId) {
        boolean fairIdPresent = fairId != null;
        boolean isFairScope = scope == CommissionRateScope.FAIR;
        if (isFairScope != fairIdPresent) {
            throw new CommonException(ErrorCode.COMMISSION_RATE_INVALID_SCOPE);
        }

        CommissionRateRow row = new CommissionRateRow();
        row.setScope(scope.name());
        row.setFairId(fairId);
        row.setRate(rate);
        row.setUpdatedByUserId(updatedByUserId);
        row.setUpdatedAt(LocalDateTime.now());

        commissionRateMapper.insert(row);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("scope", scope.name());
        if (fairId != null) after.put("fairId", fairId);
        after.put("rate", rate);

        auditLogService.record(
                updatedByUserId,
                ActorType.ADMIN,
                "SUPER_ADMIN",
                ActionType.COMMISSION_RATE_UPDATE,
                TargetType.COMMISSION_RATE,
                row.getCommissionRateId(),
                null,
                after
        );

        return CommissionRateResponse.from(row);
    }
}
