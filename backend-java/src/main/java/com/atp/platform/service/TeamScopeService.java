package com.atp.platform.service;

import com.atp.platform.entity.User;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.UserRepository;
import com.atp.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamScopeService {

    private final UserRepository userRepository;

    public boolean bypassTeamScope() {
        return SecurityUtils.hasAnyRole("super_admin");
    }

    /** 非超管返回当前团队 ID；超管返回 null 表示不过滤 */
    public Long scopeTeamId() {
        if (bypassTeamScope()) {
            return null;
        }
        return requireTeamId();
    }

    public Long requireTeamId() {
        Long teamId = SecurityUtils.currentTeamId();
        if (teamId == null) {
            Long userId = SecurityUtils.currentUserId();
            if (userId != null) {
                teamId = userRepository.findById(userId).map(User::getTeamId).orElse(null);
            }
        }
        if (teamId == null) {
            throw new AppException("NO_TEAM", "用户未归属团队，请联系管理员", HttpStatus.FORBIDDEN);
        }
        return teamId;
    }

    public void assertTeamAccess(Long resourceTeamId) {
        if (bypassTeamScope()) {
            return;
        }
        // 调度器、Agent 等后台线程无 SecurityContext，按任务已绑定的 teamId 执行
        if (SecurityUtils.currentUserId() == null) {
            return;
        }
        Long teamId = requireTeamId();
        if (resourceTeamId == null || !teamId.equals(resourceTeamId)) {
            throw new AppException("FORBIDDEN", "无权访问其他团队资源", HttpStatus.FORBIDDEN);
        }
    }

    /** 创建资源时写入团队 ID；超管未指定时使用默认团队 1 */
    public Long teamIdForCreate(Long requestedTeamId) {
        if (bypassTeamScope()) {
            return requestedTeamId != null ? requestedTeamId : 1L;
        }
        return requireTeamId();
    }
}
