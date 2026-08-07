"""知识库上传页：项目绑定 + 多文件上传。"""
import hashlib
import json
import os
from datetime import datetime

from django.conf import settings
from django.http import JsonResponse
from django.shortcuts import render
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods

from apps.core.models import KnowledgeBase, TestCase
from apps.knowledge.milvus_helper import process_singel_file
from apps.knowledge.service import get_knowledgeService_instance
from apps.utils.logger_manager import get_logger

logger = get_logger(__name__)
knowledge_service = get_knowledgeService_instance()

ALLOWED_EXTS = {".pdf", ".docx", ".doc", ".txt", ".md"}
MAX_BYTES = 20 * 1024 * 1024


def upload_page(request):
    """GET：上传页；POST：兼容旧入口，转发到 upload_file。"""
    if request.method == "GET":
        project = (request.GET.get("project") or "").strip()
        return render(
            request,
            "upload.html",
            {
                "projects": list(TestCase.BU_CHOICES),
                "current_project": project,
            },
        )
    return upload_file(request)


@csrf_exempt
@require_http_methods(["POST"])
def upload_file(request):
    """上传单个文件到知识库（前端可循环调用实现多文件）。"""
    try:
        project = (request.POST.get("project") or "").strip()
        doc_name = (request.POST.get("doc_name") or "").strip()
        remark = (request.POST.get("remark") or "").strip()

        valid = {k for k, _ in TestCase.BU_CHOICES}
        if not project or project not in valid:
            return JsonResponse({"success": False, "error": "请先选择项目归属", "code": "no_project"})

        uploaded_file = request.FILES.get("single_file") or request.FILES.get("file")
        if not uploaded_file:
            return JsonResponse({"success": False, "error": "未接收到文件", "code": "no_file"})

        original_name = uploaded_file.name
        file_ext = os.path.splitext(original_name)[1].lower()
        if file_ext not in ALLOWED_EXTS:
            return JsonResponse(
                {
                    "success": False,
                    "error": f"格式不支持：{file_ext}，仅支持 {', '.join(sorted(ALLOWED_EXTS))}",
                    "code": "bad_format",
                }
            )

        if uploaded_file.size > MAX_BYTES:
            return JsonResponse(
                {
                    "success": False,
                    "error": f"文件过大：超过单文件上限 20MB（当前 {uploaded_file.size // (1024 * 1024)}MB）",
                    "code": "too_large",
                }
            )

        display_name = doc_name or os.path.splitext(original_name)[0]
        # 保存时尽量保留扩展名
        save_name = display_name if display_name.lower().endswith(file_ext) else f"{display_name}{file_ext}"
        # 避免路径穿越
        save_name = os.path.basename(save_name.replace("\\", "/"))

        upload_dir = settings.MEDIA_ROOT
        # 按项目分子目录，实现多业务隔离
        project_dir = os.path.join(upload_dir, project)
        os.makedirs(project_dir, exist_ok=True)
        file_path = os.path.join(project_dir, save_name)

        is_overwrite = False
        if os.path.exists(file_path):
            is_overwrite = True
            try:
                knowledge_service.vector_store.delete_by_source(file_path)
            except Exception as e:
                logger.error(f"删除旧向量记录失败: {e}")
                return JsonResponse({"success": False, "error": f"覆盖文件时删除旧数据失败: {e}", "code": "overwrite_fail"})
            try:
                os.remove(file_path)
            except Exception as e:
                return JsonResponse({"success": False, "error": f"删除旧文件失败: {e}", "code": "overwrite_fail"})

        with open(file_path, "wb+") as f:
            for chunk in uploaded_file.chunks():
                f.write(chunk)

        try:
            chunks = process_singel_file(file_path)
        except Exception as e:
            logger.error(f"文件解析失败: {e}", exc_info=True)
            return JsonResponse({"success": False, "error": f"解析失败：{e}", "code": "parse_fail"})

        if not chunks:
            return JsonResponse({"success": False, "error": "解析失败：文件中无有效内容", "code": "parse_fail"})

        text_contents = []
        if isinstance(chunks, list):
            for chunk in chunks:
                text_contents.append(str(chunk.text) if hasattr(chunk, "text") else str(chunk))
        else:
            text_contents = [str(chunks.text) if hasattr(chunks, "text") else str(chunks)]

        try:
            all_embeddings = knowledge_service.embedder.get_embeddings(
                texts=text_contents, show_progress_bar=False
            )
            embeddings_list = []
            for emb in all_embeddings:
                if hasattr(emb, "tolist"):
                    emb = emb.tolist()
                embeddings_list.append(emb)

            meta = {
                "bu": project,
                "doc_name": display_name,
                "remark": remark,
                "original_name": original_name,
            }
            data_to_insert = []
            for i in range(len(text_contents)):
                data_to_insert.append(
                    {
                        "embedding": embeddings_list[i],
                        "content": text_contents[i],
                        "metadata": json.dumps(meta, ensure_ascii=False),
                        "source": file_path,
                        "doc_type": file_ext,
                        "chunk_id": f"{hashlib.md5(os.path.basename(file_path).encode()).hexdigest()[:10]}_{i:04d}",
                        "upload_time": datetime.now().isoformat(),
                    }
                )
            knowledge_service.vector_store.add_data(data_to_insert)
        except Exception as e:
            logger.error(f"向量生成/入库失败: {e}", exc_info=True)
            return JsonResponse({"success": False, "error": f"解析失败：向量入库异常 {e}", "code": "parse_fail"})

        # 同步一条 KnowledgeBase 记录，便于首页动态与知识库列表
        kb_title = display_name
        kb_content = text_contents[0][:2000] if text_contents else ""
        if remark:
            kb_content = f"[项目:{project}][备注:{remark}]\n{kb_content}"
        else:
            kb_content = f"[项目:{project}]\n{kb_content}"
        KnowledgeBase.objects.create(title=kb_title, content=kb_content)

        return JsonResponse(
            {
                "success": True,
                "count": len(text_contents),
                "message": f"成功{'覆盖' if is_overwrite else '导入'}文件到知识库",
                "project": project,
                "doc_name": display_name,
            }
        )
    except Exception as e:
        logger.error(f"上传失败: {e}", exc_info=True)
        return JsonResponse({"success": False, "error": str(e), "code": "unknown"})
