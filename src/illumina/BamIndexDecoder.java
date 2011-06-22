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
 */

package illumina;

import net.sf.picard.cmdline.Option;
import net.sf.picard.cmdline.StandardOptionDefinitions;
import net.sf.picard.cmdline.Usage;
import net.sf.picard.io.IoUtil;
import net.sf.picard.metrics.MetricsFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.sf.picard.util.Log;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileWriter;
import net.sf.samtools.SAMFileWriterFactory;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;

/**
 * This class is used decode the multiplexed bam file.
 * 
 * Each read in BAM file will be marked in its read name and read group,
 * There is an option to output bam file by tag.
 * 
 * @author gq1@sanger.ac.uk
 * 
 */

public class BamIndexDecoder extends Illumina2bamCommandLine {
    
    private final Log log = Log.getInstance(BamIndexDecoder.class);
    
    private final String programName = "BamIndexDecoder";
    
    private final String programDS = "A command-line tool to decode multiplexed bam file";
   
    @Usage(programVersion= version)
    public final String USAGE = this.getStandardUsagePreamble() + this.programDS + ". ";
   
    @Option(shortName= StandardOptionDefinitions.INPUT_SHORT_NAME, doc="The input SAM or BAM file to decode.")
    public File INPUT;
    
    @Option(shortName=StandardOptionDefinitions.OUTPUT_SHORT_NAME, doc="The output file after decoding.", mutex = {"OUTPUT_DIR"} )
    public File OUTPUT;
    
    @Option(doc="The output directory for bam files for each barcode", mutex = {"OUTPUT"})
    public File OUTPUT_DIR;
    
    @Option(doc="The prefix for bam or sam file when you want to split output by barcodes", mutex = {"OUTPUT"})
    public String OUTPUT_PREFIX;
    
    @Option(doc="The extension name for bam or sam file when you want to split output by barcodes", mutex = {"OUTPUT"})
    public String OUTPUT_FORMAT;
    
    @Option(doc="The tag name to store barcode read in bam records")
    public String BARCODE_TAG_NAME = "RT";

    @Option(doc="Barcode sequence.  These must be unique, and all the same length.", mutex = {"BARCODE_FILE"})
    public List<String> BARCODE = new ArrayList<String>();

    @Option(doc="Tab-delimited file of barcode sequences, and optionally barcode name and library name.  " +
            "Barcodes must be unique, and all the same length.  Column headers must be 'barcode_sequence', " +
            "'barcode_name', and 'library_name'.", mutex = {"BARCODE"})
    public File BARCODE_FILE;

    @Option(doc="Per-barcode and per-lane metrics written to this file.", shortName = StandardOptionDefinitions.METRICS_FILE_SHORT_NAME)
    public File METRICS_FILE;

    @Option(doc="Maximum mismatches for a barcode to be considered a match.")
    public int MAX_MISMATCHES = 1;

    @Option(doc="Minimum difference between number of mismatches in the best and second best barcodes for a barcode to be considered a match.")
    public int MIN_MISMATCH_DELTA = 1;

    @Option(doc="Maximum allowable number of no-calls in a barcode read before it is considered unmatchable.")
    public int MAX_NO_CALLS = 2;

    private int barcodeLength;

    private IndexDecoder indexDecoder;
    
    private SAMFileWriter out;
    private HashMap<String, SAMFileWriter> outputList;
    private HashMap<String, String> barcodeNameList;
    
    public BamIndexDecoder() {
    }

    @Override
    protected int doWork() {
        
        this.log.info("Checking input and output file");
        IoUtil.assertFileIsReadable(INPUT);
        if(OUTPUT != null){
            IoUtil.assertFileIsWritable(OUTPUT);
        }
        if(OUTPUT_DIR != null){
            IoUtil.assertDirectoryIsWritable(OUTPUT_DIR);
        }
        IoUtil.assertFileIsWritable(METRICS_FILE);
        
        log.info("Open input file: " + INPUT.getName());
        final SAMFileReader in  = new SAMFileReader(INPUT);        
        final SAMFileHeader header = in.getFileHeader();
        
        this.generateOutputFile(header);
                
        log.info("Decoding records");        
        SAMRecordIterator inIterator = in.iterator();
        while(inIterator.hasNext()){
            
            String barcodeRead = null;

            SAMRecord record = inIterator.next();            
            String readName = record.getReadName();
            boolean isPaired = record.getReadPairedFlag();
            boolean isPf = ! record.getReadFailsVendorQualityCheckFlag();
            Object barcodeReadObject = record.getAttribute(this.BARCODE_TAG_NAME);
            if(barcodeReadObject != null){
                    barcodeRead = barcodeReadObject.toString();
            }
            
            SAMRecord pairedRecord = null;
            
            if(isPaired){
                
                pairedRecord = inIterator.next();
                String readName2 = pairedRecord.getReadName();
                boolean isPaired2 = pairedRecord.getReadPairedFlag();
                
                if( !readName.equals(readName2) || !isPaired2 ){
                    throw new RuntimeException("The paired reads are not together: " + readName + " " + readName2);
                }
                
                Object barcodeReadObject2= pairedRecord.getAttribute(this.BARCODE_TAG_NAME);
                if(barcodeReadObject != null
                        && barcodeReadObject2 != null
                        && ! barcodeReadObject.equals(barcodeReadObject2) ){
                    
                    throw new RuntimeException("barcode read bases are different in paired two reads: "
                            + barcodeReadObject + " " + barcodeReadObject2);
                } else if( barcodeRead == null && barcodeReadObject2 != null ){
                    barcodeRead = barcodeReadObject2.toString();
                }                
            }
            
            if(barcodeRead == null ){
                throw new RuntimeException("No barcode read found for record: " + readName );
            }
 
            IndexDecoder.BarcodeMatch match = this.indexDecoder.extractBarcode(barcodeRead, isPf);
            String barcode = match.barcode;
            barcode = barcode.toUpperCase();
            String barcodeName = this.barcodeNameList.get(barcode);

            record.setReadName(readName + "#" + barcodeName);
            record.setAttribute("RG", barcodeName);
            if (isPaired) {
                pairedRecord.setReadName(readName + "#" + barcodeName);
                pairedRecord.setAttribute("RG", barcodeName);
            }
            
            if( OUTPUT != null ){
                out.addAlignment(record);
                if(isPaired){
                    out.addAlignment(pairedRecord);
                }
            } else {
                
                SAMFileWriter outPerBarcode = this.outputList.get(barcode);
                outPerBarcode.addAlignment(record);
                if(isPaired){
                    outPerBarcode.addAlignment(pairedRecord);
                }                
            }
            
        }
        
        if(out != null){
           out.close();
        }
        this.closeOutputList();
        
        log.info("Decoding finished");
        
        
        log.info("Writing out metrhics file");        
        final MetricsFile<IndexDecoder.BarcodeMetric, Integer> metrics = getMetricsFile();        
        indexDecoder.writeMetrics(metrics, METRICS_FILE);
        
        log.info("All finished");

        return 0;
    }
    
    public void generateOutputFile(SAMFileHeader header) {

        if (OUTPUT != null) {
            log.info("Open output file with header: " + OUTPUT.getName());
            final SAMFileHeader outputHeader = header.clone();
            this.addProgramRecordToHead(outputHeader, this.getThisProgramRecord(programName, programDS));
            out = new SAMFileWriterFactory().makeSAMOrBAMWriter(outputHeader, true, OUTPUT);
        } else if (OUTPUT_DIR != null) {
            log.info("Open a list of output bam/sam file per barcode");
            outputList = new HashMap<String, SAMFileWriter>();
        }

        barcodeNameList = new HashMap<String, String>();

        List<IndexDecoder.NamedBarcode> barcodeList = indexDecoder.getNamedBarcodes();

        for (int count = 0; count <= barcodeList.size(); count++) {

            String barcodeName = null;
            String barcode = null;

            if ( count != 0 ) {
                IndexDecoder.NamedBarcode namedBarcode = barcodeList.get(count - 1);
                barcodeName = namedBarcode.barcodeName;
                barcode = namedBarcode.barcode;
            }

            if (barcodeName == null || barcodeName.equals("")) {
                barcodeName = Integer.toString(count);
            }

            if (barcode == null) {
                barcode = "";
            }
            barcode = barcode.toUpperCase();

            if (OUTPUT_DIR != null) {
                String barcodeBamOutputName = OUTPUT_DIR
                        + File.separator
                        + OUTPUT_PREFIX
                        + "#"
                        + barcodeName
                        + "."
                        + OUTPUT_FORMAT;
                final SAMFileHeader outputHeader = header.clone();
                this.addProgramRecordToHead(outputHeader, this.getThisProgramRecord(programName, programDS));
                final SAMFileWriter outPerBarcode = new SAMFileWriterFactory().makeSAMOrBAMWriter(outputHeader, true, new File(barcodeBamOutputName));
                outputList.put(barcode, outPerBarcode);
            }
            barcodeNameList.put(barcode, barcodeName);
        }
    }
    
    public void closeOutputList(){
        if( this.outputList != null ){
            for(SAMFileWriter writer: this.outputList.values()){
                writer.close();
            }
        }
    }

    /**
     *
     * @return null if command line is valid.  If command line is invalid, returns an array of error message
     *         to be written to the appropriate place.
     */
    @Override
    protected String[] customCommandLineValidation() {
        
        final ArrayList<String> messages = new ArrayList<String>();

        if (BARCODE_FILE != null) {
            this.indexDecoder = new IndexDecoder(BARCODE_FILE);
        } else {
            this.indexDecoder = new IndexDecoder(BARCODE);
        }
        
        indexDecoder.prepareDecode(messages);
        this.barcodeLength = indexDecoder.getBarcodeLength();

        if (messages.isEmpty()) {
            return null;
        }
        return messages.toArray(new String[messages.size()]);
    }

    public static void main(final String[] argv) {
        System.exit(new BamIndexDecoder().instanceMain(argv));
    }

}