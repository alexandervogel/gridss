package au.edu.wehi.idsv.sim;

import htsjdk.samtools.util.SequenceUtil;

import java.nio.charset.StandardCharsets;

import au.edu.wehi.idsv.BreakendDirection;
import au.edu.wehi.idsv.BreakendSummary;

public class Fragment {
	private int start;
	private int end;
	private String seq;
	private boolean negative;
	private int referenceIndex;
	public Fragment(int referenceIndex, int start, byte[] seq, boolean negative) {
		this.referenceIndex = referenceIndex;
		this.start = start;
		this.end = start + seq.length;
		this.seq = new String(seq, StandardCharsets.US_ASCII);
		this.negative = negative;
	}
	public String getSequence() {
		if (negative) {
			return SequenceUtil.reverseComplement(seq);
		} else {
			return seq;
		}
	}
	public BreakendSummary getStartBreakend() {
		return getBreakend(negative);
	}
	public BreakendSummary getEndBreakend() {
		return getBreakend(!negative);
	}
	private BreakendSummary getBreakend(boolean high) {
		if (high) {
			return new BreakendSummary(referenceIndex, BreakendDirection.Forward, end, end);
		} else {
			return new BreakendSummary(referenceIndex, BreakendDirection.Backward, start, start);	
		}
	}
}
