package datawave.webservice.common.audit.remote;

import com.codahale.metrics.Counter;
import com.codahale.metrics.annotation.Metric;
import com.codahale.metrics.annotation.Timed;
import datawave.configuration.RefreshableScope;
import datawave.security.authorization.DatawavePrincipal;
import datawave.webservice.common.audit.AuditService;
import datawave.webservice.common.remote.RemoteHttpService;
import datawave.webservice.util.NotEqualPropertyExpressionInterpreter;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.deltaspike.core.api.exclude.Exclude;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;

import javax.annotation.Priority;
import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.EJBContext;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import javax.interceptor.Interceptor;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This default auditor sends audits to a remote audit microservice.
 */
@RefreshableScope
@Alternative
// Make this alternative active for the entire application per the CDI 1.2 specification
@Priority(Interceptor.Priority.APPLICATION)
@Exclude(onExpression = "dw.audit.use.remoteauditservice!=true", interpretedBy = NotEqualPropertyExpressionInterpreter.class)
public class RemoteAuditor extends RemoteHttpService implements AuditService {
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.useSrvDnsLookup", defaultValue = "false")
    private boolean useSrvDNS;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.srvDnsServers", defaultValue = "127.0.0.1")
    private List<String> srvDnsServers;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.srvDnsPort", defaultValue = "8600")
    private int srvDnsPort;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.scheme", defaultValue = "https")
    private String auditServiceScheme;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.host", defaultValue = "localhost")
    private String auditServiceHost;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.port", defaultValue = "8443")
    private int auditServicePort;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.uri", defaultValue = "/audit/v1/")
    private String auditServiceURI;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.maxConnections", defaultValue = "100")
    private int maxConnections;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.retryCount", defaultValue = "5")
    private int retryCount;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.unavailableRetryCount", defaultValue = "15")
    private int unavailableRetryCount;
    
    @Inject
    @ConfigProperty(name = "dw.remoteDatawaveAuditService.unavailableRetryDelayMS", defaultValue = "2000")
    private int unavailableRetryDelay;
    
    @Inject
    @Metric(name = "dw.remoteDatawaveAuditService.retries", absolute = true)
    private Counter retryCounter;
    
    @Inject
    @Metric(name = "dw.remoteDatawaveAuditService.failures", absolute = true)
    private Counter failureCounter;
    
    @Resource
    private EJBContext ctx;
    
    @Override
    @Timed(name = "dw.remoteAuditService.audit", absolute = true)
    public String audit(Map<String,String> params) {
        Principal p = ctx.getCallerPrincipal();
        DatawavePrincipal dp = null;
        if (p instanceof DatawavePrincipal)
            dp = (DatawavePrincipal) p;
        
        final String bearerHeader = "Bearer " + jwtTokenHandler.createTokenFromUsers(dp.getName(), dp.getProxiedUsers());
        // @formatter:off
        return executePostMethodWithRuntimeException(
                "audit",
                uriBuilder -> {
                    for (String param : params.keySet())
                        uriBuilder.addParameter(param, params.get(param));
                },
                httpPost -> httpPost.setHeader("Authorization", bearerHeader),
                EntityUtils::toString,
                () -> "audit [" + params + "]");
        // @formatter:on
    }
    
    @Asynchronous
    protected <T> void executePostMethodAsyncWithRuntimeException(String uriSuffix, Consumer<URIBuilder> uriCustomizer, Consumer<HttpPost> requestCustomizer,
                    IOFunction<T> resultConverter, Supplier<String> errorSupplier) {
        T response = executePostMethodWithRuntimeException(uriSuffix, uriCustomizer, requestCustomizer, resultConverter, errorSupplier);
        log.debug(response.toString());
    }
    
    protected <T> T executePostMethodWithRuntimeException(String uriSuffix, Consumer<URIBuilder> uriCustomizer, Consumer<HttpPost> requestCustomizer,
                    IOFunction<T> resultConverter, Supplier<String> errorSupplier) {
        try {
            return executePostMethod(uriSuffix, uriCustomizer, requestCustomizer, resultConverter, errorSupplier);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URI: " + e.getMessage(), e);
        } catch (IOException e) {
            failureCounter.inc();
            throw new RuntimeException(e.getMessage(), e);
        }
    }
    
    @Override
    protected String serviceHost() {
        return auditServiceHost;
    }
    
    @Override
    protected int servicePort() {
        return auditServicePort;
    }
    
    @Override
    protected String serviceURI() {
        return auditServiceURI;
    }
    
    @Override
    protected boolean useSrvDns() {
        return useSrvDNS;
    }
    
    @Override
    protected List<String> srvDnsServers() {
        return srvDnsServers;
    }
    
    @Override
    protected int srvDnsPort() {
        return srvDnsPort;
    }
    
    @Override
    protected String serviceScheme() {
        return auditServiceScheme;
    }
    
    @Override
    protected int maxConnections() {
        return maxConnections;
    }
    
    @Override
    protected int retryCount() {
        return retryCount;
    }
    
    @Override
    protected int unavailableRetryCount() {
        return unavailableRetryCount;
    }
    
    @Override
    protected int unavailableRetryDelay() {
        return unavailableRetryDelay;
    }
    
    @Override
    protected Counter retryCounter() {
        return retryCounter;
    }
}
