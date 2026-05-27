package idusw.sbb.triplinker.domain.auth.service;

import idusw.sbb.triplinker.domain.auth.entity.EmailAuth;
import idusw.sbb.triplinker.domain.auth.repository.EmailAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 이메일 인증 관련 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * 인증번호 생성, DB 저장, 실제 메일 발송의 역할을 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class EmailAuthService {

    // application.properties에 설정한 구글 계정을 통해 실제로 메일을 발송해 주는 스프링 내장 도구입니다.
    private final JavaMailSender mailSender;

    // 발송한 인증번호를 DB에 저장하기 위한 Repository입니다.
    private final EmailAuthRepository emailAuthRepository;

    /**
     * 프론트엔드에서 이메일을 넘겨주면, 6자리 난수를 생성하여 발송하고 DB에 기록합니다.
     * @param email 사용자가 입력한 이메일 주소
     */
    @Transactional
    public void sendEmailAuthCode(String email) {
        // 1. 6자리 랜덤 숫자(인증번호) 생성
        String authCode = generateRandomCode();

        // 2. DB 임시 창고에 저장하기 위한 엔티티 조립
        // 인증 만료 시간은 현재 시간(LocalDateTime.now())에 3분을 더해서 설정합니다.
        EmailAuth emailAuth = EmailAuth.builder()
                .email(email)
                .authCode(authCode)
                .expiryDate(LocalDateTime.now().plusMinutes(3))
                .build();

        // DB에 방금 만든 인증 정보 저장
        emailAuthRepository.save(emailAuth);

        // 3. 구글 우체부를 통해 실제 메일 발송 설정
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email); // 수신자 이메일
        message.setSubject("[TripLinker] 회원가입 이메일 인증번호입니다."); // 이메일 제목
        message.setText("안녕하세요! TripLinker 가입을 환영합니다.\n\n"
                + "인증번호는 [" + authCode + "] 입니다.\n"
                + "해당 인증번호를 3분 안에 화면에 입력해 주세요."); // 이메일 본문 내용

        // 메일 최종 발송 슝!
        mailSender.send(message);
    }

    /**
     * 사용자가 화면에 입력한 인증번호가 맞는지, 3분이 지나지 않았는지 깐깐하게 검사(채점)합니다.
     * @param email 사용자 이메일
     * @param userInputCode 사용자가 화면에 입력한 6자리 숫자
     */
    @Transactional(readOnly = true) // 데이터베이스를 읽기만 할 때 성능을 높여주는 옵션입니다.
    public boolean verifyEmailCode(String email, String userInputCode) {

        // 1. 해당 이메일로 발송된 가장 최근의 인증 정보(정답지)를 DB 창고에서 꺼내옵니다.
        EmailAuth emailAuth = emailAuthRepository.findTopByEmailOrderByExpiryDateDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 인증 번호를 요청한 이력이 없습니다."));

        // 2. 만료 시간(3분)이 지났는지 확인합니다. (현재 시간과 비교)
        if (emailAuth.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("인증 번호 유효시간(3분)이 초과되었습니다. 다시 요청해 주세요.");
        }

        // 3. 사용자가 입력한 번호와 DB에 저장된 진짜 번호가 똑같은지 비교합니다.
        if (!emailAuth.getAuthCode().equals(userInputCode)) {
            throw new IllegalArgumentException("인증 번호가 일치하지 않습니다.");
        }

        // 위의 3가지 관문을 모두 에러 없이 통과했다면 인증 성공(true)을 반환합니다!
        return true;
    }

    /**
     * 100000 ~ 999999 사이의 6자리 랜덤 숫자를 생성하는 내부 헬퍼 메서드입니다.
     */
    private String generateRandomCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}