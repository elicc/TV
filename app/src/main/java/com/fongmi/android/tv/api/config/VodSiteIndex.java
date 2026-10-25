package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Remembers which provider every site of a VOD config pointed at when that config last loaded.
 * History rows only carry a site key, and several configs often aggregate the same providers, so
 * this lets a row from another config open in the current one without loading that config.
 */
public final class VodSiteIndex {

    public static final String TAG = VodSiteIndex.class.getSimpleName();

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(8);
    // Object ext keys that only tell a spider where to fetch login credentials. An aggregating
    // subscription rewrites them to its own endpoint without changing what a vod id points at.
    private static final List<String> CREDENTIALS = List.of("Cloud-drive", "token", "cookie", "aliToken", "quarkCookie");

    private static final Map<Integer, Map<String, String>> cache = new HashMap<>();

    private VodSiteIndex() {
    }

    /** Whether {@code site} of the current config is the provider that {@code key} named in config {@code cid}. */
    public static boolean matches(int cid, String key, Site site) {
        if (site == null || site.isEmpty() || !key.equals(site.getKey())) return false;
        String fingerprint = read(cid).get(key);
        return fingerprint != null && fingerprint.equals(fingerprint(site));
    }

    /** Whether config {@code cid} was indexed; one never loaded since indexing began is not. */
    public static boolean has(int cid) {
        return !read(cid).isEmpty();
    }

    /** Indexes {@code config} from its JSON alone: no spider, rule or other global state is touched. */
    public static void build(Config config) throws Exception {
        String json = Decoder.getJson(OkHttp.client(TIMEOUT), UrlUtil.convert(config.getUrl()), TAG);
        JsonObject object = Json.parse(json).getAsJsonObject();
        // A depot or an error message has no sites of its own.
        if (!object.has("sites")) return;
        String spider = Json.safeString(object, "spider");
        save(config, Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).toList());
    }

    static void save(Config config, List<Site> sites) {
        if (config.getId() <= 0) return;
        Map<String, String> items = new HashMap<>();
        for (Site site : sites) items.put(site.getKey(), fingerprint(site));
        synchronized (cache) {
            if (items.equals(cache.get(config.getId()))) return;
            cache.put(config.getId(), items);
        }
        write(file(config.getId()), App.gson().toJson(items));
    }

    public static void delete(int cid) {
        synchronized (cache) {
            cache.remove(cid);
        }
        file(cid).delete();
    }

    private static Map<String, String> read(int cid) {
        synchronized (cache) {
            Map<String, String> items = cache.get(cid);
            if (items != null) return items;
            try {
                items = App.gson().fromJson(Path.read(file(cid)), TypeToken.getParameterized(Map.class, String.class, String.class).getType());
            } catch (Exception ignored) {
            }
            if (items == null) items = new HashMap<>();
            cache.put(cid, items);
            return items;
        }
    }

    /** A jar only defines csp_ spiders; its md5 suffix changes with every release of the same jar. */
    private static String fingerprint(Site site) {
        String jar = site.getApi().startsWith("csp_") ? site.getJar().split(";md5;")[0] : "";
        return Crypto.md5(site.getType() + "\n" + site.getApi() + "\n" + ext(site.getExt()) + "\n" + jar);
    }

    private static String ext(String ext) {
        if (!ext.startsWith("{")) return ext;
        try {
            JsonElement element = JsonParser.parseString(ext);
            if (!element.isJsonObject()) return ext;
            JsonObject object = element.getAsJsonObject();
            CREDENTIALS.forEach(object::remove);
            return object.toString();
        } catch (Exception e) {
            return ext;
        }
    }

    private static File file(int cid) {
        return new File(Path.files("vod_sites"), cid + ".json");
    }

    private static void write(File file, String json) {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
            if (!temp.renameTo(file)) temp.delete();
        } catch (Exception e) {
            temp.delete();
        }
    }
}
