package com.aistock.market.marketreview;
import com.aistock.common.*;import com.aistock.common.marketreview.MarketReviewContract;import com.aistock.market.adminquery.AdminMarketDataAccessFilter;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import org.springframework.http.*;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@RestController public class MarketReviewController {
 private final MarketReviewService service;
 public MarketReviewController(MarketReviewService service){this.service=service;}
 @RequestMapping({"/api/v1/admin/market-data/{stock}/versions/{version}/reviews","/api/v1/admin/market-data/{stock}/versions/{version}/reviews/{operation}"})
 public ApiResponse<?> handle(@PathVariable String stock,@PathVariable String version,@PathVariable(required=false)String operation,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r,HttpServletResponse s)throws IOException{
  s.setHeader("Cache-Control","no-store");boolean post="POST".equals(r.getMethod());if(!(post?MarketReviewContract.writePath(r.getRequestURI()):"GET".equals(r.getMethod())&&MarketReviewContract.readPath(r.getRequestURI())))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
  if(!Boolean.TRUE.equals(r.getAttribute(AdminMarketDataAccessFilter.ADMIN)))throw new BusinessException("ACCESS_DENIED","仅管理员可以处理审核",HttpStatus.FORBIDDEN);
  if(!query.isEmpty())throw MarketReviewContract.invalid();String actor=(String)r.getAttribute(AdminMarketDataAccessFilter.ACTOR);Object data;
  if(post){var types=Collections.list(r.getHeaders("Content-Type"));try{if(types.size()!=1)throw MarketReviewContract.invalid();var type=MediaType.parseMediaType(types.get(0));if(!"application".equals(type.getType())||!"json".equals(type.getSubtype()))throw MarketReviewContract.invalid();}catch(RuntimeException e){throw MarketReviewContract.invalid();}if(r.getContentLengthLong()>4096)throw MarketReviewContract.invalid();var c=MarketReviewContract.parse(r.getInputStream().readNBytes(4097));data=service.save(stock,version,actor,c);}
  else{if(r.getInputStream().read()!=-1)throw MarketReviewContract.invalid();data=operation==null?service.read(stock,version):service.operation(stock,version,actor,operation);}
  return ApiResponse.success(data,TraceIds.normalize(r.getHeader("X-Trace-Id")));
 }
}
