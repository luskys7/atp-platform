package com.atp.platform.service;

import com.atp.platform.entity.TestEnvironment;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.TestEnvironmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private final TestEnvironmentRepository repository;

    public List<TestEnvironment> list() {
        return repository.findByStatusOrderByNameAsc(TestEnvironment.EnvStatus.active);
    }

    public TestEnvironment get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "环境不存在", HttpStatus.NOT_FOUND));
    }

    public TestEnvironment create(Map<String, Object> body) {
        TestEnvironment e = map(new TestEnvironment(), body);
        return repository.save(e);
    }

    public TestEnvironment update(Long id, Map<String, Object> body) {
        TestEnvironment e = get(id);
        map(e, body);
        return repository.save(e);
    }

    public void delete(Long id) {
        TestEnvironment e = get(id);
        e.setStatus(TestEnvironment.EnvStatus.disabled);
        repository.save(e);
    }

    private TestEnvironment map(TestEnvironment e, Map<String, Object> body) {
        if (body.containsKey("name")) e.setName(body.get("name").toString());
        if (body.containsKey("env_type")) e.setEnvType(TestEnvironment.EnvType.valueOf(body.get("env_type").toString()));
        if (body.containsKey("base_url")) e.setBaseUrl(str(body.get("base_url")));
        if (body.containsKey("config_json")) e.setConfigJson(str(body.get("config_json")));
        if (body.containsKey("description")) e.setDescription(str(body.get("description")));
        return e;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
