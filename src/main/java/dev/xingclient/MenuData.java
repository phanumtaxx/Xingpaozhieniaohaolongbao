package dev.xingclient;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MenuData {
    public record Category(String name, List<String> modules, List<String> on) {}
    public static final List<Category> CATEGORIES;
    static {
        try (var stream = MenuData.class.getResourceAsStream("/assets/xingclient/modules.json")) {
            if (stream == null) throw new IllegalStateException("Missing module catalog");
            CATEGORIES = List.of(new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Category[].class));
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    public static String key(Category category, String name) { return category.name() + "." + name; }
    private MenuData() {}
}
