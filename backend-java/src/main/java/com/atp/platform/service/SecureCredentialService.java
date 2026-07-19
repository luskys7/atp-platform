package com.atp.platform.service;

import com.atp.platform.entity.SecureCredential;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.SecureCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SecureCredentialService {

    private static final Pattern NAME_RE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Set<String> SCOPES = Set.of("platform", "project", "team");

    private final SecureCredentialRepository repository;
    private final CryptoService cryptoService;

    public List<Map<String, Object>> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::toView).toList();
    }

    public SecureCredential get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "凭据不存在", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public SecureCredential create(Map<String, Object> body, Long userId) {
        if (body.get("value") == null || body.get("value").toString().isBlank()) {
            throw new AppException("INVALID", "新建凭据必须填写密钥内容", HttpStatus.BAD_REQUEST);
        }
        SecureCredential c = map(new SecureCredential(), body, true);
        c.setCreatedBy(userId);
        return repository.save(c);
    }

    @Transactional
    public SecureCredential update(Long id, Map<String, Object> body) {
        SecureCredential c = get(id);
        return repository.save(map(c, body, false));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(get(id));
    }

    /** 执行时按名称解密取值，注入变量链 */
    public String resolvePlainValue(String name) {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .map(c -> cryptoService.decrypt(c.getValueCipher()))
                .orElse(null);
    }

    private SecureCredential map(SecureCredential c, Map<String, Object> body, boolean creating) {
        if (body.containsKey("name")) {
            String name = body.get("name") == null ? "" : body.get("name").toString().trim();
            if (name.isBlank()) {
                throw new AppException("INVALID", "凭据名称不能为空", HttpStatus.BAD_REQUEST);
            }
            if (!NAME_RE.matcher(name).matches()) {
                throw new AppException("INVALID", "凭据名称仅支持大写字母、数字、下划线，且需以字母开头", HttpStatus.BAD_REQUEST);
            }
            c.setName(name);
        } else if (creating) {
            throw new AppException("INVALID", "凭据名称不能为空", HttpStatus.BAD_REQUEST);
        }
        if (body.containsKey("category")) c.setCategory(str(body.get("category")));
        if (body.containsKey("description")) c.setDescription(str(body.get("description")));
        if (body.containsKey("visibility_scope")) {
            String scope = normalizeScope(str(body.get("visibility_scope")));
            c.setVisibilityScope(scope);
        } else if (creating && (c.getVisibilityScope() == null || c.getVisibilityScope().isBlank())) {
            c.setVisibilityScope("platform");
        }
        if (body.containsKey("env_id")) {
            Object env = body.get("env_id");
            if (env == null || "".equals(env.toString().trim())) {
                c.setEnvId(null);
            } else {
                c.setEnvId(Long.valueOf(env.toString()));
            }
        }
        if (body.containsKey("value") && body.get("value") != null && !body.get("value").toString().isBlank()) {
            c.setValueCipher(cryptoService.encrypt(body.get("value").toString()));
        } else if (c.getValueCipher() == null) {
            throw new AppException("INVALID", "凭据值不能为空", HttpStatus.BAD_REQUEST);
        }
        return c;
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return "platform";
        String s = scope.trim().toLowerCase();
        if (!SCOPES.contains(s)) {
            throw new AppException("INVALID", "可见权限范围无效", HttpStatus.BAD_REQUEST);
        }
        return s;
    }

    private Map<String, Object> toView(SecureCredential c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("name", c.getName());
        row.put("category", c.getCategory());
        row.put("visibility_scope", c.getVisibilityScope() == null || c.getVisibilityScope().isBlank()
                ? "platform" : c.getVisibilityScope());
        row.put("env_id", c.getEnvId());
        row.put("description", c.getDescription());
        row.put("value_masked", mask(cryptoService.decrypt(c.getValueCipher())));
        row.put("updated_at", c.getUpdatedAt());
        return row;
    }

    private String mask(String plain) {
        if (plain == null || plain.isBlank()) return "";
        if (plain.length() <= 4) return "****";
        return plain.substring(0, 2) + "****" + plain.substring(plain.length() - 2);
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
