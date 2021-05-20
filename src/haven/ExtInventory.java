package haven;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ExtInventory extends Widget implements DTarget {
    private static final int margin = UI.scale(5);
    private static final int listw = UI.scale(125);
    private static final int itemh = UI.scale(20);
    private static final Color even = new Color(255, 255, 255, 16);
    private static final Color odd = new Color(255, 255, 255, 32);
    private static final Color found = new Color(255, 255, 0, 32);
    private static final String CFG_GROUP = "ext.group";
    private static final String CFG_SHOW = "ext.show";
    public final Inventory inv;
    private final ItemGroupList list;
    private final Widget extension;
    private SortedMap<ItemType, List<GItem>> groups;
    private final Dropbox<Grouping> grouping;
    private boolean disabled = false;
    private WindowX wnd;
    private final ACheckBox chb_show = new ICheckBox("gfx/hud/btn-extlist", "", "-d", "-h")
	.changed(this::setVisibility);
    
    public ExtInventory(Coord sz) {
	inv = new Inventory(sz);
	extension = new Widget();
	Composer composer = new Composer(extension).hmrgn(margin).vmrgn(margin);
	grouping = new Dropbox<Grouping>(UI.scale(75), 5, UI.scale(16)) {
	    {bgcolor = new Color(16, 16, 16, 128);}
	    
	    @Override
	    protected Grouping listitem(int i) {
		return Grouping.values()[i];
	    }
	    
	    @Override
	    protected int listitems() {
		return Grouping.values().length;
	    }
	    
	    @Override
	    protected void drawitem(GOut g, Grouping item, int i) {
		g.atext(item.name, UI.scale(3, 8), 0, 0.5);
	    }
	    
	    @Override
	    public void change(Grouping item) {
		if(item != sel && wnd != null) {
		    wnd.cfg.setValue(CFG_GROUP, item.name());
		    wnd.storeCfg();
		}
		super.change(item);
	    }
	};
	grouping.sel = Grouping.NONE;
	composer.addr(new Label("Group by:"), grouping);
	list = new ItemGroupList(listw, (inv.sz.y - composer.y() - margin) / itemh, itemh);
	composer.add(list);
	extension.pack();
	composer = new Composer(this).hmrgn(margin);
	composer.addr(inv, extension);
	pack();
    }
    
    public void hideExtension() {
	extension.hide();
	pack();
	if(wnd != null) {wnd.pack();}
    }
    
    public void showExtension() {
	extension.show();
	pack();
	if(wnd != null) {wnd.pack();}
    }
    
    public void disable() {
	hideExtension();
	disabled = true;
	chb_show.hide();
	if(wnd != null) {wnd.placetwdgs();}
    }
    
    @Override
    protected void added() {
	wnd = getparent(WindowX.class);
	if(wnd != null) {
	    boolean vis = !disabled && wnd.cfg.getValue(CFG_SHOW, false);
	    if(!disabled) {
		chb_show.a = vis;
		wnd.addtwdg(wnd.add(chb_show).settip("Toggle extra info"));
		grouping.sel = Grouping.valueOf(wnd.cfg.getValue(CFG_GROUP, Grouping.NONE.name()));
	    }
	    if(!vis) {
		hideExtension();
	    }
	}
    }
    
    private void setVisibility(boolean v) {
	if(wnd != null) {
	    wnd.cfg.setValue(CFG_SHOW, v);
	    wnd.storeCfg();
	}
	if(v) {
	    showExtension();
	} else {
	    hideExtension();
	}
    }
    
    @Override
    public boolean drop(Coord cc, Coord ul) {
	return (inv.drop(cc, ul));
    }
    
    @Override
    public boolean iteminteract(Coord cc, Coord ul) {
	return (inv.iteminteract(cc, ul));
    }

    @Override
    public void addchild(Widget child, Object... args) {
	inv.addchild(child, args);
    }

    @Override
    public void cdestroy(Widget w) {
	super.cdestroy(w);
	inv.cdestroy(w);
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == inv) {
	    super.wdgmsg(this, msg, args);
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }

    @Override
    public void uimsg(String msg, Object... args) {
	if(msg.equals("sz") || msg.equals("mode")) {
	    int szx = inv.sz.x;
	    int szy = inv.sz.y;
	    inv.uimsg(msg, args);
	    if((szx != inv.sz.x) || (szy != inv.sz.y)) {
		extension.move(new Coord(inv.c.x + inv.sz.x + margin, extension.c.y));
		list.resize(new Coord(list.sz.x, list.sz.y + inv.sz.y - szy));
		extension.pack();
		pack();
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
	super.mousewheel(c, amount);
	return(true);
    }

    @Override
    public void tick(double dt) {
	SortedMap<ItemType, List<GItem>> groups = new TreeMap<>();
	inv.forEachItem((g, w) -> {
	    try {
		Double quality = quality(g, grouping.sel);
		groups.computeIfAbsent(new ItemType(name(g), quality), k -> new ArrayList<>()).add(g);
	    } catch (Loading ignored) {
	    }
	});
	this.groups = groups;
	super.tick(dt);
    }

    private static String name(GItem item) {
	String name = "???";
	for(ItemInfo v : item.info()) {
	    if(v instanceof ItemInfo.Name) {
		name = ((ItemInfo.Name) v).str.text;
		break;
	    }
	}
	ItemInfo.Contents contents = findcontents(item.info());
	if(contents != null) {
	    String contentName = findname(contents.sub);
	    if(contentName != null) {
		name += " " + contentName;
	    }
	}
	return (name);
    }
    
    private static Double quality(GItem item) {
	return quality(item, Grouping.Q);
    }
    
    private static Double quality(GItem item, Grouping g) {
	if(g == null || g == Grouping.NONE) {return null;}
	try {
	    ItemInfo.Contents contents = findcontents(item.info());
	    if(contents != null) {
		Double quality = findquality(contents.sub);
		if(quality != null) {
		    return quantifyQ(quality, g);
		}
	    }
	    return quantifyQ(findquality(item.info()), g);
	} catch (NoSuchFieldException | IllegalAccessException e) {
	    e.printStackTrace();
	}
	return (null);
    }
    
    private static Double quantifyQ(Double q, Grouping g) {
	if(q == null) {return null;}
	if(g == Grouping.Q1) {
	    q = Math.floor(q);
	} else if(g == Grouping.Q5) {
	    q = Math.floor(q);
	    q -= q % 5;
	} else if(g == Grouping.Q10) {
	    q = Math.floor(q);
	    q -= q % 10;
	}
	return q;
    }
    
    private static String findname(List<ItemInfo> info) {
	for (ItemInfo v : info) {
	    if(v instanceof ItemInfo.Name) {
		return (((ItemInfo.Name) v).str.text);
	    }
	}
	return (null);
    }
    
    private static Double findquality(List<ItemInfo> info) throws NoSuchFieldException, IllegalAccessException {
	for(ItemInfo v : info) {
	    if(v.getClass().getName().equals("Quality")) {
		return((Double) v.getClass().getField("q").get(v));
	    }
	}
	return(null);
    }

    private static ItemInfo.Contents findcontents(List<ItemInfo> info) {
	for(ItemInfo v : info) {
	    if(v instanceof ItemInfo.Contents) {
		return((ItemInfo.Contents) v);
	    }
	}
	return(null);
    }

    private static class ItemType implements Comparable<ItemType> {
	final String name;
	final Double quality;

	public ItemType(String name, Double quality) {
	    this.name = name;
	    this.quality = quality;
	}

	@Override
	public int compareTo(ItemType other) {
	    int byName = name.compareTo(other.name);
	    if((byName != 0) || (quality == null) || (other.quality == null)) {
		return(byName);
	    }
	    return(-Double.compare(quality, other.quality));
	}
    }
    
    private static class ItemsGroup extends Widget {
	private static final BufferedImage def = WItem.missing.layer(Resource.imgc).img;
	private static final Text.Foundry foundry = new Text.Foundry(Text.sans, 12).aa(true);
	final ItemType type;
	final List<GItem> items;
	final WItem sample;
	private final Text.Line text;
	private final Grouping g;
	private Tex icon;
	
	public ItemsGroup(ItemType type, List<GItem> items, UI ui, Grouping g) {
	    super(new Coord(listw, itemh));
	    this.g = g;
	    this.ui = ui;
	    this.type = type;
	    this.items = items;
	    this.sample = new WItem(items.get(0));
	    double quality;
	    if(type.quality == null) {
		quality = items.stream().map(ExtInventory::quality).filter(Objects::nonNull).reduce(0.0, Double::sum)
		    / items.stream().map(ExtInventory::quality).filter(Objects::nonNull).count();
	    } else {
		quality = type.quality;
	    }
	    String format = (g == Grouping.NONE || g == Grouping.Q) ? "%sq%.1f (%d)" : "%sq%.0f+ (%d)";
	    this.text = foundry.render(String.format(format, type.quality != null ? "" : "avg ", quality, items.size()));
	}

	@Override
	public void draw(GOut g) {
	    if(icon == null) {
		try {
		    GSprite sprite = sample.item.sprite();
		    if(sprite instanceof GSprite.ImageSprite) {
			icon = GobIcon.SettingsWindow.Icon.tex(((GSprite.ImageSprite) sprite).image());
		    } else {
			Resource.Image image = sample.item.resource().layer(Resource.imgc);
			if(image == null) {
			    icon = GobIcon.SettingsWindow.Icon.tex(def);
			} else {
			    icon = GobIcon.SettingsWindow.Icon.tex(image.img);
			}
		    }
		} catch (Loading ignored) {
		}
	    }
	    if(icon != null) {
		g.aimage(icon, new Coord(0, itemh / 2), 0.0, 0.5);
		g.aimage(text.tex(), new Coord(icon.sz().x + margin, itemh / 2), 0.0, 0.5);
	    } else {
		g.aimage(text.tex(), new Coord(margin, itemh / 2), 0.0, 0.5);
	    }
	}

	@Override
	public boolean mousedown(Coord c, int button) {
	    if(button == 1) {
		if(ui.modshift) {
		    if(ui.modmeta) {
			for(GItem item : items) {
			    item.wdgmsg("transfer", Inventory.sqsz.div(2), 1);
			}
		    } else {
			items.get(0).wdgmsg("transfer", Inventory.sqsz.div(2), 1);
		    }
		} else if(ui.modctrl) {
		    if(ui.modmeta) {
			for(GItem item : items) {
			    item.wdgmsg("drop", Inventory.sqsz.div(2), 1);
			}
		    } else {
			items.get(0).wdgmsg("drop", Inventory.sqsz.div(2), 1);
		    }
		} else {
		    items.get(0).wdgmsg("take", Inventory.sqsz.div(2));
		}
		return(true);
	    } else if(button == 3) {
		items.get(0).wdgmsg("iact", Inventory.sqsz.div(2), ui.modflags());
		return(true);
	    }
	    return(false);
	}

	@Override
	public Object tooltip(Coord c, Widget prev) {
	    return(sample.tooltip(c, prev));
	}
    }

    private class ItemGroupList extends Searchbox<ItemsGroup> {
	private List<ItemsGroup> groups = Collections.emptyList();

	public ItemGroupList(int w, int h, int itemh) {
	    super(w, h, itemh);
	}

	@Override
	protected boolean searchmatch(int idx, String text) {
	    return(groups.get(idx).type.name.toLowerCase().contains(text.toLowerCase()));
	}

	@Override
	protected ItemsGroup listitem(int i) {
	    return(groups.get(i));
	}

	@Override
	protected int listitems() {
	    return(groups.size());
	}

	@Override
	protected void drawitem(GOut g, ItemsGroup item, int i) {
	    if(soughtitem(i)) {
		g.chcolor(found);
		g.frect(Coord.z, g.sz());
	    }
	    g.chcolor(((i % 2) == 0) ? even : odd);
	    g.frect(Coord.z, g.sz());
	    g.chcolor();
	    item.draw(g);
	}

	@Override
	public void tick(double dt) {
	    if(ExtInventory.this.groups == null) {
		groups = Collections.emptyList();
	    } else {
		groups = ExtInventory.this.groups.entrySet().stream()
		    .map(v -> new ItemsGroup(v.getKey(), v.getValue(), ui, grouping.sel)).collect(Collectors.toList());
	    }
	    super.tick(dt);
	}
    
	@Override
	protected void drawbg(GOut g) {
	}
    
	@Override
	public Object tooltip(Coord c, Widget prev) {
	    int idx = idxat(c);
	    ItemsGroup item = (idx >= listitems()) ? null : listitem(idx);
	    if(item != null) {
		return item.tooltip(Coord.z, prev);
	    }
	    return super.tooltip(c, prev);
	}
    }
    
    public static Inventory inventory(Widget wdg) {
	if(wdg instanceof ExtInventory) {
	    return ((ExtInventory) wdg).inv;
	} else if(wdg instanceof Inventory) {
	    return (Inventory) wdg;
	} else {
	    return null;
	}
    }
    
    enum Grouping {
	NONE("Type"), Q("Quality"), Q1("Quality 1"), Q5("Quality 5"), Q10("Quality 10");
	
	private final String name;
	
	Grouping(String name) {this.name = name;}
    }
}
