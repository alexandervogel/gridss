package au.edu.wehi.idsv.debruijn.positional;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

import au.edu.wehi.idsv.DirectedEvidence;
import au.edu.wehi.idsv.SAMEvidenceSource;
import au.edu.wehi.idsv.TestHelper;


public class SupportNodeIteratorTest extends TestHelper {
	public static List<DirectedEvidence> scrp(int k, String sequence, int minFragSize, int maxFragSize) {
		SAMEvidenceSource ses = SES(minFragSize, maxFragSize);
		byte[] qual = new byte[sequence.length()];
		for (int i = 0; i < qual.length; i++) {
			qual[i] = (byte)i;
		}
		List<DirectedEvidence> input = new ArrayList<DirectedEvidence>();
		for (int i = 1; i <= 100; i++) {
			input.add(SCE(FWD, ses, withQual(qual, withSequence(sequence, Read(0, i, "1S4M6S")))));
			input.add(SCE(BWD, ses, withQual(qual, withSequence(sequence, Read(0, i, "1S4M6S")))));
			input.add(NRRP(ses, withQual(qual, withSequence(sequence, DP(0, i, "1S9M1S", false, 1, 1, "11M", true)))));
			input.add(NRRP(ses, withQual(qual, withSequence(sequence, DP(0, i, "1S9M1S", false, 1, 1, "11M", false)))));
		}
		Collections.sort(input, DirectedEvidence.ByStartEnd);
		return input;
	}
	@Test
	public void should_return_kmernodes_in_start_position_order() {
		int k = 4;
		List<DirectedEvidence> input = scrp(k, "ACGTTATACCG", 30, 60);
		List<KmerSupportNode> output = Lists.newArrayList(new SupportNodeIterator(k, input.iterator(), 60));
		assertTrue(KmerNodeUtil.ByStartPosition.isOrdered(output));
		assertEquals(100 * ( 10-3 + 5-3 + 11-3 + 11-3 ), output.size());
	}
}