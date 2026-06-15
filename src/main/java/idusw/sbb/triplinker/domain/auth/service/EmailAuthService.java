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


//인증번호 생성, DB 저장, 실제 메일 발송

@Service
@RequiredArgsConstructor
public class EmailAuthService {
    // 구글 계정을 통해 실제로 메일을 발송해 주는 스프링 내장 도구
    private final JavaMailSender mailSender;

    // 발송한 인증번호를 DB에 저장하기 위한 Repository
    private final EmailAuthRepository emailAuthRepository;

    //프론트엔드에서 이메일을 넘겨주면, 6자리 난수를 생성하여 발송하고 DB에 기록
    @Transactional
    public void sendEmailAuthCode(String email, String type) {
        // 6자리 랜덤 숫자(인증번호) 생성
        String authCode = generateRandomCode();
        String subject;
        String text;

        if ("reset".equals(type)) {
            subject = "[TripLinker] 비밀번호 재설정 인증번호입니다.";
            text = "비밀번호 재설정을 위한 인증번호입니다.\n\n인증번호: " + authCode;
        } else {
            subject = "[TripLinker] 회원가입 이메일 인증번호입니다.";
            text = "TripLinker 회원가입을 환영합니다!\n\n인증번호: " + authCode;
        }

        // DB 임시 창고에 저장하기 위한 엔티티
        // 인증 만료 시간은 현재 시간에 3분 플러스
        EmailAuth emailAuth = EmailAuth.builder()
                .email(email)
                .authCode(authCode)
                .expiryDate(LocalDateTime.now().plusMinutes(3))
                .build();

        // DB에 방금 만든 인증 정보 저장
        emailAuthRepository.save(emailAuth);

        // 구글을 통해 실제 메일 발송 설정
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);

        // 메일 최종 발송
        mailSender.send(message);
    }


    //사용자가 화면에 입력한 인증번호가 맞는지, 3분이 지나지 않았는지 확인

    @Transactional(readOnly = true) // 데이터베이스를 읽기만 할 때 성능을 높여주는 옵션
    public boolean verifyEmailCode(String email, String userInputCode) {

        // 해당 이메일로 발송된 가장 최근의 인증 정보를 DB 창고에서 꺼내옵니다
        EmailAuth emailAuth = emailAuthRepository.findTopByEmailOrderByExpiryDateDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 인증 번호를 요청한 이력이 없습니다."));

        // 만료 시간(3분)이 지났는지 확인합니다. (현재 시간과 비교)
        if (emailAuth.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("인증 번호 유효시간(3분)이 초과되었습니다. 다시 요청해 주세요.");
        }

        // 사용자가 입력한 번호와 DB에 저장된 진짜 번호가 똑같은지 비교합니다.
        if (!emailAuth.getAuthCode().equals(userInputCode)) {
            throw new IllegalArgumentException("인증 번호가 일치하지 않습니다.");
        }

        // 에러 없이 통과했다면 인증 성공
        return true;
    }

    //100000 ~ 999999 사이의 6자리 랜덤 숫자를 생성하는 내부 헬퍼 메서드

    private String generateRandomCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}