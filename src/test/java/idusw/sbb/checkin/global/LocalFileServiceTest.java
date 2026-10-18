package idusw.sbb.checkin.global;

import idusw.sbb.checkin.global.service.LocalFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 게시글 사진 올리기·지우기. 사진이 아닌 파일과 업로드 폴더 밖 경로를 막는다 */
class LocalFileServiceTest {

    @TempDir Path root;
    Path uploads;
    LocalFileService files = new LocalFileService();

    @BeforeEach
    void setUp() throws Exception {
        uploads = Files.createDirectories(root.resolve("uploads"));
        ReflectionTestUtils.setField(files, "uploadPath", uploads + "/");
        ReflectionTestUtils.setField(files, "uploadUrl", "/uploads/posts/");
    }

    @Test
    void html_파일은_사진으로_올릴_수_없다() {
        var html = new MockMultipartFile("files", "x.html", "text/html", "<script>alert(1)</script>".getBytes());
        assertThatThrownBy(() -> files.save(html)).isInstanceOf(IllegalArgumentException.class);
        var svg = new MockMultipartFile("files", "x.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes());
        assertThatThrownBy(() -> files.save(svg)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 사진은_올라가고_확장자는_소문자로_남는다() throws Exception {
        String url = files.save(new MockMultipartFile("files", "a.PNG", "image/png", new byte[]{1, 2}));
        assertThat(url).startsWith("/uploads/posts/").endsWith(".png");
        assertThat(files.isOwnUpload(url)).isTrue();
    }

    @Test
    void 업로드_폴더_밖의_파일은_지우지_않는다() throws Exception {
        Path outside = Files.writeString(root.resolve("secret.txt"), "x");
        files.delete("/uploads/posts/../secret.txt");
        assertThat(outside).exists();
        assertThat(files.isOwnUpload("/uploads/posts/../secret.txt")).isFalse();
        assertThat(files.isOwnUpload("https://evil.example/a.png")).isFalse();
    }
}
