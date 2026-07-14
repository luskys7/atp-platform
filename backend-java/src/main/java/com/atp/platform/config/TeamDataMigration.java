package com.atp.platform.config;

import com.atp.platform.entity.*;
import com.atp.platform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeamDataMigration implements CommandLineRunner {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final CaseFolderRepository folderRepository;
    private final TestCaseRepository caseRepository;
    private final TestTaskRepository taskRepository;
    private final TestSuiteRepository suiteRepository;
    private final DeviceRepository deviceRepository;

    @Override
    public void run(String... args) {
        Team defaultTeam = teamRepository.findByCode("default").orElseGet(() -> {
            Team t = new Team();
            t.setName("默认团队");
            t.setCode("default");
            t.setDescription("平台默认工作空间");
            t.setStatus((byte) 1);
            return teamRepository.save(t);
        });

        userRepository.findAll().forEach(u -> {
            if (u.getTeamId() == null) {
                u.setTeamId(defaultTeam.getId());
                userRepository.save(u);
            }
        });
        folderRepository.findAll().forEach(f -> backfillTeam(f.getTeamId(), defaultTeam.getId(), () -> {
            f.setTeamId(defaultTeam.getId());
            folderRepository.save(f);
        }));
        caseRepository.findAll().forEach(c -> backfillTeam(c.getTeamId(), defaultTeam.getId(), () -> {
            c.setTeamId(defaultTeam.getId());
            caseRepository.save(c);
        }));
        taskRepository.findAll().forEach(t -> backfillTeam(t.getTeamId(), defaultTeam.getId(), () -> {
            t.setTeamId(defaultTeam.getId());
            taskRepository.save(t);
        }));
        suiteRepository.findAll().forEach(s -> backfillTeam(s.getTeamId(), defaultTeam.getId(), () -> {
            s.setTeamId(defaultTeam.getId());
            suiteRepository.save(s);
        }));
        deviceRepository.findAll().forEach(d -> backfillTeam(d.getTeamId(), defaultTeam.getId(), () -> {
            d.setTeamId(defaultTeam.getId());
            deviceRepository.save(d);
        }));
    }

    private void backfillTeam(Long current, Long defaultId, Runnable save) {
        if (current == null) {
            save.run();
        }
    }
}
