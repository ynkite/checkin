package idusw.sbb.checkin.global.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LocalFileService {

    /* 사진만 받는다. .html·.svg 를 올리면 같은 주소에서 스크립트가 돈다.
       저장 이름은 서버가 정한 UUID 라 경로 문자가 들어갈 수 없다 */
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Pattern SAVED_NAME = Pattern.compile("[0-9a-f-]{36}\\.(jpg|jpeg|png|gif|webp)");

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.url}")
    private String uploadUrl;

    public String save(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        String ext = (name != null && name.contains("."))
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!IMAGE_EXT.contains(ext)) throw new IllegalArgumentException("사진 파일(jpg·png·gif·webp)만 올릴 수 있습니다.");

        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String savedFilename = UUID.randomUUID() + "." + ext;
        file.transferTo(new File(uploadPath + savedFilename));
        return uploadUrl + savedFilename;
    }

    /** 이 서버가 올려 준 사진 주소인가. 게시글에 붙일 수 있는 것도, 지울 수 있는 것도 이것뿐이다 */
    public boolean isOwnUpload(String fileUrl) {
        return fileUrl != null && fileUrl.startsWith(uploadUrl)
                && SAVED_NAME.matcher(fileUrl.substring(uploadUrl.length())).matches();
    }

    public void delete(String fileUrl) {
        if (!isOwnUpload(fileUrl)) return;
        File file = new File(uploadPath + fileUrl.substring(uploadUrl.length()));
        if (file.exists()) {
            file.delete();
        }
    }
}
