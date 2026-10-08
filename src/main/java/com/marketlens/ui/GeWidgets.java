package com.marketlens.ui;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.FontID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;

/**
 * Builders for widgets in the Grand Exchange's visual style. Sprites, sizes and offsets match
 * the GE interface (group 465) as the game builds it. Client thread only.
 */
@Singleton
public class GeWidgets
{
	/** Size of the GE frame, so a window built with {@link #frame} lines up with the GE exactly. */
	public static final int FRAME_WIDTH = 484;
	public static final int FRAME_HEIGHT = 304;

	private static final int CORNER_WIDTH = 25;
	private static final int CORNER_HEIGHT = 30;
	private static final int EDGE_THICKNESS = 36;
	private static final int EDGE_OVERHANG = 15;

	/** The small GE buttons (+1, +10, -5%...) are one 35x35 sprite. */
	public static final int SMALL_BUTTON_SIZE = 35;

	private static final int BACK_ARROW_WIDTH = 30;
	private static final int BACK_ARROW_HEIGHT = 23;
	/** Back arrow position in the GE frame, measured from the left and bottom edges. */
	private static final int BACK_ARROW_LEFT = 17;
	private static final int BACK_ARROW_BOTTOM = 47;
	private static final int BACK_ARROW_HOVER_OPACITY = 100;

	private final WidgetFactory widgets;

	@Inject
	GeWidgets(WidgetFactory widgets)
	{
		this.widgets = widgets;
	}

	/**
	 * The GE window frame: stone background, steel border, title divider and title text.
	 * Returns the title text widget.
	 */
	public Widget frame(Widget parent, int w, int h)
	{
		widgets.tiledSprite(parent, SpriteID.TRADEBACKING, 1, 1, w - 2, h - 2);
		Widget title = widgets.text(parent, "", FontID.BOLD_12, Palette.ORANGE, WidgetTextAlignment.CENTER, 6, 6, w - 12, 24);

		widgets.sprite(parent, SpriteID.Steelborder.TOP_LEFT, 0, 0, CORNER_WIDTH, CORNER_HEIGHT);
		widgets.sprite(parent, SpriteID.Steelborder.TOP_RIGHT, w - CORNER_WIDTH, 0, CORNER_WIDTH, CORNER_HEIGHT);
		widgets.sprite(parent, SpriteID.Steelborder.BOTTOM_LEFT, 0, h - CORNER_HEIGHT, CORNER_WIDTH, CORNER_HEIGHT);
		widgets.sprite(parent, SpriteID.Steelborder.BOTTOM_RIGHT, w - CORNER_WIDTH, h - CORNER_HEIGHT, CORNER_WIDTH, CORNER_HEIGHT);

		int edgeLength = h - 2 * CORNER_HEIGHT;
		int edgeWidth = w - 2 * CORNER_WIDTH;
		widgets.tiledSprite(parent, SpriteID.Miscgraphics.IRON_RIVETS_VERTICAL,
			-EDGE_OVERHANG, CORNER_HEIGHT, EDGE_THICKNESS, edgeLength);
		widgets.tiledSprite(parent, SpriteID.Steelborder2.EDGE_RIGHT,
			w - EDGE_THICKNESS + EDGE_OVERHANG, CORNER_HEIGHT, EDGE_THICKNESS, edgeLength);
		widgets.tiledSprite(parent, SpriteID.Steelborder2.EDGE_TOP,
			CORNER_WIDTH, -EDGE_OVERHANG, edgeWidth, EDGE_THICKNESS);
		widgets.tiledSprite(parent, SpriteID.Miscgraphics.IRON_RIVETS_HORIZONTAL,
			CORNER_WIDTH, h - EDGE_THICKNESS + EDGE_OVERHANG, edgeWidth, EDGE_THICKNESS);

		widgets.tiledSprite(parent, SpriteID.SteelborderDivider._0, 5, 14, w - 10, 26);
		return title;
	}

	/**
	 * An icon-only small GE button, like the guide-price button: the 35x35 GE button sprite
	 * with {@code icon} centred on it. Hovering shows the GE's hover sprite and the menu shows "action name".
	 */
	public Widget iconButton(Widget parent, MarketLensSprite icon, String name, String action, int x, int y, Runnable onClick)
	{
		Widget button = widgets.layer(parent, x, y, SMALL_BUTTON_SIZE, SMALL_BUTTON_SIZE);
		Widget background = widgets.sprite(button, SpriteID.GeIcons.BUTTON, 0, 0, SMALL_BUTTON_SIZE, SMALL_BUTTON_SIZE);
		widgets.sprite(button, icon.getSpriteId(), (SMALL_BUTTON_SIZE - icon.getWidth()) / 2,
			(SMALL_BUTTON_SIZE - icon.getHeight()) / 2, icon.getWidth(), icon.getHeight());

		makeClickable(button, name, action, onClick);
		button.setOnMouseOverListener((JavaScriptCallback) e -> background.setSpriteId(SpriteID.GeIcons.BUTTON_HOVERED));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> background.setSpriteId(SpriteID.GeIcons.BUTTON));
		return button;
	}

	/** The GE's bottom-left back arrow, placed where the GE places it inside a frame of height {@code frameHeight}. */
	public Widget backArrow(Widget parent, int frameHeight, Runnable onClick)
	{
		Widget arrow = widgets.sprite(parent, SpriteID.GE_BACKBUTTON,
			BACK_ARROW_LEFT, frameHeight - BACK_ARROW_BOTTOM, BACK_ARROW_WIDTH, BACK_ARROW_HEIGHT);
		makeClickable(arrow, null, "Back", onClick);
		arrow.setOnMouseOverListener((JavaScriptCallback) e -> arrow.setOpacity(BACK_ARROW_HOVER_OPACITY));
		arrow.setOnMouseLeaveListener((JavaScriptCallback) e -> arrow.setOpacity(0));
		return arrow;
	}

	private static void makeClickable(Widget widget, String name, String action, Runnable onClick)
	{
		if (name != null)
		{
			widget.setName("<col=ff9040>" + name + "</col>");
		}
		widget.setAction(0, action);
		widget.setHasListener(true);
		widget.setOnOpListener((JavaScriptCallback) e -> onClick.run());
	}
}
