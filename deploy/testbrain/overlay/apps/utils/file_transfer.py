"""Word→Markdown：无 pypandoc/pandoc 时降级，避免拖垮 prd_analyzer 路由导入。"""


def word_to_markdown(input_file, output_file):
    try:
        import pypandoc

        output = pypandoc.convert_file(input_file, "markdown", outputfile=output_file)
        if output == "":
            print(f"成功将 {input_file} 转换为 {output_file}")
        else:
            print("转换过程中出现问题:", output)
        return
    except Exception as e:
        print(f"pypandoc 不可用，降级提取文本: {e}")

    try:
        from docx import Document

        doc = Document(input_file)
        lines = [p.text for p in doc.paragraphs if p.text]
        with open(output_file, "w", encoding="utf-8") as f:
            f.write("\n\n".join(lines))
        print(f"已用 python-docx 降级写入 {output_file}")
    except Exception as e:
        print(f"转换失败: {e}")
        with open(output_file, "w", encoding="utf-8") as f:
            f.write(f"# 文档转换失败\n\n原文件: {input_file}\n错误: {e}\n")
