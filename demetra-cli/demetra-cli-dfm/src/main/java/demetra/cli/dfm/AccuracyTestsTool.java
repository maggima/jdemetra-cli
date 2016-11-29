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
package demetra.cli.dfm;

import be.nbb.demetra.toolset.Record;
import ec.tss.TsCollectionInformation;
import ec.tss.TsInformation;
import ec.tstoolkit.design.ServiceDefinition;
import ec.tstoolkit.information.InformationSet;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.Value;
import org.openide.util.Lookup;

/**
 *
 * @author Mats Maggi
 */
@ServiceDefinition(isSingleton = true)
public interface AccuracyTestsTool {

    @Value
    public static class Options {

        String type;
        Boolean twoSided;
    }

    @Data
    public static class AccuracyTestsResult {

        private String name, method;
        private double rmse1, rmse2, dm1, dm2, enc1, weight1, enc2, weight2, encB1, bWeight1, encB2, bWeight2,
                bias1, bias2, bias3, biasPVal1, biasPVal2, biasPVal3,
                eff1, eff2, eff3, effPVal1, effPVal2, effPVal3,
                effY1, effY2, effY3, effYPVal1, effYPVal2, effYPVal3;
    }

    @Data
    public static class SpecificationInfo {

        private String spec;
        private Integer p, d, q, bp, bd, bq, neffectiveobs, np;
        private Boolean log;
    }

    @Data
    public static class AccuracyTestsResults implements Record {

        private AccuracyTestsResult sa0, sa12, fcts1, fcts12;
        private SpecificationInfo tramoseats, x13, airline;

        @Override
        public InformationSet generate() {
            InformationSet info = new InformationSet();
            info.set("sa0", sa0);
            info.set("sa-12", sa12);
            info.set("fcts1", fcts1);
            info.set("fcts12", fcts12);
            info.set("tramoseats", tramoseats);
            info.set("x13", x13);
            info.set("airline", airline);
            return info;
        }

        public static List<String> getTitles() {
            return Arrays.asList("SERIES", "METHOD",
                    "RMSE1", "RMSE2",
                    "DM1", "DM2",
                    "ENC1", "WEIGHT1", "ENC2", "WEIGHT2",
                    "ENCB1", "WEIGHTB1", "ENCB2", "WEIGHTB2",
                    "BIAS1", "BIAS2", "BIAS3", "BIAS-PVAL1", "BIAS-PVAL2", "BIAS-PVAL3",
                    "EFF1", "EFF2", "EFF3", "EFF-PVAL1", "EFF-PVAL2", "EFF-PVAL3",
                    "EFFY1", "EFFY2", "EFFY3", "EFFY-PVAL1", "EFFY-PVAL2", "EFFY-PVAL3",
                    "p(TS)", "d(TS)", "q(TS)", "bp(TS)", "bd(TS)", "bq(TS)", "nobs(TS)", "np(TS)", "log(TS)",
                    "p(X13)", "d(X13)", "q(X13)", "bp(X13)", "bd(X13)", "bq(X13)", "nobs(X13)", "np(X13)", "log(X13)",
                    "p(AIR)", "d(AIR)", "q(AIR)", "bp(AIR)", "bd(AIR)", "bq(AIR)", "nobs(AIR)", "np(AIR)", "log(AIR)"
            );
        }
    }

    @Nonnull
    AccuracyTestsResults create(@Nonnull TsInformation info, @Nonnull Options options);

    @Nonnull
    default List<InformationSet> create(TsCollectionInformation info, Options options) {
        return info.items.parallelStream().map(o -> create(o, options).generate()).collect(Collectors.toList());
    }

    public static AccuracyTestsTool getDefault() {
        return Lookup.getDefault().lookup(AccuracyTestsTool.class);
    }
}
