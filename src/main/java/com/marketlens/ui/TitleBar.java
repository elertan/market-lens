package com.marketlens.ui;

import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;

/**
 * The window's title bar content: the small Market Lens icon and muted name on the left,
 * and the item icon and name centred. Client thread only.
 */
class TitleBar
{
	/** Vertical centre of the title text in the frame's title bar. */
	private static final int CENTER_Y = 18;
	private static final int ICON_GAP = 4;
	/** Left inset of the brand, where the GE shows its History button. */
	private static final int BRAND_LEFT = 12;

	private final Widget title;
	private final Widget itemIcon;

	/** @param title the frame's title text, from {@link GeWidgets#frame} */
	TitleBar(WidgetFactory widgets, Widget parent, Widget title)
	{
		this.title = title;
		itemIcon = widgets.sprite(parent, -1, 0, CENTER_Y - SmallItemSprite.HEIGHT / 2,
			SmallItemSprite.WIDTH, SmallItemSprite.HEIGHT);

		MarketLensSprite brand = MarketLensSprite.CHART_ICON_SMALL;
		widgets.sprite(parent, brand.getSpriteId(), BRAND_LEFT, CENTER_Y - brand.getHeight() / 2,
			brand.getWidth(), brand.getHeight());
		widgets.text(parent, "Market Lens", FontID.PLAIN_11, Palette.MUTED, WidgetTextAlignment.LEFT,
			BRAND_LEFT + brand.getWidth() + ICON_GAP, 6, 120, 24);
	}

	/** Shows the item name centred, with its icon just left of the text. */
	void show(String itemName, int itemSpriteId)
	{
		title.setText(itemName);
		FontTypeFace font = title.getFont();
		int textWidth = font != null ? font.getTextWidth(itemName) : itemName.length() * 8;
		int centerX = title.getOriginalX() + title.getOriginalWidth() / 2;
		itemIcon.setSpriteId(itemSpriteId);
		itemIcon.setOriginalX(centerX - textWidth / 2 - ICON_GAP - SmallItemSprite.WIDTH);
		itemIcon.revalidate();
	}
}
