package com.splitmanager.utils;

import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Transfer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles payment calculations and formatting for settlements between players.
 */
public class PaymentProcessor
{

	/**
	 * Compute direct payments as text instructions.
	 */
	public static List<String> computeDirectPayments(List<PlayerMetrics> data)
	{
		List<PlayerMetrics> receivers = new ArrayList<>();
		List<PlayerMetrics> payers = new ArrayList<>();

		for (PlayerMetrics pm : data)
		{
			if (pm.split > 0)
			{
				receivers.add(pm);
			}
			else if (pm.split < 0)
			{
				payers.add(pm);
			}
		}

		receivers.sort((a, b) -> Long.compare(b.split, a.split));
		payers.sort((a, b) -> Long.compare(Math.abs(b.split), Math.abs(a.split)));

		List<String> lines = new ArrayList<>();
		DecimalFormat df = Formats.getDecimalFormat();

		int i = 0, j = 0;
		long recvLeft = receivers.isEmpty() ? 0 : receivers.get(0).split;
		long payLeft = payers.isEmpty() ? 0 : -payers.get(0).split;
		while (i < receivers.size() && j < payers.size())
		{
			long amt = Math.min(recvLeft, payLeft);
			if (amt > 0)
			{
				String from = payers.get(j).player;
				String to = receivers.get(i).player;
				lines.add(from + " -> " + to + ": " + df.format(amt));
				recvLeft -= amt;
				payLeft -= amt;
			}
			if (recvLeft == 0)
			{
				i++;
				if (i < receivers.size())
				{
					recvLeft = receivers.get(i).split;
				}
			}
			if (payLeft == 0)
			{
				j++;
				if (j < payers.size())
				{
					payLeft = -payers.get(j).split;
				}
			}
		}

		return lines;
	}

	/**
	 * Compute direct payments as structured Transfer objects.
	 */
	public static List<Transfer> computeDirectPaymentsStructured(List<PlayerMetrics> data)
	{
		List<PlayerMetrics> receivers = new ArrayList<>();
		List<PlayerMetrics> payers = new ArrayList<>();
		for (PlayerMetrics pm : data)
		{
			if (pm.split > 0)
			{
				receivers.add(pm);
			}
			else if (pm.split < 0)
			{
				payers.add(pm);
			}
		}
		receivers.sort((a, b) -> Long.compare(b.split, a.split));
		payers.sort((a, b) -> Long.compare(Math.abs(b.split), Math.abs(a.split)));

		List<Transfer> out = new ArrayList<>();
		int i = 0, j = 0;
		long recvLeft = receivers.isEmpty() ? 0 : receivers.get(0).split;
		long payLeft = payers.isEmpty() ? 0 : -payers.get(0).split;

		while (i < receivers.size() && j < payers.size())
		{
			long amt = Math.min(recvLeft, payLeft);
			if (amt > 0)
			{
				String from = payers.get(j).player;
				String to = receivers.get(i).player;
				out.add(new Transfer(from, to, amt));
				recvLeft -= amt;
				payLeft -= amt;
			}
			if (recvLeft == 0)
			{
				i++;
				if (i < receivers.size())
				{
					recvLeft = receivers.get(i).split;
				}
			}
			if (payLeft == 0)
			{
				j++;
				if (j < payers.size())
				{
					payLeft = -payers.get(j).split;
				}
			}
		}
		return out;
	}

}
