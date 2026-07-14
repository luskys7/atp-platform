package com.atp.platform.config;

import com.atp.platform.entity.*;
import com.atp.platform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer {

    private final UserRepository userRepository;
    private final DeviceWhitelistRepository whitelistRepository;
    private final CaseFolderRepository folderRepository;
    private final TestEnvironmentRepository environmentRepository;
    private final TeamRepository teamRepository;
    private final DeviceRepository deviceRepository;
    private final TestCaseRepository caseRepository;
    private final TestTaskRepository taskRepository;
    private final TestSuiteRepository suiteRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initLocalData() {
        return args -> {
            Team defaultTeam = teamRepository.findByCode("default").orElseGet(() -> {
                Team t = new Team();
                t.setName("默认团队");
                t.setCode("default");
                t.setDescription("平台默认工作空间");
                t.setStatus((byte) 1);
                return teamRepository.save(t);
            });

            User admin = userRepository.findByUsernameAndStatus("admin", (byte) 1)
                    .orElseGet(() -> {
                        User u = new User();
                        u.setUsername("admin");
                        u.setDisplayName("超级管理员");
                        u.setRole(User.UserRole.super_admin);
                        u.setStatus((byte) 1);
                        return u;
                    });
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            if (admin.getTeamId() == null) {
                admin.setTeamId(defaultTeam.getId());
            }
            userRepository.save(admin);
            log.info("默认管理员已就绪: admin / admin123");

            if (whitelistRepository.findBySerialNumber("local-test-device").isEmpty()) {
                DeviceWhitelist wl = new DeviceWhitelist();
                wl.setSerialNumber("local-test-device");
                wl.setPlatform(Device.Platform.android);
                wl.setRemark("本地开发测试设备");
                wl.setCreatedBy(admin.getId() != null ? admin.getId() : 1L);
                whitelistRepository.save(wl);
                log.info("已创建测试白名单设备: local-test-device");
            }

            if (folderRepository.count() == 0) {
                CaseFolder root = new CaseFolder();
                root.setName("默认模块");
                root.setSortOrder(0);
                root.setTeamId(defaultTeam.getId());
                folderRepository.save(root);
                CaseFolder smoke = new CaseFolder();
                smoke.setName("冒烟测试");
                smoke.setParentId(root.getId());
                smoke.setTeamId(defaultTeam.getId());
                folderRepository.save(smoke);
                log.info("已初始化用例目录");
            }

            folderRepository.findAll().forEach(f -> {
                if (f.getTeamId() == null) {
                    f.setTeamId(defaultTeam.getId());
                    folderRepository.save(f);
                }
            });
            caseRepository.findAll().forEach(c -> {
                if (c.getTeamId() == null) {
                    c.setTeamId(defaultTeam.getId());
                    caseRepository.save(c);
                }
            });
            taskRepository.findAll().forEach(t -> {
                if (t.getTeamId() == null) {
                    t.setTeamId(defaultTeam.getId());
                    taskRepository.save(t);
                }
            });
            suiteRepository.findAll().forEach(s -> {
                if (s.getTeamId() == null) {
                    s.setTeamId(defaultTeam.getId());
                    suiteRepository.save(s);
                }
            });
            deviceRepository.findAll().forEach(d -> {
                if (d.getTeamId() == null) {
                    d.setTeamId(defaultTeam.getId());
                    deviceRepository.save(d);
                }
            });

            if (environmentRepository.count() == 0) {
                TestEnvironment test = new TestEnvironment();
                test.setName("测试环境");
                test.setEnvType(TestEnvironment.EnvType.test);
                test.setBaseUrl("https://test.example.com");
                test.setConfigJson("{\"env\":\"test\"}");
                environmentRepository.save(test);
                log.info("已初始化默认测试环境");
            }
        };
    }
}
