package com.ms.petopia.api.booth.mapper;

import com.ms.petopia.api.booth.domain.Booth;
import com.ms.petopia.api.booth.domain.BoothItem;
import com.ms.petopia.api.booth.dto.response.BoothFavoriteResponse;
import com.ms.petopia.api.booth.dto.response.ConfirmedBoothResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BoothMapper {

    // 부스 ID로 조회
    Booth selectById(@Param("boothId") Long boothId);

    // 부스에 속한 상품·이벤트 목록 조회
    List<BoothItem> selectItemsByBoothId(@Param("boothId") Long boothId);

    // 부스 생성 (신청 확정 시 자동 생성)
    void insertBooth(Booth booth);

    // 신청 ID 기준으로 부스 삭제 (취소 승인 시, 이전 상태가 CONFIRMED였을 때)
    void deleteBoothByApplicationId(@Param("applicationId") Long applicationId);

    // 부스 프로필 부분 수정 (null이 아닌 필드만 갱신)
    void updateBooth(Booth booth);

    // 판매상품·이벤트 등록
    void insertBoothItem(BoothItem item);

    // 상품·이벤트 단건 조회 (소유권 확인용 boothId 조회 + 존재 확인)
    BoothItem selectItemById(@Param("boothItemId") Long boothItemId);

    // 상품·이벤트 부분 수정 (null이 아닌 필드만 갱신)
    void updateBoothItem(BoothItem item);

    // 상품·이벤트 삭제
    void deleteBoothItem(@Param("boothItemId") Long boothItemId);

    // 행사 존재 확인 (안내판 조회 전 검증용)
    boolean existsFair(@Param("fairId") Long fairId);

    // 확정 부스 안내판 - 슬롯 하나당 한 행(같은 부스가 슬롯 여러 개 쓰면 여러 번 나옴)
    List<ConfirmedBoothResponse> selectConfirmedBooths(@Param("fairId") Long fairId);

    // 신청 ID 기준으로 그 부스에 딸린 판매상품·이벤트 전부 삭제 (부스 삭제 전 자식 레코드 정리용)
    void deleteBoothItemsByApplicationId(@Param("applicationId") Long applicationId);

    // 즐겨찾기 여부 확인 (부스 상세 조회 시 favorited 계산용)
    boolean existsFavorite(@Param("userId") Long userId,
                           @Param("boothId") Long boothId);

    // 내 즐겨찾기 목록 (부스명·이미지·소속 행사명까지 조인해서 한 번에 조회)
    List<BoothFavoriteResponse> selectFavoritesByUserId(@Param("userId") Long userId);

    // 신청 ID 기준으로 그 부스를 즐겨찾기한 행 전부 삭제 (부스 삭제 전 정리용)
    void deleteFavoritesByApplicationId(@Param("applicationId") Long applicationId);

    // 즐겨찾기 추가 (INSERT IGNORE로 멱등 처리 — 중복 클릭 방어)
    void insertFavorite(@Param("userId") Long userId,
                        @Param("boothId") Long boothId);

    // 즐겨찾기 삭제 (없는 걸 지워도 0행, 에러 아님)
    void deleteFavorite(@Param("userId") Long userId,
                        @Param("boothId") Long boothId);

}
