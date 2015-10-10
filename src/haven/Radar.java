package haven;

import haven.RadarCFG.MarkerCFG;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Radar {
    public static final List<Marker> markers = new LinkedList<>();
    public static final List<Queued> queue = new LinkedList<>();
    private static final MarkerCFG DEFAULT = new DefMarker();
    public static final Comparator<Marker> MARKER_COMPARATOR = new Comparator<Marker>() {
	@Override
	public int compare(Marker o1, Marker o2) {
	    return o1.prio() - o2.prio();
	}
    };
    private static long lastsort = 0;

    public static void add(Gob gob, Indir<Resource> res) {
	if(gob.getattr(Marker.class) == null) {
	    synchronized(queue) {
		queue.add(new Queued(gob, res));
	    }
	}
    }

    public static void add(Gob gob) {
	Drawable drawable = gob.getattr(Drawable.class);
	Indir<Resource> res = null;
	if(drawable != null) {
	    if(drawable instanceof Composite) {
		res = ((Composite) drawable).base;
	    } else if(drawable instanceof ResDrawable) {
		res = ((ResDrawable) drawable).res;
	    }
	}
	if(res != null) {
	    add(gob, res);
	}
    }

    private static MarkerCFG cfg(String resname) {
	if(resname == null) {
	    return null;
	}
	for(RadarCFG.Group group : RadarCFG.groups) {
	    for(MarkerCFG cfg : group.markerCFGs) {
		if(cfg.match(resname)) {
		    return cfg;
		}
	    }
	}
	return DEFAULT;
    }

    public static void tick() {
	synchronized(queue) {
	    Iterator<Queued> iterator = queue.iterator();
	    while(iterator.hasNext()) {
		Queued queued = iterator.next();
		if(queued.ready()) {
		    MarkerCFG cfg = cfg(queued.resname());
		    if(cfg != null || queued.gob.getattr(GobIcon.class) != null) {
			Marker marker = new Marker(queued.gob, queued.resname());
			synchronized(markers) {
			    markers.add(marker);
			}
			queued.gob.setattr(marker);
		    }
		    iterator.remove();
		}
	    }
	}

	long now = System.currentTimeMillis();
	if(now - lastsort > 100) {
	    synchronized(markers) {
		Collections.sort(markers, MARKER_COMPARATOR);
		lastsort = now;
	    }
	}
    }

    public static void remove(Gob gob, boolean onlyDef) {
	if(gob != null) {
	    Marker marker = gob.getattr(Marker.class);
	    if(marker != null) {
		if(!onlyDef || marker.isDefault()) {
		    synchronized(markers) {
			markers.remove(marker);
		    }

		    gob.delattr(Marker.class);
		}
	    }
	    if(!onlyDef) {
		synchronized(queue) {
		    for(int i = 0; i < queue.size(); i++) {
			if(queue.get(i).gob == gob) {
			    queue.remove(i);
			    break;
			}
		    }
		}
	    }
	}
    }

    public static class Marker extends GAttrib {
	private final String resname;
	private MarkerCFG cfg;
	private Tex tex;

	public Marker(Gob gob, String res) {
	    super(gob);
	    this.resname = res;
	    cfg = cfg(resname);
	}

	public Tex tex() {
	    if(tex == null) {
		if(cfg == DEFAULT) {
		    GobIcon gi = gob.getattr(GobIcon.class);
		    if(gi != null) {
			tex = gi.tex();
		    }
		} else {
		    tex = cfg.tex();
		}
	    }
	    return tex;
	}

	public String tooltip() {
	    KinInfo ki = gob.getattr(KinInfo.class);
	    if(ki != null) {
		return ki.name;
	    } else if(cfg != null) {
		if(cfg.name != null) {
		    return cfg.name;
		} else {
		    return resname;
		}
	    }
	    return null;
	}

	public Color color() {
	    KinInfo ki = gob.getattr(KinInfo.class);
	    if(ki != null) {
		return BuddyWnd.gc[ki.group % BuddyWnd.gc.length];
//	    } else if(cfg != null && cfg.color != null) {
//		return cfg.color;
	    }
	    return Color.WHITE;
	}

	public boolean isDefault() {
	    return cfg == DEFAULT;
	}

	public int prio() {
	    return (tex == null) ? 0 : cfg.priority();
	}
    }

    public static class DefMarker extends RadarCFG.MarkerCFG {
	@Override
	public Tex tex() {
	    return null;
	}

	@Override
	public int priority() {
	    return 0;
	}
    }

    private static class Queued {
	public final Gob gob;
	public final Indir<Resource> res;

	public Queued(Gob gob, Indir<Resource> res) {
	    this.gob = gob;
	    this.res = res;
	}

	public boolean ready() {
	    boolean ready = true;
	    try {
		resname();
	    } catch(Loading e) {
		ready = false;
	    }
	    return ready;
	}

	public String resname() {
	    String name;
	    if(res instanceof Resource.Named) {
		name = ((Resource.Named) res).name;
	    } else {
		name = res.get().name;
	    }
	    return name;
	}
    }

}
