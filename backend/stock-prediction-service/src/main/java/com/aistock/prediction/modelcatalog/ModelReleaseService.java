package com.aistock.prediction.modelcatalog;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import static com.aistock.prediction.modelcatalog.ModelRecords.*;
public final class ModelReleaseService {
    private final ModelRepository repo;private final ArtifactInspector inspector;private final Clock clock;private final ObjectMapper mapper;
    public ModelReleaseService(ModelRepository repo,ArtifactInspector inspector,Clock clock,ObjectMapper mapper){this.repo=repo;this.inspector=inspector;this.clock=clock;this.mapper=mapper;}
    public ActionResult mutate(Actor actor,UUID modelId,String action,ActionInput input){
        requireAdmin(actor);
        if(modelId==null||!Set.of("publish","reject","offline","recommend","rollback").contains(action)||input==null||input.operationId()==null||input.expectedRevision()==null||input.expectedHeadRevision()==null||input.expectedRevision()<0||input.expectedHeadRevision()<0||input.reason()==null||input.reason().strip().length()<10||input.reason().length()>500)throw new BusinessException("MODEL_ACTION_INVALID","请填写有效的版本和10—500字审核理由",HttpStatus.BAD_REQUEST);
        boolean inspect=Set.of("publish","recommend","rollback").contains(action);
        if(inspect&&!Boolean.TRUE.equals(input.researchLimitationsAcknowledged()))throw new BusinessException("MODEL_REVIEW_REQUIRED","请确认已阅读回顾性误差与基线局限",HttpStatus.BAD_REQUEST);
        var requestHash=digest(mapper,Map.of("modelId",modelId,"action",action,"input",input));
        var previous=repo.operation(input.operationId());if(previous!=null)return replay(previous,actor,requestHash);
        var before=repo.find(modelId,false);
        if(inspect){var manifest=inspector.inspect(before.artifactId(),before.manifestHash());checkManifest(before.artifactId(),manifest);if(!before.manifest().equals(ModelReleaseService::compareManifestValue,manifest))throw conflict("MODEL_ARTIFACT_CHANGED");}
        return repo.locked(head->{
            var operation=repo.operation(input.operationId());if(operation!=null)return replay(operation,actor,requestHash);
            var version=repo.find(modelId,true);
            if(version.revision()!=input.expectedRevision()||head.revision()!=input.expectedHeadRevision())throw conflict("MODEL_REVISION_CONFLICT");
            String state=version.state();boolean ever=version.everPublished();UUID current=head.currentModelId();
            switch(action){
                case "publish"->{if(!Set.of("PENDING","OFFLINE").contains(state))throw conflict("MODEL_STATE_CONFLICT");state="PUBLISHED";ever=true;}
                case "reject"->{if(!"PENDING".equals(state))throw conflict("MODEL_STATE_CONFLICT");state="REJECTED";}
                case "offline"->{if(!"PUBLISHED".equals(state))throw conflict("MODEL_STATE_CONFLICT");state="OFFLINE";if(modelId.equals(current))current=null;}
                case "recommend"->{if(!"PUBLISHED".equals(state))throw conflict("MODEL_STATE_CONFLICT");current=modelId;}
                case "rollback"->{if(!ever||!Set.of("PUBLISHED","OFFLINE").contains(state))throw conflict("MODEL_STATE_CONFLICT");state="PUBLISHED";current=modelId;}
                default->throw new IllegalStateException("Validated action missing");
            }
            var changed=new Version(version.modelId(),version.artifactId(),version.manifestHash(),version.checkpointHash(),state,Math.addExact(version.revision(),1),ever,version.registrationHash(),version.manifest(),version.evaluation(),version.createdAt());
            var newHead=new Head(SCOPE,current,Math.addExact(head.revision(),1));var eventId=UUID.randomUUID();var result=new ActionResult(changed,newHead,eventId);
            repo.state(modelId,state,ever,changed.revision());repo.head(newHead);repo.event(new Event(eventId,input.operationId(),actor.userId(),modelId,action.toUpperCase(Locale.ROOT),input.reason().strip(),clock.instant()),requestHash,result);return result;
        });
    }
    private static int compareManifestValue(com.fasterxml.jackson.databind.JsonNode stored,com.fasterxml.jackson.databind.JsonNode inspected){
        if(stored.equals(inspected))return 0;
        // The inspector verifies the exact immutable manifest hash. MySQL JSON
        // double serialization can move a parsed floating value by one ULP.
        // Keep integer, string and structural equality strict; allow only that
        // single representational step, never a business-level epsilon.
        if(stored.isFloatingPointNumber()&&inspected.isFloatingPointNumber()){
            double a=stored.doubleValue(),b=inspected.doubleValue();
            if(Double.isFinite(a)&&Double.isFinite(b)&&a!=0&&b!=0
                &&Math.abs(a-b)<=Math.ulp(Math.max(Math.abs(a),Math.abs(b))))return 0;
        }
        return 1;
    }
    private ActionResult replay(Operation operation,Actor actor,String hash){if(!actor.userId().equals(operation.actorId())||!hash.equals(operation.requestHash()))throw conflict("MODEL_OPERATION_CONFLICT");return operation.result();}
    public static void requireAdmin(Actor actor){if(actor==null||actor.userId()==null||!"ADMIN".equals(actor.role()))throw new BusinessException("ACCESS_DENIED","需要管理员权限",HttpStatus.FORBIDDEN);}
}
