# 개발 환경 설정

클론하고 **3분 안에** 앱이 뜨는 것을 목표로 한다.

---

## 1. 설정 파일 복사

설정 파일은 저장소에 올리지 않는다. 템플릿을 복사해서 쓴다.

**Windows (PowerShell)**

```powershell
cd src\main\resources
copy application.properties.example application.properties
copy application-local.properties.example application-local.properties
cd ..\..\..
```

**macOS / Linux**

```bash
cd src/main/resources
cp application.properties.example application.properties
cp application-local.properties.example application-local.properties
cd ../../..
```

복사만 하면 **키가 없어도 앱이 뜬다.** 비밀 값은 모두 `${환경변수:}` 형태라 비어 있어도 부팅을 막지 않는다.

---

## 2. DB 준비

MariaDB 가 `localhost:3306` 에 떠 있어야 한다.
계정이 `root` / 빈 비밀번호면 추가 설정이 필요 없다.

**DB 이름은 각자 다르게 쓴다.** 같은 스키마를 공유하면 `ddl-auto=update` 가 서로의 테이블을 건드린다.

```powershell
$env:DB_NAME="checkin_ysy"     # 본인 이니셜
```

또는 `application-local.properties` 의 `spring.datasource.url` 에서 DB 이름을 직접 바꾼다.
`createDatabaseIfNotExist=true` 가 붙어 있어 DB 를 미리 만들지 않아도 된다.

계정이 root 가 아니면:

```powershell
$env:DB_USER="myuser"
$env:DB_PASSWORD="mypass"
```

---

## 3. 실행

```powershell
.\gradlew.bat bootRun --console=plain
```

`http://localhost:8080` 으로 열린다.

---

## 4. 키가 필요한 기능

키 없이도 앱은 뜨지만 아래 기능은 동작하지 않는다. **필요한 것만** 받아서 넣으면 된다.

| 기능 | 환경변수 | 받는 곳 |
|---|---|---|
| 지도 표시 | `KAKAO_JS_KEY` | 팀장 (카카오 JavaScript 키) |
| 장소 검색·경로 | `KAKAO_REST_KEY` | 팀장 (카카오 REST API 키) |
| 카카오 로그인 | `KAKAO_CLIENT_ID` · `KAKAO_CLIENT_SECRET` | 팀장 |
| 구글 로그인 | `GOOGLE_CLIENT_ID` · `GOOGLE_CLIENT_SECRET` | 팀장 |
| 로그인 세션 | `JWT_SECRET` | 아무 랜덤 문자열 32자 이상. 각자 달라도 된다 |
| 메일 인증 | `MAIL_USERNAME` · `MAIL_PASSWORD` | 팀장 |
| 날씨 | `WEATHER_API_KEY` | 팀장 (기상청) |
| 챗봇 | `OPENAI_API_KEY` · `ANTHROPIC_API_KEY` · `GOOGLE_AI_KEY` | 팀장 |
| **관광공사 OpenAPI** | `TOUR_API_KEY` | 이수환 (**디코딩키**를 쓴다) |
| **TMAP** | `TMAP_APP_KEY` | 이수환 |

### 환경변수 설정하는 법

**그때그때 (해당 터미널에서만)**

```powershell
$env:KAKAO_JS_KEY="받은값"
.\gradlew.bat bootRun --console=plain
```

**계속 쓰려면 (PowerShell 프로필에 넣는다)**

```powershell
notepad $PROFILE
```

파일에 아래처럼 적고 저장한 뒤 터미널을 다시 연다.

```powershell
$env:KAKAO_JS_KEY="받은값"
$env:KAKAO_REST_KEY="받은값"
$env:TOUR_API_KEY="받은값"
```

**또는** `application-local.properties` 에 직접 적어도 된다.
그 파일은 `.gitignore` 대상이라 저장소에 올라가지 않는다.

```properties
kakao.maps.api.key=받은값
```

---

## 5. 절대 하지 말 것

| 금지 | 이유 |
|---|---|
| `application.properties` 나 `application-local.properties` 를 커밋 | 키가 GitHub 에 올라간다. `.gitignore` 로 막아 뒀지만 `-f` 로 강제 add 하지 말 것 |
| `.example` 파일에 실제 키를 적기 | 이건 추적되는 파일이다. `${환경변수:}` 형태를 유지한다 |
| 팀원과 같은 DB 이름 쓰기 | `ddl-auto=update` 가 서로의 테이블을 바꾼다 |
| `build/` 폴더를 압축해 공유 | 빌드 산출물에 키가 복사돼 있다 |

키를 주고받을 때는 **팀 채팅 DM** 을 쓴다. 이슈·PR·커밋 메시지에 붙이지 않는다.

---

## 6. 커밋 전 확인

```bash
git status --short
```

`application.properties` · `application-local.properties` 가 목록에 보이면 **커밋하지 않는다.**
`.gitignore` 로 막아 뒀으니 정상이면 애초에 안 보인다.

저장소에 올리기 전 각자 한 번 실행해 둘 로컬 설정이 있다.
**팀 문서 `02_작업분담.md` 의 0순위 항목**을 그대로 따른다. 이건 선택이 아니다.

---

## 막히면

| 증상 | 확인 |
|---|---|
| 부팅 시 DB 연결 실패 | MariaDB 실행 여부 → 계정/비밀번호 → DB 이름 |
| 포트 사용 중 | 다른 사람 서버가 8080 을 쓰는 중. `$env:SERVER_PORT="8090"` |
| 카카오 로그인 리다이렉트 오류 | 포트를 바꿨으면 카카오 콘솔의 리다이렉트 URI 도 바꿔야 한다 |
| 지도가 안 뜸 | `KAKAO_JS_KEY` 미설정. 콘솔에 401 이 뜬다 |
| 관광공사 API `SERVICE_KEY_IS_NOT_REGISTERED_ERROR` | 인코딩키를 넣었다. **디코딩키**로 바꾼다 |
