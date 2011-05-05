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
 * The class to process an illumina lane
 */

package illumina;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import net.sf.samtools.SAMFileHeader;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import net.sf.samtools.SAMFileWriter;
import net.sf.samtools.SAMFileWriterFactory;
import net.sf.samtools.SAMProgramRecord;
import net.sf.samtools.SAMReadGroupRecord;


/**
 *
 * @author Guoying Qi
 */
public class Lane {

    //fields must be given about input data
    private final String intensityDir;
    private final String baseCallDir;
    private final int laneNumber;

    //fields must be given about what to output
    private final boolean includeSecondCall;
    private final boolean pfFilter;

    //fields must be given for output bam
    private final File output;

    private SAMReadGroupRecord readGroup;

    //config xml file name and XML Documetns
    private final String baseCallsConfig;
    private final String intensityConfig;
    private Document baseCallsConfigDoc = null;
    private Document intensityConfigDoc = null;


    //read from config file
    private String id;
    private HashMap<String, int[]> cycleRangeByRead;
    private int [] tileList;
    private SAMProgramRecord baseCallProgram;
    private SAMProgramRecord instrumentProgram;

    //xpath
    private final XPath xpath;

    /**
     *
     * @param intensityDir
     * @param baseCallDir
     * @param laneNumber
     * @param secondCall
     * @param pfFilter
     */
    public Lane(String intensityDir,
                String baseCallDir,
                int laneNumber,
                boolean secondCall,
                boolean pfFilter,
                File output){

        this.intensityDir      = intensityDir;
        this.baseCallDir       = baseCallDir;
        this.laneNumber        = laneNumber;
        this.includeSecondCall = secondCall;
        this.pfFilter          = pfFilter;
        this.output            = output;

        this.baseCallsConfig = this.baseCallDir
                             + File.separator
                             + "config.xml";

        this.intensityConfig = this.intensityDir
                             + File.separator
                             + "config.xml";

        XPathFactory factory = XPathFactory.newInstance();
        xpath = factory.newXPath();

        this.initConfigsDoc();
    }

    /**
     *
     * @return true if successfully
     * @throws Exception
     */
    public boolean readConfigs() throws Exception{

        this.readBaseCallsConfig();
        this.readIntensityConfig();
        return true;
    }

    /**
     *
     * @return
     */
    public SAMFileWriter generateOutputSamStream(){

        SAMFileWriterFactory factory = new SAMFileWriterFactory();

        SAMFileHeader header = this.generateHeader();

        SAMFileWriter outputSam = factory.makeSAMOrBAMWriter(header, true, output);

        return outputSam;
    }

    /**
     *
     * @param outputSam
     * @return
     * @throws Exception
     */
    public boolean processTiles(SAMFileWriter outputSam) throws Exception{

        for(int tileNumber : this.tileList){
            
            Tile tile = new Tile(intensityDir, baseCallDir, id, laneNumber, tileNumber,
                                 cycleRangeByRead,
                                 this.includeSecondCall, this.pfFilter);
            tile.openBaseCallFiles();
            tile.processTile(outputSam);
            tile.closeBaseCallFiles();
        }

        return true;
    }

    /**
     * initial XML document
     */
    private void initConfigsDoc(){

        DocumentBuilderFactory dbf =
                DocumentBuilderFactory.newInstance();

        DocumentBuilder db = null;
        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            baseCallsConfigDoc = db.parse(new File(this.baseCallsConfig));
        } catch (SAXException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            intensityConfigDoc = db.parse(new File(this.intensityConfig));
        } catch (SAXException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
        }
   
    }

    /**
     * read base calls configure XML file
     * @throws Exception
     */
    private void readBaseCallsConfig() throws Exception {

        if (baseCallsConfigDoc == null) {
            throw new Exception("Problems to read baseCalls config file: " + this.baseCallsConfig);
        }

        //read basecall software name and version
        this.baseCallProgram = this.readBaseCallProgramRecord();
        if(baseCallProgram == null){
            throw new Exception("Problems to get base call software version from config file: " + this.baseCallsConfig);
        }

        //read tile list
        this.tileList = this.readTileList();
        if(tileList == null){
            throw new Exception("Problems to read tile list from config file:" + this.baseCallsConfig);
        }

        this.id = this.readInstrumentAndRunID();
        if(id == null){
            throw new Exception("Problems to read run id and instruament name from config file:" + this.baseCallsConfig);
        }

        this.cycleRangeByRead = this.checkCycleRangeByRead();

    }

    /**
     * read intensity configure XML file
     * @throws Exception
     */
    private void readIntensityConfig() throws Exception {

        if (intensityConfigDoc == null) {
            throw new Exception("Problems to read intensity config file: " + this.intensityConfig);
        }

        //read instrument software name and version
        this.instrumentProgram = this.readInstrumentProgramRecord();
        if(instrumentProgram == null){
            throw new Exception("Problems to get instrument software version from config file: " + this.intensityConfig);
        }

    }
    /**
     *
     * @return
     */
    public SAMProgramRecord readBaseCallProgramRecord (){

        Node nodeSoftware;
        try {
            XPathExpression exprBaseCallSoftware = xpath.compile("/BaseCallAnalysis/Run/Software");
            nodeSoftware = (Node) exprBaseCallSoftware.evaluate(baseCallsConfigDoc, XPathConstants.NODE);
        } catch (XPathExpressionException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

        NamedNodeMap nodeMapSoftware = nodeSoftware.getAttributes();
        String softwareName = nodeMapSoftware.getNamedItem("Name").getNodeValue();
        String softwareVersion = nodeMapSoftware.getNamedItem("Version").getNodeValue();

        SAMProgramRecord baseCallProgramConfig = new SAMProgramRecord("basecalling");
        baseCallProgramConfig.setProgramName(softwareName);
        baseCallProgramConfig.setProgramVersion(softwareVersion);
        baseCallProgramConfig.setAttribute("DS", "Basecalling Package");

        return baseCallProgramConfig;
    }

    /**
     *
     * @return
     */
    public int[] readTileList() {

        NodeList tilesForLane;
        try {
            XPathExpression exprLane = xpath.compile("/BaseCallAnalysis/Run/TileSelection/Lane[@Index=" + this.laneNumber + "]/Tile/text()");
            tilesForLane = (NodeList) exprLane.evaluate(baseCallsConfigDoc, XPathConstants.NODESET);
        } catch (XPathExpressionException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }       

        int[] tileListConfig = new int[tilesForLane.getLength()];
        for (int i = 0; i < tilesForLane.getLength(); i++) {
            Node tile = tilesForLane.item(i);
            tileListConfig[i] = Integer.parseInt(tile.getNodeValue());
        }
        
        //TODO: the order of tile numbers
        Arrays.sort(tileListConfig);

        //TODO: <Sample>s</Sample> used in filename?

        return tileListConfig;
    }

    /**
     *
     * @return
     */
    public String readInstrumentAndRunID(){

        Node nodeRunID;
        Node nodeInstrument;

        try {
            XPathExpression exprRunID = xpath.compile("/BaseCallAnalysis/Run/RunParameters/RunFolderId/text()");
            nodeRunID = (Node) exprRunID.evaluate(baseCallsConfigDoc, XPathConstants.NODE);

            XPathExpression exprInstrument = xpath.compile("/BaseCallAnalysis/Run/RunParameters/Instrument/text()");
            nodeInstrument = (Node) exprInstrument.evaluate(baseCallsConfigDoc, XPathConstants.NODE);
        } catch (XPathExpressionException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

        String runID = nodeRunID.getNodeValue();
        String instrument = nodeInstrument.getNodeValue();
        if(runID == null || instrument ==null){
            return null;
        }
        return instrument + "_" + runID;
    }

    /**
     *
     * @return
     * @throws Exception
     */
    public HashMap<String, int[]> checkCycleRangeByRead() throws Exception {
        HashMap<String, int[]> cycleRangeByReadMap = new HashMap<String, int[]>();

        int [][] cycleRangeByReadConfig = this.readCycleRangeByRead();
        int [] barCodeCycleList = this.readBarCodeIndexCycles();

        int numberOfReads = cycleRangeByReadConfig.length;

        if( (numberOfReads  > 3
                || numberOfReads <1
                ||(barCodeCycleList == null && numberOfReads >2))){
            throw new Exception("Problems with number of reads in config file: " + numberOfReads);
        }

        int countActualReads = 0;
        boolean indexReadFound = false;

        for(int i = 0; i < numberOfReads; i++){
            int firstCycle = cycleRangeByReadConfig[i][0];
            int lastCycle  = cycleRangeByReadConfig[i][1];
            int readLength = lastCycle - firstCycle + 1;
            if(barCodeCycleList != null
                    && firstCycle == barCodeCycleList[0]
                    && readLength == barCodeCycleList.length){
                
                indexReadFound = true;
                cycleRangeByReadMap.put("readIndex", cycleRangeByReadConfig[i]);
            }else{
                countActualReads++;
                cycleRangeByReadMap.put("read"+ countActualReads, cycleRangeByReadConfig[i]);
            }
        }

        if( !indexReadFound && barCodeCycleList != null ) {
            throw new Exception("Barcode cycle not found in read list");
        }

        return cycleRangeByReadMap;
    }

    /**
     *
     * @return
     */
    public int [][] readCycleRangeByRead(){

        int [][] cycleRangeByReadConfig = null;
        NodeList readList = null;
        try {
            XPathExpression exprReads = xpath.compile("/BaseCallAnalysis/Run/RunParameters/Reads");
            readList = (NodeList) exprReads.evaluate(baseCallsConfigDoc, XPathConstants.NODESET);

            cycleRangeByReadConfig = new int [readList.getLength()][2];

            for(int i = 0; i<readList.getLength(); i++){

                Node readNode = readList.item(i);

                int readIndex = Integer.parseInt(readNode.getAttributes().getNamedItem("Index").getNodeValue());
                readIndex--;

                XPathExpression exprFirstCycle= xpath.compile("FirstCycle/text()");
                Node firstCycleNode = (Node) exprFirstCycle.evaluate(readNode, XPathConstants.NODE);
                cycleRangeByReadConfig[readIndex][0] = Integer.parseInt(firstCycleNode.getNodeValue());

                XPathExpression exprLastCycle= xpath.compile("LastCycle/text()");
                Node lastCycleNode = (Node) exprLastCycle.evaluate(readNode, XPathConstants.NODE);
                cycleRangeByReadConfig[readIndex][1] = Integer.parseInt(lastCycleNode.getNodeValue());
            }

        } catch (XPathExpressionException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        return cycleRangeByReadConfig;
    }

    /**
     *
     * @return
     */
    public int [] readBarCodeIndexCycles(){
        
        int [] barCodeCycleList = null;
        try {
            XPathExpression exprBarCode = xpath.compile("/BaseCallAnalysis/Run/RunParameters/Barcode/Cycle/text()");
            NodeList barCodeNodeList = (NodeList) exprBarCode.evaluate(baseCallsConfigDoc, XPathConstants.NODESET);
            if(barCodeNodeList.getLength() == 0){
                return null;
            }
            barCodeCycleList = new int[barCodeNodeList.getLength()];
            for(int i=0; i<barCodeNodeList.getLength(); i++){
                barCodeCycleList[i] = Integer.parseInt(barCodeNodeList.item(i).getNodeValue());
            }
            Arrays.sort(barCodeCycleList);
        } catch (XPathExpressionException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
        }
        return barCodeCycleList;
    }

    /**
     * 
     * @return 
     */
    public SAMProgramRecord readInstrumentProgramRecord(){

        Node nodeSoftware;
        try {
            XPathExpression exprBaseCallSoftware = xpath.compile("/ImageAnalysis/Run/Software");
            nodeSoftware = (Node) exprBaseCallSoftware.evaluate(intensityConfigDoc, XPathConstants.NODE);
        } catch (XPathExpressionException ex) {
            Logger.getLogger(Lane.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

        NamedNodeMap nodeMapSoftware = nodeSoftware.getAttributes();
        String softwareName = nodeMapSoftware.getNamedItem("Name").getNodeValue();
        String softwareVersion = nodeMapSoftware.getNamedItem("Version").getNodeValue();

        SAMProgramRecord instrumentProgramConfig = new SAMProgramRecord("SCS");
        instrumentProgramConfig.setProgramName(softwareName);
        instrumentProgramConfig.setProgramVersion(softwareVersion);
        instrumentProgramConfig.setAttribute("DS", "Controlling software on instrument");

        return instrumentProgramConfig;
    }

    /**
     * 
     * @return
     */
    public SAMFileHeader generateHeader(){
         SAMFileHeader header = new SAMFileHeader();

         header.addProgramRecord(this.instrumentProgram);

         this.baseCallProgram.setPreviousProgramGroupId(this.instrumentProgram.getId());
         header.addProgramRecord(baseCallProgram);

         if(this.readGroup != null){
           header.addReadGroup(readGroup);
         }

         return header;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @param cycleRangeByRead the cycleRangeByRead to set
     */
    public void setCycleRangeByRead(HashMap<String, int[]> cycleRangeByRead) {
        this.cycleRangeByRead = cycleRangeByRead;
    }

    /**
     * @param tileList the tileList to set
     */
    public void setTileList(int[] tileList) {
        this.tileList = tileList;
    }

    /**
     * @param readGroup the readGroup to set
     */
    public void setReadGroup(SAMReadGroupRecord readGroup) {
        this.readGroup = readGroup;
    }
}
