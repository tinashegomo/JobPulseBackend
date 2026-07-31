package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.dto.keyword.KeywordRequest;
import com.TinasheGomo.JobPulse.dto.keyword.KeywordResponse;
import com.TinasheGomo.JobPulse.security.AuthUser;
import com.TinasheGomo.JobPulse.service.KeywordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final KeywordService keywordService;

    @GetMapping
    public ResponseEntity<List<KeywordResponse>> getKeywords(
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(keywordService.getKeywordsByUser(authUser.getUser()));
    }

    @PostMapping
    public ResponseEntity<KeywordResponse> createKeyword(
            @Valid @RequestBody KeywordRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(keywordService.createKeyword(authUser.getUser(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKeyword(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        keywordService.deleteKeyword(authUser.getUser(), id);
        return ResponseEntity.noContent().build();
    }
}
