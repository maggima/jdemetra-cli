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

import data.Data;
import ec.satoolkit.ISaSpecification;
import ec.satoolkit.algorithm.implementation.TramoSeatsProcessingFactory;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.satoolkit.x13.X13Specification;
import ec.tss.Ts;
import ec.tss.TsCollection;
import ec.tss.TsFactory;
import ec.tss.sa.EstimationPolicyType;
import ec.tss.sa.SaItem;
import ec.tss.sa.SaManager;
import ec.tss.sa.processors.TramoSeatsProcessor;
import ec.tss.sa.processors.X13Processor;
import ec.tss.timeseries.diagnostics.AccuracyTests;
import ec.tss.timeseries.diagnostics.AccuracyTests.AsymptoticsType;
import ec.tss.timeseries.diagnostics.EncompassingTest;
import ec.tss.timeseries.diagnostics.ForecastEvaluation;
import ec.tss.timeseries.diagnostics.GlobalForecastingEvaluation;
import ec.tstoolkit.modelling.arima.PreprocessingModel;
import ec.tstoolkit.modelling.arima.tramo.ArimaSpec;
import ec.tstoolkit.timeseries.TsAggregationType;
import ec.tstoolkit.timeseries.TsPeriodSelector;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsDataCollector;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import org.junit.Test;

/**
 *
 * @author Mats Maggi
 */
public class AccuracyTestsImplTest {

    @Test
    public void testForecastConstruction() {
        SaManager.instance.add(new TramoSeatsProcessor());
        SaManager.instance.add(new X13Processor());
        TsData trueData = Data.X;

        EncompassingTest enc1 = new EncompassingTest(
                generateFcts(trueData, TramoSeatsSpecification.RSA5, 1),
                generateFcts(trueData, X13Specification.RSA5, 1),
                trueData,
                AsymptoticsType.STANDARD);
        enc1.setBenchmarkEncompassesModel(true);
        double beta = enc1.calcWeights();

        SaItem refItem = new SaItem(TramoSeatsSpecification.RSA5, TsFactory.instance.createTs("TsFull", null, trueData));
        TsData tsFullSample = refItem.process().getData("sa", TsData.class);

        refItem = new SaItem(X13Specification.RSA5, TsFactory.instance.createTs("X13Full", null, trueData));
        TsData x13FullSample = refItem.process().getData("sa", TsData.class);

        TsData trueSA = tsFullSample.times(beta).plus(x13FullSample.times(1.0 - beta));

        TramoSeatsSpecification airlineSpec = TramoSeatsSpecification.RSA5.clone();
        ArimaSpec arimaSpec = new ArimaSpec();
        arimaSpec.airline();
        airlineSpec.getTramoSpecification().setArima(arimaSpec);

        printTitles();
        AccuracyTestsTool.AccuracyTestsResults results = new AccuracyTestsTool.AccuracyTestsResults();

        results.setSa0(buildResults("SERIE", "SA(0)", generateSa(trueData, TramoSeatsSpecification.RSA5, 0),
                generateSa(trueData, X13Specification.RSA5, 0),
                generateSa(trueData, airlineSpec, 0),
                trueSA));

        results.setSa12(buildResults("SERIE", "SA(-12)", generateSa(trueData, TramoSeatsSpecification.RSA5, -12),
                generateSa(trueData, X13Specification.RSA5, -12),
                generateSa(trueData, airlineSpec, -12),
                trueSA));

        results.setFcts1(buildResults("SERIE", "Fcts(1)", generateFcts(trueData, TramoSeatsSpecification.RSA5, 1),
                generateFcts(trueData, X13Specification.RSA5, 1),
                generateFcts(trueData, airlineSpec, 1),
                trueData));

        results.setFcts12(buildResults("SERIE", "Fcts(12)", generateFcts(trueData, TramoSeatsSpecification.RSA5, 12),
                generateFcts(trueData, X13Specification.RSA5, 12),
                generateFcts(trueData, airlineSpec, 12),
                trueData));
    }

    public TsData generateSa(TsData trueData, ISaSpecification spec, int horizon) {
        int half = (int) Math.round((double) trueData.getLength() / 2);

        TsPeriodSelector selector = new TsPeriodSelector();
        selector.first(half);
        Ts cut = TsFactory.instance.createTs("first half", null, trueData.select(selector));
        SaItem refItem = new SaItem(spec, cut);

        int freq = trueData.getFrequency().intValue();
        TsDataCollector collector = new TsDataCollector();
        EstimationPolicyType policy;
        int p = 0;
        for (int i = half; i <= trueData.getLength(); ++i, ++p) {   // horizon 0
            if (p % freq != 0) {
                policy = EstimationPolicyType.FreeParameters;
            } else {
                policy = EstimationPolicyType.Complete;
            }
            selector.first(i);
            TsData subTs = trueData.select(selector);
            cut = TsFactory.instance.createTs("first half", null, subTs);
            ISaSpecification nspec = SaManager.instance.createSpecification(refItem, null, policy, false);

            SaItem item = new SaItem(nspec, cut);
            TsData sa = item.process().getData("sa", TsData.class);
            TsPeriod current = subTs.getLastPeriod();

            collector.addObservation(current.plus(horizon).middle(), sa.get(current.plus(horizon)));
        }
        selector.last(trueData.getLength() - half);

        cut = TsFactory.instance.createTs("second half", null, trueData.select(selector));
        refItem = new SaItem(spec, cut);

        p = 0;
        for (int i = trueData.getLength() - half; i <= trueData.getLength(); ++i, ++p) {   // horizon 0
            if (p % freq != 0) {
                policy = EstimationPolicyType.FreeParameters;
            } else {
                policy = EstimationPolicyType.Complete;
            }
            selector.last(i);
            TsData subTs = trueData.select(selector);
            cut = TsFactory.instance.createTs("second half", null, subTs);
            ISaSpecification nspec = SaManager.instance.createSpecification(refItem, null, policy, false);

            SaItem item = new SaItem(nspec, cut);
            TsData sa = item.process().getData("sa", TsData.class);
            TsPeriod current = subTs.getStart();

            collector.addObservation(current.minus(horizon).middle(), sa.get(current.minus(horizon)));
        }

        return collector.make(trueData.getFrequency(), TsAggregationType.First);
    }

    public TsData generateFcts(TsData trueData, ISaSpecification spec, int horizon) {
        int half = (int) Math.round((double) trueData.getLength() / 2);

        TsPeriodSelector selector = new TsPeriodSelector();
        selector.first(half);
        Ts cut = TsFactory.instance.createTs("first half", null, trueData.select(selector));
        SaItem refItem = new SaItem(spec, cut);

        int freq = trueData.getFrequency().intValue();
        TsDataCollector collector = new TsDataCollector();
        EstimationPolicyType policy;
        int p = 0;
        for (int i = half; i <= trueData.getLength() - Math.abs(horizon); ++i, p++) {
            if (p % freq != 0) {
                policy = EstimationPolicyType.FreeParameters;
            } else {
                policy = EstimationPolicyType.Complete;
            }
            selector.first(i);
            cut = TsFactory.instance.createTs("second half", null, trueData.select(selector));
            ISaSpecification nspec = SaManager.instance.createSpecification(refItem, null, policy, false);
            SaItem item = new SaItem(nspec, cut);

            PreprocessingModel model = item.process().get(TramoSeatsProcessingFactory.PREPROCESSING, PreprocessingModel.class);
            TsData fcts = model.forecast(Math.abs(horizon), false);
            collector.addObservation(fcts.getLastPeriod().middle(), fcts.get(fcts.getLastPeriod()));
        }

        selector.last(trueData.getLength() - half);

        cut = TsFactory.instance.createTs("second half", null, trueData.select(selector));
        refItem = new SaItem(spec, cut);

        p = 0;
        for (int i = trueData.getLength() - half; i <= trueData.getLength() - Math.abs(horizon); ++i, p++) {
            if (p % freq != 0) {
                policy = EstimationPolicyType.FreeParameters;
            } else {
                policy = EstimationPolicyType.Complete;
            }
            selector.last(i);
            cut = TsFactory.instance.createTs("second half", null, trueData.select(selector));
            ISaSpecification nspec = SaManager.instance.createSpecification(refItem, null, policy, false);
            SaItem item = new SaItem(nspec, cut);

            PreprocessingModel model = item.process().get(TramoSeatsProcessingFactory.PREPROCESSING, PreprocessingModel.class);

            TsData backcasts = model.backcast(Math.abs(horizon), false);
            collector.addObservation(backcasts.getStart().middle(), backcasts.get(backcasts.getStart()));

        }

        return collector.make(trueData.getFrequency(), TsAggregationType.First);
    }

    private TsCollection coll = TsFactory.instance.createTsCollection();
    private String[] strings = {"SA(0)", "SA(-12)", "Fcts(1)", "Fcts(12)"};
    private int index = 0;

    private AccuracyTestsTool.AccuracyTestsResult buildResults(String name, String method, TsData tramoSeats, TsData x13, TsData bench, TsData trueData) {
        AccuracyTestsTool.AccuracyTestsResult result = new AccuracyTestsTool.AccuracyTestsResult();
        ForecastEvaluation evalFctsMethod1 = new ForecastEvaluation(tramoSeats, bench, trueData);
        coll.add(TsFactory.instance.createTs("TS - " + strings[index], null, evalFctsMethod1.getForecastError()));
        ForecastEvaluation evalFctsMethod2 = new ForecastEvaluation(x13, bench, trueData);
        coll.add(TsFactory.instance.createTs("X13 - " + strings[index], null, evalFctsMethod2.getForecastError()));
        coll.add(TsFactory.instance.createTs("ARIMA - " + strings[index], null, evalFctsMethod1.getForecastErrorBenchmark()));

        index++;
        
        result.setName(name);
        result.setMethod(method);

        result.setRmse1(evalFctsMethod1.calcRelRMSE());
        result.setRmse2(evalFctsMethod2.calcRelRMSE());
        GlobalForecastingEvaluation eval1 = new GlobalForecastingEvaluation(tramoSeats, bench, trueData, AccuracyTests.AsymptoticsType.STANDARD);
        GlobalForecastingEvaluation eval2 = new GlobalForecastingEvaluation(x13, bench, trueData, AccuracyTests.AsymptoticsType.STANDARD);

        boolean twoSided = true;
        result.setDm1(eval1.getDieboldMarianoTest().getPValue(twoSided));
        result.setDm2(eval2.getDieboldMarianoTest().getPValue(twoSided));
        result.setEnc1(eval1.getModelEncompassesBenchmarkTest().getPValue(twoSided));
        result.setWeight1(eval1.getModelEncompassesBenchmarkTest().calcWeights());
        result.setEnc2(eval2.getModelEncompassesBenchmarkTest().getPValue(twoSided));
        result.setWeight2(eval2.getModelEncompassesBenchmarkTest().calcWeights());
        result.setEncB1(eval1.getBenchmarkEncompassesModelTest().getPValue(twoSided));
        result.setBWeight1(eval1.getBenchmarkEncompassesModelTest().calcWeights());

        result.setEncB2(eval2.getBenchmarkEncompassesModelTest().getPValue(twoSided));
        result.setBWeight2(eval2.getBenchmarkEncompassesModelTest().calcWeights());

        result.setBias1(eval1.getBiasTest().getAverageLoss());
        result.setBias2(eval2.getBiasTest().getAverageLoss());
        result.setBias3(eval1.getBiasBenchmarkTest().getAverageLoss());
        result.setEff1(eval1.getEfficiencyTest().getAverageLoss());
        result.setEff2(eval2.getEfficiencyTest().getAverageLoss());
        result.setEff3(eval1.getEfficiencyBenchmarkTest().getAverageLoss());
        result.setEffY1(eval1.getEfficiencyYearlyTest().getAverageLoss());
        result.setEffY2(eval2.getEfficiencyYearlyTest().getAverageLoss());
        result.setEffY3(eval1.getEfficiencyYearlyBenchmarkTest().getAverageLoss());

        result.setBiasPVal1(eval1.getBiasTest().getPValue(twoSided));
        result.setBiasPVal2(eval2.getBiasTest().getPValue(twoSided));
        result.setBiasPVal3(eval1.getBiasBenchmarkTest().getPValue(twoSided));
        result.setEffPVal1(eval1.getEfficiencyTest().getPValue(twoSided));
        result.setEffPVal2(eval2.getEfficiencyTest().getPValue(twoSided));
        result.setEffPVal3(eval1.getEfficiencyBenchmarkTest().getPValue(twoSided));
        result.setEffYPVal1(eval1.getEfficiencyYearlyTest().getPValue(twoSided));
        result.setEffYPVal2(eval2.getEfficiencyYearlyTest().getPValue(twoSided));
        result.setEffYPVal3(eval1.getEfficiencyYearlyBenchmarkTest().getPValue(twoSided));

        return result;
    }

    private void printTitles() {
        System.out.println("\t"
                + "RMSE1\tRMSE2\t"
                + "DM1\tDM2\t"
                + "ENC1\tWEIGHT1\tENC2\tWEIGHT2\t"
                + "ENC_B1\tWEIGHT_B1\tENC_B2\tWEIGHT_B2\t"
                + "BIAS1\tBIAS2\tBIAS3\t"
                + "BIAS_PVAL1\tBIAS_PVAL2\tBIAS_PVAL3\t"
                + "EFF1\tEFF2\tEFF3\t"
                + "EFF_PVAL1\tEFF_PVAL2\tEFF_PVAL3\t"
                + "EFFY1\tEFFY2\tEFFY3\t"
                + "EFFY_PVAL1\tEFFY_PVAL2\tEFFY_PVAL3");
    }
}
