package com.atp.platform.config;

import com.atp.platform.entity.*;
import com.atp.platform.repository.*;
import com.atp.platform.service.SeedDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

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
    private final MachineInfoRepository machineInfoRepository;
    private final FunctionTagRepository functionTagRepository;
    private final MachineTagRelRepository machineTagRelRepository;
    private final CaseTagRelRepository caseTagRelRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedDataService seedDataService;

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

            // 空库优先导入便携种子（控件/用例），避免随后再造空目录占位
            try {
                var seedResult = seedDataService.importIfEmpty();
                if (seedResult != null && !Boolean.TRUE.equals(seedResult.get("skipped"))) {
                    log.info("便携种子导入结果: {}", seedResult);
                }
            } catch (Exception e) {
                log.warn("便携种子导入跳过: {}", e.getMessage());
            }

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

            // 机型适配：仅补齐缺失的示例功能项，不覆盖/不停用用户自行添加的数据
            String[][] featureCatalog = {
                    {"清洁功能", "清洁", "清扫"},
                    {"清洁功能", "清洁", "拖地"},
                    {"地图功能", "地图", "建图"},
                    {"地图功能", "地图", "编辑地图"},
                    {"智能功能", "智能", "语音控制"},
                    {"设备管理", "设备", "设备信息"},
                    {"通知体系", "通知", "任务完成通知"},
                    {"通知体系", "通知", "推送设置"},
                    {"用户体系", "用户", "登录注册"}
            };
            int featureAdded = 0;
            for (String[] def : featureCatalog) {
                var existingList = functionTagRepository.findByTagTypeAndTagNameAndContentNameAndTeamId(
                        def[0], def[1], def[2], defaultTeam.getId());
                if (existingList == null || existingList.isEmpty()) {
                    FunctionTag tag = new FunctionTag();
                    tag.setTagType(def[0]);
                    tag.setTagName(def[1]);
                    tag.setContentName(def[2]);
                    tag.setStatus((byte) 1);
                    tag.setTeamId(defaultTeam.getId());
                    functionTagRepository.save(tag);
                    featureAdded++;
                }
            }
            if (featureAdded > 0) {
                log.info("功能集示例数据已补齐: 新增={}", featureAdded);
            }
            // 仅执行一次：恢复此前被种子白名单误停用的自建功能项
            java.nio.file.Path reviveMarker = java.nio.file.Path.of("data", ".feature_tag_revive_done");
            if (!java.nio.file.Files.exists(reviveMarker)) {
                int revived = 0;
                for (FunctionTag tag : functionTagRepository.findByTeamIdOrderByTagTypeAscTagNameAscContentNameAsc(defaultTeam.getId())) {
                    String content = tag.getContentName() == null ? "" : tag.getContentName().trim();
                    if (!content.isEmpty() && (tag.getStatus() == null || tag.getStatus() == 0)) {
                        tag.setStatus((byte) 1);
                        functionTagRepository.save(tag);
                        revived++;
                    }
                }
                try {
                    java.nio.file.Files.createDirectories(reviveMarker.getParent());
                    java.nio.file.Files.writeString(reviveMarker, "ok");
                } catch (Exception ignored) {
                    // ignore marker write failure
                }
                if (revived > 0) {
                    log.info("已恢复被误停用的功能项: {}", revived);
                }
            }

            if (machineInfoRepository.count() == 0) {
                MachineInfo ax17 = new MachineInfo();
                ax17.setMachineName("AX17");
                ax17.setHardVersion("HW-A");
                ax17.setFirmVersion("1.2.0");
                ax17.setStatus((byte) 1);
                ax17.setTeamId(defaultTeam.getId());
                ax17.setRemark("示例机型-清洁/设备基础能力");
                ax17 = machineInfoRepository.save(ax17);

                MachineInfo x30 = new MachineInfo();
                x30.setMachineName("X30");
                x30.setHardVersion("HW-B");
                x30.setFirmVersion("2.0.1");
                x30.setStatus((byte) 1);
                x30.setTeamId(defaultTeam.getId());
                x30.setRemark("示例机型-地图/智能进阶能力");
                x30 = machineInfoRepository.save(x30);

                Map<String, Long> tagIds = functionTagRepository
                        .findByTeamIdAndStatusOrderByTagTypeAscTagNameAscContentNameAsc(defaultTeam.getId(), (byte) 1)
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(
                                FunctionTag::getContentName, FunctionTag::getId, (a, b) -> a));

                bindMachineSupport(ax17.getId(), tagIds, "清扫", "设备信息", "登录注册");
                bindMachineSupport(x30.getId(), tagIds, "清扫", "建图", "语音控制", "任务完成通知");

                List<TestCase> cases = caseRepository.findByDeletedAtIsNull();
                if (!cases.isEmpty() && tagIds.containsKey("清扫")) {
                    CaseTagRel r1 = new CaseTagRel();
                    r1.setCaseId(cases.get(0).getId());
                    r1.setTagId(tagIds.get("清扫"));
                    caseTagRelRepository.save(r1);
                }
                if (cases.size() > 1 && tagIds.containsKey("设备信息")) {
                    CaseTagRel r2 = new CaseTagRel();
                    r2.setCaseId(cases.get(1).getId());
                    r2.setTagId(tagIds.get("设备信息"));
                    caseTagRelRepository.save(r2);
                }
                log.info("已初始化机型档案种子数据: AX17 / X30");
            }
        };
    }

    private void bindMachineSupport(Long machineId, Map<String, Long> tagIds, String... tagNames) {
        for (String name : tagNames) {
            Long tagId = tagIds.get(name);
            if (tagId == null) {
                continue;
            }
            MachineTagRel rel = new MachineTagRel();
            rel.setMachineId(machineId);
            rel.setTagId(tagId);
            rel.setIsSupport((byte) 1);
            machineTagRelRepository.save(rel);
        }
    }
}
