package gridss.cmdline;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import au.edu.wehi.idsv.GenomicProcessingContext;
import au.edu.wehi.idsv.LinearGenomicCoordinate;
import au.edu.wehi.idsv.PaddedLinearGenomicCoordinate;
import au.edu.wehi.idsv.ReadPairConcordanceCalculator;
import au.edu.wehi.idsv.ReadPairConcordanceMethod;
import au.edu.wehi.idsv.bed.IntervalBed;
import gridss.analysis.InsertSizeDistribution;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.Log;
import picard.cmdline.Option;

/**
 * Base class used to transform a VCF breakpoint call set given the full evidence available.
 * 
 * 
 * @author Daniel Cameron
 *
 */
public abstract class ProcessStructuralVariantReadsCommandLineProgram extends ByReadNameSinglePassSamProgram {
	private static final Log log = Log.getInstance(ProcessStructuralVariantReadsCommandLineProgram.class);
	@Option(doc="Minimum indel size", optional=true)
    public int MIN_INDEL_SIZE = 1;
    @Option(doc="Minimum bases clipped", optional=true)
    public int MIN_CLIP_LENGTH = 1;
    @Option(doc="Include hard and soft clipped reads in output", optional=true)
    public boolean CLIPPED = true;
    @Option(doc="Include reads containing indels in output", optional=true)
    public boolean INDELS = true;
    @Option(doc="Include split reads in output", optional=true)
    public boolean SPLIT = true;
    @Option(doc="Include read pairs in which only one of the read is aligned to the reference.", optional=true)
    public boolean SINGLE_MAPPED_PAIRED = true;
    @Option(doc="Include read pairs that align do not align in the expected orientation within the expected fragment size distribution.", optional=true)
    public boolean DISCORDANT_READ_PAIRS = true;
    @Option(doc="Method of calculating read pair concordance. Valid values are SAM_FLAG, PERCENTAGE, and FIXED", optional=true)
    public ReadPairConcordanceMethod READ_PAIR_CONCORDANCE_METHOD = ReadPairConcordanceMethod.SAM_FLAG;
    @Option(doc="Minimum concordant read pair fragment size if using the FIXED method of calculation", optional=true)
    public int FIXED_READ_PAIR_CONCORDANCE_MIN_FRAGMENT_SIZE = 0;
    @Option(doc="Maximum concordant read pair fragment size if using the FIXED method of calculation", optional=true)
    public int FIXED_READ_PAIR_CONCORDANCE_MAX_FRAGMENT_SIZE = 0;
    @Option(doc = "Percent (0.0-1.0) of read pairs considered concorant if using the PERCENTAGE method of calculation.", optional=true)
    public Float READ_PAIR_CONCORDANT_PERCENT = 0.995f;
    @Option(doc="Picard tools insert size distribution metrics txt file. Required if using the PERCENTAGE read pair concordance calculation method.", optional=true)
    public File INSERT_SIZE_METRICS = null;
    @Option(doc="Include unmapped reads", optional=true)
    public boolean UNMAPPED_READS = true;
    @Option(doc="Blacklist BED files for which reads/read pairs with any overlapping alignment or inferred breakpoint are excluded from extraction.", optional=true)
    public List<File> BLACKLIST;
    @Override
	protected String[] customCommandLineValidation() {
		if (READ_PAIR_CONCORDANCE_METHOD == ReadPairConcordanceMethod.PERCENTAGE && INSERT_SIZE_METRICS == null) {
			return new String[] { "INSERT_SIZE_METRICS is required when using percentage based read pair concordance" };
		}
		return super.customCommandLineValidation();
	}
    private IntervalBed blacklist = null;
    public IntervalBed getBlacklist(SAMSequenceDictionary dictionary) {
    	if (blacklist == null) {
    		assert(dictionary != null);
    		SAMSequenceDictionary dict = dictionary;
    		LinearGenomicCoordinate lgc = new PaddedLinearGenomicCoordinate(dictionary, GenomicProcessingContext.LINEAR_COORDINATE_CHROMOSOME_BUFFER);
    		blacklist = new IntervalBed(dict, lgc);
    		if (BLACKLIST != null) {
    			blacklist = IntervalBed.merge(dict, lgc, BLACKLIST.stream()
    					.map(file -> { try { return new IntervalBed(dict, lgc, file); } catch (IOException e) { throw new RuntimeException(e); } } )
    					.collect(Collectors.toList()));
    		}
    	}
    	return blacklist;
    }
    public void setBlacklist(IntervalBed bl) {
    	blacklist = bl;
    }
    /**
     * Determines whether any read alignment or potential breakend position overlaps any blacklisted region.
     * @param records records from the same fragment
     * @param lookup
     * @return
     */
    public boolean involvesBlacklistedRegion(final List<SAMRecord> records) {
    	if (records == null || records.size() == 0) return false;
    	IntervalBed bl = getBlacklist(records.get(0).getHeader().getSequenceDictionary());
    	for (SAMRecord r : records) {
    		if (r.getReadUnmappedFlag()) continue;
    		int start = Math.min(r.getUnclippedStart(), r.getUnclippedEnd() - r.getReadLength() + 1);
    		int end = Math.max(r.getUnclippedEnd(), r.getUnclippedStart() + r.getReadLength() - 1);
    		if (r.getReadPairedFlag()) {
	    		if ((r.getFirstOfPairFlag() && !r.getReadNegativeStrandFlag()) || 
	    				r.getSecondOfPairFlag() && r.getReadNegativeStrandFlag()) {
	    			end += getReadPairConcordanceCalculator().maxConcordantFragmentSize() - r.getReadLength();
	    		}
	    		if ((r.getFirstOfPairFlag() && r.getReadNegativeStrandFlag()) || 
	    				r.getSecondOfPairFlag() && !r.getReadNegativeStrandFlag()) {
	    			start -= getReadPairConcordanceCalculator().maxConcordantFragmentSize() - r.getReadLength();
	    		}
    		}
    		// Check for potential read overlap
    		if (bl.overlaps(r.getReferenceIndex(), start, end)) {
    			return true;
    		}
    	}
    	return false;
    }
    private ReadPairConcordanceCalculator rpcc = null;
    public ReadPairConcordanceCalculator getReadPairConcordanceCalculator() {
    	if (rpcc == null) {
    		// Read metrics file
    		InsertSizeDistribution isd = null;
    		if (INSERT_SIZE_METRICS != null) {
    			if (!INSERT_SIZE_METRICS.exists()) {
    				log.warn("Missing " + INSERT_SIZE_METRICS);
    			} else {
    				isd = InsertSizeDistribution.create(INSERT_SIZE_METRICS);
    			}
    		}
    		rpcc = ReadPairConcordanceCalculator.create(
    				READ_PAIR_CONCORDANCE_METHOD,
    				FIXED_READ_PAIR_CONCORDANCE_MIN_FRAGMENT_SIZE,
    				FIXED_READ_PAIR_CONCORDANCE_MAX_FRAGMENT_SIZE,
    				READ_PAIR_CONCORDANT_PERCENT,
    				isd,
    				null);
    	}
    	return rpcc;
    }
    public void copyInput(ProcessStructuralVariantReadsCommandLineProgram to) {
    	super.copyInput(to);
    	to.MIN_INDEL_SIZE = MIN_INDEL_SIZE;
    	to.MIN_CLIP_LENGTH = MIN_CLIP_LENGTH;
    	to.CLIPPED = CLIPPED;
    	to.INDELS = INDELS;
    	to.SPLIT = SPLIT;
    	to.SINGLE_MAPPED_PAIRED = SINGLE_MAPPED_PAIRED;
    	to.DISCORDANT_READ_PAIRS = DISCORDANT_READ_PAIRS;
    	to.READ_PAIR_CONCORDANCE_METHOD = READ_PAIR_CONCORDANCE_METHOD;
    	to.FIXED_READ_PAIR_CONCORDANCE_MIN_FRAGMENT_SIZE = FIXED_READ_PAIR_CONCORDANCE_MIN_FRAGMENT_SIZE;
    	to.FIXED_READ_PAIR_CONCORDANCE_MAX_FRAGMENT_SIZE = FIXED_READ_PAIR_CONCORDANCE_MAX_FRAGMENT_SIZE;
    	to.READ_PAIR_CONCORDANT_PERCENT = READ_PAIR_CONCORDANT_PERCENT;
    	to.INSERT_SIZE_METRICS = INSERT_SIZE_METRICS;
    	to.UNMAPPED_READS = UNMAPPED_READS;
    }
    @Override
	public boolean referenceRequired() { return false; }
}