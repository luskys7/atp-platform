package com.atp.platform.service.aicase;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 本地文档解析为纯文本，供 AI 用例生成使用。
 */
@Service
@RequiredArgsConstructor
public class AiDocumentParseService {

    private final AtpProperties properties;

    public Map<String, Object> parseUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("AI_CASE", "请上传文件", HttpStatus.BAD_REQUEST);
        }
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            String text;
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")
                    || lower.endsWith(".csv") || lower.endsWith(".log")) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else if (lower.endsWith(".docx")) {
                text = parseDocx(file.getInputStream());
            } else if (lower.endsWith(".pdf")) {
                text = parsePdf(file.getBytes());
            } else if (lower.endsWith(".doc")) {
                throw new AppException("AI_CASE", "暂不支持 .doc，请另存为 .docx / .pdf / .md / .txt",
                        HttpStatus.BAD_REQUEST);
            } else {
                throw new AppException("AI_CASE",
                        "不支持的文件类型，请上传 txt / md / docx / pdf", HttpStatus.BAD_REQUEST);
            }
            return wrap(name, text);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("AI_CASE", "文档解析失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> wrap(String source, String text) {
        String cleaned = normalize(text);
        int max = Math.max(5000, properties.getAiCase().getMaxDocChars());
        boolean truncated = cleaned.length() > max;
        if (truncated) cleaned = cleaned.substring(0, max);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source);
        out.put("prd_text", cleaned);
        out.put("char_count", cleaned.length());
        out.put("truncated", truncated);
        return out;
    }

    private String parseDocx(InputStream in) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            return doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n"));
        }
    }

    private String parsePdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    static String normalize(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    Map<String, Object> wrapExternal(String source, String text) {
        return wrap(source, text);
    }
}
