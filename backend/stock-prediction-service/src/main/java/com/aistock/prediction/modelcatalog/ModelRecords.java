package com.aistock.prediction.modelcatalog;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
public final class ModelRecords {
    private ModelRecords(){}
    public static final String SCOPE="TRANSFORMER_60_5_V1";
    public static final String ID="transformer-s3-2023-seed(42|43|44)(?:-[a-f0-9]{16})?";
    private static final String ROLLING_ID="transformer-s3-rolling-[0-9]{8}-seed(42|43|44)-[a-f0-9]{16}";
    private static final String EXPANSION_ID="transformer-s3-expansion-(?:sh|sz)[0-9]{6}-[a-f0-9]{16}";
    private static final String POOL_ID="transformer-s3-pool-[a-f0-9]{16}";
    public record ExpansionEvaluation(String instrumentId,String from,String through,int windows,
                                      String mae,String baselineMae,String directionAccuracy){}
    public record RollingEvaluation(String datasetHash,String holdoutMae,String holdoutBaselineMae,String holdoutDirection,int holdoutWindows){}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Evaluation(String sourceHash,String scope,int seed,Integer validationYear,String validationMae,
                             String validationBaselineMae,String mae2025,String mae2026,String direction2026,RollingEvaluation rolling,
                             ExpansionEvaluation expansion,List<ExpansionEvaluation> pool) {
        public Evaluation(String sourceHash,String scope,int seed,Integer validationYear,String validationMae,
                          String validationBaselineMae,String mae2025,String mae2026,String direction2026,
                          RollingEvaluation rolling,ExpansionEvaluation expansion) {
            this(sourceHash,scope,seed,validationYear,validationMae,validationBaselineMae,mae2025,mae2026,
                direction2026,rolling,expansion,null);
        }
        public Evaluation(String sourceHash,String scope,int seed,Integer validationYear,String validationMae,
                          String validationBaselineMae,String mae2025,String mae2026,String direction2026,
                          RollingEvaluation rolling) {
            this(sourceHash,scope,seed,validationYear,validationMae,validationBaselineMae,mae2025,mae2026,direction2026,rolling,null);
        }
        public Evaluation(String sourceHash,String scope,int seed,int validationYear,String validationMae,
                          String validationBaselineMae,String mae2025,String mae2026,String direction2026) {
            this(sourceHash,scope,seed,validationYear,validationMae,validationBaselineMae,mae2025,mae2026,direction2026,null);
        }
    }
    public record PublicationDescriptor(UUID modelId,String artifactId,String manifestHash,String checkpointHash,long publicationRevision){}
    public record Registration(String artifactId,String manifestHash,Evaluation evaluation){}
    public record Version(UUID modelId,String artifactId,String manifestHash,String checkpointHash,String state,long revision,boolean everPublished,String registrationHash,JsonNode manifest,Evaluation evaluation,Instant createdAt){}
    public record Head(String scope,UUID currentModelId,long revision){}
    public record Actor(UUID userId,String role){}
    public record ActionInput(UUID operationId,Long expectedRevision,Long expectedHeadRevision,String reason,Boolean researchLimitationsAcknowledged){}
    public record ActionResult(Version model,Head head,UUID eventId){}
    public record Event(UUID eventId,UUID operationId,UUID actorId,UUID modelId,String action,String reason,Instant createdAt){}
    public record Operation(UUID actorId,String requestHash,ActionResult result){}
    public static BusinessException invalid(){return new BusinessException("MODEL_REGISTRATION_INVALID","模型登记参数不正确",HttpStatus.BAD_REQUEST);}
    public static BusinessException conflict(String code){return new BusinessException(code,"模型或推荐状态已变化，请刷新后重试",HttpStatus.CONFLICT);}
    public static void validate(Registration r){
        if(r==null||r.artifactId()==null||!hash(r.manifestHash())||r.evaluation()==null)throw invalid();
        var e=r.evaluation();
        if(!hash(e.sourceHash())||!Set.of(42,43,44).contains(e.seed()))throw invalid();
        if(r.artifactId().matches(POOL_ID)){
            if(!"STOCK_POOL".equals(e.scope())||e.pool()==null||e.expansion()!=null||e.rolling()!=null
                ||e.validationYear()!=null||e.validationMae()!=null||e.validationBaselineMae()!=null
                ||e.mae2025()!=null||e.mae2026()!=null||e.direction2026()!=null)throw invalid();
            validatePool(e.pool());
            return;
        }
        if(e.pool()!=null)throw invalid();
        if(r.artifactId().matches(EXPANSION_ID)){
            var expansion=e.expansion();
            if(!"STOCK_EXPANSION".equals(e.scope())||expansion==null||e.rolling()!=null
                ||e.validationYear()!=null||e.validationMae()!=null||e.validationBaselineMae()!=null
                ||e.mae2025()!=null||e.mae2026()!=null||e.direction2026()!=null)throw invalid();
            validateExpansion(r.artifactId(),expansion);
            return;
        }
        if(e.expansion()!=null)throw invalid();
        metric(e.validationMae(),10);
        if(metric(e.validationBaselineMae(),10).signum()==0)throw invalid();
        if(r.artifactId().matches(ROLLING_ID)){
            var rolling=e.rolling();
            if(!"ROLLING_OUT_OF_SAMPLE".equals(e.scope())||rolling==null||e.validationYear()!=null||e.mae2025()!=null
                ||e.mae2026()!=null||e.direction2026()!=null||!hash(rolling.datasetHash())
                ||rolling.holdoutWindows()<1||rolling.holdoutWindows()>400000
                ||!r.artifactId().contains("-seed"+e.seed()+"-"))throw invalid();
            metric(rolling.holdoutMae(),10);metric(rolling.holdoutBaselineMae(),10);metric(rolling.holdoutDirection(),1);
        }else{
            if(!r.artifactId().matches(ID)||!"RETROSPECTIVE_DEVELOPMENT".equals(e.scope())||e.rolling()!=null
                ||!Objects.equals(e.validationYear(),2024)||!r.artifactId().startsWith("transformer-s3-2023-seed"+e.seed()))throw invalid();
            metric(e.mae2025(),10);metric(e.mae2026(),10);metric(e.direction2026(),1);
        }
    }
    private static void validatePool(List<ExpansionEvaluation> pool){
        if(pool.size()<2||pool.size()>50)throw incompatible();
        String previous="";
        for(var item:pool){
            validateExpansion(null,item);
            if(previous.compareTo(item.instrumentId())>=0)throw incompatible();
            previous=item.instrumentId();
        }
    }
    private static void validateExpansion(String id,ExpansionEvaluation expansion){
        if(expansion==null||expansion.instrumentId()==null||!expansion.instrumentId().matches("(SH|SZ)[0-9]{6}")
            ||id!=null&&!id.startsWith("transformer-s3-expansion-"+expansion.instrumentId().toLowerCase(Locale.ROOT)+"-")
            ||expansion.windows()<1||expansion.windows()>2000
            ||expansion.from()==null||expansion.through()==null
            ||trainingDate(TextNode.valueOf(expansion.from())).isAfter(trainingDate(TextNode.valueOf(expansion.through()))))
            throw incompatible();
        metric(expansion.mae(),10);
        if(metric(expansion.baselineMae(),10).signum()==0)throw invalid();
        metric(expansion.directionAccuracy(),1);
    }
    private static BigDecimal metric(String v,int max){
        if(v==null||v.length()>18||!v.matches("[0-9]+(?:\\.[0-9]{1,12})?"))throw invalid();
        var n=new BigDecimal(v);if(n.compareTo(BigDecimal.valueOf(max))>0)throw invalid();return n;
    }
    public static boolean hash(String h){return h!=null&&h.matches("[a-f0-9]{64}");}
    public static void checkManifest(String id,JsonNode m){
        boolean expansion=id.matches(EXPANSION_ID);
        boolean pool=id.matches(POOL_ID);
        var expected=expansion&&m!=null
            ?new ObjectMapper().valueToTree(List.of(m.path("expansion").path("instrumentId").asText()))
            :new ObjectMapper().valueToTree(List.of("SH600030","SH600276","SZ000002"));
        var features=new ObjectMapper().valueToTree(List.of("open","high","low","close","vwap","volume"));
        boolean valid=m!=null&&m.isObject()&&id.equals(m.path("artifactId").asText())&&"CANDIDATE".equals(m.path("status").asText())&&hash(m.path("checkpointHash").asText())&&m.path("config").path("seq_len").asInt()==60&&m.path("config").path("label_len").asInt()==30&&m.path("config").path("pred_len").asInt()==5&&m.path("config").path("enc_in").asInt()==6&&m.path("config").path("dec_in").asInt()==6&&m.path("config").path("c_out").asInt()==1&&(pool||expected.equals(m.path("instruments")))&&features.equals(m.path("features"))&&"baostock-six-price-volume-v1".equals(m.path("featureDefinition").asText())&&"baostock-backward-close-over-raw-v1".equals(m.path("sourceDefinition").asText())&&"adjusted-close-relative-origin-v1".equals(m.path("targetDefinition").asText());
        if(!valid)throw incompatible();
        if(pool){
            checkPoolManifest(id,m);
        }else if(expansion){
            var binding=m.path("expansion");var parent=binding.path("parentArtifactId").asText();
            if(!m.path("schemaVersion").isIntegralNumber()||m.path("schemaVersion").asInt()!=3
                ||!binding.isObject()||binding.size()!=10||!binding.path("windows").isIntegralNumber()
                ||!binding.path("windows").canConvertToInt()||!(parent.matches(ID)||parent.matches(ROLLING_ID))
                ||!hash(binding.path("parentManifestHash").asText())||!hash(binding.path("evidenceHash").asText()))
                throw incompatible();
            validateExpansion(id,expansionMetrics(binding));
        }else if(id.matches(ROLLING_ID))checkRollingManifest(id,m);
        else if(!id.matches(ID)||m.path("schemaVersion").asInt()!=1||!"2023-12-31".equals(m.path("trainingCutoff").asText()))
            throw incompatible();
    }
    static void checkEvaluationBinding(Registration input,JsonNode manifest) {
        var evaluation=input.evaluation();
        if(evaluation.pool()!=null){
            var binding=manifest.path("pool");
            if(!evaluation.sourceHash().equals(binding.path("evidenceHash").asText())
                ||!binding.path("parentArtifactId").asText().contains("-seed"+evaluation.seed())
                ||!evaluation.pool().equals(poolMetrics(binding)))throw incompatible();
            return;
        }
        if(evaluation.expansion()!=null){
            var binding=manifest.path("expansion");
            if(!evaluation.sourceHash().equals(binding.path("evidenceHash").asText())
                ||!binding.path("parentArtifactId").asText().contains("-seed"+evaluation.seed())
                ||!evaluation.expansion().equals(expansionMetrics(binding)))throw incompatible();
            return;
        }
        if(evaluation.rolling()==null)return;
        var data=manifest.path("trainingData");
        if(!evaluation.rolling().datasetHash().equals(data.path("datasetHash").textValue())
            ||evaluation.seed()!=data.path("seed").intValue())throw incompatible();
    }
    private static List<ExpansionEvaluation> poolMetrics(JsonNode binding){
        var stocks=binding.path("stocks");
        if(!stocks.isArray()||stocks.size()<2||stocks.size()>50)throw incompatible();
        var result=new ArrayList<ExpansionEvaluation>(stocks.size());
        for(var stock:stocks){
            if(!stock.isObject()||stock.size()!=8||!stock.path("windows").isIntegralNumber()
                ||!stock.path("windows").canConvertToInt()||!hash(stock.path("evidenceHash").asText()))
                throw incompatible();
            result.add(expansionMetrics(stock));
        }
        validatePool(result);
        return result;
    }
    private static void checkPoolManifest(String id,JsonNode manifest){
        var binding=manifest.path("pool");
        var parent=binding.path("parentArtifactId").asText();
        var evidenceHash=binding.path("evidenceHash").asText();
        if(!manifest.path("schemaVersion").isIntegralNumber()||manifest.path("schemaVersion").asInt()!=4
            ||!binding.isObject()||binding.size()!=4||!hash(evidenceHash)
            ||!id.equals("transformer-s3-pool-"+evidenceHash.substring(0,16))
            ||!hash(binding.path("parentManifestHash").asText())||!(parent.matches(ID)||parent.matches(ROLLING_ID)))
            throw incompatible();
        var ids=poolMetrics(binding).stream().map(ExpansionEvaluation::instrumentId).toList();
        if(!new ObjectMapper().valueToTree(ids).equals(manifest.path("instruments")))throw incompatible();
    }
    private static ExpansionEvaluation expansionMetrics(JsonNode binding){
        return new ExpansionEvaluation(binding.path("instrumentId").textValue(),binding.path("from").textValue(),
            binding.path("through").textValue(),binding.path("windows").intValue(),binding.path("mae").textValue(),
            binding.path("baselineMae").textValue(),binding.path("directionAccuracy").textValue());
    }
    private static void checkRollingManifest(String id,JsonNode manifest) {
        var data=manifest.path("trainingData");
        var seed=data.path("seed");
        if(!manifest.path("schemaVersion").isIntegralNumber()||manifest.path("schemaVersion").intValue()!=2
            ||!data.isObject()||data.size()!=5||!hash(data.path("datasetHash").textValue())
            ||!seed.isIntegralNumber()||!seed.canConvertToInt()||!Set.of(42,43,44).contains(seed.intValue()))throw incompatible();
        LocalDate previous=null;
        String cutoff=null;
        for(String label:List.of("train","validation","holdout")){
            var range=data.path(label);
            if(!range.isObject()||range.size()!=2)throw incompatible();
            var start=trainingDate(range.path("from"));
            var end=trainingDate(range.path("through"));
            if(start.isAfter(end)||previous!=null&&!start.isAfter(previous))throw incompatible();
            if("train".equals(label))cutoff=end.toString();
            previous=end;
        }
        if(!cutoff.equals(manifest.path("trainingCutoff").textValue())
            ||!id.startsWith("transformer-s3-rolling-"+cutoff.replace("-","")+"-seed"+seed.intValue()+"-"))
            throw incompatible();
    }
    private static LocalDate trainingDate(JsonNode date) {
        if(!date.isTextual()||!date.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw incompatible();
        try{return LocalDate.parse(date.textValue());}catch(java.time.DateTimeException invalid){throw incompatible();}
    }
    private static BusinessException incompatible() {
        return new BusinessException("MODEL_ARTIFACT_INCOMPATIBLE","模型制品或特征契约不匹配",HttpStatus.CONFLICT);
    }
    private static JsonNode sorted(JsonNode n){
        if(n.isObject()){var out=JsonNodeFactory.instance.objectNode();var keys=new TreeSet<String>();n.fieldNames().forEachRemaining(keys::add);keys.forEach(k->out.set(k,sorted(n.get(k))));return out;}
        if(n.isArray()){var out=JsonNodeFactory.instance.arrayNode();n.forEach(v->out.add(sorted(v)));return out;}return n;
    }
    public static String json(ObjectMapper mapper,Object value){try{return mapper.writeValueAsString(sorted(mapper.valueToTree(value)));}catch(Exception e){throw new BusinessException("MODEL_METADATA_INVALID","模型记录暂不可用",HttpStatus.INTERNAL_SERVER_ERROR);}}
    public static String digest(ObjectMapper mapper,Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(mapper,value).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
