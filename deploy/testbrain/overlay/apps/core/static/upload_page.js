// 知识库上传页

const ALLOWED = ['.pdf', '.docx', '.doc', '.txt', '.md'];
const MAX_BYTES = 20 * 1024 * 1024;
const PROJECT_KEY = 'tb_current_project';

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

function extOf(name) {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i).toLowerCase() : '';
}

function formatSize(n) {
  if (n < 1024) return n + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  return (n / (1024 * 1024)).toFixed(2) + ' MB';
}

document.addEventListener('DOMContentLoaded', function () {
  const projectSelect = document.getElementById('project-select');
  const docName = document.getElementById('doc-name');
  const docRemark = document.getElementById('doc-remark');
  const dropZone = document.getElementById('drop-zone');
  const fileInput = document.getElementById('file-input');
  const fileListCard = document.getElementById('file-list-card');
  const fileList = document.getElementById('file-list');
  const fileCount = document.getElementById('file-count');
  const btnUpload = document.getElementById('btn-upload');
  const btnCancel = document.getElementById('btn-cancel');
  const progressWrap = document.getElementById('progress-wrap');
  const progressBar = document.getElementById('progress-bar');
  const progressText = document.getElementById('progress-text');

  /** @type {File[]} */
  let files = [];
  let uploading = false;

  // 与首页/评审项目联动
  const saved = localStorage.getItem(PROJECT_KEY) || '';
  if (saved && !projectSelect.value) {
    for (let i = 0; i < projectSelect.options.length; i++) {
      if (projectSelect.options[i].value === saved) {
        projectSelect.value = saved;
        break;
      }
    }
  }

  function hasProject() {
    return !!(projectSelect && projectSelect.value);
  }

  function syncProjectGate() {
    const ok = hasProject();
    dropZone.classList.toggle('disabled', !ok || uploading);
    dropZone.querySelector('.drop-sub').textContent = ok
      ? '支持多选；点击或拖拽添加文件'
      : '支持多选；请先选择项目归属';
    syncUploadBtn();
  }

  function syncUploadBtn() {
    const enabled = hasProject() && files.length > 0 && !uploading;
    btnUpload.disabled = !enabled;
  }

  function syncDocNameDefault() {
    if (!docName) return;
    if (files.length === 1) {
      const base = files[0].name.replace(/\.[^.]+$/, '');
      if (!docName.dataset.manual || docName.dataset.manual === '0') {
        docName.value = base;
      }
    } else if (files.length > 1) {
      if (!docName.dataset.manual || docName.dataset.manual === '0') {
        docName.value = '';
        docName.placeholder = '多文件时默认使用各自原始文件名';
      }
    } else {
      docName.value = '';
      docName.placeholder = '默认回填原始文件名，可手动修改';
      docName.dataset.manual = '0';
    }
  }

  function renderList() {
    if (!files.length) {
      fileListCard.style.display = 'none';
      fileList.innerHTML = '';
      syncDocNameDefault();
      syncUploadBtn();
      return;
    }
    fileListCard.style.display = 'block';
    fileCount.textContent = '（' + files.length + '）';
    fileList.innerHTML = files
      .map(function (f, idx) {
        return (
          '<li class="file-item" data-idx="' +
          idx +
          '">' +
          '<div class="file-meta">' +
          '<div class="file-name">' +
          escapeHtml(f.name) +
          '</div>' +
          '<div class="file-size">' +
          formatSize(f.size) +
          '</div>' +
          '</div>' +
          '<button type="button" class="btn-remove" data-idx="' +
          idx +
          '">移除</button>' +
          '</li>'
        );
      })
      .join('');
    syncDocNameDefault();
    syncUploadBtn();
  }

  function addFiles(fileListLike) {
    const incoming = Array.from(fileListLike || []);
    const errors = [];
    incoming.forEach(function (f) {
      const ext = extOf(f.name);
      if (ALLOWED.indexOf(ext) < 0) {
        errors.push(f.name + '：格式不支持');
        return;
      }
      if (f.size > MAX_BYTES) {
        errors.push(f.name + '：超过 20MB');
        return;
      }
      const dup = files.some(function (x) {
        return x.name === f.name && x.size === f.size;
      });
      if (!dup) files.push(f);
    });
    if (errors.length) alert(errors.join('\n'));
    renderList();
  }

  projectSelect.addEventListener('change', function () {
    localStorage.setItem(PROJECT_KEY, this.value || '');
    syncProjectGate();
  });

  docName.addEventListener('input', function () {
    this.dataset.manual = this.value.trim() ? '1' : '0';
  });

  dropZone.addEventListener('click', function () {
    if (!hasProject() || uploading) return;
    fileInput.click();
  });

  fileInput.addEventListener('change', function () {
    addFiles(this.files);
    this.value = '';
  });

  ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (ev) {
    dropZone.addEventListener(ev, function (e) {
      e.preventDefault();
      e.stopPropagation();
    });
  });
  ['dragenter', 'dragover'].forEach(function (ev) {
    dropZone.addEventListener(ev, function () {
      if (!hasProject() || uploading) return;
      dropZone.classList.add('dragover');
    });
  });
  ['dragleave', 'drop'].forEach(function (ev) {
    dropZone.addEventListener(ev, function () {
      dropZone.classList.remove('dragover');
    });
  });
  dropZone.addEventListener('drop', function (e) {
    if (!hasProject() || uploading) return;
    addFiles(e.dataTransfer.files);
  });

  fileList.addEventListener('click', function (e) {
    const btn = e.target.closest('.btn-remove');
    if (!btn) return;
    const idx = parseInt(btn.getAttribute('data-idx'), 10);
    files.splice(idx, 1);
    renderList();
  });

  btnCancel.addEventListener('click', function () {
    window.location.href = tbUrl('/knowledge/');
  });

  btnUpload.addEventListener('click', async function () {
    if (!hasProject()) {
      alert('请先选择项目归属');
      return;
    }
    if (!files.length) {
      alert('请至少选择一个文件');
      return;
    }

    uploading = true;
    syncProjectGate();
    btnUpload.disabled = true;
    btnUpload.innerHTML = '<span class="spinner-border spinner-border-sm mr-1"></span>上传中…';
    progressWrap.classList.add('show');
    setProgress(0, '开始上传…');

    const project = projectSelect.value;
    const remark = (docRemark.value || '').trim();
    const manualName = (docName.value || '').trim();
    const total = files.length;
    let done = 0;
    const failures = [];

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const perName =
        total === 1 && manualName
          ? manualName
          : manualName && total > 1
            ? manualName + '-' + (i + 1)
            : file.name.replace(/\.[^.]+$/, '');

      setProgress(Math.round((done / total) * 100), '正在上传（' + (i + 1) + '/' + total + '）：' + file.name);

      try {
        await uploadOne(file, project, perName, remark, function (pct) {
          const overall = Math.round(((done + pct / 100) / total) * 100);
          setProgress(overall, '正在上传（' + (i + 1) + '/' + total + '）：' + file.name + ' ' + pct + '%');
        });
        done += 1;
        setProgress(Math.round((done / total) * 100), '已完成 ' + done + '/' + total);
      } catch (err) {
        failures.push(file.name + '：' + (err.message || String(err)));
        done += 1;
      }
    }

    uploading = false;
    btnUpload.innerHTML = '上传并存储到知识库';
    syncProjectGate();

    if (failures.length === 0) {
      setProgress(100, '全部上传成功');
      alert('上传成功！文件已写入知识库，稍后可在知识库管理中查看。');
      window.location.href = tbUrl('/knowledge/');
    } else if (failures.length < total) {
      alert('部分文件上传失败：\n' + failures.join('\n'));
      files = [];
      renderList();
      progressWrap.classList.remove('show');
    } else {
      alert('上传失败：\n' + failures.join('\n'));
      progressWrap.classList.remove('show');
      syncUploadBtn();
    }
  });

  function setProgress(pct, text) {
    progressBar.style.width = Math.max(0, Math.min(100, pct)) + '%';
    progressText.textContent = text || '';
  }

  function uploadOne(file, project, name, remark, onProgress) {
    return new Promise(function (resolve, reject) {
      const xhr = new XMLHttpRequest();
      const fd = new FormData();
      fd.append('single_file', file);
      fd.append('project', project);
      fd.append('doc_name', name || '');
      fd.append('remark', remark || '');

      xhr.open('POST', tbUrl('/api/upload-knowledge/'));
      xhr.setRequestHeader('X-CSRFToken', getCsrf());
      xhr.withCredentials = true;

      xhr.upload.onprogress = function (e) {
        if (e.lengthComputable && onProgress) {
          onProgress(Math.round((e.loaded / e.total) * 100));
        }
      };

      xhr.onload = function () {
        let data;
        try {
          data = JSON.parse(xhr.responseText);
        } catch (err) {
          reject(new Error('服务器返回异常'));
          return;
        }
        if (xhr.status >= 200 && xhr.status < 300 && data.success) {
          resolve(data);
        } else {
          reject(new Error(data.error || data.message || '上传失败'));
        }
      };
      xhr.onerror = function () {
        reject(new Error('网络错误'));
      };
      xhr.send(fd);
    });
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  syncProjectGate();
  renderList();
});
