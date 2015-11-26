package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import haven.QualityList.SingleType;
import me.ender.Reflect;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class CFG<T> {
    public static final CFG<String> VERSION = new CFG<>("version", "");
    public static final CFG<Boolean> DISPLAY_KINNAMES = new CFG<>("display.kinnames", true);
    public static final CFG<Boolean> DISPLAY_FLAVOR = new CFG<>("display.flavor", true);
    public static final CFG<Boolean> DISPLAY_GOB_INFO = new CFG<>("display.gob_info", false);
    public static final CFG<Boolean> SHOW_GOB_PATH = new CFG<>("display.show_gob_path", false);
    public static final CFG<Boolean> SIMPLE_CROPS = new CFG<>("display.simple_crops", false);
    public static final CFG<Boolean> STORE_MAP = new CFG<>("general.storemap", false);

    public static final CFG<Boolean> SHOW_CHAT_TIMESTAMP = new CFG<>("ui.chat.timestamp", true);
    public static final CFG<Boolean> STORE_CHAT_LOGS = new CFG<>("ui.chat.logs", false);
    public static final CFG<Boolean> LOCK_STUDY = new CFG<>("ui.lock_study", false);
    public static final CFG<Boolean> MMAP_FLOAT = new CFG<>("ui.mmapfloat", false);
    public static final CFG<Boolean> MMAP_VIEW = new CFG<>("ui.mmap_view", false);
    public static final CFG<Boolean> MMAP_GRID = new CFG<>("ui.mmap_grid", false);
    public static final CFG<Boolean> MMAP_SHOW_BIOMES = new CFG<>("ui.mmap_biomes", true);
    public static final CFG<Boolean> MENU_SINGLE_CTRL_CLICK = new CFG<>("ui.menu_single_ctrl_click", true);

    public static final CFG<Boolean> SHOW_ITEM_DURABILITY = new CFG<>("ui.item_durability", false);
    public static final CFG<Boolean> SHOW_ITEM_WEAR_BAR = new CFG<>("ui.item_wear_bar", true);
    public static final CFG<Boolean> SHOW_ITEM_ARMOR = new CFG<>("ui.item_armor", false);
    public static final CFG<Boolean> SWAP_NUM_AND_Q = new CFG<>("ui.swap_num_and_q", false);
    public static final CFG<Boolean> PROGRESS_NUMBER = new CFG<>("ui.progress_number", false);
    public static final CFG<Boolean> FEP_METER = new CFG<>("ui.fep_meter", false);
    public static final CFG<Boolean> HUNGER_METER = new CFG<>("ui.hunger_meter", false);

    public static final CFG<Float> CAMERA_BRIGHT = new CFG<>("camera.bright", 0f);

    public static final CFG<Boolean> Q_SHOW_ALL_SHIFT = new CFG<>("ui.q.allmods_shift", true);
    public static final CFG<Boolean> Q_SHOW_ALL_ALT = new CFG<>("ui.q.allmods_alt", true);
    public static final CFG<Boolean> Q_SHOW_ALL_CTRL = new CFG<>("ui.q.allmods_ctrl", true);
    public static final CFG<Boolean> Q_SHOW_SINGLE = new CFG<>("ui.q.showsingle", true);
    public static final CFG<SingleType> Q_SINGLE_TYPE = new CFG<>("ui.q.singletype", SingleType.Average);

    private static final String CONFIG_JSON = "config.json";
    private static final Map<Object, Object> cfg;
    private static final Map<String, Object> cache = new HashMap<>();
    private static final Gson gson;
    private final String path;
    public final T def;
    private Observer<T> observer;

    static {
	gson = (new GsonBuilder()).setPrettyPrinting().create();
	Map<Object, Object> tmp = null;
	try {
	    Type type = new TypeToken<Map<Object, Object>>() {
	    }.getType();
	    tmp = gson.fromJson(Config.loadFile(CONFIG_JSON), type);
	} catch (Exception ignored) {
	}
	if(tmp == null) {
	    tmp = new HashMap<>();
	}
	cfg = tmp;
    }

    public interface Observer<T> {
	void updated(CFG<T> cfg);
    }

    CFG(String path, T def) {
	this.path = path;
	this.def = def;
    }

    public T get() {
	return CFG.get(this);
    }

    public void set(T value) {
	CFG.set(this, value);
	if(observer != null) {
	    observer.updated(this);
	}
    }

    public void set(T value, boolean observe) {
	set(value);
	if(observe && observer != null) {
	    observer.updated(this);
	}
    }

    public void setObserver(Observer<T> observer) {
	this.observer = observer;
    }

    @SuppressWarnings("unchecked")
    public static synchronized <E> E get(CFG<E> name) {
	E value = name.def;
	try {
	    if(cache.containsKey(name.path)) {
		return (E) cache.get(name.path);
	    } else {
		if(name.path != null) {
		    Object data = retrieve(name);
		    Class<?> defClass = name.def.getClass();
		    if(defClass.isAssignableFrom(data.getClass())) {
			value = (E) data;
		    } else if(Number.class.isAssignableFrom(defClass)) {
			Number n = (Number) data;
			value = (E) defClass.getConstructor(String.class).newInstance(n.toString());
		    } else if(Enum.class.isAssignableFrom(defClass)) {
			Class<? extends Enum> enumType = Reflect.getEnumSuperclass(defClass);
			if(enumType != null) {
			    value = (E) Enum.valueOf(enumType, data.toString());
			}
		    }
		}
		cache.put(name.path, value);
	    }
	} catch (Exception ignored) {}
	return value;
    }

    @SuppressWarnings("unchecked")
    public static synchronized <E> void set(CFG<E> name, E value) {
	cache.put(name.path, value);
	if(name.path == null) {return;}
	String[] parts = name.path.split("\\.");
	int i;
	Object cur = cfg;
	for (i = 0; i < parts.length - 1; i++) {
	    String part = parts[i];
	    if(cur instanceof Map) {
		Map<Object, Object> map = (Map<Object, Object>) cur;
		if(map.containsKey(part)) {
		    cur = map.get(part);
		} else {
		    cur = new HashMap<String, Object>();
		    map.put(part, cur);
		}
	    }
	}
	if(cur instanceof Map) {
	    Map<Object, Object> map = (Map<Object, Object>) cur;
	    map.put(parts[parts.length - 1], value);
	}
	store();
    }

    private static synchronized void store() {
	Config.saveFile(CONFIG_JSON, gson.toJson(cfg));
    }

    private static Object retrieve(CFG name) {
	String[] parts = name.path.split("\\.");
	Object cur = cfg;
	for (String part : parts) {
	    if(cur instanceof Map) {
		Map map = (Map) cur;
		if(map.containsKey(part)) {
		    cur = map.get(part);
		} else {
		    return name.def;
		}
	    } else {
		return name.def;
	    }
	}
	return cur;
    }
}