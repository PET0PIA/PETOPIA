package com.ms.petopia.api.business.dto.request;

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

    private String name; // 업체명
    private String ceoName; // 대표자명
    private String bizRegNo; // 사업자등록번호
    private LocalDate startDate; // 개업일자
    private String address; // 사업장 주소
    private String phone; // 연락처
    private String website; // 웹사이트 URL, 선택 입력

}
