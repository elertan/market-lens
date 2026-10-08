package com.marketlens.ui;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.SpritePixels;
import net.runelite.client.util.ImageUtil;

/**
 * A half-size item icon, kept in one custom sprite slot. Item graphics in widgets are always 36x32,
 * so this renders the item, scales it down and registers it as a sprite. Client thread only.
 */
@Singleton
public class SmallItemSprite
{
	/** Negative so it never collides with sprites from the game cache. */
	public static final int SPRITE_ID = -5402;
	public static final int WIDTH = 18;
	public static final int HEIGHT = 16;

	private final Client client;
	private int itemId = -1;

	@Inject
	SmallItemSprite(Client client)
	{
		this.client = client;
	}

	/** Makes the sprite show {@code itemId}; does nothing if it already does. */
	public void show(int itemId)
	{
		if (itemId == this.itemId)
		{
			return;
		}

		SpritePixels item = client.createItemSprite(itemId, 1, 1, SpritePixels.DEFAULT_SHADOW_COLOR, 0, false,
			Constants.CLIENT_DEFAULT_ZOOM);
		if (item == null)
		{
			return;
		}

		BufferedImage small = ImageUtil.resizeImage(item.toBufferedImage(), WIDTH, HEIGHT);
		client.getSpriteOverrides().put(SPRITE_ID, ImageUtil.getImageSpritePixels(small, client));
		// Widgets cache their sprites by id; drop the cache so they pick up the new image.
		client.getWidgetSpriteCache().reset();
		this.itemId = itemId;
	}

	public void clear()
	{
		client.getSpriteOverrides().remove(SPRITE_ID);
		client.getWidgetSpriteCache().reset();
		itemId = -1;
	}
}
