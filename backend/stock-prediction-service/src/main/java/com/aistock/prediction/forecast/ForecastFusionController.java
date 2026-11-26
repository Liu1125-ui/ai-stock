package com.aistock.prediction.forecast;

import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import com.aistock.prediction.modelcatalog.ModelRecords.Actor;
import com.aistock.prediction.modelcatalog.ModelRepository;
import com.aistock.prediction.verification.VerificationQueryRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ForecastFusionController {
    private final ForecastFusion fusion;
    private final ObjectReader reader;

    public ForecastFusionController(ForecastRepository forecasts,ModelRepository models,
                                    VerificationQueryRepository verification,ObjectMapper mapper) {
        this.fusion=new ForecastFusion(forecasts,models,verification);
        this.reader=mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(path="/api/v1/predictions/fusion-preview",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ForecastFusion.Preview>> preview(Authentication auth,HttpServletRequest request) throws IOException {
        if(auth==null||!(auth.getPrincipal() instanceof Actor actor))throw ForecastRepository.error("AUTH_REQUIRED",HttpStatus.UNAUTHORIZED);
        if(!request.getParameterMap().isEmpty())throw ForecastFusion.invalid();
        var payload=request.getInputStream().readNBytes(1025);
        if(payload.length>1024)throw ForecastFusion.invalid();
        var selections=new ArrayList<ForecastFusion.Selection>(3);
        try {
            var root=reader.readTree(payload);
            if(root==null||!root.isObject()||root.size()!=1)throw ForecastFusion.invalid();
            var items=root.path("items");
            if(!items.isArray()||items.size()<2||items.size()>3)throw ForecastFusion.invalid();
            for(var row:items) {
                var id=row.path("predictionId");
                var weight=row.path("weightBps");
                if(!row.isObject()||row.size()!=2||!id.isTextual()||!ForecastService.uuid(id.textValue())
                    ||!weight.isIntegralNumber()||!weight.canConvertToInt())throw ForecastFusion.invalid();
                selections.add(new ForecastFusion.Selection(UUID.fromString(id.textValue()),weight.intValue()));
            }
        } catch(IOException malformed) {
            throw ForecastFusion.invalid();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ApiResponse.success(fusion.preview(actor.userId(),selections),TraceIds.normalize(request.getHeader("X-Trace-Id"))));
    }
}
