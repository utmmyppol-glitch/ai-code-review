package com.bomin.codereview.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_comments")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id")
    private PullRequest pullRequest;

    @Column(nullable = false)
    private String filePath;

    private Integer lineNumber;

    @Column(nullable = false, length = 3000)
    private String comment; // AI가 작성한 한글 리뷰 코멘트

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommentType type;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private boolean postedToGithub; // GitHub에 코멘트 작성 완료 여부

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum CommentType {
        BUG,            // 버그 가능성
        SECURITY,       // 보안 취약점
        PERFORMANCE,    // 성능 이슈
        CODE_STYLE,     // 코드 스타일
        SUGGESTION,     // 개선 제안
        PRAISE          // 잘한 점
    }

    public enum Severity {
        INFO,       // 참고
        WARNING,    // 경고
        CRITICAL    // 심각
    }
}
