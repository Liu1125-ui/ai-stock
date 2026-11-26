package com.aistock.gateway;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;

public final class ProxyHeaderPolicy {
    private static final String UUID="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private ProxyHeaderPolicy(){}
    public static boolean allowed(String method,String path){
        return switch(method){
            case "GET" -> path.matches("/api/v1/users/me/email-change-challenges/"+UUID) || trainingRead(path) || com.aistock.common.marketreview.MarketReviewContract.readPath(path) || com.aistock.common.marketrefs.MarketReferenceInput.publicPath(path) || com.aistock.common.adminrefresh.AdminMarketRefreshInput.readPath(path) || com.aistock.common.adminmarket.AdminMarketQuery.readPath(path) || statisticsPath(path) || adminTaskReadPath(path) || path.equals("/api/v1/portfolio-questions") || path.equals("/api/v1/portfolio-questions/rules") || path.matches("/api/v1/portfolio-questions/"+UUID) || path.matches("/api/v1/portfolio-questions/operations/"+UUID) || path.equals("/api/v1/portfolio-analyses") || path.matches("/api/v1/portfolio-analyses/"+UUID) || path.matches("/api/v1/portfolio-analyses/operations/"+UUID) || path.equals("/api/v1/users/me/positions") || path.matches("/api/v1/users/me/positions/operations/"+UUID) || path.equals("/api/v1/comparison-explanations") || path.matches("/api/v1/comparison-explanations/"+UUID) || path.matches("/api/v1/comparison-explanations/operations/"+UUID) || path.equals("/api/v1/comparisons") || path.equals("/api/v1/comparisons/options") || path.matches("/api/v1/comparisons/"+UUID) || path.matches("/api/v1/comparisons/operations/"+UUID) || path.matches("/api/v1/messages/"+UUID+"/email-delivery") || path.equals("/api/v1/messages") || path.equals("/api/v1/messages/unread-count") || path.matches("/api/v1/alerts/"+UUID+"/triggers") || path.equals("/api/v1/alerts") || path.equals("/api/v1/notification-preferences") || path.equals("/api/v1/stock-questions") || path.matches("/api/v1/stock-questions/"+UUID) || path.matches("/api/v1/stock-questions/operations/"+UUID) || path.equals("/api/v1/stock-analyses/prediction-options") || path.equals("/api/v1/stock-analyses") || path.matches("/api/v1/stock-analyses/"+UUID) || path.matches("/api/v1/predictions/"+UUID+"/explanation") || path.matches("/api/v1/predictions/"+UUID+"/verifications(/"+UUID+")?") || path.equals("/api/v1/predictions/latest-by-instrument") || path.equals("/api/v1/predictions") || path.matches("/api/v1/predictions/"+UUID) || path.equals("/api/v1/models") || path.equals("/api/v1/admin/models") || path.matches("/api/v1/admin/models/"+UUID+"/(events|holdout-comparison)") || Set.of("/api/v1/auth/csrf","/api/v1/users/me","/api/v1/admin/users","/api/v1/users/me/watchlist").contains(path)
                    ||path.matches("/api/v1/auth/registration-challenges/"+UUID)
                    || path.equals("/api/v1/instruments") || path.equals("/api/v1/instruments/summaries") || path.equals("/api/v1/instruments/market-overview") || (path.matches("/api/v1/instruments/(SH|SZ)[0-9]{6}(/daily|/valuation)?") || path.matches("/api/v1/instruments/HK00700(/daily|/realtime)?"));
            case "DELETE" -> path.matches("/api/v1/users/me/positions/"+UUID) || path.matches("/api/v1/users/me/watchlist/(?:(?:SH|SZ)[0-9]{6}|HK00700)");
            case "POST" -> Set.of("/api/v1/users/me/email-change","/api/v1/users/me/email-change-challenges").contains(path) || path.equals("/api/v1/predictions/fusion-preview") || path.equals("/api/v1/training-submissions") || com.aistock.common.marketreview.MarketReviewContract.writePath(path) || com.aistock.common.adminrefresh.AdminMarketRefreshInput.writePath(path) || adminTaskWritePath(path) || path.equals("/api/v1/portfolio-questions") || path.equals("/api/v1/portfolio-analyses") || path.equals("/api/v1/users/me/positions") || path.equals("/api/v1/comparison-explanations") || path.equals("/api/v1/comparisons") || path.equals("/api/v1/messages/read") || path.equals("/api/v1/alerts") || path.equals("/api/v1/stock-questions") || path.equals("/api/v1/stock-analyses") || path.matches("/api/v1/predictions/"+UUID+"/explanation") || path.equals("/api/v1/predictions") || path.matches("/api/v1/predictions/"+UUID+"/hide") || path.matches("/api/v1/admin/models/"+UUID+"/(publish|reject|offline|recommend|rollback)") || Set.of("/api/v1/auth/registration-challenges","/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/logout","/api/v1/users/me/watchlist").contains(path);
            case "PATCH" -> path.matches("/api/v1/messages/"+UUID+"/read") || path.equals("/api/v1/notification-preferences") || path.matches("/api/v1/alerts/"+UUID) || path.equals("/api/v1/users/me")||path.matches("/api/v1/admin/users/"+UUID+"/status");
            case "PUT" -> path.matches("/api/v1/users/me/positions/"+UUID) || path.equals("/api/v1/users/me/password")||path.matches("/api/v1/admin/users/"+UUID+"/experiment-grant");
            case "OPTIONS" -> Set.of("GET","POST","PATCH","PUT","DELETE").stream().anyMatch(candidate->allowed(candidate,path));
            default -> false;
        };
    }
    static boolean trainingRead(String path) {
        String base="/api/v1/training-submissions";
        String id="[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}";
        return path.equals(base)||path.equals(base+"/options")||path.matches(base+"/"+id+"(?:/status|/tasks/"+id+"/metrics)?");
    }
    public static boolean statisticsPath(String p){return Set.of("/api/v1/admin/statistics/users","/api/v1/admin/statistics/predictions","/api/v1/admin/statistics/tasks","/api/v1/admin/statistics/market","/api/v1/admin/statistics/http").contains(p);}
    public static boolean adminTaskWritePath(String path){return path.matches("/api/v1/admin/tasks/[-A-Za-z0-9]{1,64}/(retry|cancel)")||path.matches("/api/v1/admin/tasks/training/"+UUID+"/cancel");}
    private static boolean adminTaskReadPath(String path){return path.equals("/api/v1/admin/tasks") || path.matches("/api/v1/admin/tasks/[-A-Za-z0-9]{1,64}(/(attempts|action-options|operations))?") || path.matches("/api/v1/admin/tasks/[-A-Za-z0-9]{1,64}/operations/[-A-Za-z0-9]{1,64}");}
    public static Map<String,String> request(HttpServletRequest request){
        var result=new LinkedHashMap<String,String>();
        for(String name: new String[]{"Cookie","Content-Type","Origin","X-Aistock-Admin-Action","X-XSRF-TOKEN","X-Trace-Id","Access-Control-Request-Method","Access-Control-Request-Headers"}){
            String value=request.getHeader(name);if(value!=null)result.put(name,value);
        }
        return result;
    }
    public static HttpHeaders response(HttpHeaders remote){
        var headers=new HttpHeaders();
        for(String name:new String[]{"Set-Cookie","Content-Type","Cache-Control","Vary","Access-Control-Allow-Origin","Access-Control-Allow-Credentials","Access-Control-Allow-Methods","Access-Control-Allow-Headers"}){
            if(remote.containsKey(name))headers.put(name,java.util.List.copyOf(remote.get(name)));
        }
        headers.setCacheControl("no-store");return headers;
    }
}
