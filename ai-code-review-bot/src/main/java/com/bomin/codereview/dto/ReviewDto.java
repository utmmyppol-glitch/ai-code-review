package com.bomin.codereview.dto;

import com.bomin.codereview.model.PullRequest;
import com.bomin.codereview.model.ReviewComment;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class ReviewDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class PrResponse {
        private Long id;
        private Integer prNumber;
        private String repository;
        private String title;
        private String author;
        private String branch;
        private Integer filesChanged;
        private Integer additions;
        private Integer deletions;
        private String riskLevel;
        private String riskEmoji;
        private String aiSummary;
        private String status;
        private List<CommentResponse> comments;
        private LocalDateTime createdAt;
        private LocalDateTime reviewedAt;

        public static PrResponse from(PullRequest pr) {
            String emoji = switch (pr.getRiskLevel()) {
                case LOW -> "🟢";
                case MEDIUM -> "🟡";
                case HIGH -> "🔴";
            };

            return PrResponse.builder()
                    .id(pr.getId())
                    .prNumber(pr.getPrNumber())
                    .repository(pr.getRepository())
                    .title(pr.getTitle())
                    .author(pr.getAuthor())
                    .branch(pr.getBranch())
                    .filesChanged(pr.getFilesChanged())
                    .additions(pr.getAdditions())
                    .deletions(pr.getDeletions())
                    .riskLevel(pr.getRiskLevel().name())
                    .riskEmoji(emoji)
                    .aiSummary(pr.getAiSummary())
                    .status(pr.getStatus().name())
                    .comments(pr.getComments() != null ?
                            pr.getComments().stream().map(CommentResponse::from).toList() :
                            List.of())
                    .createdAt(pr.getCreatedAt())
                    .reviewedAt(pr.getReviewedAt())
                    .build();
        }
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CommentResponse {
        private Long id;
        private String filePath;
        private Integer lineNumber;
        private String comment;
        private String type;
        private String severity;
        private boolean postedToGithub;

        public static CommentResponse from(ReviewComment c) {
            return CommentResponse.builder()
                    .id(c.getId())
                    .filePath(c.getFilePath())
                    .lineNumber(c.getLineNumber())
                    .comment(c.getComment())
                    .type(c.getType().name())
                    .severity(c.getSeverity() != null ? c.getSeverity().name() : null)
                    .postedToGithub(c.isPostedToGithub())
                    .build();
        }
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class StatsResponse {
        private long totalPrs;
        private long totalComments;
        private long highRiskCount;
        private long mediumRiskCount;
        private long lowRiskCount;
        private double avgCommentsPerPr;
        private String mostCommonIssueType;
        private List<RepoStats> repoBreakdown;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class RepoStats {
        private String repository;
        private long prCount;
        private long commentCount;
        private String avgRisk;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class WebhookEvent {
        private String action;
        private Integer prNumber;
        private String repository;
        private String title;
        private String author;
    }
}
