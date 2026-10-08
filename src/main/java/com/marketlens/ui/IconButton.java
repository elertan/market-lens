package com.marketlens.ui;

import java.util.List;
import lombok.Getter;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;

/**
 * A small GE button with an icon, built by {@link GeWidgets#iconButton}. It brightens on hover and can be
 * marked as selected, for toggles: a selected button keeps the bright look.
 * Client thread only.
 */
public final class IconButton
{
	@Getter
	private final Widget widget;
	private final List<Widget> background;
	private final Widget icon;
	private boolean selected;
	private boolean hovered;

	IconButton(Widget widget, List<Widget> background, Widget icon)
	{
		this.widget = widget;
		this.background = background;
		this.icon = icon;
	}

	public void setSelected(boolean selected)
	{
		this.selected = selected;
		update();
	}

	void setHovered(boolean hovered)
	{
		this.hovered = hovered;
		update();
	}

	private void update()
	{
		int sprite = selected || hovered ? SpriteID.GeIcons.BUTTON_HOVERED : SpriteID.GeIcons.BUTTON;
		for (Widget part : background)
		{
			part.setSpriteId(sprite);
		}
	}
}
