package com.marketlens.chart;

import java.util.Arrays;

/** Positions axis labels (like price tags) so they never overlap. */
public final class LabelLayout
{
	private LabelLayout()
	{
	}

	/**
	 * Moves label centres apart so labels of {@code height} px don't overlap, staying within [min, max].
	 * Labels keep their order; a label is only pushed down if it collides with the one above it,
	 * and the whole stack shifts up if that pushes it past {@code max}.
	 *
	 * @param centers desired vertical centres, any order
	 * @return adjusted centres, in the same order as {@code centers}
	 */
	public static int[] separate(int[] centers, int height, int min, int max)
	{
		Integer[] order = new Integer[centers.length];
		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Integer.compare(centers[a], centers[b]));

		int[] result = centers.clone();
		int half = height / 2;
		for (int k = 0; k < order.length; k++)
		{
			int i = order[k];
			int lowest = k == 0 ? min + half : result[order[k - 1]] + height;
			result[i] = Math.max(result[i], lowest);
		}
		for (int k = order.length - 1; k >= 0; k--)
		{
			int i = order[k];
			int highest = k == order.length - 1 ? max - half : result[order[k + 1]] - height;
			result[i] = Math.min(result[i], highest);
		}
		return result;
	}
}
