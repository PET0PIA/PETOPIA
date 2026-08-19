package com.ms.petopia.api.notice.dto.request;

import com.ms.petopia.api.notice.domain.NoticeCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 공지 등록/수정 요청. 관리자 화면이 폼 전체를 한 번에 보내므로 두 경우의 모양이 같아
 * 등록·수정이 이 클래스 하나를 공유한다(배너·팝업은 부분 수정이라 Create/Update가 나뉘어 있다).
 */
@Getter
@Setter
public class NoticeSaveRequest {

    @NotNull
    private NoticeCategory category;

    @NotBlank
    @Size(max = 200)
    private String title;

    /** 에디터가 만든 HTML. */
    @NotBlank
    private String content;

    /** 연결할 행사(선택). 비우면 전체 대상 공지. */
    private Long fairId;

    private boolean pinned;

    /**
     * 게시 여부. 생략(null)하면 등록 시에는 바로 게시, 수정 시에는 <b>현재 상태를 그대로 둔다</b>.
     * 목록의 빠른 토글은 별도 엔드포인트({@code PATCH .../publish})가 담당한다.
     *
     * <p>기본값을 true인 boolean으로 두면, 수정 요청이 이 값을 빠뜨렸을 때 숨겨둔 글이
     * 의도치 않게 공개된다. 그래서 "안 보냈다"와 "false로 보냈다"를 구분할 수 있는 Boolean이다.
     */
    private Boolean published;

    @Valid
    private List<NoticeAttachmentRequest> attachments;

    @AssertTrue(message = "모집공고(RECRUIT)는 공지로 등록할 수 없습니다. 행사 모집공고 화면에서 작성해 주세요.")
    public boolean isStoredCategory() {
        return category == null || category.isStored();
    }
}
