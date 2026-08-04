package com.ms.petopia.api.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/*
 * 사업자 등록 요청 (POST /api/businesses)
 * name, ceoName, bizRegNo, startDate, address, phone은 필수
 * website는 선택 입력
 */
@Getter
@Setter
public class BusinessRegisterRequest {

    @NotBlank(message = "업체명은 필수입니다.")
    private String name; // 업체명

    @NotBlank(message = "대표자명은 필수입니다.")
    private String ceoName; // 대표자명

    @NotBlank(message = "사업자등록번호는 필수입니다.")
    @Pattern(regexp = "\\d{10}", message = "사업자등록번호는 숫자 10자리여야 합니다.")
    private String bizRegNo; // 사업자등록번호

    @NotNull(message = "개업일자는 필수입니다.")
    private LocalDate startDate; // 개업일자

    @NotBlank(message = "사업장 주소는 필수입니다.")
    private String address; // 사업장 주소

    @NotBlank(message = "연락처는 필수입니다.")
    private String phone; // 연락처

    private String website; // 웹사이트 URL, 선택 입력

}
