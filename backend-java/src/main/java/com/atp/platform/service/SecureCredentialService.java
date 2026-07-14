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

@Service
@RequiredArgsConstructor
public class SecureCredentialService {

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
        SecureCredential c = map(new SecureCredential(), body);
        c.setCreatedBy(userId);
        return repository.save(c);
    }

    @Transactional
    public SecureCredential update(Long id, Map<String, Object> body) {
        SecureCredential c = get(id);
        return repository.save(map(c, body));
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

    private SecureCredential map(SecureCredential c, Map<String, Object> body) {
        if (body.containsKey("name")) c.setName(body.get("name").toString());
        if (body.containsKey("category")) c.setCategory(str(body.get("category")));
        if (body.containsKey("description")) c.setDescription(str(body.get("description")));
        if (body.containsKey("env_id") && body.get("env_id") != null) {
            c.setEnvId(Long.valueOf(body.get("env_id").toString()));
        }
        if (body.containsKey("value") && body.get("value") != null && !body.get("value").toString().isBlank()) {
            c.setValueCipher(cryptoService.encrypt(body.get("value").toString()));
        } else if (c.getValueCipher() == null) {
            throw new AppException("INVALID", "凭据值不能为空", HttpStatus.BAD_REQUEST);
        }
        return c;
    }

    private Map<String, Object> toView(SecureCredential c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("name", c.getName());
        row.put("category", c.getCategory());
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
