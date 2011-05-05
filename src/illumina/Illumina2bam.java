/*
 * Copyright (C) 2011 GRL
 *
 * This library is free software. You can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This is the main class to covert Illumina BCL file to BAM
 *
 */

package illumina;

import java.io.File;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.picard.cmdline.CommandLineProgram;
import net.sf.picard.cmdline.Option;
import net.sf.picard.cmdline.Usage;
import net.sf.samtools.SAMFileWriter;

import net.sf.picard.io.IoUtil;
import net.sf.samtools.SAMReadGroupRecord;

/**
 *
 * @author Guoying Qi
 */
public class Illumina2bam extends CommandLineProgram {
    
    @Usage public final String USAGE ="Covert Illumina BCL to BAM or SAM file";

    @Option(shortName="I", doc="Illumina intensities diretory including config xml file and clocs files under lane directory")
    public String INTENSITY_DIR;

    @Option(shortName="B", doc="Illumina basecalls diretory including config xml file, and filter files, bcl and scl files under lane cycle directory")
    public String BASECALLS_DIR;
    
    @Option(shortName="L", doc="Lane number")
    public Integer LANE;

    @Option(shortName="O", doc="Output file name")
    public File OUTPUT;

    @Option(shortName="E2", doc="Including second base call or not, default false", optional=true)
    public boolean GENERATE_SECONDARY_BASE_CALLS = false;

    @Option(shortName="PF", doc="Filter cluster or not, default true", optional=true)
    public boolean PF_FILTER = true;

    @Option(shortName="RG", doc="ID used to link RG header record with RG tag in SAM record, default 1", optional=true)
    public String READ_GROUP_ID = "1";

    @Option(shortName="SM", doc="The name of the sequenced sample", optional=true)
    public String SAMPLE_ALIAS;

    @Option(shortName="LB", doc="The name of the sequenced library", optional=true)
    public String LIBRARY_NAME;

    @Option(shortName="ST", doc="The name of the study", optional=true)
    public String STUDY_NAME;

    @Option(shortName="PU", doc="The platform unit", optional=true)
    public String PLATFORM_UNIT;

    @Option(doc="The start date of the run.", optional=true)
    public Date RUN_START_DATE;

    @Option(shortName="SC", doc="Sequence center name, default SC for Sanger Center", optional=true)
    public String SEQUENCING_CENTER = "SC";

    @Option(doc="The name of the sequencing technology that produced the read.", optional=true)
    public String PLATFORM = "ILLUMINA";

    @Option(doc="If set, this is the first tile to be processed (for debugging).  Note that tiles are not processed in numerical order.",
    optional = true)
    public Integer FIRST_TILE;
    
    @Option(doc="If set, process no more than this many tiles (for debugging).", optional=true)
    public Integer TILE_LIMIT;

    @Override
    protected int doWork() {

        IoUtil.assertFileIsWritable(OUTPUT);

        Lane lane = new Lane(this.INTENSITY_DIR, this.BASECALLS_DIR, this.LANE, this.GENERATE_SECONDARY_BASE_CALLS, this.PF_FILTER, OUTPUT);

        try {
            lane.readConfigs();
        } catch (Exception ex) {
            Logger.getLogger(Illumina2bam.class.getName()).log(Level.SEVERE, null, ex);
            return 1;
        }

        SAMFileWriter outBam = lane.generateOutputSamStream();
        try {
            lane.processTiles(outBam);
        } catch (Exception ex) {
            Logger.getLogger(Illumina2bam.class.getName()).log(Level.SEVERE, null, ex);
            return 1;
        }

        outBam.close();

        return 0;
    }


    /** Stock main method. */
    public static void main(final String[] args) {
        System.exit(new Illumina2bam().instanceMain(args));
    }
}
