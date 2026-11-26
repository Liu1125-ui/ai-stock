package com.aistock.prediction.forecast;

import com.aistock.common.BusinessException;
import com.aistock.prediction.modelcatalog.ModelRecords.Version;
import com.aistock.prediction.modelcatalog.ModelRepository;
import com.aistock.prediction.verification.VerificationCalculator;
import com.aistock.prediction.verification.VerificationQueryRepository;
import com.aistock.prediction.verification.VerificationRecords;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public final class ForecastFusion {
    private static final int WEIGHT_TOTAL = 10_000;
    private static final List<String> EVIDENCE = List.of("instrumentId","inputVersionId","rawVersionId",
        "inputHash","rawContentHash","calendarHash","originDate","originRawClose","targetDates");
    private static final List<String> CONTRACT = List.of("featureDefinition","targetDefinition","sourceDefinition","features");

    public record Selection(UUID predictionId,int weightBps) {}
    public record Source(UUID predictionId,UUID modelId,String artifactId,long publicationRevision,int weightBps,List<String> referencePrices) {}
    public record Point(int step,String targetDate,String ratio,String referencePrice) {}
    public record Preview(String instrumentId,String originDate,String inputVersionId,String rawVersionId,
                          String baselinePrice,List<Source> sources,List<Point> points,Assessment assessment) {}
    public record MemberScore(UUID predictionId,VerificationRecords.Metrics metrics) {}
    public record Assessment(String state,VerificationRecords.Result fusion,List<MemberScore> members) {}

    private final ForecastRepository records;
    private final ModelRepository models;

    private final VerificationQueryRepository verification;
    private final VerificationCalculator calculator=new VerificationCalculator();

    public ForecastFusion(ForecastRepository records,ModelRepository models,VerificationQueryRepository verification) {
        this.records=records;
        this.models=models;
        this.verification=verification;
    }

    public Preview preview(UUID user,List<Selection> selections) {
        validate(selections);
        var owned=records.fusionSources(user,selections.stream().map(Selection::predictionId).toList());
        if(owned.size()!=selections.size())throw ForecastRepository.error("RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);
        var byId=owned.stream().collect(Collectors.toMap(ForecastRecords.Record::predictionId,Function.identity()));
        var preview=models.locked(head->{
            JsonNode window=null;
            JsonNode contract=null;
            var sources=new ArrayList<Source>(selections.size());
            var outputs=new ArrayList<JsonNode>(selections.size());
            var seenModels=new HashSet<UUID>();
            for(var selection:selections) {
                var record=byId.get(selection.predictionId());
                if(!"SUCCEEDED".equals(record.state())||record.snapshot()==null||record.horizon()!=5)throw mismatch();
                if(!seenModels.add(record.modelId()))throw invalid();
                var model=models.find(record.modelId(),false);
                if(!"PUBLISHED".equals(model.state()))throw ForecastRepository.error("MODEL_NOT_PUBLISHED",HttpStatus.CONFLICT);
                var input=record.snapshot().path("input");
                var output=record.snapshot().path("prediction");
                checkContract(model,contract);
                if(window!=null&&!matches(input,window,EVIDENCE))throw mismatch();
                try {
                    ForecastService.verify(record.instrumentId(),model,input,output);
                } catch(BusinessException failure) {
                    throw mismatch();
                }
                window=input;
                contract=model.manifest();
                var points=output.path("points");
                outputs.add(points);
                var prices=new ArrayList<String>(5);
                points.forEach(point->prices.add(point.path("referencePrice").asText()));
                sources.add(new Source(record.predictionId(),model.modelId(),model.artifactId(),model.revision(),
                    selection.weightBps(),List.copyOf(prices)));
            }
            return calculate(window,sources,outputs);
        });
        var selected=selections.stream().map(selection->byId.get(selection.predictionId())).toList();
        var assessment=assess(user,selected,preview.points());
        return new Preview(preview.instrumentId(),preview.originDate(),preview.inputVersionId(),preview.rawVersionId(),
            preview.baselinePrice(),preview.sources(),preview.points(),assessment);
    }

    private Assessment assess(UUID owner,List<ForecastRecords.Record> selected,List<Point> points) {
        try {
            var common=verification.sharedFusionEvidence(owner,selected.stream().map(ForecastRecords.Record::predictionId).toList());
            if(common.isEmpty())return new Assessment("NO_SHARED_EVIDENCE",null,List.of());
            var evidence=common.orElseThrow();
            var original=selected.get(0).snapshot();
            var fused=original.deepCopy();
            var values=fused.path("prediction").path("points");
            for(int index=0;index<points.size();index++) {
                var point=points.get(index);
                ((ObjectNode)values.get(index)).put("referencePrice",point.referencePrice()).put("ratio",point.ratio());
            }
            var result=calculator.calculate(fused,evidence);
            var scores=new ArrayList<MemberScore>(selected.size());
            for(var item:selected) {
                var modelScore=calculator.calculate(item.snapshot(),evidence);
                scores.add(new MemberScore(item.predictionId(),modelScore.metrics()));
            }
            return new Assessment(result.state(),result,List.copyOf(scores));
        } catch(BusinessException invalidEvidence) {
            return new Assessment("EVIDENCE_UNAVAILABLE",null,List.of());
        }
    }

    private static Preview calculate(JsonNode window,List<Source> sources,List<JsonNode> outputs) {
        var points=new ArrayList<Point>(5);
        var baseline=ForecastService.number(window.path("originRawClose"));
        for(int day=0;day<5;day++) {
            BigDecimal ratio=BigDecimal.ZERO;
            for(int member=0;member<sources.size();member++) {
                var value=ForecastService.number(outputs.get(member).get(day).path("ratio"));
                ratio=ratio.add(value.multiply(BigDecimal.valueOf(sources.get(member).weightBps(),4)));
            }
            ratio=ratio.setScale(12,RoundingMode.HALF_UP);
            var price=baseline.multiply(ratio).setScale(6,RoundingMode.HALF_UP);
            points.add(new Point(day+1,window.path("targetDates").get(day).asText(),ratio.toPlainString(),price.toPlainString()));
        }
        return new Preview(window.path("instrumentId").asText(),window.path("originDate").asText(),
            window.path("inputVersionId").asText(),window.path("rawVersionId").asText(),
            baseline.toPlainString(),List.copyOf(sources),List.copyOf(points),null);
    }

    private static void checkContract(Version model,JsonNode expected) {
        var manifest=model.manifest();
        var config=manifest.path("config");
        if(config.path("seq_len").asInt()!=60||config.path("pred_len").asInt()!=5
            ||CONTRACT.stream().anyMatch(field->!manifest.hasNonNull(field))
            ||expected!=null&&!matches(manifest,expected,CONTRACT))throw mismatch();
    }

    private static boolean matches(JsonNode left,JsonNode right,List<String> keys) {
        return keys.stream().allMatch(key->left.hasNonNull(key)&&left.path(key).equals(right.path(key)));
    }

    private static void validate(List<Selection> selections) {
        if(selections==null||selections.size()<2||selections.size()>3)throw invalid();
        Set<UUID> ids=new HashSet<>();
        int total=0;
        for(var selection:selections) {
            if(selection==null||selection.predictionId()==null||!ids.add(selection.predictionId())
                ||selection.weightBps()<0||selection.weightBps()>WEIGHT_TOTAL)throw invalid();
            total+=selection.weightBps();
        }
        if(total!=WEIGHT_TOTAL)throw invalid();
    }

    static BusinessException invalid() {
        return new BusinessException("FUSION_REQUEST_INVALID","请选择两至三条不同的预测记录，权重合计须为 100%",HttpStatus.BAD_REQUEST);
    }

    private static BusinessException mismatch() {
        return new BusinessException("FUSION_EVIDENCE_MISMATCH","预测的模型版本、行情或目标交易日不一致，无法融合",HttpStatus.CONFLICT);
    }
}
