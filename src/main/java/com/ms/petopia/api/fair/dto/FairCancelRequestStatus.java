package com.ms.petopia.api.fair.dto;

/**
 * fair_cancel_requests.status.
 *
 * <pre>
 * PENDING -> APPROVED (승인, FairCancelRequestService.review - fairs.canceled_at도 함께 채운다)
 * PENDING -> REJECTED (반려, FairCancelRequestService.review)
 * </pre>
 */
public enum FairCancelRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
