package com.marketlens.price;

import lombok.Value;

/** One entry from /mapping. Only the fields the plugin uses. */
@Value
public class ItemMapping
{
	int id;
	String name;
	Integer limit;
}
