package com.precise.test.analyze;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.model.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接口自动识别引擎单元测试。
 *
 * <p>覆盖两类场景：
 * <ol>
 *   <li>字符串解析（{@link ApiScanner#scanSource}）：不依赖文件系统，快速验证
 *       路径拼接 / HTTP 方法 / 参数结构 / DTO 校验注解 / 废弃方法过滤；</li>
 *   <li>临时目录扫描（{@link ApiScanner#scanDetailed}）：验证真实文件遍历、
 *       相对路径输出、跨文件 DTO 解析、非 Controller 忽略与坏文件容错。</li>
 * </ol>
 */
class ApiScannerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ApiScanner SCANNER = new ApiScanner();

    // ====================================================================
    // 场景一：字符串解析
    // ====================================================================

    @Test
    void scanSource_shouldBuildDefinitionsForMixedMappings() throws Exception {
        String source = """
                package com.example.demo;

                import org.springframework.web.bind.annotation.*;
                import javax.validation.Valid;
                import javax.validation.constraints.Min;
                import javax.validation.constraints.NotBlank;

                @RestController
                @RequestMapping("/user")
                public class UserController {

                    @GetMapping("/list")
                    public HttpResult<UserVO> list(@RequestParam(value = "page", required = false, defaultValue = "1") int page) {
                        return null;
                    }

                    @GetMapping("/{id}")
                    public HttpResult<UserVO> detail(@PathVariable("id") Long id) {
                        return null;
                    }

                    @PostMapping("/create")
                    public HttpResult<UserVO> create(@Valid @RequestBody UserCreateDTO dto) {
                        return null;
                    }

                    @PutMapping("/{id}")
                    public HttpResult<UserVO> update(@PathVariable Long id, @RequestBody UserCreateDTO dto) {
                        return null;
                    }

                    @DeleteMapping("/{id}")
                    public HttpResult<UserVO> remove(@PathVariable("id") Long id) {
                        return null;
                    }

                    @RequestMapping(value = "/query", method = RequestMethod.GET)
                    public HttpResult<UserVO> query(@RequestParam String keyword) {
                        return null;
                    }

                    @Deprecated
                    @GetMapping("/old")
                    public HttpResult<UserVO> oldApi() {
                        return null;
                    }
                }

                class UserCreateDTO {
                    @NotBlank(message = "姓名不能为空")
                    private String name;
                    @Min(value = 1, message = "年龄最小为1")
                    private Integer age;
                    private String email;
                }

                class UserVO {
                    private Long id;
                    private String name;
                }
                """;

        List<ApiDefinition> defs = SCANNER.scanSource(1L, source);

        // 6 个有效接口（/old 被 @Deprecated 过滤），路径/方法正确
        assertEquals(6, defs.size(), "应识别 6 个接口（@Deprecated 的 /old 被过滤）");
        assertTrue(defs.stream().noneMatch(d -> d.getApiPath().equals("/user/old")),
                "@Deprecated 方法不应被识别");

        // ---- 路径拼接与 HTTP 方法 ----
        ApiDefinition list = findBy(defs, "/user/list", "GET");
        ApiDefinition detail = findBy(defs, "/user/{id}", "GET");
        ApiDefinition create = findBy(defs, "/user/create", "POST");
        ApiDefinition update = findBy(defs, "/user/{id}", "PUT");
        ApiDefinition remove = findBy(defs, "/user/{id}", "DELETE");
        ApiDefinition query = findBy(defs, "/user/query", "GET");

        // 同一路径 /user/{id} 存在 GET/PUT/DELETE 三个方法
        assertEquals(3, defs.stream().filter(d -> d.getApiPath().equals("/user/{id}")).count(),
                "/user/{id} 应有 GET/PUT/DELETE 三个接口");

        // ---- 代码定位 ----
        assertEquals("com.example.demo.UserController", list.getControllerClass());
        assertEquals("list", list.getControllerMethod());
        assertNotNull(list.getLineNo());
        assertTrue(list.getLineNo() > 0, "lineNo 应为方法声明起始行号");
        assertEquals(ApiDefinition.STATUS_PENDING, list.getStatus());

        // ---- 参数结构：@RequestParam ----
        JsonNode listParams = params(list);
        JsonNode page = param(listParams, "page");
        assertEquals("QUERY", page.get("source").asText());
        assertFalse(page.get("required").asBoolean(), "required=false 应被解析");
        assertEquals("1", page.get("defaultValue").asText());
        assertEquals("int", page.get("type").asText());

        // ---- 参数结构：@PathVariable ----
        JsonNode detailParams = params(detail);
        JsonNode id = param(detailParams, "id");
        assertEquals("PATH", id.get("source").asText());
        assertTrue(id.get("required").asBoolean(), "@PathVariable 默认必填");
        assertEquals("Long", id.get("type").asText());

        // ---- 参数结构：@RequestBody + DTO 字段 + 校验注解 ----
        JsonNode createParams = params(create);
        JsonNode dto = param(createParams, "dto");
        assertEquals("BODY", dto.get("source").asText());
        assertTrue(dto.get("required").asBoolean(), "@RequestBody 默认必填");
        assertTrue(dto.get("validated").asBoolean(), "@Valid 应被标记");
        assertEquals("UserCreateDTO", dto.get("type").asText());
        assertEquals("com.example.demo.UserCreateDTO", dto.get("qualifiedType").asText());

        JsonNode fields = dto.get("fields");
        assertEquals(3, fields.size(), "DTO 应包含 name/age/email 三个字段");
        JsonNode nameField = field(fields, "name");
        assertTrue(hasValidation(nameField, "NotBlank"), "name 字段应带 @NotBlank");
        assertEquals("姓名不能为空", validationMessage(nameField, "NotBlank"));
        JsonNode ageField = field(fields, "age");
        assertTrue(hasValidation(ageField, "Min"), "age 字段应带 @Min");
        assertEquals("年龄最小为1", validationMessage(ageField, "Min"));
        JsonNode emailField = field(fields, "email");
        assertTrue(emailField.get("validations") == null, "无校验注解的字段不应输出 validations");

        // ---- 参数结构：无注解参数（Spring 默认 Query 绑定）与必填默认值 ----
        JsonNode queryParams = params(query);
        JsonNode keyword = param(queryParams, "keyword");
        assertEquals("QUERY", keyword.get("source").asText());
        assertTrue(keyword.get("required").asBoolean(), "@RequestParam 未声明 required 时默认必填");

        // ---- 返回结构 ----
        assertEquals("HttpResult<UserVO>", MAPPER.readTree(list.getResponseSchemaJson()).get("returnType").asText());
    }

    @Test
    void scanSource_shouldHandleClassWithoutPrefixAndRequestMappingDefaults() throws Exception {
        String source = """
                package com.example.demo2;

                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.*;

                @Controller
                public class HtmlController {

                    @GetMapping("/page")
                    public String page() {
                        return "index";
                    }

                    @RequestMapping(value = "/ping")
                    public String ping() {
                        return "pong";
                    }
                }

                @RestController
                public class HealthController {

                    @GetMapping
                    public String root() {
                        return "ok";
                    }

                    @PostMapping("/save")
                    public String save(@RequestBody(required = false) String body) {
                        return "saved";
                    }
                }
                """;

        List<ApiDefinition> defs = SCANNER.scanSource(2L, source);

        // @Controller（非 Rest）类也应被识别
        ApiDefinition page = findBy(defs, "/page", "GET");
        assertEquals("com.example.demo2.HtmlController", page.getControllerClass());
        // @RequestMapping 未声明 method 时默认 GET
        findBy(defs, "/ping", "GET");
        // 类级无前缀 + 方法级无路径 → 根路径 "/"
        ApiDefinition root = findBy(defs, "/", "GET");
        assertEquals("com.example.demo2.HealthController", root.getControllerClass());
        // @RequestBody(required = false)
        ApiDefinition save = findBy(defs, "/save", "POST");
        JsonNode body = param(params(save), "body");
        assertEquals("BODY", body.get("source").asText());
        assertFalse(body.get("required").asBoolean(), "required=false 应被解析");
        assertEquals("String", body.get("type").asText());
    }

    // ====================================================================
    // 场景二：临时目录扫描
    // ====================================================================

    @Test
    void scanTempDir_shouldResolveRelativePathsAndCrossFileDtos(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(src.resolve("api"));
        Files.createDirectories(src.resolve("dto"));
        Files.createDirectories(src.resolve("config"));

        Files.writeString(src.resolve("api/UserController.java"), """
                package com.example.api;

                import org.springframework.web.bind.annotation.*;
                import jakarta.validation.Valid;
                import com.example.dto.UserSaveDTO;

                @RestController
                @RequestMapping("/user")
                public class UserController {

                    @GetMapping("/info")
                    public HttpResult<UserVO> info() {
                        return null;
                    }

                    @PostMapping("/save")
                    public HttpResult<UserVO> save(@Valid @RequestBody UserSaveDTO dto) {
                        return null;
                    }
                }
                """);

        // DTO 与 Controller 在不同包/文件中：验证跨文件类型索引
        Files.writeString(src.resolve("dto/UserSaveDTO.java"), """
                package com.example.dto;

                import jakarta.validation.constraints.NotBlank;
                import jakarta.validation.constraints.Size;

                public class UserSaveDTO {

                    @NotBlank(message = "用户名不能为空")
                    private String username;

                    @Size(min = 6, max = 20, message = "密码长度必须在6-20之间")
                    private String password;

                    private Integer age;

                    public String getUsername() { return username; }
                    public void setUsername(String username) { this.username = username; }
                    public String getPassword() { return password; }
                    public void setPassword(String password) { this.password = password; }
                    public Integer getAge() { return age; }
                    public void setAge(Integer age) { this.age = age; }
                }
                """);

        // 非 Controller 类：应被忽略
        Files.writeString(src.resolve("config/AppConfig.java"), """
                package com.example.config;

                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class AppConfig {
                    // 非 Controller，不应被识别为接口
                }
                """);

        ScanResult result = new ApiScanner().scanDetailed(3L, tempDir);
        List<ApiDefinition> defs = result.apis();

        assertEquals(2, defs.size(), "应识别 2 个接口，AppConfig 不应被识别");
        ApiDefinition info = findBy(defs, "/user/info", "GET");
        ApiDefinition save = findBy(defs, "/user/save", "POST");

        // 相对路径（相对扫描根目录）与行号
        assertEquals("src/main/java/com/example/api/UserController.java", info.getFilePath());
        assertTrue(info.getLineNo() > 0);

        // 跨文件 DTO 解析（UserSaveDTO 在另一个文件/包中）
        JsonNode dto = param(params(save), "dto");
        assertEquals("com.example.dto.UserSaveDTO", dto.get("qualifiedType").asText());
        JsonNode fields = dto.get("fields");
        JsonNode username = field(fields, "username");
        assertTrue(hasValidation(username, "NotBlank"));
        assertEquals("用户名不能为空", validationMessage(username, "NotBlank"));
        JsonNode password = field(fields, "password");
        assertTrue(hasValidation(password, "Size"), "password 字段应带 @Size");
        assertEquals("6", password.get("validations").get(0).get("min").asText());
        assertEquals("20", password.get("validations").get(0).get("max").asText());
        field(fields, "age");
        // java.lang 类型推导
        assertEquals("java.lang.String", username.get("qualifiedType").asText());
    }

    @Test
    void scanTempDir_shouldNotAbortOnBrokenFile(@TempDir Path tempDir) throws Exception {
        // 语法错误的文件：不应中断整体扫描
        Files.writeString(tempDir.resolve("Broken.java"), "public class Broken { public void x( { }");

        Files.writeString(tempDir.resolve("OkController.java"), """
                package com.example;

                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.bind.annotation.GetMapping;

                @RestController
                public class OkController {

                    @GetMapping("/ok")
                    public String ok() {
                        return "ok";
                    }
                }
                """);

        ScanResult result = new ApiScanner().scanDetailed(4L, tempDir);

        assertFalse(result.apis().isEmpty(), "坏文件不应影响有效接口的识别");
        findBy(result.apis(), "/ok", "GET");
        assertFalse(result.warnings().isEmpty(), "坏文件应产生警告");
    }

    // ====================================================================
    // 断言辅助
    // ====================================================================

    private static ApiDefinition findBy(List<ApiDefinition> defs, String apiPath, String httpMethod) {
        return defs.stream()
                .filter(d -> apiPath.equals(d.getApiPath()) && httpMethod.equals(d.getHttpMethod()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到接口 " + httpMethod + " " + apiPath
                        + "，实际: " + defs.stream().map(d -> d.getHttpMethod() + " " + d.getApiPath()).toList()));
    }

    private static JsonNode params(ApiDefinition def) throws JsonProcessingException {
        return MAPPER.readTree(def.getParamSchemaJson()).get("params");
    }

    private static JsonNode param(JsonNode params, String name) {
        for (JsonNode p : params) {
            if (name.equals(p.get("name").asText())) {
                return p;
            }
        }
        throw new AssertionError("未找到参数: " + name + "，实际: " + params);
    }

    private static JsonNode field(JsonNode fields, String name) {
        for (JsonNode f : fields) {
            if (name.equals(f.get("name").asText())) {
                return f;
            }
        }
        throw new AssertionError("未找到 DTO 字段: " + name + "，实际: " + fields);
    }

    private static boolean hasValidation(JsonNode fieldNode, String annotation) {
        JsonNode validations = fieldNode.get("validations");
        if (validations == null) {
            return false;
        }
        for (JsonNode v : validations) {
            if (annotation.equals(v.get("annotation").asText())) {
                return true;
            }
        }
        return false;
    }

    private static String validationMessage(JsonNode fieldNode, String annotation) {
        JsonNode validations = fieldNode.get("validations");
        if (validations == null) {
            return null;
        }
        for (JsonNode v : validations) {
            if (annotation.equals(v.get("annotation").asText())) {
                return v.get("message").asText();
            }
        }
        return null;
    }
}
