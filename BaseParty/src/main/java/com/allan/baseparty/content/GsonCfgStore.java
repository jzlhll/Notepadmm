package com.allan.baseparty.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * JSON 配置存储：每个实例对应 ~/.atools_notepadmm/ 下一个独立的 json 文件，
 * 内存中持有 JsonObject（唯一事实源），懒加载；set 异步落盘（所有实例共享 daemon 单线程，
 * 每次写最新全量快照），remove/flush 同步落盘，文件写入原子替换。key 仅为顶层节点。
 */
public final class GsonCfgStore {
    private static final String ROOT_DIR_NAME = ".atools_notepadmm";
    private static final String FILE_SUFFIX = ".json";

    private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "gson-cfg-saver");
        t.setDaemon(true);
        return t;
    });

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path cfgFile;
    private static final Object LOCK = new Object();
    private static final Object WRITE_LOCK = new Object();

    private JsonObject root = new JsonObject();
    private boolean loaded = false;
    //脏标记：置位表示内存有未落盘修改；保存任务在加锁前 CAS 清零，清零后的新修改会重新置脏
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public GsonCfgStore(String cfgName) {
        if (cfgName == null || cfgName.isEmpty()
                || cfgName.indexOf('/') >= 0 || cfgName.indexOf('\\') >= 0
                || cfgName.indexOf(':') >= 0) {
            throw new IllegalArgumentException("illegal cfg name: " + cfgName);
        }

        Path dir = Path.of(System.getProperty("user.home"), ROOT_DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.out.println("GsonCfgStore: cannot create dir " + dir + " : " + e);
        }
        cfgFile = dir.resolve(cfgName + FILE_SUFFIX);
    }

    private void ensureLoadedLocked() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isReadable(cfgFile)) {
            return;
        }
        try {
            var content = Files.readString(cfgFile, StandardCharsets.UTF_8);
            if (!content.isBlank()) {
                var element = JsonParser.parseString(content);
                if (element.isJsonObject()) {
                    root = element.getAsJsonObject();
                }
            }
        } catch (Exception e) {
            System.out.println("GsonCfgStore: cannot read " + cfgFile + " : " + e);
        }
    }

    private void scheduleSaveLocked() {
        dirty.set(true);
        SAVER.execute(this::saveLatestSync);
    }

    private void saveLatestSync() {
        //无锁快速路径：干净的 store 直接跳过；先清脏再取快照，清零后到来的修改会重新置脏并再调度
        if (!dirty.compareAndSet(true, false)) {
            return;
        }
        final String snapshot;
        synchronized (LOCK) {
            snapshot = gson.toJson(root);
        }
        synchronized (WRITE_LOCK) {
            writeAtomic(snapshot);
        }
    }

    private void writeAtomic(String content) {
        try {
            var dir = cfgFile.getParent();
            var tmp = Files.createTempFile(dir, cfgFile.getFileName().toString(), ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, cfgFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, cfgFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.out.println("GsonCfgStore: write failed " + cfgFile + " : " + e);
        }
    }

    public void set(String key, Object value) {
        if (value == null) {
            remove(key);
            return;
        }
        var tree = gson.toJsonTree(value);
        synchronized (LOCK) {
            ensureLoadedLocked();
            root.add(key, tree);
            scheduleSaveLocked();
        }
    }

    public void setString(String key, String value) {
        set(key, value);
    }

    public void setBoolean(String key, boolean value) {
        set(key, value);
    }

    public void setInt(String key, int value) {
        set(key, value);
    }

    public void setLong(String key, long value) {
        set(key, value);
    }

    public void setFloat(String key, float value) {
        set(key, value);
    }

    public void setStringList(String key, List<String> value) {
        set(key, value);
    }

    public void setIntList(String key, List<Integer> value) {
        set(key, value);
    }

    public String getString(String key, String defValue) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            var el = root.get(key);
            if (isPrimitive(el, JsonPrimitive::isString)) {
                return el.getAsString();
            }
            return defValue;
        }
    }

    public boolean getBoolean(String key, boolean defValue) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            var el = root.get(key);
            if (isPrimitive(el, JsonPrimitive::isBoolean)) {
                return el.getAsBoolean();
            }
            return defValue;
        }
    }

    public int getInt(String key, int defValue) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            var el = root.get(key);
            if (isPrimitive(el, JsonPrimitive::isNumber)) {
                return el.getAsInt();
            }
            return defValue;
        }
    }

    public long getLong(String key, long defValue) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            var el = root.get(key);
            if (isPrimitive(el, JsonPrimitive::isNumber)) {
                return el.getAsLong();
            }
            return defValue;
        }
    }

    public float getFloat(String key, float defValue) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            var el = root.get(key);
            if (isPrimitive(el, JsonPrimitive::isNumber)) {
                return el.getAsFloat();
            }
            return defValue;
        }
    }

    public List<String> getStringList(String key, List<String> defValue) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            var el = root.get(key);
            if (el == null || !el.isJsonArray()) {
                return defValue;
            }
            var arr = el.getAsJsonArray();
            var result = new ArrayList<String>(arr.size());
            for (var item : arr) {
                if (!isPrimitive(item, JsonPrimitive::isString)) {
                    return defValue;
                }
                result.add(item.getAsString());
            }
            return result;
        }
    }

    public List<Integer> getIntList(String key, List<Integer> defValue) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            var el = root.get(key);
            if (el == null || !el.isJsonArray()) {
                return defValue;
            }
            var arr = el.getAsJsonArray();
            var result = new ArrayList<Integer>(arr.size());
            for (var item : arr) {
                if (!isPrimitive(item, JsonPrimitive::isNumber)) {
                    return defValue;
                }
                result.add(item.getAsInt());
            }
            return result;
        }
    }

    public void remove(String key) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            root.remove(key);
        }
        dirty.set(true);
        saveLatestSync();
    }

    public boolean contains(String key) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            return root.has(key);
        }
    }

    public Set<String> keys() {
        synchronized (LOCK) {
            ensureLoadedLocked();
            return new HashSet<>(root.keySet());
        }
    }

    //TypeToken/Class 重载用于约束泛型 T（裸 Type 无法让 defValue 正确推断），Type 版仅作内部实现
    public <T> T getObject(String key, TypeToken<T> typeToken, T defValue) {
        return getObject(key, typeToken.getType(), defValue);
    }

    public <T> T getObject(String key, Class<T> classOfT, T defValue) {
        return getObject(key, (Type) classOfT, defValue);
    }

    private <T> T getObject(String key, Type typeOfT, T defValue) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            var el = root.get(key);
            if (el == null || el.isJsonNull()) {
                return defValue;
            }
            try {
                //var 会把 gson 泛型推断成 Object，须显式声明 T 让三目表达式类型正确
                T value = gson.fromJson(el, typeOfT);
                return value != null ? value : defValue;
            } catch (Exception e) {
                System.out.println("GsonCfgStore: parse failed " + cfgFile + "#" + key + " : " + e);
                return defValue;
            }
        }
    }

    public void flush() {
        //干净 store 直接跳过：dirty 置位必经 set/remove（锁内已加载），此处无需 ensure、无锁零 IO
        if (!dirty.get()) {
            return;
        }
        saveLatestSync();
    }

    private static boolean isPrimitive(JsonElement el, Predicate<JsonPrimitive> check) {
        return el != null && el.isJsonPrimitive() && check.test(el.getAsJsonPrimitive());
    }
}
