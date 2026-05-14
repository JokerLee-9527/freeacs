package com.github.freeacs.controllers;

import com.github.freeacs.dbi.DBI;
import com.github.freeacs.dbi.Unit;
import com.github.freeacs.security.AcsUnit;
import com.github.freeacs.tr069.Properties;
import com.github.freeacs.tr069.SessionData;
import com.github.freeacs.tr069.SessionLogging;
import com.github.freeacs.tr069.background.ActiveDeviceDetectionTask;
import com.github.freeacs.tr069.background.MessageListenerTask;
import com.github.freeacs.tr069.background.ScheduledKickTask;
import com.github.freeacs.tr069.base.BaseCache;
import com.github.freeacs.tr069.base.Log;
import com.github.freeacs.tr069.http.HTTPRequestData;
import com.github.freeacs.tr069.http.HTTPRequestResponseData;
import com.github.freeacs.tr069.http.HTTPResponseData;
import com.github.freeacs.tr069.methods.ProvisioningMethod;
import com.github.freeacs.tr069.methods.ProvisioningStrategy;
import com.github.freeacs.tr069.xml.Parser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This is the "main-class" of TR069 Provisioning. It receives the HTTP-request from the CPE and
 * returns an HTTP-response. The content of the request/reponse can be both TR-069 request/response.
 * 这是TR069配置的"主类"。它接收来自CPE的HTTP请求并返回HTTP响应。
 * 请求/响应的内容可以是TR-069请求或响应。
 */
@Slf4j
@RestController
public class Tr069Controller {

    @Value("${context-path}")
    private String contextPath;

    private final DBI dbi;
    private final Properties properties;

    public Tr069Controller(DBI dbi, Properties properties) {
        this.properties = properties;
        this.dbi = dbi;
    }

    /**
     * This is the entry point for TR-069 Clients - everything starts here!!!
     * 这是TR-069客户端的入口点 - 一切从这里开始！！！
     *
     * <p>A TR-069 session consists of many rounds of HTTP request/responses, however each
     * request/response non-the-less follows a standard pattern:
     * 一个TR-069会话由多轮HTTP请求/响应组成，但每个请求/响应都遵循标准模式：
     *
     * <p>1. Check special HTTP headers for a "early return" (CONTINUE) 2. Check authentication -
     * challenge client if necessary. If not authenticated - return 3. Check concurrent sessions from
     * same unit - if detected: return 4. Extract XML from request - store in sessionData object 5.
     * Process HTTP Request (xml-parsing, find methodname, test-verification) 6. Decide upon next step
     * - may contain logic that processes the request and decide response 7. Produce HTTP Response
     * (xml-creation) 8. Some details about the xml-response like content-type/Empty response 9.
     * Return response to TR-069 client
     * 1. 检查特殊HTTP头以进行"提前返回"(CONTINUE)
     * 2. 检查认证 - 必要时质询客户端。如果未认证 - 返回
     * 3. 检查来自同一设备的并发会话 - 如果检测到：返回
     * 4. 从请求中提取XML - 存储在sessionData对象中
     * 5. 处理HTTP请求（xml解析，查找方法名，测试验证）
     * 6. 决定下一步 - 可能包含处理请求并决定响应的逻辑
     * 7. 生成HTTP响应（xml创建）
     * 8. 关于xml响应的一些细节，如content-type/空响应
     * 9. 返回响应给TR-069客户端
     *
     * <p>At the end we have error handling, to make sure that no matter what, we do return an EMTPY
     * response to the client - to signal end of conversation/TR-069-session.
     * 最后我们有错误处理，确保无论如何我们都返回一个空响应给客户端 - 以表示对话/TR-069会话结束。
     *
     * <p>In the finally loop we check if a TR-069 Session is in-fact completed (one way or the other)
     * and if so, logging is performed. Also, if unit-parameters are queued up for writing, those will
     * be written now (instead of writing some here and some there along the entire TR-069 session).
     * 在finally循环中，我们检查TR-069会话是否实际完成（无论何种方式），
     * 如果是，则执行日志记录。此外，如果有排队等待写入的设备参数，现在将写入
     * （而不是在整个TR-069会话中分散写入）。
     *
     * <p>In special cases the server will kick the device to "come back" and continue testing a new
     * test case.
     * 在特殊情况下，服务器会踢设备"回来"并继续测试新的测试用例。
     */
    @PostMapping(value = {"${context-path}", "${context-path}/prov"})
    public ResponseEntity<String> doPost(Authentication authentication,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        HTTPRequestResponseData requestResponseData = null;
        try {
            requestResponseData = new HTTPRequestResponseData(request);
            requestResponseData.getRequestData().setContextPath(contextPath);
            Parser parser = new Parser(request.getInputStream(), request.getContentLength(), Log.isConversationLogEnabled());
            requestResponseData.getRequestData().setParser(parser);
            if (authentication != null && requestResponseData.getSessionData().getUnit() == null) {
                String username = ((AcsUnit) authentication.getPrincipal()).getUsername();
                SessionData sessionData = requestResponseData.getSessionData();
                sessionData.setUnitId(username);
                sessionData.setUnit(dbi.getACSUnit().getUnitById(username));
            }

            ProvisioningStrategy.getStrategy(properties, dbi).process(requestResponseData);

            return ResponseEntity
                    .status("Empty".equals(requestResponseData.getResponseData().getMethod())
                            ? HttpStatus.NO_CONTENT
                            : HttpStatus.OK)
                    .contentType(StringUtils.isNotEmpty(requestResponseData.getResponseData().getXml())
                            ? MediaType.TEXT_XML
                            : MediaType.TEXT_HTML)
                    .headers(StringUtils.isNotEmpty(requestResponseData.getResponseData().getXml())
                            ? getSOAPActionHeader()
                            : null)
                    .body(requestResponseData.getResponseData().getXml());
        } catch (Throwable t) {
            log.error("An error occurred during processing the request", t);
            if (requestResponseData != null) {
                requestResponseData.setThrowable(t);
            }
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("");
        } finally {
            if (requestResponseData != null && endOfSession(requestResponseData)) {
                log.debug("End of session is reached, " +
                        "will write queued unit parameters " +
                        "if unit (" + requestResponseData.getSessionData().getUnit() + ") is not null");
                if (requestResponseData.getSessionData().getUnit() != null) {
                    writeQueuedUnitParameters(requestResponseData);
                }
                SessionLogging.log(requestResponseData);
                BaseCache.removeSessionData(requestResponseData.getSessionData().getUnitId());
                BaseCache.removeSessionData(requestResponseData.getSessionData().getId());
                response.setHeader("Connection", "close");
                new SecurityContextLogoutHandler().logout(request, null, null);
            }
        }
    }

    private HttpHeaders getSOAPActionHeader() {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("SOAPAction", "");
        return responseHeaders;
    }

    // every 5 minute 每5分钟
    @Scheduled(cron = "0 0/5 * * * *")
    private void scheduleActiveDeviceDetectionTask() {
        final ActiveDeviceDetectionTask activeDeviceDetectionTask =
                new ActiveDeviceDetectionTask("ActiveDeviceDetection TR069", dbi);
        activeDeviceDetectionTask.setThisLaunchTms(System.currentTimeMillis());
        activeDeviceDetectionTask.run();
    }

    // every 1 second 每1秒
    @Scheduled(cron = "* * * ? * *")
    private void scheduleKickTask() {
        final ScheduledKickTask scheduledKickTask =
                new ScheduledKickTask("ScheduledKick", dbi);
        scheduledKickTask.setThisLaunchTms(System.currentTimeMillis());
        scheduledKickTask.run();
    }

    // every 5 sec 每5秒
    @Scheduled(cron = "0/5 * * ? * *")
    private void scheduleMessageListenerTask() {
        final MessageListenerTask messageListenerTask =
                new MessageListenerTask("MessageListener", dbi);
        messageListenerTask.setThisLaunchTms(System.currentTimeMillis());
        messageListenerTask.run();
    }

    private void writeQueuedUnitParameters(HTTPRequestResponseData reqRes) {
        try {
            Unit unit = reqRes.getSessionData().getUnit();
            if (unit != null) {
                dbi.getACSUnit().addOrChangeQueuedUnitParameters(unit);
            }
        } catch (Throwable t) {
            log.error("An error occured when writing queued unit parameters to Fusion. May affect provisioning", t);
        }
    }

    private boolean endOfSession(HTTPRequestResponseData reqRes) {
        try {
            if (reqRes.getThrowable() != null) {
                return true;
            }
            SessionData sessionData = reqRes.getSessionData();
            HTTPRequestData reqData = reqRes.getRequestData();
            HTTPResponseData resData = reqRes.getResponseData();
            if (reqData.getMethod() != null
                    && resData != null
                    && ProvisioningMethod.Empty.name().equals(resData.getMethod())) {
                boolean terminationQuirk = properties.isTerminationQuirk(sessionData);
                return !terminationQuirk || ProvisioningMethod.Empty.name().equals(reqData.getMethod());
            }
            return false;
        } catch (Throwable t) {
            log.warn("An error occured when determining endOfSession. Does not affect provisioning", t);
            return false;
        }
    }

}
