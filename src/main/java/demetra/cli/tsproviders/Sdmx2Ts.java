/*
 * Copyright 2015 National Bank of Belgium
 * 
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved 
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
package demetra.cli.tsproviders;

import com.google.common.annotations.VisibleForTesting;
import demetra.cli.helpers.BasicArgsParser;
import demetra.cli.helpers.BasicCliLauncher;
import static demetra.cli.helpers.ComposedOptionSpec.newOutputOptionsSpec;
import static demetra.cli.helpers.ComposedOptionSpec.newStandardOptionsSpec;
import demetra.cli.helpers.OutputOptions;
import demetra.cli.helpers.StandardOptions;
import ec.tss.TsCollectionInformation;
import ec.tss.TsInformationType;
import ec.tss.tsproviders.sdmx.SdmxBean;
import ec.tss.tsproviders.sdmx.SdmxProvider;
import ec.tss.xml.XmlTsCollection;
import java.io.File;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import demetra.cli.helpers.BasicCommand;
import demetra.cli.helpers.ComposedOptionSpec;
import org.openide.util.NbBundle;

/**
 * Retrieves time series from an SDMX file.
 *
 * @author Philippe Charles
 */
public final class Sdmx2Ts implements BasicCommand<Sdmx2Ts.Parameters> {

    public static void main(String[] args) {
        BasicCliLauncher.run(args, Parser::new, Sdmx2Ts::new, o -> o.so);
    }

    public static final class Parameters {

        StandardOptions so;
        public SdmxBean sdmx;
        public OutputOptions output;
    }

    @Override
    public void exec(Parameters params) throws Exception {
        SdmxProvider provider = new SdmxProvider();
        provider.setCompactNaming(true);
        ProviderTool.getDefault().applyWorkingDir(provider);
        TsCollectionInformation result = ProviderTool.getDefault().getTsCollection(provider, params.sdmx, TsInformationType.All);
        params.output.writeValue(XmlTsCollection.class, result);
        provider.dispose();
    }

    @VisibleForTesting
    static final class Parser extends BasicArgsParser<Parameters> {

        private final ComposedOptionSpec<StandardOptions> so = newStandardOptionsSpec(parser);
        private final ComposedOptionSpec<SdmxBean> sdmx = new SdmxOptionsSpec(parser);
        private final ComposedOptionSpec<OutputOptions> output = newOutputOptionsSpec(parser);

        @Override
        protected Parameters parse(OptionSet o) {
            Parameters result = new Parameters();
            result.sdmx = sdmx.value(o);
            result.output = output.value(o);
            result.so = so.value(o);
            return result;
        }
    }

    @NbBundle.Messages({
        "sdmx2ts.label=Attribute used as title"
    })
    private static final class SdmxOptionsSpec implements ComposedOptionSpec<SdmxBean> {

        // source
        private final ComposedOptionSpec<File> file;
        // options
        private final OptionSpec<String> label;

        public SdmxOptionsSpec(OptionParser p) {
            this.file = TsProviderOptionSpecs.newInputFileSpec(p);
            this.label = p
                    .accepts("label", Bundle.sdmx2ts_label())
                    .withRequiredArg()
                    .defaultsTo("");
        }

        @Override
        public SdmxBean value(OptionSet o) {
            SdmxBean result = new SdmxBean();
            result.setFile(file.value(o));
            result.setTitleAttribute(label.value(o));
            return result;
        }
    }
}
