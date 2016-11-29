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

import be.nbb.cli.util.BasicCliLauncher;
import be.nbb.cli.util.BasicCommand;
import be.nbb.cli.util.InputOptions;
import be.nbb.cli.util.OutputOptions;
import be.nbb.cli.util.StandardOptions;
import be.nbb.cli.util.joptsimple.ComposedOptionSpec;
import static be.nbb.cli.util.joptsimple.ComposedOptionSpec.newInputOptionsSpec;
import static be.nbb.cli.util.joptsimple.ComposedOptionSpec.newOutputOptionsSpec;
import static be.nbb.cli.util.joptsimple.ComposedOptionSpec.newStandardOptionsSpec;
import be.nbb.cli.util.joptsimple.JOptSimpleArgsParser;
import be.nbb.cli.util.proc.CommandRegistration;
import com.google.common.annotations.VisibleForTesting;
import demetra.cli.dfm.AccuracyTestsTool.AccuracyTestsResults;
import demetra.cli.dfm.AccuracyTestsTool.SpecificationInfo;
import demetra.cli.helpers.XmlUtil;
import ec.tss.TsCollectionInformation;
import ec.tss.TsInformation;
import ec.tss.timeseries.diagnostics.AccuracyTests;
import ec.tss.tsproviders.utils.MultiLineNameUtil;
import ec.tss.xml.XmlTsCollection;
import ec.util.spreadsheet.Book;
import ec.util.spreadsheet.helpers.ArrayBook;
import ec.util.spreadsheet.helpers.ArraySheet;
import static java.util.Arrays.asList;
import java.util.Optional;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import lombok.AllArgsConstructor;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 *
 * @author Mats Maggi
 */
public final class AccuracyTest implements BasicCommand<AccuracyTest.Parameters> {

    @CommandRegistration
    public static void main(String[] args) {
        BasicCliLauncher.run(args, Parser::new, AccuracyTest::new, o -> o.so);
    }

    @AllArgsConstructor
    public static class Parameters {

        StandardOptions so;
        public InputOptions input;
        public OutputOptions output;
        public AccuracyTestsTool.Options test;
    }

    @Override
    public void exec(Parameters params) throws Exception {
        TsCollectionInformation input = XmlUtil.readValue(params.input, XmlTsCollection.class);
        ArrayBook.Builder bb = ArrayBook.builder();
        ArraySheet.Builder builder = ArraySheet.builder();
        builder.name("Test Results");
        builder.row(0, 0, AccuracyTestsResults.getTitles());

        boolean verbose = params.so.isVerbose();
        int i = 1;
        for (TsInformation info : input.items) {
            if (verbose) {
                System.out.print("\nComputing series : " + MultiLineNameUtil.join(info.name) + "...");
            }
            AccuracyTestsResults rslts = AccuracyTestsTool.getDefault().create(info, params.test);
            if (rslts.getSa0() != null) {
                builder.row(i++, 0, generateRow(rslts, rslts.getSa0()));
            }
            if (rslts.getSa12() != null) {
                builder.row(i++, 0, generateRow(rslts, rslts.getSa12()));
            }
            if (rslts.getFcts1() != null) {
                builder.row(i++, 0, generateRow(rslts, rslts.getFcts1()));
            }
            if (rslts.getFcts12() != null) {
                builder.row(i++, 0, generateRow(rslts, rslts.getFcts12()));
            }
            
            if (verbose) {
                System.out.print("\t[DONE]");
            }
        }
        if (verbose) {
            System.out.println("\n\nWriting all results into output file...");
        }

        ArrayBook book = bb.sheet(builder.build()).build();
        if (params.output.getFile().isPresent()) {
            Optional<? extends Book.Factory> factory = Lookup.getDefault()
                    .lookupAll(Book.Factory.class)
                    .stream()
                    .filter(o -> o.accept(params.output.getFile().get()))
                    .findFirst();
            if (factory.isPresent()) {
                factory.get().store(params.output.getFile().get(), book);
            } else {
                // ???
            }
        } else {
            // console ?
        }

        if (verbose) {
            System.err.println("Processing " + input.items.size() + " time series");
        }
    }

    private Object[] generateRow(AccuracyTestsResults total, AccuracyTestsTool.AccuracyTestsResult rslts) {
        if (total == null || rslts == null) {
            return new Object[]{};
        }
        SpecificationInfo ts = total.getTramoseats();
        SpecificationInfo x13 = total.getX13();
        SpecificationInfo air = total.getAirline();

        return new Object[]{
            MultiLineNameUtil.join(rslts.getName()), rslts.getMethod(),
            rslts.getRmse1(), rslts.getRmse2(),
            rslts.getDm1(), rslts.getDm2(),
            rslts.getEnc1(), rslts.getWeight1(), rslts.getEnc2(), rslts.getWeight2(),
            rslts.getEncB1(), rslts.getBWeight1(), rslts.getEncB2(), rslts.getBWeight2(),
            rslts.getBias1(), rslts.getBias2(), rslts.getBias3(),
            rslts.getBiasPVal1(), rslts.getBiasPVal2(), rslts.getBiasPVal3(),
            rslts.getEff1(), rslts.getEff2(), rslts.getEff3(),
            rslts.getEffPVal1(), rslts.getEffPVal2(), rslts.getEffPVal3(),
            rslts.getEffY1(), rslts.getEffY2(), rslts.getEffY3(),
            rslts.getEffYPVal1(), rslts.getEffYPVal2(), rslts.getEffYPVal3(),
            ts.getP(), ts.getD(), ts.getQ(), ts.getBp(), ts.getBd(), ts.getBq(), ts.getNeffectiveobs(), ts.getNp(), ts.getLog(),
            x13.getP(), x13.getD(), x13.getQ(), x13.getBp(), x13.getBd(), x13.getBq(), x13.getNeffectiveobs(), x13.getNp(), x13.getLog(),
            air.getP(), air.getD(), air.getQ(), air.getBp(), air.getBd(), air.getBq(), air.getNeffectiveobs(), air.getNp(), air.getLog()
        };
    }

    @VisibleForTesting
    static final class Parser extends JOptSimpleArgsParser<Parameters> {

        private final ComposedOptionSpec<StandardOptions> so = newStandardOptionsSpec(parser);
        private final ComposedOptionSpec<InputOptions> input = newInputOptionsSpec(parser);
        private final ComposedOptionSpec<OutputOptions> output = newOutputOptionsSpec(parser);
        private final ComposedOptionSpec<AccuracyTestsTool.Options> test = new AccuracyOptionsSpec(parser);

        @Override
        protected Parameters parse(OptionSet o) {
            return new Parameters(so.value(o), input.value(o), output.value(o), test.value(o));
        }
    }

    @NbBundle.Messages({
        "accuracytest.type=Asymptotic Type",
        "accuracytest.twosided=Two Sided test"
    })
    private static final class AccuracyOptionsSpec implements ComposedOptionSpec<AccuracyTestsTool.Options> {

        private final OptionSpec<String> type;
        private final OptionSpec<Boolean> twoSided;

        public AccuracyOptionsSpec(OptionParser p) {
            this.type = p
                    .acceptsAll(asList("t", "type"), Bundle.accuracytest_type())
                    .withRequiredArg()
                    .ofType(String.class)
                    .defaultsTo(AccuracyTests.AsymptoticsType.STANDARD_FIXED_B.toString());
            this.twoSided = p
                    .acceptsAll(asList("ts", "twosided"), Bundle.accuracytest_twosided())
                    .withRequiredArg()
                    .ofType(Boolean.class)
                    .defaultsTo(true)
                    .describedAs("bool");
        }

        @Override
        public AccuracyTestsTool.Options value(OptionSet o) {
            return new AccuracyTestsTool.Options(type.value(o), twoSided.value(o));
        }
    }
}
