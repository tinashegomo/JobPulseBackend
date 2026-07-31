package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.dto.resume.ResumeAnalysisRequest;
import com.TinasheGomo.JobPulse.dto.resume.ResumeAnalysisResponse;
import com.TinasheGomo.JobPulse.service.ResumeAnalyzer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResumeAnalysisController {

    private final ResumeAnalyzer resumeAnalyzer;

    @PostMapping("/analyze-resume")
    public ResponseEntity<ResumeAnalysisResponse> analyzeResume(
            @Valid @RequestBody ResumeAnalysisRequest request) {
        ResumeAnalysisResponse profile = resumeAnalyzer.analyze(request.getResumeText());
        return ResponseEntity.ok(profile);
    }
}
