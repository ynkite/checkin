package idusw.sbb.triplinker.global.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
public class LocalFileService {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.url}")
    private String uploadUrl;

    public String save(MultipartFile file) throws IOException {
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String savedFilename = UUID.randomUUID() + extension;
        file.transferTo(new File(uploadPath + savedFilename));

        return uploadUrl + savedFilename;
    }

    public void delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(uploadUrl)) {
            return;
        }
        String filename = fileUrl.substring(uploadUrl.length());
        File file = new File(uploadPath + filename);
        if (file.exists()) {
            file.delete();
        }
    }
}