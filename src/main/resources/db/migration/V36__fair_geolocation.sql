ALTER TABLE `fairs`
    ADD COLUMN `latitude`  DECIMAL(10,7) NULL COMMENT '행사 장소 위도 (카카오 지오코딩 결과, 실패 시 NULL)' AFTER `address`,
    ADD COLUMN `longitude` DECIMAL(10,7) NULL COMMENT '행사 장소 경도 (카카오 지오코딩 결과, 실패 시 NULL)' AFTER `latitude`;
