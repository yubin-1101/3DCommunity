package com.community.controller;

import com.community.dto.*;
import com.community.model.Role;
import com.community.model.SuspensionHistory;
import com.community.model.User;
import com.community.service.AdminService;
import com.community.service.AdminMessageService;
import com.community.service.ActiveUserService;
import com.community.service.AuditLogService;
import com.community.service.PaymentService;
import com.community.service.StatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AuditLogService auditLogService;
    private final AdminMessageService adminMessageService;
    private final PaymentService paymentService;
    private final StatisticsService statisticsService;
    private final ActiveUserService activeUserService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 대시보드 통계 조회
     */
    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<DashboardStatsDto> getDashboardStats() {
        DashboardStatsDto stats = adminService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 관리자 활동 로그 조회
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long adminId
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<AuditLogDto> logs;

        if (action != null) {
            logs = auditLogService.getLogsByAction(action, pageable).map(AuditLogDto::fromEntity);
        } else if (adminId != null) {
            logs = auditLogService.getLogsByAdmin(adminId, pageable).map(AuditLogDto::fromEntity);
        } else {
            logs = auditLogService.getAllLogs(pageable).map(AuditLogDto::fromEntity);
        }

        return ResponseEntity.ok(logs);
    }

    /**
     * 관리자 권한 확인 엔드포인트
     */
    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<String> checkAdminAccess(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok("Admin access granted for user: " + user.getUsername());
    }

    // ==================== 사용자 관리 API ====================

    /**
     * 사용자 목록 조회 (검색, 필터, 페이지네이션)
     */
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsDTO> getStatistics() {
        return ResponseEntity.ok(statisticsService.getDashboardStats());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<Page<UserManagementDto>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isSuspended,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Role roleEnum = null;
        if (role != null && !role.isEmpty()) {
            try {
                roleEnum = Role.valueOf(role);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        Page<User> users = adminService.searchUsers(search, roleEnum, isSuspended, pageable);
        Page<UserManagementDto> userDtos = users.map(UserManagementDto::fromEntity);

        return ResponseEntity.ok(userDtos);
    }

    /**
     * 사용자 상세 조회
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<UserManagementDto> getUserDetail(@PathVariable Long userId) {
        User user = adminService.searchUsers(null, null, null, PageRequest.of(0, 1))
                .stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        return ResponseEntity.ok(UserManagementDto.fromEntity(user));
    }

    /**
     * 사용자 제재
     */
    @PostMapping("/users/{userId}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<UserManagementDto> suspendUser(
            @PathVariable Long userId,
            @RequestBody SuspensionRequest request,
            @AuthenticationPrincipal User admin,
            HttpServletRequest httpRequest
    ) {
        try {
            User user = adminService.suspendUser(userId, request, admin, httpRequest);
            return ResponseEntity.ok(UserManagementDto.fromEntity(user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 역할 변경
     */
    @PostMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('DEVELOPER')")  // DEVELOPER만 역할 변경 가능
    public ResponseEntity<UserManagementDto> changeUserRole(
            @PathVariable Long userId,
            @RequestBody RoleChangeRequest request,
            @AuthenticationPrincipal User admin,
            HttpServletRequest httpRequest
    ) {
        try {
            User user = adminService.changeUserRole(userId, request, admin, httpRequest);
            return ResponseEntity.ok(UserManagementDto.fromEntity(user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 사용자 제재 이력 조회
     */
    @GetMapping("/users/{userId}/suspension-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<Page<SuspensionHistoryDto>> getUserSuspensionHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SuspensionHistory> history = adminService.getUserSuspensionHistory(userId, pageable);
        Page<SuspensionHistoryDto> historyDtos = history.map(SuspensionHistoryDto::fromEntity);

        return ResponseEntity.ok(historyDtos);
    }

    // ==================== 게시글 관리 API ====================

    /**
     * 관리자 권한으로 게시글 삭제
     */
    @DeleteMapping("/posts/{postId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<String> deletePostAsAdmin(
            @PathVariable Long postId,
            @AuthenticationPrincipal User admin,
            HttpServletRequest httpRequest
    ) {
        try {
            adminService.deletePost(postId, admin, httpRequest);
            return ResponseEntity.ok("게시글이 삭제되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==================== 채팅 로그 관리 API ====================

    /**
     * 전체 채팅 로그 조회 (페이지네이션)
     */
    @GetMapping("/chat-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<Page<ChatLogDto>> getAllChatLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatLogDto> logs = adminMessageService.getAllChatLogs(pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 메시지 타입별 채팅 로그 조회 (PLAZA, DM, LOCAL_ROOM)
     */
    @GetMapping("/chat-logs/type/{messageType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<Page<ChatLogDto>> getChatLogsByType(
            @PathVariable String messageType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<ChatLogDto> logs = adminMessageService.getChatLogsByType(messageType, pageable);
            return ResponseEntity.ok(logs);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 특정 사용자의 채팅 로그 조회
     */
    @GetMapping("/chat-logs/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<Page<ChatLogDto>> getChatLogsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatLogDto> logs = adminMessageService.getChatLogsByUser(userId, pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 키워드로 채팅 로그 검색
     */
    @GetMapping("/chat-logs/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<Page<ChatLogDto>> searchChatLogs(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatLogDto> logs = adminMessageService.searchChatLogs(keyword, pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 메시지 삭제 (소프트 삭제)
     */
    @DeleteMapping("/chat-logs/{messageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<String> deleteMessage(@PathVariable Long messageId) {
        try {
            adminMessageService.deleteMessage(messageId);
            return ResponseEntity.ok("메시지가 삭제되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 메시지 복구
     */
    @PostMapping("/chat-logs/{messageId}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<String> restoreMessage(@PathVariable Long messageId) {
        try {
            adminMessageService.restoreMessage(messageId);
            return ResponseEntity.ok("메시지가 복구되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 메시지 영구 삭제
     */
    @DeleteMapping("/chat-logs/{messageId}/permanent")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<String> permanentlyDeleteMessage(@PathVariable Long messageId) {
        try {
            adminMessageService.permanentlyDeleteMessage(messageId);
            return ResponseEntity.ok("메시지가 영구 삭제되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 7일 이상 된 메시지 수동 삭제
     */
    @PostMapping("/chat-logs/cleanup")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<String> manualCleanup() {
        long deletedCount = adminMessageService.deleteOldMessagesManually();
        return ResponseEntity.ok(deletedCount + "개의 오래된 메시지가 삭제되었습니다.");
    }

    // ==================== 결제/환불 관리 API ====================

    /**
     * 전체 결제 내역 조회
     */
    @GetMapping("/payment/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<Page<PaymentHistoryDTO>> getAllPaymentHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        
        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        return ResponseEntity.ok(paymentService.getAllPaymentHistory(pageable));
    }

    /**
     * 결제 취소/환불
     */
    @PostMapping("/payment/cancel/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<PaymentResponseDTO> cancelPayment(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "관리자 취소");
        return ResponseEntity.ok(paymentService.cancelPayment(orderId, reason));
    }

    // ==================== 세션 관리 API ====================

    /**
     * 활성 세션 목록 조회
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<List<ActiveSessionDto>> getActiveSessions() {
        List<ActiveSessionDto> sessions = activeUserService.getAllActiveSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * 특정 유저 강제 로그아웃
     */
    @DeleteMapping("/sessions/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public ResponseEntity<?> forceLogout(@PathVariable String userId) {
        try {
            String sessionId = activeUserService.getSessionIdByUserId(userId);
            if (sessionId == null) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "해당 사용자는 현재 접속하지 않았습니다.");
                return ResponseEntity.badRequest().body(error);
            }

            // 세션 제거
            activeUserService.removeUserById(userId);

            // 해당 사용자에게 강제 로그아웃 메시지 전송
            Map<String, Object> logoutMessage = new HashMap<>();
            logoutMessage.put("type", "FORCE_LOGOUT");
            logoutMessage.put("message", "관리자에 의해 강제 로그아웃되었습니다.");
            messagingTemplate.convertAndSendToUser(userId, "/queue/logout", logoutMessage);

            Map<String, String> response = new HashMap<>();
            response.put("message", "사용자가 강제 로그아웃되었습니다.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "강제 로그아웃 실패: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
