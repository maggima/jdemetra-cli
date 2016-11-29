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

import ec.satoolkit.ISaSpecification;
import ec.satoolkit.algorithm.implementation.TramoSeatsProcessingFactory;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.satoolkit.x13.X13Specification;
import ec.tss.Ts;
import ec.tss.TsFactory;
import ec.tss.TsInformation;
import ec.tss.sa.EstimationPolicyType;
import ec.tss.sa.SaItem;
import ec.tss.sa.SaManager;
import ec.tss.sa.processors.TramoSeatsProcessor;
import ec.tss.sa.processors.X13Processor;
import ec.tss.timeseries.diagnostics.AccuracyTests.AsymptoticsType;
import ec.tss.timeseries.diagnostics.EncompassingTest;
import ec.tss.timeseries.diagnostics.ForecastEvaluation;
import ec.tss.timeseries.diagnostics.GlobalForecastingEvaluation;
import ec.tstoolkit.algorithm.CompositeResults;
import ec.tstoolkit.modelling.arima.PreprocessingModel;
import ec.tstoolkit.modelling.arima.tramo.ArimaSpec;
import ec.tstoolkit.timeseries.TsAggregationType;
import ec.tstoolkit.timeseries.TsPeriodSelector;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsDataCollector;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Mats Maggi
 */
@ServiceProvider(service = AccuracyTestsTool.class)
public final class AccuracyTestsImpl implements AccuracyTestsTool {

    private AsymptoticsType asympType;

    @Override
    public AccuracyTestsResults create(TsInformation info, Options options) {
        AccuracyTestsTool.AccuracyTestsResults results = new AccuracyTestsTool.AccuracyTestsResults();
        try {
            try {
                asympType = AsymptoticsType.valueOf(options.getType());
            } catch (IllegalArgumentException e) {
                asympType = AsymptoticsType.STANDARD_FIXED_B;
            }
            
            boolean twoSided = options.getTwoSided();

            SaManager.instance.add(new TramoSeatsProcessor());
            SaManager.instance.add(new X13Processor());
            TsData trueData = info.data.cleanExtremities();

            // Calculation of true SA
            EncompassingTest enc1 = new EncompassingTest(
                    generateFcts(trueData, TramoSeatsSpecification.RSA5, 1),
                    generateFcts(trueData, X13Specification.RSA5, 1),
                    trueData,
                    asympType);
            enc1.setBenchmarkEncompassesModel(true);

            SaItem refItem = new SaItem(TramoSeatsSpecification.RSA5, TsFactory.instance.createTs("TsFull", null, trueData));
            CompositeResults rslts = refItem.process();
            TsData tsFullSample = rslts.getData("sa", TsData.class);
            extractResults("TS", rslts, results);

            refItem = new SaItem(X13Specification.RSA5, TsFactory.instance.createTs("X13Full", null, trueData));
            rslts = refItem.process();
            TsData x13FullSample = rslts.getData("sa", TsData.class);
            extractResults("X13", rslts, results);
            
            TramoSeatsSpecification airlineSpec = TramoSeatsSpecification.RSA5.clone();
            ArimaSpec arimaSpec = new ArimaSpec();
            arimaSpec.airline();
            airlineSpec.getTramoSpecification().setArima(arimaSpec);
            
            refItem = new SaItem(airlineSpec, TsFactory.instance.createTs("AirlineFull", null, trueData));
            rslts = refItem.process();
            TsData airlineFullSample = rslts.getData("sa", TsData.class);
            extractResults("AIRLINE", rslts, results);
            
            TsData trueSA = tsFullSample.times(1d/3d).plus(x13FullSample.times(1d/3d))
                    .plus(airlineFullSample.times(1d/3d));
            
            ExecutorService executor = Executors.newFixedThreadPool(4);

            executor.submit(() -> {
                try {
                    results.setSa0(buildResults(info.name, "SA(0)", generateSa(trueData, TramoSeatsSpecification.RSA5, 0),
                            generateSa(trueData, X13Specification.RSA5, 0),
                            generateSa(trueData, airlineSpec, 0),
                            trueSA, twoSided));
                } catch (Exception e) {
                }
            });

            executor.submit(() -> {
                try {
                    results.setSa12(buildResults(info.name, "SA(-12)", generateSa(trueData, TramoSeatsSpecification.RSA5, -12),
                            generateSa(trueData, X13Specification.RSA5, -12),
                            generateSa(trueData, airlineSpec, -12),
                            trueSA, twoSided));
                } catch (Exception e) {
                }
            });

            executor.submit(() -> {
                try {
                    results.setFcts1(buildResults(info.name, "Fcts(1)", generateFcts(trueData, TramoSeatsSpecification.RSA5, 1),
                            generateFcts(trueData, X13Specification.RSA5, 1),
                            generateFcts(trueData, airlineSpec, 1),
                            trueData, twoSided));
                } catch (Exception e) {
                }
            });

            executor.submit(() -> {
                try {
                    results.setFcts12(buildResults(info.name, "Fcts(12)", generateFcts(trueData, TramoSeatsSpecification.RSA5, 12),
                            generateFcts(trueData, X13Specification.RSA5, 12),
                            generateFcts(trueData, airlineSpec, 12),
                            trueData, twoSided));
                } catch (Exception e) {
                }
            });
            executor.shutdown();

            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
        } finally {
            return results;
        }
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
        for (int i = half; i <= trueData.getLength(); ++i, ++p) {
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

    //private TsCollection coll = TsFactory.instance.createTsCollection();
    private AccuracyTestsTool.AccuracyTestsResult buildResults(String name, String method, TsData tramoSeats, TsData x13, TsData bench, TsData trueData, boolean twoSidedTests) {
        AccuracyTestsTool.AccuracyTestsResult result = new AccuracyTestsTool.AccuracyTestsResult();
        ForecastEvaluation evalFctsMethod1 = new ForecastEvaluation(tramoSeats, bench, trueData);
        //coll.add(TsFactory.instance.createTs("TS - " + method, null, evalFctsMethod1.getForecastError()));
        ForecastEvaluation evalFctsMethod2 = new ForecastEvaluation(x13, bench, trueData);
        //coll.add(TsFactory.instance.createTs("X13 - " + method, null, evalFctsMethod2.getForecastError()));
        //coll.add(TsFactory.instance.createTs("ARIMA - " + method, null, evalFctsMethod1.getForecastErrorBenchmark()));

        result.setName(name);
        result.setMethod(method);

        result.setRmse1(evalFctsMethod1.calcRelRMSE());
        result.setRmse2(evalFctsMethod2.calcRelRMSE());

        GlobalForecastingEvaluation eval1 = new GlobalForecastingEvaluation(tramoSeats, bench, trueData, asympType);
        GlobalForecastingEvaluation eval2 = new GlobalForecastingEvaluation(x13, bench, trueData, asympType);

        boolean twoSided = true;
        // DM and ENC tests using Command line option
        result.setDm1(eval1.getDieboldMarianoTest().getPValue(twoSidedTests));
        result.setDm2(eval2.getDieboldMarianoTest().getPValue(twoSidedTests));
        result.setEnc1(eval1.getModelEncompassesBenchmarkTest().getPValue(twoSidedTests));
        result.setWeight1(eval1.getModelEncompassesBenchmarkTest().calcWeights());
        result.setEnc2(eval2.getModelEncompassesBenchmarkTest().getPValue(twoSidedTests));
        result.setWeight2(eval2.getModelEncompassesBenchmarkTest().calcWeights());
        result.setEncB1(eval1.getBenchmarkEncompassesModelTest().getPValue(twoSidedTests));
        result.setBWeight1(eval1.getBenchmarkEncompassesModelTest().calcWeights());

        result.setEncB2(eval2.getBenchmarkEncompassesModelTest().getPValue(twoSidedTests));
        result.setBWeight2(eval2.getBenchmarkEncompassesModelTest().calcWeights());

        result.setBias1(eval1.getBiasTest().getAverageLoss());
        result.setBias2(eval2.getBiasTest().getAverageLoss());
        result.setBias3(eval1.getBiasBenchmarkTest().getAverageLoss());
        result.setBiasPVal1(eval1.getBiasTest().getPValue(twoSided));
        result.setBiasPVal2(eval2.getBiasTest().getPValue(twoSided));
        result.setBiasPVal3(eval1.getBiasBenchmarkTest().getPValue(twoSided));

        result.setEff1(eval1.getEfficiencyTest().calcCorrelation());
        result.setEff2(eval2.getEfficiencyTest().calcCorrelation());
        result.setEff3(eval1.getEfficiencyBenchmarkTest().calcCorrelation());
        result.setEffPVal1(eval1.getEfficiencyTest().getPValue(twoSided));
        result.setEffPVal2(eval2.getEfficiencyTest().getPValue(twoSided));
        result.setEffPVal3(eval1.getEfficiencyBenchmarkTest().getPValue(twoSided));

        result.setEffY1(eval1.getEfficiencyYearlyTest().calcCorrelation());
        result.setEffY2(eval2.getEfficiencyYearlyTest().calcCorrelation());
        result.setEffY3(eval1.getEfficiencyYearlyBenchmarkTest().calcCorrelation());
        result.setEffYPVal1(eval1.getEfficiencyYearlyTest().getPValue(twoSided));
        result.setEffYPVal2(eval2.getEfficiencyYearlyTest().getPValue(twoSided));
        result.setEffYPVal3(eval1.getEfficiencyYearlyBenchmarkTest().getPValue(twoSided));

        return result;
    }

    private void extractResults(String spec, CompositeResults rslts, AccuracyTestsResults results) {
        SpecificationInfo info = new SpecificationInfo();
        info.setP(rslts.getData("arima.p", Integer.class));
        info.setD(rslts.getData("arima.d", Integer.class));
        info.setQ(rslts.getData("arima.q", Integer.class));
        info.setBp(rslts.getData("arima.bp", Integer.class));
        info.setBd(rslts.getData("arima.bd", Integer.class));
        info.setBq(rslts.getData("arima.bq", Integer.class));
        info.setNeffectiveobs(rslts.getData("likelihood.neffectiveobs", Integer.class));
        info.setNp(rslts.getData("likelihood.np", Integer.class));
        info.setLog(rslts.getData("log", Boolean.class));
        switch (spec) {
            case "TS" : results.setTramoseats(info); break;
            case "X13" : results.setX13(info); break;
            case "AIRLINE" : results.setAirline(info); break;
            default: throw new IllegalArgumentException("Incorrect specification");
        }        
    }
}
