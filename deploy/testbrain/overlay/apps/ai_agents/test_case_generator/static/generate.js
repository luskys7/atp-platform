// 测试用例生成页 v12

const PROJECT_KEY = 'tb_current_project';
const EXAMPLE =
  'APP 登录功能，输入手机号 + 验证码，验证码 60s 倒计时，验证码错误提示弹窗，手机号格式非法拦截。';

function tbUrl(path) {
  const p = window.__ATP_TB_PREFIX__ || '';
  if (!path || path.charAt(0) !== '/') return path;
  if (path === p || path.indexOf(p + '/') === 0) return path;
  return p + path;
}

function csrf() {
  const inp = document.querySelector('[name=csrfmiddlewaretoken]');
  return inp ? inp.value : '';
}

function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function toLines(v) {
  if (Array.isArray(v)) return v.map(String);
  return String(v || '').split('\n').map((x) => x.trim()).filter(Boolean);
}

document.addEventListener('DOMContentLoaded', function () {
  const projectSelect = document.getElementById('project-select');
  const inputText = document.getElementById('input-text');
  const generateBtn = document.getElementById('generate-button');
  const clearBtn = document.getElementById('clear-button');
  const resultContainer = document.getElementById('result-container');
  const resultToolbar = document.getElementById('result-toolbar');
  const caseCount = document.getElementById('case_count');
  const selectedDocsEl = document.getElementById('selected-docs');

  let selectedDocs = []; // {id,title}
  let cases = [];
  let selectAll = false;

  // fill case count 1-100
  for (let i = 1; i <= 100; i++) {
    const o = document.createElement('option');
    o.value = String(i);
    o.textContent = i + ' 条';
    if (i === 5) o.selected = true;
    caseCount.appendChild(o);
  }

  // project sync
  const saved = localStorage.getItem(PROJECT_KEY) || '';
  if (saved) {
    for (let i = 0; i < projectSelect.options.length; i++) {
      if (projectSelect.options[i].value === saved) {
        projectSelect.value = saved;
        break;
      }
    }
  }
  projectSelect.addEventListener('change', function () {
    localStorage.setItem(PROJECT_KEY, this.value || '');
    selectedDocs = [];
    renderSelectedDocs();
    syncGenerateEnabled();
  });

  function syncGenerateEnabled() {
    const hasProject = !!projectSelect.value;
    const hasReq = !!(inputText.value || '').trim() || selectedDocs.length > 0;
    generateBtn.disabled = !(hasProject && hasReq) || generateBtn.dataset.loading === '1';
  }

  inputText.addEventListener('input', syncGenerateEnabled);

  // tabs
  document.querySelectorAll('.input-tabs .tab-btn').forEach((btn) => {
    btn.addEventListener('click', function () {
      document.querySelectorAll('.input-tabs .tab-btn').forEach((b) => b.classList.remove('active'));
      document.querySelectorAll('.tab-pane').forEach((p) => p.classList.remove('active'));
      this.classList.add('active');
      document.getElementById(this.dataset.tab === 'docs' ? 'tab-docs' : 'tab-manual').classList.add('active');
    });
  });

  // advanced default expanded
  const advToggle = document.getElementById('advanced-toggle');
  const advBody = document.getElementById('advanced-body');
  const advArrow = document.getElementById('advanced-arrow');
  advToggle.addEventListener('click', function () {
    const open = advBody.style.display !== 'none';
    advBody.style.display = open ? 'none' : 'block';
    advToggle.setAttribute('aria-expanded', open ? 'false' : 'true');
    advArrow.textContent = open ? '▾' : '▴';
  });

  // example
  document.getElementById('btn-example').addEventListener('click', () => $('#exampleModal').modal('show'));
  document.getElementById('btn-apply-example').addEventListener('click', function () {
    inputText.value = EXAMPLE;
    $('#exampleModal').modal('hide');
    syncGenerateEnabled();
  });

  // docs picker
  document.getElementById('btn-pick-docs').addEventListener('click', async function () {
    if (!projectSelect.value) {
      alert('请先选择项目归属');
      return;
    }
    const list = document.getElementById('docs-modal-list');
    list.innerHTML = '加载中…';
    $('#docsModal').modal('show');
    try {
      const res = await fetch(
        tbUrl('/test_case_generator/api/knowledge-docs/?project=' + encodeURIComponent(projectSelect.value)),
        { credentials: 'same-origin' }
      );
      const data = await res.json();
      const docs = data.docs || [];
      if (!docs.length) {
        list.innerHTML = '<div class="text-muted">当前项目暂无知识库文档，请先上传。</div>';
        return;
      }
      const selectedSet = new Set(selectedDocs.map((d) => d.id));
      list.innerHTML = docs
        .map(
          (d) =>
            '<label class="doc-modal-item"><input type="checkbox" value="' +
            escapeHtml(d.id) +
            '" data-title="' +
            escapeHtml(d.title) +
            '"' +
            (selectedSet.has(d.id) ? ' checked' : '') +
            '/> <span>' +
            escapeHtml(d.title) +
            '</span></label>'
        )
        .join('');
    } catch (e) {
      list.innerHTML = '加载失败：' + e.message;
    }
  });

  document.getElementById('btn-confirm-docs').addEventListener('click', function () {
    selectedDocs = Array.from(document.querySelectorAll('#docs-modal-list input:checked')).map((el) => ({
      id: el.value,
      title: el.getAttribute('data-title') || el.value,
    }));
    renderSelectedDocs();
    $('#docsModal').modal('hide');
    syncGenerateEnabled();
  });

  function renderSelectedDocs() {
    if (!selectedDocs.length) {
      selectedDocsEl.innerHTML = '<span class="text-muted" style="font-size:12px;">尚未选择文档</span>';
      return;
    }
    selectedDocsEl.innerHTML = selectedDocs
      .map(
        (d, i) =>
          '<span class="doc-chip">' +
          escapeHtml(d.title) +
          '<button type="button" data-i="' +
          i +
          '">移除</button></span>'
      )
      .join('');
  }
  selectedDocsEl.addEventListener('click', function (e) {
    const btn = e.target.closest('button[data-i]');
    if (!btn) return;
    selectedDocs.splice(parseInt(btn.getAttribute('data-i'), 10), 1);
    renderSelectedDocs();
    syncGenerateEnabled();
  });

  // clear
  clearBtn.addEventListener('click', function () {
    inputText.value = '';
    selectedDocs = [];
    renderSelectedDocs();
    document.getElementById('temperature').value = '0.2';
    caseCount.value = '5';
    document.getElementById('include_negative').value = '1';
    document.getElementById('include_pre_post').value = '1';
    cases = [];
    renderCases();
    syncGenerateEnabled();
  });

  // generate
  document.getElementById('generate-form').addEventListener('submit', async function (e) {
    e.preventDefault();
    if (!projectSelect.value) {
      alert('请先选择项目归属');
      return;
    }
    const requirements = (inputText.value || '').trim();
    if (!requirements && !selectedDocs.length) {
      alert('请输入需求或关联知识库文档');
      return;
    }

    generateBtn.dataset.loading = '1';
    generateBtn.disabled = true;
    generateBtn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> 生成中…';
    resultToolbar.style.display = 'none';
    resultContainer.innerHTML =
      '<div class="result-loading"><div class="spinner-border text-primary mb-2"></div><div>AI 正在分析需求，生成测试用例，请稍候...</div></div>';

    const methods = Array.from(document.getElementById('case_design_methods').selectedOptions).map((o) => o.value);
    const cats = Array.from(document.getElementById('case_categories').selectedOptions).map((o) => o.value);

    const payload = {
      project: projectSelect.value,
      requirements: requirements,
      doc_ids: selectedDocs.map((d) => d.id),
      llm_provider: document.getElementById('llm-provider').value,
      case_template: document.getElementById('case-template').value,
      case_count: caseCount.value,
      temperature: document.getElementById('temperature').value,
      include_negative: document.getElementById('include_negative').value === '1',
      include_pre_post: document.getElementById('include_pre_post').value === '1',
      case_design_methods: methods.length ? methods : ['等价类划分法', '场景法'],
      case_categories: cats.length ? cats : ['功能测试'],
    };

    try {
      const res = await fetch(tbUrl('/test_case_generator/'), {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRFToken': csrf() },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (data.success) {
        cases = (data.test_cases || []).map((tc) => ({
          description: tc.description || '',
          preconditions: tc.preconditions || '无',
          test_steps: toLines(tc.test_steps),
          expected_results: toLines(tc.expected_results),
          priority: tc.priority,
          _checked: false,
        }));
        renderCases();
      } else {
        resultContainer.innerHTML =
          '<div class="alert alert-danger mb-0">' + escapeHtml(data.message || '生成失败') + '</div>';
      }
    } catch (err) {
      resultContainer.innerHTML =
        '<div class="alert alert-danger mb-0">请求失败：' + escapeHtml(err.message) + '</div>';
    } finally {
      generateBtn.dataset.loading = '0';
      generateBtn.innerHTML = '生成测试用例';
      syncGenerateEnabled();
    }
  });

  function renderCases() {
    if (!cases.length) {
      resultToolbar.style.display = 'none';
      resultContainer.innerHTML =
        '<div class="result-empty" id="result-empty">点击【生成测试用例】，AI 产出的测试用例将在此展示</div>';
      return;
    }
    resultToolbar.style.display = 'flex';
    resultContainer.innerHTML =
      '<ul class="case-list">' +
      cases
        .map(function (tc, idx) {
          return (
            '<li class="case-row" data-idx="' +
            idx +
            '">' +
            '<input type="checkbox" class="case-check" ' +
            (tc._checked ? 'checked' : '') +
            ' />' +
            '<div>' +
            '<div class="case-title">' +
            escapeHtml(tc.description) +
            '</div>' +
            '<div class="case-meta"><strong>前置：</strong>' +
            escapeHtml(tc.preconditions || '无') +
            '</div>' +
            '<div class="case-meta"><strong>步骤：</strong>\n' +
            escapeHtml((tc.test_steps || []).join('\n')) +
            '</div>' +
            '<div class="case-meta"><strong>预期：</strong>\n' +
            escapeHtml((tc.expected_results || []).join('\n')) +
            '</div>' +
            '</div>' +
            '<div class="case-actions">' +
            '<button type="button" class="btn btn-sm btn-outline-primary btn-edit">编辑</button>' +
            '<button type="button" class="btn btn-sm btn-outline-danger btn-del">删除</button>' +
            '</div></li>'
          );
        })
        .join('') +
      '</ul>';
  }

  resultContainer.addEventListener('change', function (e) {
    if (!e.target.classList.contains('case-check')) return;
    const row = e.target.closest('.case-row');
    const idx = parseInt(row.getAttribute('data-idx'), 10);
    cases[idx]._checked = e.target.checked;
  });

  resultContainer.addEventListener('click', function (e) {
    const row = e.target.closest('.case-row');
    if (!row) return;
    const idx = parseInt(row.getAttribute('data-idx'), 10);
    if (e.target.classList.contains('btn-del')) {
      cases.splice(idx, 1);
      renderCases();
      return;
    }
    if (e.target.classList.contains('btn-edit')) {
      openEdit([idx]);
    }
  });

  document.getElementById('btn-select-all').addEventListener('click', function () {
    selectAll = !cases.every((c) => c._checked);
    cases.forEach((c) => (c._checked = selectAll));
    this.textContent = selectAll ? '取消全选' : '全选';
    renderCases();
  });

  document.getElementById('btn-batch-delete').addEventListener('click', function () {
    const before = cases.length;
    cases = cases.filter((c) => !c._checked);
    if (cases.length === before) {
      alert('请先勾选用例');
      return;
    }
    renderCases();
  });

  document.getElementById('btn-batch-edit').addEventListener('click', function () {
    const idxs = cases.map((c, i) => (c._checked ? i : -1)).filter((i) => i >= 0);
    if (!idxs.length) {
      alert('请先勾选用例');
      return;
    }
    if (idxs.length === 1) openEdit(idxs);
    else {
      // 批量：统一前置条件
      const pre = prompt('批量设置前置条件（将应用到所有勾选用例）：', cases[idxs[0]].preconditions || '无');
      if (pre == null) return;
      idxs.forEach((i) => (cases[i].preconditions = pre));
      renderCases();
    }
  });

  function openEdit(idxs) {
    const i = idxs[0];
    const tc = cases[i];
    document.getElementById('edit-index').value = String(i);
    document.getElementById('edit-title').value = tc.description || '';
    document.getElementById('edit-pre').value = tc.preconditions || '无';
    document.getElementById('edit-steps').value = (tc.test_steps || []).join('\n');
    document.getElementById('edit-expected').value = (tc.expected_results || []).join('\n');
    $('#editModal').modal('show');
  }

  document.getElementById('btn-save-edit').addEventListener('click', function () {
    const i = parseInt(document.getElementById('edit-index').value, 10);
    cases[i].description = document.getElementById('edit-title').value.trim();
    cases[i].preconditions = document.getElementById('edit-pre').value.trim() || '无';
    cases[i].test_steps = toLines(document.getElementById('edit-steps').value);
    cases[i].expected_results = toLines(document.getElementById('edit-expected').value);
    $('#editModal').modal('hide');
    renderCases();
  });

  document.getElementById('btn-submit-review').addEventListener('click', async function () {
    const picked = cases.filter((c) => c._checked);
    const payloadCases = picked.length ? picked : cases;
    if (!payloadCases.length) {
      alert('没有可提交的用例');
      return;
    }
    if (!projectSelect.value) {
      alert('请先选择项目归属');
      return;
    }
    this.disabled = true;
    try {
      const res = await fetch(tbUrl('/test_case_generator/save-test-case/'), {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRFToken': csrf() },
        body: JSON.stringify({
          test_cases: payloadCases,
          requirement: inputText.value,
          llm_provider: document.getElementById('llm-provider').value,
          project: projectSelect.value,
        }),
      });
      const data = await res.json();
      if (data.success) {
        alert(data.message || '提交成功');
        window.location.href = tbUrl(
          '/test_case_reviewer/?tab=pending&project=' + encodeURIComponent(projectSelect.value)
        );
      } else {
        alert(data.message || '提交失败');
      }
    } catch (e) {
      alert('提交失败：' + e.message);
    } finally {
      this.disabled = false;
    }
  });

  document.getElementById('btn-export').addEventListener('click', async function () {
    const picked = cases.filter((c) => c._checked);
    const payloadCases = picked.length ? picked : cases;
    if (!payloadCases.length) {
      alert('没有可导出的用例');
      return;
    }
    try {
      const res = await fetch(tbUrl('/test_case_generator/api/export-excel/'), {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRFToken': csrf() },
        body: JSON.stringify({ test_cases: payloadCases }),
      });
      const ct = res.headers.get('Content-Type') || '';
      if (ct.indexOf('json') >= 0) {
        const data = await res.json();
        alert(data.message || '导出失败');
        return;
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'generated_cases.xls';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      alert('导出失败：' + e.message);
    }
  });

  // prefer qwen
  const llm = document.getElementById('llm-provider');
  if (llm) {
    for (let i = 0; i < llm.options.length; i++) {
      const t = (llm.options[i].textContent || '').toLowerCase();
      const v = (llm.options[i].value || '').toLowerCase();
      if (t.indexOf('通义') >= 0 || v.indexOf('qwen') >= 0) {
        llm.value = llm.options[i].value;
        break;
      }
    }
  }

  renderSelectedDocs();
  renderCases();
  syncGenerateEnabled();
});
