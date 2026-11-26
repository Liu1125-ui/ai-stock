package com.aistock.prediction.modelcatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import static com.aistock.prediction.modelcatalog.ModelRecords.*;
public final class ModelCatalogService {
    private final ModelRepository repo;private final ArtifactInspector inspector;private final Clock clock;private final ObjectMapper mapper;
    public ModelCatalogService(ModelRepository repo,ArtifactInspector inspector,Clock clock,ObjectMapper mapper){this.repo=repo;this.inspector=inspector;this.clock=clock;this.mapper=mapper;}
    public Version register(Registration input){
        validate(input);var manifest=inspector.inspect(input.artifactId(),input.manifestHash());checkManifest(input.artifactId(),manifest);checkEvaluationBinding(input,manifest);
        var registrationHash=digest(mapper,new TreeMap<>(Map.of("input",input,"manifest",manifest)));
        return repo.locked(head->{
            var existing=repo.byArtifact(input.artifactId());if(existing!=null){if(!existing.registrationHash().equals(registrationHash))throw conflict("MODEL_REGISTRATION_CONFLICT");return existing;}
            var version=new Version(UUID.randomUUID(),input.artifactId(),input.manifestHash(),manifest.path("checkpointHash").asText(),"PENDING",0,false,registrationHash,manifest,input.evaluation(),clock.instant());repo.insert(version);
            var event=UUID.randomUUID();repo.event(new Event(event,null,null,version.modelId(),"REGISTER","可信服务导入候选；等待管理员审核",clock.instant()),registrationHash,new ActionResult(version,head,event));return version;
        });
    }
    public List<Version> list(){return repo.list();}
    public List<Version> published(){return repo.published();}
    public Head head(){return repo.head();}
}
