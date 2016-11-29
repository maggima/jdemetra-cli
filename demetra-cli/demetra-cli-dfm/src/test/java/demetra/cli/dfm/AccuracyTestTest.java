/*
 * Copyright 2016 National Bank of Belgium
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package demetra.cli.dfm;

import be.nbb.cli.util.InputOptions;
import be.nbb.cli.util.OutputOptions;
import be.nbb.cli.util.StandardOptions;
import com.google.common.net.MediaType;
import static com.google.common.net.MediaType.XML_UTF_8;
import data.Data;
import demetra.cli.helpers.XmlUtil;
import ec.tss.TsCollectionInformation;
import ec.tss.TsInformation;
import ec.tss.timeseries.diagnostics.AccuracyTests.AsymptoticsType;
import ec.tss.xml.XmlTsCollection;
import java.io.File;
import java.io.IOException;
import org.junit.Test;

/**
 *
 * @author Mats Maggi
 */
public class AccuracyTestTest {

    static TsCollectionInformation getSample() {
        TsCollectionInformation result = new TsCollectionInformation();
        for (int i = 0; i < 1; i++) {
            TsInformation ts = new TsInformation();
            ts.data = Data.X;
            result.items.add(ts);
        }

        return result;
    }
    
    static void write(File file, TsCollectionInformation col) throws IOException {
        XmlUtil.writeValue(OutputOptions.of(file, XML_UTF_8, false), XmlTsCollection.class, col);
    }

    @Test
    public void testExec() throws Exception {
        AccuracyTest app = new AccuracyTest();
        File in = new File("C:\\LocalData\\TEST\\jdemetra-cli-2.1.0-SNAPSHOT\\files\\sts.xml");
        File out = new File("C:\\LocalData\\TEST\\jdemetra-cli-2.1.0-SNAPSHOT\\files\\StsResults.xlsx");

        //write(in, getSample());
        
        InputOptions io = InputOptions.of(in, MediaType.XML_UTF_8);
        OutputOptions oo = OutputOptions.of(out, MediaType.OOXML_SHEET, false);
        StandardOptions so = new StandardOptions(false, false, true);
        AccuracyTestsTool.Options ao = new AccuracyTestsTool.Options(
                AsymptoticsType.STANDARD_FIXED_B.toString(),
                true
        );
        AccuracyTest.Parameters options = new AccuracyTest.Parameters(so, io, oo, ao);

        app.exec(options);
    }
}
