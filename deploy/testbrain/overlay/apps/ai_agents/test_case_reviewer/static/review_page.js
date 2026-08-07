// 测试用例评审页交互（ATP 嵌入）

function tbUrl(path) {
  const p = window.__ATP_TB_PREFIX__ || '';
  if (!path || path.charAt(0) !== '/') return path;
  if (path === p || path.indexOf(p + '/') === 0) return path;
  return p + path;
}

function getCsrf() {
  const inp = document.querySelector('[name=csrfmiddlewaretoken]');
  return inp ? inp.value : '';
}

function selectedIds(selector) {
  return Array.from(document.querySelectorAll(selector + ':checked')).map((el) =>
    el.getAttribute('data-id')
  );
}

function activeTab() {
  const a = document.querySelector('.review-tabs .nav-link.active');
  return a ? a.getAttribute('data-tab') : 'pending';
}

function checkSelectorForTab(tab) {
  if (tab === 'approved') return '.approved-check';
  if (tab === 'rejected') return '.rejected-check';
  return '.pending-check';
}

document.addEventListener('DOMContentLoaded', function () {
  const PROJECT_KEY = 'tb_current_project';
  const filterProject = document.getElementById('filter-project');
  const filterForm = document.getElementById('filter-form');
  const filterTab = document.getElementById('filter-tab');

  // 与首页项目选择联动
  if (filterProject) {
    const saved = localStorage.getItem(PROJECT_KEY);
    if (saved && !filterProject.value) {
      for (let i = 0; i < filterProject.options.length; i++) {
        if (filterProject.options[i].value === saved) {
          filterProject.value = saved;
          break;
        }
      }
    }
    filterProject.addEventListener('change', function () {
      localStorage.setItem(PROJECT_KEY, this.value || '');
    });
  }

  // Tab 切换：带筛选条件刷新
  document.querySelectorAll('.review-tabs .nav-link').forEach((link) => {
    link.addEventListener('click', function (e) {
      e.preventDefault();
      const tab = this.getAttribute('data-tab');
      if (filterTab) filterTab.value = tab;
      if (filterForm) filterForm.submit();
    });
  });

  // 全选
  document.querySelectorAll('.select-all').forEach((box) => {
    box.addEventListener('change', function () {
      const target = this.getAttribute('data-target');
      document.querySelectorAll('.' + target).forEach((cb) => {
        cb.checked = this.checked;
      });
    });
  });

  // 展开全文
  document.querySelectorAll('.btn-expand').forEach((btn) => {
    btn.addEventListener('click', function () {
      const src = this.getAttribute('data-src');
      const title = this.getAttribute('data-title') || '内容';
      const el = document.getElementById(src);
      document.getElementById('expandModalTitle').textContent = title;
      document.getElementById('expandModalBody').textContent = el ? el.value : '';
      $('#expandModal').modal('show');
    });
  });

  // 查看详情
  document.querySelectorAll('.btn-detail').forEach((btn) => {
    btn.addEventListener('click', async function () {
      const id = this.getAttribute('data-id');
      const body = document.getElementById('detailModalBody');
      body.innerHTML = '加载中…';
      $('#detailModal').modal('show');
      try {
        const res = await fetch(
          tbUrl('/test_case_reviewer/case-review-detail/api/test-case/' + id + '/'),
          { credentials: 'same-origin' }
        );
        const data = await res.json();
        if (!data.success && data.error) {
          body.textContent = data.error;
          return;
        }
        const statusMap = { pending: '待评审', approved: '已通过', rejected: '未通过' };
        const reviews = data.reviews || [];
        let hist =
          reviews.length === 0
            ? '<div class="text-muted">暂无历史评审记录</div>'
            : reviews
                .map(
                  (r) =>
                    '<div class="review-history-item"><div class="meta">' +
                    (r.review_date || '') +
                    (r.reviewer ? ' · ' + r.reviewer : '') +
                    '</div><div>' +
                    escapeHtml(r.comments || '') +
                    '</div></div>'
                )
                .join('');
        body.innerHTML =
          '<p><strong>状态：</strong>' +
          (statusMap[data.status] || data.status) +
          '</p>' +
          '<p><strong>创建时间：</strong>' +
          (data.created_at || '-') +
          '</p>' +
          '<p><strong>用例描述：</strong></p><pre>' +
          escapeHtml(data.description || '') +
          '</pre>' +
          '<p><strong>测试步骤：</strong></p><pre>' +
          escapeHtml(data.test_steps || '') +
          '</pre>' +
          '<p><strong>预期结果：</strong></p><pre>' +
          escapeHtml(data.expected_results || '') +
          '</pre>' +
          '<hr/><p><strong>历史评审记录</strong></p>' +
          hist;
      } catch (err) {
        body.textContent = '加载失败：' + err.message;
      }
    });
  });

  // AI 评审
  document.querySelectorAll('.btn-ai-review').forEach((btn) => {
    btn.addEventListener('click', async function () {
      const id = this.getAttribute('data-id');
      const out = document.getElementById('ai-result-body');
      out.textContent = 'AI 评审中，请稍候…';
      this.disabled = true;
      $('#aiModal').modal('show');
      try {
        const res = await fetch(tbUrl('/test_case_reviewer/case-review-detail/api/review/'), {
          method: 'POST',
          credentials: 'same-origin',
          headers: {
            'Content-Type': 'application/json',
            'X-CSRFToken': getCsrf(),
          },
          body: JSON.stringify({ test_case_id: id }),
        });
        const data = await res.json();
        if (data.success) {
          out.textContent = data.review_result || '无评审内容';
        } else {
          out.textContent = data.message || 'AI 评审失败';
        }
      } catch (err) {
        out.textContent = '请求失败：' + err.message;
      } finally {
        this.disabled = false;
      }
    });
  });

  // 单条人工评审
  document.querySelectorAll('.btn-manual-review').forEach((btn) => {
    btn.addEventListener('click', function () {
      openManualModal({
        mode: 'single',
        ids: [this.getAttribute('data-id')],
        title: '评审处理',
        lockStatus: false,
        defaultStatus: 'approved',
      });
    });
  });

  document.getElementById('manual-submit').addEventListener('click', async function () {
    const mode = document.getElementById('manual-mode').value;
    const idsRaw = document.getElementById('manual-ids').value || '';
    const ids = idsRaw.split(',').filter(Boolean);
    const status = document.getElementById('manual-status').value;
    const comments = (document.getElementById('manual-comments').value || '').trim();
    if (!comments) {
      alert('请填写评审意见');
      return;
    }
    this.disabled = true;
    try {
      let url, body;
      if (mode === 'batch') {
        url = tbUrl('/test_case_reviewer/api/batch-manual-review/');
        body = { ids: ids, status: status, comments: comments };
      } else {
        url = tbUrl('/test_case_reviewer/api/manual-review/');
        body = { test_case_id: ids[0], status: status, comments: comments };
      }
      const res = await fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRFToken': getCsrf(),
        },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (data.success) {
        $('#manualModal').modal('hide');
        alert(data.message || '操作成功');
        window.location.reload();
      } else {
        alert(data.message || '操作失败');
      }
    } catch (err) {
      alert('请求失败：' + err.message);
    } finally {
      this.disabled = false;
    }
  });

  // 批量操作
  document.querySelectorAll('.batch-bar .btn').forEach((btn) => {
    btn.addEventListener('click', async function () {
      const action = this.getAttribute('data-action');
      const tab = this.closest('.batch-bar').getAttribute('data-tab') || activeTab();
      const ids = selectedIds(checkSelectorForTab(tab));
      if (!ids.length) {
        alert('请先勾选用例');
        return;
      }

      if (action === 'copy') {
        try {
          const res = await fetch(
            tbUrl('/test_case_reviewer/test_case_reviewer/api/copy-test-cases/?ids=' + ids.join(',')),
            {
              method: 'POST',
              credentials: 'same-origin',
              headers: { 'X-CSRFToken': getCsrf(), 'Content-Type': 'application/json' },
            }
          );
          const data = await res.json();
          if (!data.success) {
            alert(data.message || '复制失败');
            return;
          }
          const text = (data.test_cases || [])
            .map((tc, i) => {
              return (
                '#' +
                (i + 1) +
                '\n描述: ' +
                (tc.description || '') +
                '\n步骤: ' +
                (tc.test_steps || '') +
                '\n预期: ' +
                (tc.expected_results || '')
              );
            })
            .join('\n\n');
          await navigator.clipboard.writeText(text);
          alert('已复制 ' + ids.length + ' 条用例到剪贴板');
        } catch (err) {
          alert('复制失败：' + err.message);
        }
        return;
      }

      if (action === 'export') {
        window.location.href = tbUrl(
          '/test_case_reviewer/test_case_reviewer/api/export-test-cases-excel/?ids=' + ids.join(',')
        );
        return;
      }

      if (action === 'approve') {
        if (tab !== 'pending') {
          alert('仅待评审用例支持批量通过');
          return;
        }
        openManualModal({
          mode: 'batch',
          ids: ids,
          title: '批量评审通过',
          lockStatus: true,
          defaultStatus: 'approved',
        });
        return;
      }

      if (action === 'reject') {
        if (tab !== 'pending') {
          alert('仅待评审用例支持批量驳回');
          return;
        }
        openManualModal({
          mode: 'batch',
          ids: ids,
          title: '批量驳回',
          lockStatus: true,
          defaultStatus: 'rejected',
        });
        return;
      }

      if (action === 'delete') {
        if (!confirm('确认删除选中的 ' + ids.length + ' 条用例？')) return;
        try {
          const res = await fetch(
            tbUrl('/test_case_reviewer/api/delete-test-cases/?ids=' + ids.join(',')),
            {
              method: 'DELETE',
              credentials: 'same-origin',
              headers: { 'X-CSRFToken': getCsrf() },
            }
          );
          const data = await res.json();
          if (data.success) {
            alert(data.message || '删除成功');
            window.location.reload();
          } else {
            alert(data.message || '删除失败');
          }
        } catch (err) {
          alert('删除失败：' + err.message);
        }
      }
    });
  });

  function openManualModal(opts) {
    document.getElementById('manual-mode').value = opts.mode;
    document.getElementById('manual-ids').value = (opts.ids || []).join(',');
    document.getElementById('manualModalTitle').textContent = opts.title || '评审处理';
    document.getElementById('manual-comments').value = '';
    const statusSel = document.getElementById('manual-status');
    statusSel.value = opts.defaultStatus || 'approved';
    statusSel.disabled = !!opts.lockStatus;
    document.getElementById('manual-status-group').style.display = opts.lockStatus ? 'none' : 'block';
    $('#manualModal').modal('show');
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
});
