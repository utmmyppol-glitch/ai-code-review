package com.bomin.codereview.service;

import com.bomin.codereview.model.PullRequest;
import com.bomin.codereview.model.ReviewComment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CodeReviewService {

    @PersistenceContext
    private EntityManager em;

    private final GitHubApiService githubApiService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.model}")
    private String model;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    /**
     * PR 리뷰 처리 (비동기)
     */
    @Async
    public void processReview(long githubPrId, int prNumber, String repository,
                               String title, String description, String author,
                               String branch, String baseBranch,
                               int filesChanged, int additions, int deletions) {
        try {
            // 1. PR 정보 저장
            PullRequest pr = PullRequest.builder()
                    .githubPrId(githubPrId)
                    .prNumber(prNumber)
                    .repository(repository)
                    .title(title)
                    .description(description)
                    .author(author)
                    .branch(branch)
                    .baseBranch(baseBranch)
                    .filesChanged(filesChanged)
                    .additions(additions)
                    .deletions(deletions)
                    .status(PullRequest.ReviewStatus.REVIEWING)
                    .build();
            em.persist(pr);
            em.flush();

            // 2. GitHub API로 diff 조회
            String diff = githubApiService.getPrDiff(repository, prNumber);
            if (diff == null || diff.isBlank()) {
                pr.setStatus(PullRequest.ReviewStatus.FAILED);
                em.merge(pr);
                return;
            }

            // diff가 너무 길면 자르기 (토큰 제한)
            if (diff.length() > 12000) {
                diff = diff.substring(0, 12000) + "\n... (truncated)";
            }

            // 3. AI 코드 리뷰
            String aiResponse = requestAiReview(diff, title, description);

            // 4. AI 응답 파싱
            JsonNode reviewResult = parseAiResponse(aiResponse);

            // 5. 요약 + 위험도 저장
            pr.setAiSummary(reviewResult.path("summary").asText("요약 없음"));
            String risk = reviewResult.path("riskLevel").asText("LOW");
            pr.setRiskLevel(PullRequest.RiskLevel.valueOf(risk));

            // 6. 코멘트 저장
            JsonNode commentsNode = reviewResult.path("comments");
            if (commentsNode.isArray()) {
                for (JsonNode commentNode : commentsNode) {
                    ReviewComment comment = ReviewComment.builder()
                            .pullRequest(pr)
                            .filePath(commentNode.path("file").asText("general"))
                            .lineNumber(commentNode.has("line") ? commentNode.path("line").asInt() : null)
                            .comment(commentNode.path("comment").asText())
                            .type(parseCommentType(commentNode.path("type").asText()))
                            .severity(parseSeverity(commentNode.path("severity").asText()))
                            .build();
                    em.persist(comment);
                    pr.getComments().add(comment);
                }
            }

            pr.setStatus(PullRequest.ReviewStatus.COMPLETED);
            pr.setReviewedAt(LocalDateTime.now());
            em.merge(pr);

            // 7. GitHub에 리뷰 코멘트 작성
            String githubComment = buildGithubComment(pr);
            boolean posted = githubApiService.postIssueComment(repository, prNumber, githubComment);
            if (posted) {
                log.info("GitHub 코멘트 작성 완료: {} #{}", repository, prNumber);
            }

            // 8. WebSocket 알림
            messagingTemplate.convertAndSend("/topic/reviews", Map.of(
                    "type", "REVIEW_COMPLETED",
                    "prNumber", prNumber,
                    "repository", repository,
                    "riskLevel", risk,
                    "commentCount", pr.getComments().size()
            ));

            log.info("AI 코드 리뷰 완료: {} #{} (위험도: {}, 코멘트: {}개)",
                    repository, prNumber, risk, pr.getComments().size());

        } catch (Exception e) {
            log.error("코드 리뷰 처리 실패: {} #{} - {}",
                    repository, prNumber, e.getMessage());
        }
    }

    /**
     * OpenAI에 코드 리뷰 요청
     */
    private String requestAiReview(String diff, String title, String description) {
        String prompt = """
                당신은 시니어 개발자 코드 리뷰어입니다. 다음 Pull Request의 diff를 분석해주세요.
                반드시 JSON 형식으로만 응답하세요.
                
                [PR 정보]
                제목: %s
                설명: %s
                
                [변경 코드 (diff)]
                %s
                
                [분석 항목]
                1. 버그 가능성
                2. 보안 취약점 (SQL Injection, XSS, 하드코딩된 비밀키 등)
                3. 성능 이슈 (N+1 쿼리, 불필요한 연산 등)
                4. 코드 스타일 및 가독성
                5. 잘한 점
                
                [응답 형식]
                {
                    "summary": "이 PR의 한글 요약 (2~3줄)",
                    "riskLevel": "LOW" 또는 "MEDIUM" 또는 "HIGH",
                    "comments": [
                        {
                            "file": "파일경로",
                            "line": 라인번호 또는 null,
                            "type": "BUG|SECURITY|PERFORMANCE|CODE_STYLE|SUGGESTION|PRAISE",
                            "severity": "INFO|WARNING|CRITICAL",
                            "comment": "한글 리뷰 코멘트"
                        }
                    ]
                }
                """.formatted(title, description != null ? description : "없음", diff);

        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getInterceptors().add((req, body, exec) -> {
                req.getHeaders().setBearerAuth(openAiApiKey);
                req.getHeaders().set("Content-Type", "application/json");
                return exec.execute(req, body);
            });

            Map<String, Object> request = Map.of(
                    "model", model,
                    "max_tokens", 2000,
                    "temperature", 0.3,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "당신은 한국어로 코드 리뷰하는 시니어 개발자입니다. JSON으로만 응답하세요."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String response = restTemplate.postForObject(OPENAI_URL, request, String.class);
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("AI 리뷰 요청 실패: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode parseAiResponse(String response) {
        try {
            if (response == null) {
                return objectMapper.readTree("{\"summary\":\"분석 실패\",\"riskLevel\":\"LOW\",\"comments\":[]}");
            }
            String json = response;
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                json = json.substring(0, json.indexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
            }
            return objectMapper.readTree(json.trim());
        } catch (Exception e) {
            log.error("AI 응답 파싱 실패: {}", e.getMessage());
            try {
                return objectMapper.readTree("{\"summary\":\"파싱 실패\",\"riskLevel\":\"LOW\",\"comments\":[]}");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * GitHub에 올릴 리뷰 코멘트 마크다운 생성
     */
    private String buildGithubComment(PullRequest pr) {
        StringBuilder sb = new StringBuilder();
        String emoji = switch (pr.getRiskLevel()) {
            case LOW -> "🟢";
            case MEDIUM -> "🟡";
            case HIGH -> "🔴";
        };

        sb.append("## 🤖 AI Code Review\n\n");
        sb.append("**위험도:** ").append(emoji).append(" ").append(pr.getRiskLevel()).append("\n\n");
        sb.append("### 📋 요약\n").append(pr.getAiSummary()).append("\n\n");

        if (!pr.getComments().isEmpty()) {
            sb.append("### 💬 리뷰 코멘트\n\n");
            for (ReviewComment comment : pr.getComments()) {
                String typeEmoji = switch (comment.getType()) {
                    case BUG -> "🐛";
                    case SECURITY -> "🔒";
                    case PERFORMANCE -> "⚡";
                    case CODE_STYLE -> "🎨";
                    case SUGGESTION -> "💡";
                    case PRAISE -> "👍";
                };
                sb.append("- ").append(typeEmoji).append(" **[").append(comment.getType()).append("]** ");
                if (comment.getFilePath() != null && !comment.getFilePath().equals("general")) {
                    sb.append("`").append(comment.getFilePath()).append("`");
                    if (comment.getLineNumber() != null) {
                        sb.append(" L").append(comment.getLineNumber());
                    }
                    sb.append(": ");
                }
                sb.append(comment.getComment()).append("\n");
            }
        }

        sb.append("\n---\n*Powered by AI Code Review Bot* 🤖");
        return sb.toString();
    }

    // ── 조회 메서드 ──

    @Transactional(readOnly = true)
    public List<com.bomin.codereview.dto.ReviewDto.PrResponse> getReviews(String repository) {
        String jpql = repository != null ?
                "SELECT p FROM PullRequest p WHERE p.repository = :repo ORDER BY p.createdAt DESC" :
                "SELECT p FROM PullRequest p ORDER BY p.createdAt DESC";

        TypedQuery<PullRequest> query = em.createQuery(jpql, PullRequest.class);
        if (repository != null) query.setParameter("repo", repository);
        query.setMaxResults(50);

        return query.getResultList().stream()
                .map(com.bomin.codereview.dto.ReviewDto.PrResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public com.bomin.codereview.dto.ReviewDto.PrResponse getReviewByPrNumber(String repository, int prNumber) {
        TypedQuery<PullRequest> query = em.createQuery(
                "SELECT p FROM PullRequest p LEFT JOIN FETCH p.comments WHERE p.repository = :repo AND p.prNumber = :num",
                PullRequest.class);
        query.setParameter("repo", repository);
        query.setParameter("num", prNumber);
        PullRequest pr = query.getSingleResult();
        return com.bomin.codereview.dto.ReviewDto.PrResponse.from(pr);
    }

    @Transactional(readOnly = true)
    public com.bomin.codereview.dto.ReviewDto.StatsResponse getStats() {
        long totalPrs = em.createQuery("SELECT COUNT(p) FROM PullRequest p", Long.class).getSingleResult();
        long totalComments = em.createQuery("SELECT COUNT(c) FROM ReviewComment c", Long.class).getSingleResult();
        long high = em.createQuery("SELECT COUNT(p) FROM PullRequest p WHERE p.riskLevel = 'HIGH'", Long.class).getSingleResult();
        long medium = em.createQuery("SELECT COUNT(p) FROM PullRequest p WHERE p.riskLevel = 'MEDIUM'", Long.class).getSingleResult();
        long low = em.createQuery("SELECT COUNT(p) FROM PullRequest p WHERE p.riskLevel = 'LOW'", Long.class).getSingleResult();

        return com.bomin.codereview.dto.ReviewDto.StatsResponse.builder()
                .totalPrs(totalPrs)
                .totalComments(totalComments)
                .highRiskCount(high)
                .mediumRiskCount(medium)
                .lowRiskCount(low)
                .avgCommentsPerPr(totalPrs > 0 ? (double) totalComments / totalPrs : 0)
                .build();
    }

    private ReviewComment.CommentType parseCommentType(String type) {
        try { return ReviewComment.CommentType.valueOf(type); }
        catch (Exception e) { return ReviewComment.CommentType.SUGGESTION; }
    }

    private ReviewComment.Severity parseSeverity(String severity) {
        try { return ReviewComment.Severity.valueOf(severity); }
        catch (Exception e) { return ReviewComment.Severity.INFO; }
    }
}
