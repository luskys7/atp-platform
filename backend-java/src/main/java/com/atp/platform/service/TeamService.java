package com.atp.platform.service;

import com.atp.platform.entity.Team;
import com.atp.platform.entity.User;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.TeamRepository;
import com.atp.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public List<Team> listActive() {
        return teamRepository.findByStatusOrderByNameAsc((byte) 1);
    }

    public Team get(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "团队不存在", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Team create(Map<String, Object> body) {
        String name = str(body.get("name"));
        String code = str(body.get("code"));
        if (name.isBlank() || code.isBlank()) {
            throw new AppException("INVALID", "名称和编码不能为空", HttpStatus.BAD_REQUEST);
        }
        if (teamRepository.findByCode(code).isPresent()) {
            throw new AppException("DUPLICATE", "团队编码已存在", HttpStatus.CONFLICT);
        }
        Team team = new Team();
        team.setName(name);
        team.setCode(code);
        team.setDescription(str(body.get("description")));
        team.setStatus((byte) 1);
        return teamRepository.save(team);
    }

    @Transactional
    public Team update(Long id, Map<String, Object> body) {
        Team team = get(id);
        if (body.containsKey("name")) team.setName(str(body.get("name")));
        if (body.containsKey("description")) team.setDescription(str(body.get("description")));
        if (body.containsKey("status") && body.get("status") != null) {
            team.setStatus(Byte.valueOf(body.get("status").toString()));
        }
        return teamRepository.save(team);
    }

    @Transactional
    public User assignUserTeam(Long userId, Long teamId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (teamId != null) {
            get(teamId);
            user.setTeamId(teamId);
        } else {
            user.setTeamId(null);
        }
        return userRepository.save(user);
    }

    private String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
