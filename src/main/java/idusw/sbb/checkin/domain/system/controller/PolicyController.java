package idusw.sbb.checkin.domain.system.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인정보 처리방침.
 *
 * <p>왜 서버에서 한 장으로 내는가 — 구글 OAuth 검수와 공모전 제출 양쪽이
 * <b>실제로 열리는 주소</b>를 요구한다. 푸터 링크는 아직 비활성이고,
 * 화면 쪽 구조를 건드리지 않으려고 이 페이지만 따로 세웠다.
 * 화면에 붙일 때는 이 주소를 링크로 걸면 된다.
 *
 * <p>내용은 코드에 있는 것만 적는다. 수집하지 않는 것을 수집한다고 쓰거나,
 * 서버로 보내는 것을 안 보낸다고 쓰면 그게 더 큰 문제가 된다.
 */
@RestController
public class PolicyController {

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> privacy() {
        return ResponseEntity.ok(page("개인정보 처리방침", PRIVACY));
    }

    private String page(String title, String body) {
        return """
                <!doctype html>
                <html lang="ko" data-sky="day">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s · 체크인</title>
                  <link rel="stylesheet" href="/css/tokens.css">
                  <style>
                    body{margin:0;background:var(--page-2);color:var(--ink);
                         font-family:var(--sans,system-ui);}
                    main{max-width:720px;margin:0 auto;padding:56px 22px 96px;
                         word-break:keep-all;overflow-wrap:break-word;line-height:1.7}
                    h1{font-size:28px;letter-spacing:-.02em;margin:0 0 6px}
                    h2{font-size:18px;margin:36px 0 10px}
                    p,li{font-size:15px;color:var(--ink-2)}
                    .when{font-family:var(--fig,monospace);font-size:12px;color:var(--ink-3)}
                    table{width:100%%;border-collapse:collapse;margin-top:10px}
                    th,td{text-align:left;font-size:14px;padding:10px 8px;
                          border-bottom:1px solid var(--ui-line-2);vertical-align:top}
                    th{color:var(--ink-3);font-weight:600;width:34%%}
                    a{color:var(--ink)}
                  </style>
                </head>
                <body><main>%s</main></body>
                </html>
                """.formatted(title, body);
    }

    private static final String PRIVACY = """
            <h1>개인정보 처리방침</h1>
            <p class="when">시행일 2026-09-18</p>
            <p>체크인(이하 「서비스」)은 여행 중 동선과 예산을 다시 짜는 웹 서비스입니다.
               이 방침은 서비스가 무엇을 받고, 무엇에 쓰고, 언제 지우는지를 적은 것입니다.</p>

            <h2>1. 수집하는 것</h2>
            <table>
              <tr><th>계정</th><td>아이디, 비밀번호(복원 불가능한 형태로 변환해 저장), 이름, 이메일.
                  소셜 로그인은 해당 제공사에서 이메일과 닉네임, 프로필 사진을 받습니다.
                  비밀번호는 저장하지 않습니다</td></tr>
              <tr><th>선택 입력</th><td>지역, 생년월일, 성별, MBTI. 넣지 않아도 서비스는 동작합니다</td></tr>
              <tr><th>여행 기록</th><td>일정, 방문할 장소, 예산 항목과 금액, 스크랩, 게시글과 댓글</td></tr>
              <tr><th>위치</th><td>아래 3항에 따로 적습니다</td></tr>
              <tr><th>알림</th><td>브라우저 푸시 주소와 암호화 키. 캘린더 구독 주소에 쓰는 무작위 토큰</td></tr>
              <tr><th>대화</th><td>챗봇에 보낸 문장과 답변</td></tr>
            </table>

            <h2>2. 쓰는 곳</h2>
            <ul>
              <li>계정 식별과 로그인</li>
              <li>동선·예산 계산, 혼잡·날씨·이동시간에 따른 재계획 제안</li>
              <li>일정 변경 알림(웹 푸시), 캘린더 구독 파일 생성</li>
              <li>문의 응대와 부정 이용 차단</li>
            </ul>
            <p>광고에 쓰지 않습니다. 이용자 정보를 팔지 않습니다.</p>

            <h2>3. 위치 정보</h2>
            <p>위치는 브라우저가 물어본 뒤에만 받습니다. 거부해도 서비스는 시각을 기준으로 동작합니다.</p>
            <ul>
              <li><b>브라우저 안에서만 쓰는 것</b> — 지도 표시, 현재 위치 표시, 거리 계산</li>
              <li><b>서버로 보내는 것</b> — 길 안내와 「지금 어디쯤인가」 조회는 경로를 계산해야 해서
                  좌표가 서버를 거칩니다. 이 좌표는 <b>응답을 만드는 데만 쓰고 저장하지 않습니다</b></li>
              <li><b>기록으로 남기는 것</b> — 혼잡·날씨 감지 기록에는 정밀 좌표 대신
                  약 1km 격자로 뭉갠 값만 씁니다. 같은 격자 안의 서로 다른 위치는 같은 값이 되어
                  개인을 식별할 수 없습니다</li>
              <li>길 안내를 켜는 동안에만 위치를 계속 받습니다. 안내를 끄면 멈춥니다</li>
            </ul>

            <h2>4. 밖으로 나가는 것</h2>
            <p>개인정보를 제3자에게 제공하지 않습니다. 다만 아래 기능은 외부 서비스를 호출하며,
               그때 <b>조회에 필요한 값만</b> 전달합니다.</p>
            <table>
              <tr><th>한국관광공사</th><td>지역 코드, 장소 식별자 — 관광지 정보와 혼잡도 조회</td></tr>
              <tr><th>기상청</th><td>지역 이름, 날짜 — 날씨 조회</td></tr>
              <tr><th>TMAP(SK)</th><td>출발·도착 좌표 — 경로와 소요시간 계산</td></tr>
              <tr><th>카카오</th><td>장소 이름, 좌표 — 지도 표시와 장소 검색. 소셜 로그인 시 계정 정보</td></tr>
              <tr><th>한국석유공사</th><td>좌표 — 주변 주유소 가격 조회</td></tr>
              <tr><th>AI 제공사</th><td>챗봇에 보낸 문장과 동선 생성에 쓰는 여행 조건.
                  <b>대화 내용이 AI 제공사 서버로 전송됩니다</b></td></tr>
              <tr><th>메일 발송</th><td>이메일 주소 — 가입 인증과 비밀번호 재설정</td></tr>
            </table>

            <h2>5. 보관 기간</h2>
            <ul>
              <li>계정과 여행 기록 — 탈퇴할 때까지. 탈퇴하면 지웁니다</li>
              <li>길 안내·실시간 조회에 쓰인 좌표 — 저장하지 않습니다</li>
              <li>푸시 구독 정보 — 알림을 끄거나 탈퇴하면 지웁니다</li>
              <li>법령이 보관을 요구하는 기록은 그 기간 동안 따로 보관합니다</li>
            </ul>

            <h2>6. 이용자가 할 수 있는 것</h2>
            <ul>
              <li>내 정보 조회와 수정 — 마이페이지</li>
              <li>탈퇴 — 마이페이지. 탈퇴하면 계정과 여행 기록을 지웁니다</li>
              <li>알림 끄기 — 브라우저 설정 또는 마이페이지</li>
              <li>캘린더 구독 주소 재발급·폐기 — 주소가 새어 나갔을 때 끊을 수 있습니다</li>
              <li>위치 권한 철회 — 브라우저 설정에서 언제든지</li>
            </ul>

            <h2>7. 안전하게 지키려고 하는 것</h2>
            <ul>
              <li>비밀번호는 복원할 수 없는 형태로 바꿔 저장합니다</li>
              <li>모든 통신은 HTTPS 로 암호화합니다</li>
              <li>공유 링크와 캘린더 구독 주소는 추측할 수 없는 무작위 토큰을 씁니다.
                  읽기 링크에서 편집 링크를 알아낼 수 없습니다</li>
              <li>정밀 좌표는 기록에 남기지 않습니다(3항)</li>
            </ul>

            <h2>8. 어린이</h2>
            <p>만 14세 미만은 가입 대상이 아닙니다.</p>

            <h2>9. 바뀌면</h2>
            <p>이 방침이 바뀌면 이 페이지에 바뀐 내용과 시행일을 적습니다.</p>

            <h2>10. 문의</h2>
            <p>서비스 안의 문의하기 또는 운영자 이메일로 연락해 주세요.
               개인정보 열람·정정·삭제 요청도 같은 곳으로 받습니다.</p>

            <p class="when">이 서비스는 2026 관광데이터 활용 공모전 출품작입니다.
               출처: ⓒ한국관광공사 · 날씨 정보 ⓒ기상청</p>
            """;
}
