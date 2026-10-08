package com.marketlens.ui;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.SpritePixels;
import net.runelite.client.util.ImageUtil;

/**
 * Half-size item icons as custom sprites. Item graphics in widgets are always 36x32, so this renders
 * the item, scales it down and registers it under a sprite id of its own. Each item gets a different id,
 * so the client's widget sprite cache never holds a stale image and never has to be reset.
 * Only the most recent item's sprite is kept. Client thread only.
 */
@Singleton
public class SmallItemSprite
{
	/** Item sprites use ids below this; negative so they never collide with sprites from the game cache. */
	private static final int BASE_SPRITE_ID = -100_000;
	public static final int WIDTH = 18;
	public static final int HEIGHT = 16;

	private final Client client;
	private int spriteId = -1;

	@Inject
	SmallItemSprite(Client client)
	{
		this.client = client;
	}

	/** Returns the sprite id showing {@code itemId} at half size, creating it on first use. */
	public int spriteFor(int itemId)
	{
		int id = BASE_SPRITE_ID - itemId;
		if (id == spriteId)
		{
			return id;
		}

		SpritePixels item = client.createItemSprite(itemId, 1, 1, SpritePixels.DEFAULT_SHADOW_COLOR, 0, false,
			Constants.CLIENT_DEFAULT_ZOOM);
		if (item == null)
		{
			return -1;
		}

		BufferedImage small = ImageUtil.resizeImage(item.toBufferedImage(), WIDTH, HEIGHT);
		client.getSpriteOverrides().put(id, ImageUtil.getImageSpritePixels(small, client));
		clear();
		spriteId = id;
		return id;
	}

	public void clear()
	{
		client.getSpriteOverrides().remove(spriteId);
		spriteId = -1;
	}
}
