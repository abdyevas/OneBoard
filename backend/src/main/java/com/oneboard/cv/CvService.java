package com.oneboard.cv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.oneboard.common.NotFoundException;

@Service
public class CvService {

    private final CvRepository repository;

    public CvService(CvRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Cv> list() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Cv get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("CV", id));
    }

    @Transactional
    public Cv create(String title, String content) {
        return repository.save(new Cv(title, content));
    }

    @Transactional
    public Cv update(Long id, String title, String content) {
        Cv cv = get(id);
        cv.update(title, content);
        return cv;
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new NotFoundException("CV", id);
        repository.deleteById(id);
    }

    /** Create a CV from an uploaded .txt / .md / .pdf file. */
    @Transactional
    public Cv upload(MultipartFile file, String title) throws IOException {
        String name = file.getOriginalFilename() == null ? "cv" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        String text;
        if (lower.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                text = new PDFTextStripper().getText(doc);
            }
        } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + name + " (use .pdf, .txt or .md)");
        }
        text = text.replace("\r\n", "\n").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("No text could be extracted from " + name);
        }
        String finalTitle = title == null || title.isBlank() ? name.replaceFirst("\\.[^.]+$", "") : title;
        return create(finalTitle, text);
    }
}
