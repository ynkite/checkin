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
            return "http://52.78.185.138:8081";
        }
    }

    // 읽기 전용 공유 링크 생성
    @Transactional
    public Map<String, String> generateShareLink(Long tripId) {
        String hexToken;
        try {
            long obscure = tripId ^ 0x5A3C9B7D2EL; // 비트 마스킹으로 숫자 완전 변형
            hexToken = Long.toHexString(obscure);
        } catch (Exception e) {
            hexToken = String.valueOf(tripId);
        }

        //난수 주소 생성
        String currentHost = getDynamicHost();
        String readOnlyLink = currentHost + "/plan/view?token=" + hexToken;
        return Map.of("shareLink", readOnlyLink);
    }


    // 초대 이메일 내용 구성 및 발송 로직
    private void sendInviteEmail(String email, String name, String planTitle, Long tripId) {
        String hexToken;
        try {
            // 주소창/공유모달과 완벽하게 동일한 16진수 비트 연산 암호화 처리
            long obscure = tripId ^ 0x5A3C9B7D2EL;
            hexToken = Long.toHexString(obscure);
        } catch (Exception e) {
            hexToken = String.valueOf(tripId);
        }

        // 편집 링크 주소 뒤에 id=숫자 대신 token=난수 형태로 암호화하여 발송
        String currentHost = getDynamicHost();
        String inviteLink = currentHost + "/plan?token=" + hexToken;

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