package com.github.freeacs.tr069;

import com.github.freeacs.dbi.*;
import com.github.freeacs.dbi.Unittype.ProvisioningProtocol;
import com.github.freeacs.dbi.util.ProvisioningMessage;
import com.github.freeacs.dbi.util.SystemParameters;
import com.github.freeacs.tr069.base.ACSParameters;
import com.github.freeacs.tr069.base.PIIDecision;
import com.github.freeacs.tr069.base.SessionDataI;
import com.github.freeacs.tr069.http.HTTPRequestResponseData;
import com.github.freeacs.tr069.xml.ParameterList;
import com.github.freeacs.tr069.xml.ParameterValueStruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class SessionData implements SessionDataI {
  private static final Logger LOGGER = LoggerFactory.getLogger(SessionData.class);

  /** The session-id. 会话ID。 */
  private String id;
  /** Data for monitoring/logging. 用于监控/日志的数据。 */
  private List<HTTPRequestResponseData> reqResList = new ArrayList<>();
  /** When did the session start? 会话何时开始？ */
  private Long startupTmsForSession;

  /** The unique id for the CPE. CPE的唯一标识符。 */
  private String unitId;
  /** The unit-object. 设备对象。 */
  private Unit unit;
  /** The profile name for this CPE (defined i the DB). 此CPE的配置文件名称（在数据库中定义）。 */
  private Profile profile;
  /** The unittype for this CPE (defined in the DB). 此CPE的设备类型（在数据库中定义）。 */
  private Unittype unittype;
  /** The keyroot of this CPE (e.g. InternetGatewayDevice.) 此CPE的参数根节点（例如：InternetGatewayDevice）。 */
  private String keyRoot;

  private String serialNumber;

  /** Tells whether the CPE is doing a periodic inform or not. 指示CPE是否正在进行周期性Inform。 */
  private boolean periodic;
  /* other event codes 其他事件代码 */
  private boolean factoryReset;
  private boolean valueChange;
  private boolean kicked;
  private boolean transferComplete;
  private boolean autonomousTransferComplete;
  private boolean diagnosticsComplete;
  private boolean booted;

  /** Tells whether a job is under execution - important not to start on another job. 指示是否有作业正在执行 - 重要：不要启动另一个作业。 */
  private boolean jobUnderExecution;
  /** The event code of the inform. Inform的事件代码。 */
  private String eventCodes;

  /** Owera parameters. Owera参数。 */
  private ACSParameters acsParameters;
  /** Special parameters, will always be retrieved. 特殊参数，将始终被获取。 */
  private CPEParameters cpeParameters;
  /** Special parameter, will only be retrieved from the Inform. 特殊参数，仅从Inform中获取。 */
  private InformParameters informParameters;

  /** All parameters found in the DB, except system parameters (X). 数据库中找到的所有参数，不包括系统参数(X)。 */
  private Map<String, ParameterValueStruct> fromDB;
  /** All parameters read from the CPE. 从CPE读取的所有参数。 */
  private List<ParameterValueStruct> valuesFromCPE;
  /** All parameters that shall be written to the CPE. 将写入CPE的所有参数。 */
  private ParameterList toCPE;
  /** All parameters that shall be written to the DB. 将写入数据库的所有参数。 */
  private List<ParameterValueStruct> toDB;
  /** All parameters requested from CPE. 从CPE请求的所有参数。 */
  private List<ParameterValueStruct> requestedCPE;

  /** Job. 作业。 */
  private Job job;
  /** All parameters from a job. 作业的所有参数。 */
  private Map<String, JobParameter> jobParams;

  /** Parameterkey contains a hash of all values sent to CPE. ParameterKey包含发送给CPE的所有值的哈希。 */
  private ParameterKey parameterKey;
  /** Commandkey contains the version number of the last download - if a download was sent. CommandKey包含最后一次下载的版本号 - 如果发送了下载。 */
  private CommandKey commandKey;
  /** Provisioning allowed. False if outside servicewindow or not allowed by unitJob 允许配置。如果在服务窗口之外或unitJob不允许则为false */
  private boolean provisioningAllowed = true;

  /** The secret obtained by discovery-mode, basic auth. 通过发现模式获取的密钥，基本认证。 */
  private String secret;
  /** The flag signals a first-time connect in discovery-mode. 此标志表示发现模式下的首次连接。 */
  private boolean firstConnect;
  /** Unittype has been created, but unitId remains unknown, only for discovery-mode. 设备类型已创建，但unitId仍然未知，仅用于发现模式。 */
  private boolean unittypeCreated = true;

  /** PIIDecision is important to decide the final outcome of the next Periodic Inform Interval. PIIDecision对于决定下一次周期性Inform间隔的最终结果很重要。 */
  private PIIDecision piiDecision;

  /** An object to store all kinds of data about the provisioning. 存储有关配置的各种数据的对象。 */
  private ProvisioningMessage provisioningMessage = new ProvisioningMessage();

  /** An object to store data about a download. 存储有关下载数据的对象。 */
  private Download download;

  private String cwmpVersionNumber;

  public SessionData(String id) {
    this.id = id;
    provisioningMessage.setProvProtocol(ProvisioningProtocol.TR069);
  }

  public void setKeyRoot(String keyRoot) {
    if (keyRoot != null) {
      this.keyRoot = keyRoot;
    }
  }

  public void setUnitId(String unitId) {
    if (unitId != null) {
      this.unitId = unitId;
      this.provisioningMessage.setUniqueId(unitId);
    }
  }

  public void setNoMoreRequests(boolean noMoreRequests) {
    LOGGER.warn("Setting unused noMoreRequests field to " + noMoreRequests);
  }

  public void setToDB(List<ParameterValueStruct> toDB) {
    if (toDB == null) {
      toDB = new ArrayList<>();
    }
    this.toDB = toDB;
  }

  public String getMethodBeforePreviousResponseMethod() {
    if (reqResList != null && reqResList.size() > 2) {
      return reqResList.get(reqResList.size() - 3).getResponseData().getMethod();
    } else {
      return null;
    }
  }

  public String getPreviousResponseMethod() {
    if (reqResList != null && reqResList.size() > 1) {
      return reqResList.get(reqResList.size() - 2).getResponseData().getMethod();
    } else {
      return null;
    }
  }

  public void setEventCodes(String eventCodes) {
    this.eventCodes = eventCodes;
    this.provisioningMessage.setEventCodes(eventCodes);
  }

  public String getSoftwareVersion() {
    CPEParameters cpeParams = getCpeParameters();
    if (cpeParams != null) {
      return cpeParams.getValue(cpeParams.SOFTWARE_VERSION);
    }
    return null;
  }

  public void setSoftwareVersion(String softwareVersion) {
    CPEParameters cpeParams = getCpeParameters();
    if (cpeParams != null) {
      cpeParams.getCpeParams().put(
          cpeParams.SOFTWARE_VERSION,
          new ParameterValueStruct(cpeParams.SOFTWARE_VERSION, softwareVersion));
    }
  }

  public boolean lastProvisioningOK() {
    return getParameterKey().isEqual() && getCommandKey().isEqual();
  }

  @Override
  public PIIDecision getPIIDecision() {
    if (piiDecision == null) {
      piiDecision = new PIIDecision(this);
    }
    return piiDecision;
  }

  public boolean discoverUnittype() {
    if (acsParameters != null
        && acsParameters.getValue(SystemParameters.DISCOVER) != null
        && "1".equals(acsParameters.getValue(SystemParameters.DISCOVER))) {
      return true;
    } else if (acsParameters == null) {
      log.debug("freeacsParameters not found in discoverUnittype()");
    } else if (acsParameters.getValue(SystemParameters.DISCOVER) != null) {
      log.debug("DISCOVER parameter value is "
              + acsParameters.getValue(SystemParameters.DISCOVER)
              + " in discoverUnittype()");
    } else {
      log.debug("DISCOVER parameter not found of value is null in discoverUnittype() ");
    }
    return false;
  }

  String getUnittypeName() {
    String unittypeName = null;
    if (unittype != null) {
      unittypeName = unittype.getName();
    }
    return unittypeName;
  }

  public String getVersion() {
    String version = null;
    if (cpeParameters != null) {
      version = cpeParameters.getValue(cpeParameters.SOFTWARE_VERSION);
    }
    return version;
  }

  @Data
  @AllArgsConstructor
  public static class Download {
    private String url;
    private File file;
  }
}
