package com.splitmanager;

import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Transfer;
import com.splitmanager.utils.PaymentProcessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PaymentProcessorTest
{
	@Test
	public void testComputeDirectPaymentsEmpty()
	{
		List<PlayerMetrics> data = new ArrayList<>();
		List<String> results = PaymentProcessor.computeDirectPayments(data);
		assertTrue(results.isEmpty());

		List<Transfer> structured = PaymentProcessor.computeDirectPaymentsStructured(data);
		assertTrue(structured.isEmpty());
	}

	@Test
	public void testComputeDirectPaymentsSimple()
	{
		// P1 has split -100k (debt), P2 has split +100k (credit)
		PlayerMetrics m1 = new PlayerMetrics("P1", 200000L, -100000L, true);
		PlayerMetrics m2 = new PlayerMetrics("P2", 0L, 100000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2);

		List<String> results = PaymentProcessor.computeDirectPayments(data);
		assertEquals(1, results.size());
		// P1 is payer (split < 0), P2 is receiver (split > 0)
		assertEquals("P1 -> P2: 100,000", results.get(0));

		List<Transfer> structured = PaymentProcessor.computeDirectPaymentsStructured(data);
		assertEquals(1, structured.size());
		assertEquals("P1", structured.get(0).getFrom());
		assertEquals("P2", structured.get(0).getTo());
		assertEquals(100000L, structured.get(0).getAmount());
	}

	@Test
	public void testComputeDirectPaymentsOneReceiverMultiplePayers()
	{
		// P1: -300k (payer), P2: +100k (receiver), P3: +200k (receiver)
		PlayerMetrics m1 = new PlayerMetrics("P1", 450000L, -300000L, true);
		PlayerMetrics m2 = new PlayerMetrics("P2", 50000L, 100000L, true);
		PlayerMetrics m3 = new PlayerMetrics("P3", 0L, 200000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2, m3);

		List<String> results = PaymentProcessor.computeDirectPayments(data);
		// Sorted receivers by amount: P3 (+200k) then P2 (+100k)
		assertEquals(2, results.size());
		assertEquals("P1 -> P3: 200,000", results.get(0));
		assertEquals("P1 -> P2: 100,000", results.get(1));

		List<Transfer> structured = PaymentProcessor.computeDirectPaymentsStructured(data);
		assertEquals(2, structured.size());
		assertEquals("P1", structured.get(0).getFrom());
		assertEquals("P3", structured.get(0).getTo());
		assertEquals(200000L, structured.get(0).getAmount());
	}

	@Test
	public void testComputeDirectPaymentsMultipleReceiversOnePayer()
	{
		// P1: -100k (payer), P2: -200k (payer), P3: +300k (receiver)
		PlayerMetrics m1 = new PlayerMetrics("P1", 200000L, -100000L, true);
		PlayerMetrics m2 = new PlayerMetrics("P2", 300000L, -200000L, true);
		PlayerMetrics m3 = new PlayerMetrics("P3", 0L, 300000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2, m3);

		List<String> results = PaymentProcessor.computeDirectPayments(data);
		// Payers sorted by absolute debt: P2 (-200k) then P1 (-100k)
		assertEquals(2, results.size());
		assertEquals("P2 -> P3: 200,000", results.get(0));
		assertEquals("P1 -> P3: 100,000", results.get(1));

		List<Transfer> structured = PaymentProcessor.computeDirectPaymentsStructured(data);
		assertEquals(2, structured.size());
		assertEquals("P2", structured.get(0).getFrom());
		assertEquals("P3", structured.get(0).getTo());
		assertEquals(200000L, structured.get(0).getAmount());
	}

	@Test
	public void testComputeDirectPaymentsComplex()
	{
		// P1: -150k (debt), P2: -50k (debt), P3: +120k (credit), P4: +80k (credit)
		PlayerMetrics m1 = new PlayerMetrics("P1", 300000L, -150000L, true);
		PlayerMetrics m2 = new PlayerMetrics("P2", 200000L, -50000L, true);
		PlayerMetrics m3 = new PlayerMetrics("P3", 30000L, 120000L, true);
		PlayerMetrics m4 = new PlayerMetrics("P4", 70000L, 80000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2, m3, m4);

		List<String> results = PaymentProcessor.computeDirectPayments(data);
		
		// Sorted Payers: P1 (-150k), P2 (-50k)
		// Sorted Receivers: P3 (+120k), P4 (+80k)
		
		// 1. P1 pays P3: 120k (P3 finished, P1 left with 30k debt)
		// 2. P1 pays P4: 30k (P1 finished, P4 left with 50k credit)
		// 3. P2 pays P4: 50k (P4 finished, P2 finished)
		
		assertEquals(3, results.size());
		assertEquals("P1 -> P3: 120,000", results.get(0));
		assertEquals("P1 -> P4: 30,000", results.get(1));
		assertEquals("P2 -> P4: 50,000", results.get(2));

		List<Transfer> structured = PaymentProcessor.computeDirectPaymentsStructured(data);
		assertEquals(3, structured.size());
		assertEquals("P1", structured.get(0).getFrom());
		assertEquals("P3", structured.get(0).getTo());
		assertEquals(120000L, structured.get(0).getAmount());
	}

	@Test
	public void testComputeDirectPaymentsUnbalanced()
	{
		// In some scenarios (maybe during roster changes or rounding), splits might not perfectly sum to zero.
		// The processor should still handle it gracefully.
		// P1: -100k (payer), P2: +90k (receiver)
		PlayerMetrics m1 = new PlayerMetrics("P1", 200000L, -100000L, true);
		PlayerMetrics m2 = new PlayerMetrics("P2", 0L, 90000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2);

		List<String> results = PaymentProcessor.computeDirectPayments(data);
		assertEquals(1, results.size());
		assertEquals("P1 -> P2: 90,000", results.get(0));
	}

	@Test
	public void testComputeDirectPaymentsUserScenario()
	{
		// if p1 PlayerMetrics total is 200k and p2 is 0 then p1 made 200k and should pay 100k to p2
		// sessionAvg = 100k. p1 split = 100k - 200k = -100k. p2 split = 100k - 0 = 100k.
		PlayerMetrics m1 = new PlayerMetrics("p1", 200000L, -100000L, true);
		PlayerMetrics m2 = new PlayerMetrics("p2", 0L, 100000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2);

		List<String> results = PaymentProcessor.computeDirectPayments(data);
		assertEquals(1, results.size());
		assertEquals("p1 -> p2: 100,000", results.get(0));

		List<Transfer> structured = PaymentProcessor.computeDirectPaymentsStructured(data);
		assertEquals(1, structured.size());
		assertEquals("p1", structured.get(0).getFrom());
		assertEquals("p2", structured.get(0).getTo());
		assertEquals(100000L, structured.get(0).getAmount());
	}
}
