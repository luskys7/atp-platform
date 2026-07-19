package com.atp.platform.service;

import com.atp.platform.entity.GlobalParameter;
import com.atp.platform.entity.GlobalParameterLog;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.GlobalParameterLogRepository;
import com.atp.platform.repository.GlobalParameterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GlobalParameterService {

    private final GlobalParameterRepository repository;
    private final GlobalParameterLogRepository logRepository;
    private final CryptoService cryptoService;

    public List<GlobalParameter> list() {
        return repository.findAllByOrderByScopeAscParamKeyAsc();
    }

    public List<GlobalParameterLog> logs(Long paramId) {
        return logRepository.findByParamIdOrderByCreatedAtDesc(paramId);
    }

    public GlobalParameter get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "全局参数不存在", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public GlobalParameter create(Map<String, Object> body, Long operatorId) {
        GlobalParameter p = map(new GlobalParameter(), body);
        p.setCreatedBy(operatorId);
        p.setVersionNum(1);
        validateUnique(p);
        p = repository.save(p);
        writeLog(p.getId(), 1, null, masked(p), operatorId, "初始创建");
        return sanitize(p);
    }

    @Transactional
    public GlobalParameter update(Long id, Map<String, Object> body, Long operatorId) {
        GlobalParameter p = get(id);
        String before = snapshot(p);
        map(p, body);
        validateUnique(p);
        p.setVersionNum(p.getVersionNum() + 1);
        p = repository.save(p);
        writeLog(p.getId(), p.getVersionNum(), before, snapshot(p), operatorId,
                body.getOrDefault("change_note", "更新").toString());
        return sanitize(p);
    }

    @Transactional
    public GlobalParameter toggle(Long id, boolean enabled, Long operatorId) {
        GlobalParameter p = get(id);
        p.setEnabled(enabled);
        p.setVersionNum(p.getVersionNum() + 1);
        p = repository.save(p);
        writeLog(p.getId(), p.getVersionNum(), null, enabled ? "enabled" : "disabled", operatorId, "启用状态变更");
        return sanitize(p);
    }

    /** 供变量链注入：返回 key->value */
    public Map<String, String> resolvePlatformParams() {
        Map<String, String> vars = new LinkedHashMap<>();
        for (GlobalParameter p : repository.findByScopeAndEnabledTrueOrderByParamKeyAsc(GlobalParameter.ParamScope.platform)) {
            vars.put(p.getParamKey(), readValue(p));
        }
        return vars;
    }

    public Map<String, String> resolveEnvParams(Long envId) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (envId == null) return vars;
        for (GlobalParameter p : repository.findByScopeAndEnvIdAndEnabledTrueOrderByParamKeyAsc(
                GlobalParameter.ParamScope.env, envId)) {
            vars.put(p.getParamKey(), readValue(p));
        }
        return vars;
    }

    private void validateUnique(GlobalParameter p) {
        Long envId = p.getEnvId();
        var existing = repository.findByScopeAndEnvIdAndParamKey(p.getScope(), envId, p.getParamKey());
        if (existing.isPresent() && !existing.get().getId().equals(p.getId())) {
            throw new AppException("DUPLICATE", "参数键已存在", HttpStatus.CONFLICT);
        }
    }

    private GlobalParameter map(GlobalParameter p, Map<String, Object> body) {
        if (body.containsKey("scope")) {
            String scope = body.get("scope").toString().trim();
            // 兼容旧「环境级」：映射为项目专属，便于前端三档作用域
            if ("env".equals(scope)) {
                scope = "project";
            }
            p.setScope(GlobalParameter.ParamScope.valueOf(scope));
        }
        if (body.containsKey("env_id")) {
            p.setEnvId(body.get("env_id") != null && !"".equals(body.get("env_id").toString().trim())
                    ? Long.valueOf(body.get("env_id").toString()) : null);
        }
        // 平台 / 项目 / 团队作用域默认不绑定 env_id
        if (p.getScope() == GlobalParameter.ParamScope.platform
                || p.getScope() == GlobalParameter.ParamScope.project
                || p.getScope() == GlobalParameter.ParamScope.team) {
            if (!body.containsKey("env_id")) {
                p.setEnvId(null);
            }
        }
        if (body.containsKey("param_key")) p.setParamKey(body.get("param_key").toString().trim());
        if (body.containsKey("description")) p.setDescription(str(body.get("description")));
        if (body.containsKey("enabled") && body.get("enabled") != null) {
            p.setEnabled(Boolean.parseBoolean(body.get("enabled").toString()));
        }
        if (body.containsKey("sensitive") && body.get("sensitive") != null) {
            p.setSensitive(Boolean.parseBoolean(body.get("sensitive").toString()));
        }
        if (body.containsKey("param_value")) {
            String val = str(body.get("param_value"));
            if (Boolean.TRUE.equals(p.getSensitive()) && val != null && !val.isBlank()) {
                p.setValueCipher(cryptoService.encrypt(val));
                p.setParamValue(null);
            } else {
                p.setParamValue(val);
                p.setValueCipher(null);
            }
        }
        return p;
    }

    private String readValue(GlobalParameter p) {
        if (Boolean.TRUE.equals(p.getSensitive()) && p.getValueCipher() != null) {
            return cryptoService.decrypt(p.getValueCipher());
        }
        return p.getParamValue() != null ? p.getParamValue() : "";
    }

    private GlobalParameter sanitize(GlobalParameter p) {
        if (Boolean.TRUE.equals(p.getSensitive())) {
            p.setParamValue("******");
            p.setValueCipher(null);
        }
        return p;
    }

    private String snapshot(GlobalParameter p) {
        return masked(p);
    }

    private String masked(GlobalParameter p) {
        if (Boolean.TRUE.equals(p.getSensitive())) return "******";
        return p.getParamValue() != null ? p.getParamValue() : "";
    }

    private void writeLog(Long paramId, int version, String before, String after, Long operatorId, String note) {
        GlobalParameterLog log = new GlobalParameterLog();
        log.setParamId(paramId);
        log.setVersionNum(version);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setOperatorId(operatorId);
        log.setChangeNote(note);
        logRepository.save(log);
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
