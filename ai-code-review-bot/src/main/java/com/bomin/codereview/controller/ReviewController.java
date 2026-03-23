package com.bomin.codereview.controller;

import com.bomin.codereview.dto.ReviewDto;
import com.bomin.codereview.service.CodeReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Code Review API", description = "AI 코드 리뷰 조회 API")
public class ReviewController {

    private final CodeReviewService codeReviewService;

    @GetMapping("/reviews")
    @Operation(summary = "리뷰 목록 조회")
    public ResponseEntity<List<ReviewDto.PrResponse>> getReviews(
            @RequestParam(required = false) String repository) {
        return ResponseEntity.ok(codeReviewService.getReviews(repository));
    }

    @GetMapping("/reviews/{repo}/{prNumber}")
    @Operation(summary = "PR별 리뷰 상세 조회")
    public ResponseEntity<ReviewDto.PrResponse> getReview(
            @PathVariable String repo,
            @PathVariable int prNumber) {
        return ResponseEntity.ok(codeReviewService.getReviewByPrNumber(repo, prNumber));
    }

    @GetMapping("/stats")
    @Operation(summary = "전체 리뷰 통계")
    public ResponseEntity<ReviewDto.StatsResponse> getStats() {
        return ResponseEntity.ok(codeReviewService.getStats());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "OK", "service", "ai-code-review-bot"));
    }
}
