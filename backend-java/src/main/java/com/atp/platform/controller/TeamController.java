package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.Team;
import com.atp.platform.entity.User;
import com.atp.platform.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ApiResponse<List<Team>> list() {
        return ApiResponse.ok(teamService.listActive());
    }

    @GetMapping("/{id}")
    public ApiResponse<Team> get(@PathVariable Long id) {
        return ApiResponse.ok(teamService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('super_admin')")
    public ApiResponse<Team> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(teamService.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('super_admin')")
    public ApiResponse<Team> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(teamService.update(id, body));
    }

    @PutMapping("/users/{userId}/team")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<User> assignUser(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        Long teamId = body.get("team_id") != null ? Long.valueOf(body.get("team_id").toString()) : null;
        User user = teamService.assignUserTeam(userId, teamId);
        user.setPasswordHash(null);
        return ApiResponse.ok(user);
    }
}
