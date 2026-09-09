package idusw.sbb.triplinker.domain.planshare.service;

import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.triplinker.domain.planshare.dto.ShareInviteRequestDto;
import idusw.sbb.triplinker.domain.planshare.dto.TripMemberResponseDto;
import idusw.sbb.triplinker.domain.planshare.entity.PlanRole;
import idusw.sbb.triplinker.domain.planshare.entity.TripMember;
import idusw.sbb.triplinker.domain.planshare.repository.TripMemberRepository;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.global.util.ShareTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripShareService {

    private final TripMemberRepository tripMemberRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final UserRepository userRepository;

    // 구글 메일 발송 도구 주입
    private final JavaMailSender mailSender;

    // 멤버 목록 조회
    public List<TripMemberResponseDto> getMembers(Long tripId) {
        // 플랜 정보 가져오기 (소유자 확인용)
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        // 공유 테이블에 저장된 초대 멤버들 불러오기
        List<TripMemberResponseDto> members = tripMemberRepository.findByTravelPlanId(tripId).stream()
                .map(m -> new TripMemberResponseDto(
                        m.getUser().getName(),
                        m.getUser().getEmail(),
                        m.getRole().name()
                )).collect(Collectors.toList());

        // 소유자를 강제로 리스트 맨 앞에 1번으로 추가
        members.add(0, new TripMemberResponseDto(
                plan.getUser().getName(),
                plan.getUser().getEmail(),
                "OWNER"
        ));

        return members;
    }

    // 멤버 초대 및 실제 이메일 발송
    @Transactional
    public void inviteMember(Long tripId, ShareInviteRequestDto dto) {
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        // 이메일로 가입된 유저인지 확인
        User invitee = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입된 회원이 아닙니다. 이메일을 확인해주세요."));

        // 이미 초대된 유저인지 중복 체크
        if (tripMemberRepository.existsByTravelPlanIdAndUserEmail(tripId, dto.getEmail())) {
            throw new IllegalArgumentException("이미 초대된 그룹 멤버입니다.");
        }

        // 권한(Role)값이 안 넘어왔을 경우 방어: 기본값 EDITOR 강제 할당
        String roleStr = (dto.getRole() != null && !dto.getRole().isBlank()) ? dto.getRole() : "EDITOR";

        // DB에 멤버 추가
        TripMember newMember = TripMember.builder()
                .travelPlan(plan)
                .user(invitee)
                .role(PlanRole.valueOf(roleStr))
                .build();

        tripMemberRepository.save(newMember);

        // 초대 대상자에게 진짜 이메일 발송 실행
        sendInviteEmail(invitee.getEmail(), invitee.getName(), plan.getTitle(), tripId);
    }



    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.servlet.http.HttpServletRequest request;

    private String getDynamicHost() {
        try {
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();

            // 기본 HTTP(80) 및 HTTPS(443) 포트가 아닐 때만 주소 뒤에 포트를 동적 결합
            if ((scheme.equals("http") && serverPort == 80) || (scheme.equals("https") && serverPort == 443)) {
                return scheme + "://" + serverName;
            }
            return scheme + "://" + serverName + ":" + serverPort;
        } catch (Exception e) {
            // 예외 발생 시 AWS 서버 주소를 반환
            return "http://43.201.154.80:8081";
        }
    }

    // 공유 링크 생성 — role 에 맞는 토큰을 없으면 발급하고 링크를 돌려준다.
    // READER 는 /trip/{토큰}, EDITOR 는 /trip/{토큰}/edit. 읽기 토큰으로 편집 링크를 유도할 수 없다.
    @Transactional
    public Map<String, String> generateShareLink(Long tripId, PlanRole role) {
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        String token = ensureToken(plan, role);
        String link = shareUrl(token, role);
        return Map.of("shareLink", link, "role", role.name());
    }

    // 기존 호출 호환 — role 없이 부르면 읽기 링크
    @Transactional
    public Map<String, String> generateShareLink(Long tripId) {
        return generateShareLink(tripId, PlanRole.READER);
    }

    // 토큰 재발급 — 유출됐을 때 기존 링크를 끊고 새 토큰을 준다.
    @Transactional
    public Map<String, String> regenerateShareLink(Long tripId, PlanRole role) {
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));
        setToken(plan, role, null);
        String token = ensureToken(plan, role);
        return Map.of("shareLink", shareUrl(token, role), "role", role.name());
    }

    // 토큰 폐기 — 링크를 완전히 끊는다.
    @Transactional
    public void revokeShareLink(Long tripId, PlanRole role) {
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));
        setToken(plan, role, null);
    }

    // 읽기 토큰 → 플랜 (인증 없이 열리는 링크 진입점)
    public TravelPlan resolveByReadToken(String token) {
        return travelPlanRepository.findByShareReadToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 폐기된 링크입니다."));
    }

    // 편집 토큰 → 플랜
    public TravelPlan resolveByEditToken(String token) {
        return travelPlanRepository.findByShareEditToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 폐기된 링크입니다."));
    }

    // role 에 해당하는 토큰을 반환하되, 없으면 새로 발급해 저장한다.
    private String ensureToken(TravelPlan plan, PlanRole role) {
        String existing = (role == PlanRole.EDITOR) ? plan.getShareEditToken() : plan.getShareReadToken();
        if (existing != null) return existing;
        String token = uniqueToken(role);
        setToken(plan, role, token);
        return token;
    }

    private void setToken(TravelPlan plan, PlanRole role, String token) {
        if (role == PlanRole.EDITOR) plan.setShareEditToken(token);
        else plan.setShareReadToken(token);
    }

    // 충돌 없는 토큰 발급 (192비트라 사실상 유일하지만 방어)
    private String uniqueToken(PlanRole role) {
        for (int i = 0; i < 5; i++) {
            String t = ShareTokenGenerator.generate();
            boolean taken = (role == PlanRole.EDITOR)
                    ? travelPlanRepository.findByShareEditToken(t).isPresent()
                    : travelPlanRepository.findByShareReadToken(t).isPresent();
            if (!taken) return t;
        }
        throw new IllegalStateException("토큰 생성 실패");
    }

    private String shareUrl(String token, PlanRole role) {
        String host = getDynamicHost();
        return (role == PlanRole.EDITOR) ? host + "/trip/" + token + "/edit" : host + "/trip/" + token;
    }


    // 초대 이메일 내용 구성 및 발송 로직
    private void sendInviteEmail(String email, String name, String planTitle, Long tripId) {
        // 편집자 초대는 편집 토큰 링크를 보낸다. 없으면 발급.
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));
        String token = ensureToken(plan, PlanRole.EDITOR);
        String inviteLink = shareUrl(token, PlanRole.EDITOR);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[TripLinker] '" + planTitle + "' 여행 플랜에 초대되었습니다 ✈️");
        message.setText(name + "님, 환영합니다!\n\n" +
                "일행분이 '" + planTitle + "' 여행 플랜에 편집자로 초대하셨습니다.\n" +
                "아래 링크를 클릭하여 일정을 확인하고 함께 계획을 수정해 보세요!\n\n" +
                "👉 플랜 바로가기: " + inviteLink + "\n\n" +
                "TripLinker와 함께 즐거운 여행 되세요!");

        mailSender.send(message);
    }
}