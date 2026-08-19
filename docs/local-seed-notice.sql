-- 로컬 확인용 공지 시드. created_by = 2 는 superadmin@petopia.local 계정이다.
-- 실행: docker exec -i petopia-mysql-local mysql -uroot -p<비밀번호> petopia_db < docs/local-seed-notice.sql
INSERT INTO notice (category, title, content, fair_id, is_published, is_pinned, created_by, created_at)
VALUES
  ('NOTICE', '2026 서울 펫페어 예매가 오픈되었습니다',
   '<p>예매가 시작되었습니다. 얼리버드는 8월 20일까지입니다.</p>', 1, 1, 1, 2, '2026-08-05 10:00:00'),
  ('EVENT', '얼리버드 티켓 20% 할인 이벤트',
   '<p>선착순 500명에게 20% 할인 쿠폰을 드립니다.</p>', 1, 1, 0, 2, '2026-08-02 09:00:00'),
  ('GUIDE', '반려동물 동반 입장 시 필수 준비물 안내',
   '<p>목줄과 배변봉투는 필수입니다.</p>', NULL, 1, 0, 2, '2026-07-28 14:00:00'),
  ('NOTICE', '(비공개 테스트) 아직 게시하지 않은 글',
   '<p>이 글은 공개 목록에 나오면 안 된다.</p>', NULL, 0, 0, 2, '2026-08-18 11:00:00');
