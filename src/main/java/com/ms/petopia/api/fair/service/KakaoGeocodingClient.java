package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.GeoPoint;
import com.ms.petopia.api.fair.dto.response.KakaoAddressSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * 카카오 로컬 API로 주소 -> 좌표(위도/경도)를 변환한다.
 *
 * <p>지오코딩 실패(키 미설정, API 에러, 주소 검색 결과 없음)는 전부 {@link Optional#empty()}로
 * 흡수한다 - fail-soft. 지도가 안 뜨는 건 괜찮아도, 부가 기능 하나 때문에 행사 등록/수정
 * 자체가 막히면 안 된다({@link com.ms.petopia.api.business.service.NtsBusinessVerificationClient}와
 * 반대되는 정책이니 혼동하지 않는다).
 */
@Slf4j
@Component
public class KakaoGeocodingClient {

    private static final String ADDRESS_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoGeocodingClient(@Value("${kakao.rest-api-key}") String restApiKey) {

        this.restApiKey = restApiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

    }

    public Optional<GeoPoint> geocode(String address) {

        if (restApiKey == null || restApiKey.isBlank() || address == null || address.isBlank()) {
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder.fromUriString(ADDRESS_SEARCH_URL)
                .queryParam("query", address)
                .build()
                .encode()
                .toUri();

        try {

            KakaoAddressSearchResponse response = restClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(KakaoAddressSearchResponse.class);

            List<KakaoAddressSearchResponse.Document> documents =
                    response == null ? null : response.getDocuments();

            if (documents == null || documents.isEmpty()) {
                log.warn("카카오 지오코딩 결과 없음: 주소를 찾지 못했습니다.");
                return Optional.empty();
            }

            KakaoAddressSearchResponse.Document first = documents.get(0);

            return Optional.of(new GeoPoint(
                    new BigDecimal(first.getY()), // 위도
                    new BigDecimal(first.getX())  // 경도
            ));

        } catch (Exception e) {
            log.error("카카오 지오코딩 API 호출 실패: {}", maskApiKey(e.getMessage()));
            return Optional.empty();
        }

    }

    // 로그에 실제 restApiKey 값이 남지 않도록 마스킹한다.
    private String maskApiKey(String message) {
        if (message == null || restApiKey == null || restApiKey.isBlank()) {
            return message;
        }
        return message.replace(restApiKey, "***MASKED***");
    }

}
