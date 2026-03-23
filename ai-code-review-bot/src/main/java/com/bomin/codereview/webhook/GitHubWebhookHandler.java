package com.bomin.codereview.webhook;

import com.bomin.codereview.service.CodeReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookHandler {

    private final CodeReviewService codeReviewService;
    private final ObjectMapper objectMapper;

    @Value("${github.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestBody String payload) {

        // 1. HMAC SHA-256 서명 검증
        if (signature != null && !verifySignature(payload, signature)) {
            log.warn("Webhook 서명 검증 실패");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature"));
        }

        // 2. pull_request 이벤트만 처리
        if (!"pull_request".equals(event)) {
            return ResponseEntity.ok(Map.of("status", "ignored", "event", event != null ? event : "unknown"));
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String action = root.path("action").asText();

            // opened 또는 synchronize (새 커밋 푸시) 이벤트만 처리
            if (!"opened".equals(action) && !"synchronize".equals(action)) {
                return ResponseEntity.ok(Map.of("status", "ignored", "action", action));
            }

            JsonNode pr = root.path("pull_request");
            JsonNode repo = root.path("repository");

            String repository = repo.path("full_name").asText();
            int prNumber = pr.path("number").asInt();
            long prId = pr.path("id").asLong();
            String title = pr.path("title").asText();
            String author = pr.path("user").path("login").asText();
            String branch = pr.path("head").path("ref").asText();
            String baseBranch = pr.path("base").path("ref").asText();
            int additions = pr.path("additions").asInt();
            int deletions = pr.path("deletions").asInt();
            int changedFiles = pr.path("changed_files").asInt();
            String description = pr.path("body").asText("");

            log.info("PR Webhook 수신: {} #{} by {} (action: {})",
                    repository, prNumber, author, action);

            // 3. 비동기 AI 코드 리뷰 시작
            codeReviewService.processReview(
                    prId, prNumber, repository, title, description,
                    author, branch, baseBranch, changedFiles, additions, deletions
            );

            return ResponseEntity.ok(Map.of(
                    "status", "processing",
                    "pr", String.valueOf(prNumber),
                    "repo", repository
            ));

        } catch (Exception e) {
            log.error("Webhook 처리 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * HMAC SHA-256 서명 검증
     */
    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return expected.equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("서명 검증 오류: {}", e.getMessage());
            return false;
        }
    }
}
