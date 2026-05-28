package com.splitmanager.models;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * Captures config values that affect settlement math for historical context.
 */
@Getter
@Setter
public class SettlementConfigSnapshot implements Serializable
{
	private static final long serialVersionUID = 1L;

	private boolean accountForGeTax;
	private String geTaxMinimumValue;
	private double geTaxPercent;
	private String geTaxMaxPerLoot;

	public SettlementConfigSnapshot(boolean accountForGeTax,
	                                String geTaxMinimumValue,
	                                double geTaxPercent,
	                                String geTaxMaxPerLoot)
	{
		this.accountForGeTax = accountForGeTax;
		this.geTaxMinimumValue = geTaxMinimumValue;
		this.geTaxPercent = geTaxPercent;
		this.geTaxMaxPerLoot = geTaxMaxPerLoot;
	}
}
