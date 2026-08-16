import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 无 Spring 启动的便携种子导出：控件池 + 用例/目录/公共步骤/套件。
 * 用法见 scripts/export-portable-seed.ps1
 */
public class SeedJdbcExport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: SeedJdbcExport <jdbcUrl> <outDir>");
            System.exit(2);
        }
        String url = args[0];
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir);

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            writeJson(outDir.resolve("control_pools.json"), queryMaps(conn, "SELECT * FROM control_pools ORDER BY id"));
            writeJson(outDir.resolve("case_folders.json"), queryMaps(conn, "SELECT * FROM case_folders ORDER BY id"));
            writeJson(outDir.resolve("test_cases.json"), queryMaps(conn, "SELECT * FROM test_cases ORDER BY id"));
            writeJson(outDir.resolve("common_steps.json"), queryMaps(conn, "SELECT * FROM common_steps ORDER BY id"));
            writeJson(outDir.resolve("test_suites.json"), queryMaps(conn, "SELECT * FROM test_suites ORDER BY id"));
            writeJson(outDir.resolve("test_suite_items.json"), queryMaps(conn, "SELECT * FROM test_suite_items ORDER BY id"));
            writeJson(outDir.resolve("global_parameters.json"), queryMaps(conn, "SELECT * FROM global_parameters ORDER BY id"));
            writeJson(outDir.resolve("environments.json"), queryMaps(conn, "SELECT * FROM test_environments ORDER BY id"));

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("created_at", LocalDateTime.now().toString());
            manifest.put("version", "1.0");
            manifest.put("purpose", "portable-seed");
            manifest.put("counts", Map.of(
                    "control_pools", count(conn, "control_pools"),
                    "test_cases", count(conn, "test_cases"),
                    "common_steps", count(conn, "common_steps"),
                    "case_folders", count(conn, "case_folders"),
                    "test_suites", count(conn, "test_suites")
            ));
            writeJson(outDir.resolve("manifest.json"), manifest);
        }

        Path zipPath = outDir.resolve("atp_portable_seed.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (String name : List.of(
                    "manifest.json", "control_pools.json", "case_folders.json", "test_cases.json",
                    "common_steps.json", "test_suites.json", "test_suite_items.json",
                    "global_parameters.json", "environments.json")) {
                Path p = outDir.resolve(name);
                if (!Files.exists(p)) continue;
                zos.putNextEntry(new ZipEntry(name));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }

        System.out.println("Seed exported to " + outDir.toAbsolutePath());
        System.out.println("Zip: " + zipPath.toAbsolutePath());
    }

    private static String toCamel(String col) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (int i = 0; i < col.length(); i++) {
            char c = col.charAt(i);
            if (c == '_') {
                up = true;
                continue;
            }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }

    private static long count(Connection conn, String tableExpr) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableExpr)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void writeJson(Path path, Object data) throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(data);
        Files.write(path, bytes);
        System.out.println("Wrote " + path.getFileName() + " (" + bytes.length + " bytes)");
    }

    private static List<Map<String, Object>> queryMaps(Connection conn, String sql) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    String col = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                    Object val = rs.getObject(i);
                    if (val instanceof Timestamp ts) {
                        val = ts.toLocalDateTime().toString();
                    } else if (val instanceof java.sql.Date d) {
                        val = d.toLocalDate().toString();
                    } else if (val instanceof Clob clob) {
                        val = clob.getSubString(1, (int) Math.min(clob.length(), Integer.MAX_VALUE));
                    }
                    row.put(toCamel(col), val);
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
