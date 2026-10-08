package com.marketlens.chart;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class LabelLayoutTest
{
	@Test
	public void farApartLabelsStayPut()
	{
		assertArrayEquals(new int[]{50, 100}, LabelLayout.separate(new int[]{50, 100}, 14, 0, 200));
	}

	@Test
	public void overlappingLabelIsPushedBelowTheOneAbove()
	{
		// buy tag at 100, sell tag at 104: sell moves to 100 + 14
		assertArrayEquals(new int[]{100, 114}, LabelLayout.separate(new int[]{100, 104}, 14, 0, 200));
	}

	@Test
	public void orderOfInputIsPreserved()
	{
		assertArrayEquals(new int[]{114, 100}, LabelLayout.separate(new int[]{104, 100}, 14, 0, 200));
	}

	@Test
	public void stackShiftsUpAtTheBottomEdge()
	{
		assertArrayEquals(new int[]{179, 193}, LabelLayout.separate(new int[]{196, 198}, 14, 0, 200));
	}
}
