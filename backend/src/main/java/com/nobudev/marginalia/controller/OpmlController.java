package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.service.OpmlService;
import com.nobudev.marginalia.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/opml")
public class OpmlController {

    private final OpmlService opmlService;
    private final UserService userService;

    public OpmlController(OpmlService opmlService, UserService userService) {
        this.opmlService = opmlService;
        this.userService = userService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<FeedResponse> importOpml(@RequestParam("file") MultipartFile file) throws IOException {
        User user = userService.getCurrentUser();
        try (InputStream inputStream = file.getInputStream()) {
            return opmlService.importOpml(inputStream, user);
        }
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> exportOpml() {
        User user = userService.getCurrentUser();
        String opmlXml = opmlService.exportOpml(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"marginalia-subscriptions.opml\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(opmlXml);
    }
}
