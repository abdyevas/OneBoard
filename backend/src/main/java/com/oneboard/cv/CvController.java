package com.oneboard.cv;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cvs")
public class CvController {

    private final CvService service;

    public CvController(CvService service) {
        this.service = service;
    }

    public record CvRequest(@NotBlank @Size(max = 255) String title,
                            @NotBlank @Size(max = 100_000) String content) {}

    public record CvResponse(Long id, String title, String content, Instant createdAt, Instant updatedAt) {
        static CvResponse of(Cv cv) {
            return new CvResponse(cv.getId(), cv.getTitle(), cv.getContent(), cv.getCreatedAt(), cv.getUpdatedAt());
        }
    }

    @GetMapping
    public List<CvResponse> list() {
        return service.list().stream().map(CvResponse::of).toList();
    }

    @GetMapping("/{id}")
    public CvResponse get(@PathVariable Long id) {
        return CvResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CvResponse create(@Valid @RequestBody CvRequest req) {
        return CvResponse.of(service.create(req.title(), req.content()));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CvResponse upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "title", required = false) String title) throws IOException {
        return CvResponse.of(service.upload(file, title));
    }

    @PutMapping("/{id}")
    public CvResponse update(@PathVariable Long id, @Valid @RequestBody CvRequest req) {
        return CvResponse.of(service.update(id, req.title(), req.content()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
