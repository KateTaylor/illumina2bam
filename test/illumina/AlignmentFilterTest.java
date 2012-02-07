
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
 *
 */
package illumina;

import java.util.TimeZone;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This is the test class for BamMerger
 * 
 * @author Guoying Qi
 */

public class AlignmentFilterTest {
    
    AlignmentFilter filter = new AlignmentFilter();

    public AlignmentFilterTest() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
    }
    /**
     * Test of instanceMain method and program record.
     */
    @Test
    public void testMain() throws FileNotFoundException, IOException {
        
        System.out.println("instanceMain");

        String[] args = {
            "IN=testdata/bam/986_1.sam",
            "IN=testdata/bam/986_1_human.sam",
            "OUT=testdata/986_1.bam",
            "OUT=testdata/986_1_human.bam",
            "CREATE_MD5_FILE=true",
            "TMP_DIR=testdata/",
            "VALIDATION_STRINGENCY=SILENT"
        };

        filter.instanceMain(args);
        assertEquals(
          filter.getCommandLine(),
          "illumina.AlignmentFilter INPUT_ALIGNMENT=[testdata/bam/986_1.sam, testdata/bam/986_1_human.sam] OUTPUT_ALIGNMENT=[testdata/986_1.bam, testdata/986_1_human.bam] TMP_DIR=[testdata] VALIDATION_STRINGENCY=SILENT CREATE_MD5_FILE=true    VERBOSITY=INFO QUIET=false COMPRESSION_LEVEL=5 MAX_RECORDS_IN_RAM=500000 CREATE_INDEX=false"
                    );
        
        File filteredBamFile = new File("testdata/986_1.bam");
        //filteredBamFile.deleteOnExit();

        File md5File = new File("testdata/986_1.bam.md5");
        md5File.deleteOnExit();
        BufferedReader md5Stream = new BufferedReader(new FileReader(md5File));
        String md5 = md5Stream.readLine();

        assertEquals(md5, "be1ccf2127fb718e7e49b1b9ae84cd19");
        
        File filteredHumanBamFile = new File("testdata/986_1_human.bam");
        //filteredHumanBamFile.deleteOnExit();

        File humanMd5File = new File("testdata/986_1_human.bam.md5");
        humanMd5File.deleteOnExit();
        BufferedReader humanMd5Stream = new BufferedReader(new FileReader(humanMd5File));
        String humanMd5 = humanMd5Stream.readLine();

        assertEquals(humanMd5, "6fb543f1c6bcc945ccb8eb579b53c70b");

    }
    /**
     * Test of instanceMain method and program record to separate unmapped reads into one file.
     */
    @Test
    public void testMainUnMappedFile() throws FileNotFoundException, IOException {
        
        System.out.println("instanceMain with unmapped file");

        String[] args = {
            "IN=testdata/bam/986_1.sam",
            "IN=testdata/bam/986_1_human_unmapped_with_ref.sam",
            "OUT=testdata/986_1.bam",
            "OUT=testdata/986_1_human.bam",
            "OUTPUT_UNALIGNED=testdata/986_1_unaligned.bam",
            "CREATE_MD5_FILE=true",
            "TMP_DIR=testdata/",
            "VALIDATION_STRINGENCY=SILENT"
        };

        filter.instanceMain(args);
        assertEquals(
          filter.getCommandLine(),
          "illumina.AlignmentFilter INPUT_ALIGNMENT=[testdata/bam/986_1.sam, testdata/bam/986_1_human_unmapped_with_ref.sam] OUTPUT_ALIGNMENT=[testdata/986_1.bam, testdata/986_1_human.bam] OUTPUT_UNALIGNED=testdata/986_1_unaligned.bam TMP_DIR=[testdata] VALIDATION_STRINGENCY=SILENT CREATE_MD5_FILE=true    VERBOSITY=INFO QUIET=false COMPRESSION_LEVEL=5 MAX_RECORDS_IN_RAM=500000 CREATE_INDEX=false");
        
        File filteredBamFile = new File("testdata/986_1.bam");
        filteredBamFile.deleteOnExit();

        File md5File = new File("testdata/986_1.bam.md5");
        md5File.deleteOnExit();
        BufferedReader md5Stream = new BufferedReader(new FileReader(md5File));
        String md5 = md5Stream.readLine();

        assertEquals(md5, "eeadf4a76bfd15528f10bcc575ca4132");
        
        File filteredHumanBamFile = new File("testdata/986_1_human.bam");
        filteredHumanBamFile.deleteOnExit();

        File humanMd5File = new File("testdata/986_1_human.bam.md5");
        humanMd5File.deleteOnExit();
        BufferedReader humanMd5Stream = new BufferedReader(new FileReader(humanMd5File));
        String humanMd5 = humanMd5Stream.readLine();

        assertEquals(humanMd5, "48c5f04d9624e4706f81b0df81e83bb6");
        
        File unalignedBamFile = new File("testdata/986_1_unaligned.bam");
        unalignedBamFile.deleteOnExit();

        File unalignedMd5File = new File("testdata/986_1_unaligned.bam.md5");
        unalignedMd5File.deleteOnExit();
        BufferedReader unalignedMd5Stream = new BufferedReader(new FileReader(unalignedMd5File));
        String unalignedMd5 = unalignedMd5Stream.readLine();

        assertEquals(unalignedMd5, "2004eb37d54723f6d7e84eeb02965d71");

    }

}
