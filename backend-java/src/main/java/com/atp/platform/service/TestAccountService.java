package com.atp.platform.service;

import com.atp.platform.entity.TestAccount;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.TestAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TestAccountService {

    private final TestAccountRepository repository;
    private final CryptoService cryptoService;

    public List<Map<String, Object>> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::toView).toList();
    }

    public TestAccount get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "账号不存在", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public TestAccount create(Map<String, Object> body) {
        if (body.get("password") == null || body.get("password").toString().isBlank()) {
            throw new AppException("INVALID", "新建账号必须填写密码", HttpStatus.BAD_REQUEST);
        }
        TestAccount a = mapAccount(new TestAccount(), body);
        return repository.save(a);
    }

    @Transactional
    public TestAccount update(Long id, Map<String, Object> body) {
        TestAccount a = get(id);
        mapAccount(a, body);
        return repository.save(a);
    }

    @Transactional
    public void delete(Long id) {
        TestAccount a = get(id);
        if (a.getStatus() == TestAccount.AccountStatus.locked) {
            throw new AppException("INVALID", "占用中的账号不可删除", HttpStatus.BAD_REQUEST);
        }
        repository.delete(a);
    }

    @Transactional
    public TestAccount acquire(Long taskId) {
        return acquireForTask(taskId, null, null);
    }

    @Transactional
    public TestAccount acquireForTask(Long taskId, Long envId, String tags) {
        List<TestAccount> candidates = repository.findAvailableForUpdate(envId);
        TestAccount picked = null;
        for (TestAccount a : candidates) {
            if (matchesTags(a.getTags(), tags)) {
                picked = a;
                break;
            }
        }
        if (picked == null) {
            throw new AppException("NO_ACCOUNT", "无空闲测试账号", HttpStatus.BAD_REQUEST);
        }
        picked.setStatus(TestAccount.AccountStatus.locked);
        picked.setLockedByTaskId(taskId);
        return repository.save(picked);
    }

    @Transactional
    public void releaseByTaskId(Long taskId) {
        repository.findByLockedByTaskId(taskId).ifPresent(a -> release(a.getId()));
    }

    public Map<String, String> credentialsForTask(Long accountId) {
        if (accountId == null) return Map.of();
        TestAccount a = get(accountId);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("ACCOUNT_USERNAME", a.getUsername());
        vars.put("ACCOUNT_PASSWORD", cryptoService.decrypt(a.getPasswordCipher()));
        if (a.getPhone() != null && !a.getPhone().isBlank()) {
            vars.put("ACCOUNT_PHONE", a.getPhone());
        }
        vars.put("ACCOUNT_ID", String.valueOf(a.getId()));
        return vars;
    }

    private boolean matchesTags(String accountTags, String requiredTags) {
        if (requiredTags == null || requiredTags.isBlank()) return true;
        if (accountTags == null || accountTags.isBlank()) return false;
        String[] req = requiredTags.split(",");
        String blob = accountTags.toLowerCase();
        for (String t : req) {
            if (!t.isBlank() && !blob.contains(t.trim().toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public void release(Long id) {
        TestAccount a = get(id);
        a.setStatus(TestAccount.AccountStatus.active);
        a.setLockedByTaskId(null);
        repository.save(a);
    }

    @Transactional
    public void archive(Long id) {
        TestAccount a = get(id);
        a.setStatus(TestAccount.AccountStatus.archived);
        a.setLockedByTaskId(null);
        repository.save(a);
    }

    private TestAccount mapAccount(TestAccount a, Map<String, Object> body) {
        if (body.containsKey("username")) a.setUsername(body.get("username").toString());
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").toString().isBlank()) {
            a.setPasswordCipher(cryptoService.encrypt(body.get("password").toString()));
        }
        if (body.containsKey("phone")) a.setPhone(str(body.get("phone")));
        if (body.containsKey("tags")) a.setTags(str(body.get("tags")));
        if (body.containsKey("remark")) a.setRemark(str(body.get("remark")));
        if (body.containsKey("env_id")) {
            a.setEnvId(body.get("env_id") == null || "".equals(body.get("env_id").toString().trim())
                    ? null : Long.valueOf(body.get("env_id").toString()));
        }
        if (body.containsKey("team_id")) {
            a.setTeamId(body.get("team_id") == null || "".equals(body.get("team_id").toString().trim())
                    ? null : Long.valueOf(body.get("team_id").toString()));
        }
        if (body.containsKey("project_key")) {
            a.setProjectKey(str(body.get("project_key")));
        }
        if (body.containsKey("enabled") && body.get("enabled") != null) {
            boolean enabled = Boolean.parseBoolean(body.get("enabled").toString());
            if (!enabled) {
                a.setStatus(TestAccount.AccountStatus.archived);
                a.setLockedByTaskId(null);
            } else if (a.getStatus() == TestAccount.AccountStatus.archived) {
                a.setStatus(TestAccount.AccountStatus.active);
            }
        }
        if (body.containsKey("status")) {
            a.setStatus(TestAccount.AccountStatus.valueOf(body.get("status").toString()));
        }
        return a;
    }

    private Map<String, Object> toView(TestAccount a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("username", a.getUsername());
        row.put("password_masked", maskSecret(cryptoService.decrypt(a.getPasswordCipher())));
        row.put("phone_masked", maskPhone(a.getPhone()));
        row.put("phone", a.getPhone());
        row.put("tags", a.getTags());
        row.put("env_id", a.getEnvId());
        row.put("team_id", a.getTeamId());
        row.put("project_key", a.getProjectKey());
        row.put("status", a.getStatus().name());
        row.put("enabled", a.getStatus() != TestAccount.AccountStatus.archived);
        row.put("locked_by_task_id", a.getLockedByTaskId());
        row.put("remark", a.getRemark());
        row.put("updated_at", a.getUpdatedAt());
        return row;
    }

    private String maskSecret(String s) {
        if (s == null || s.isBlank()) return "";
        if (s.length() <= 2) return "**";
        return "*".repeat(Math.max(0, s.length() - 2)) + s.substring(s.length() - 2);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
