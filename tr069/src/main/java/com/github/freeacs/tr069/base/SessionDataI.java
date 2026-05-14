package com.github.freeacs.tr069.base;

import com.github.freeacs.dbi.*;
import com.github.freeacs.dbi.util.ProvisioningMessage;
import com.github.freeacs.tr069.xml.ParameterValueStruct;

import java.util.Map;

public interface SessionDataI {
  ACSParameters getAcsParameters();

  void setAcsParameters(ACSParameters acsParameters);

  Unittype getUnittype();

  void setUnittype(Unittype unittype);

  Profile getProfile();

  void setProfile(Profile profile);

  Unit getUnit();

  void setUnit(Unit unit);

  String getUnitId();

  void setUnitId(String unitId);

  Job getJob();

  void setJob(Job job);

  Map<String, JobParameter> getJobParams();

  void setJobParams(Map<String, JobParameter> jobParams);

  String getSoftwareVersion();

  void setSoftwareVersion(String softwareVersion);

  Map<String, ParameterValueStruct> getFromDB();

  void setFromDB(Map<String, ParameterValueStruct> fromDB);

  boolean lastProvisioningOK();

  /**
   * The PIIDecision object contains information need to calculate the next periodic inform. The
   * information needed in this object is listed in the javadoc for that class. Make sure the method
   * never return null!!
   * PIIDecision对象包含计算下一个周期性通知所需的信息。此对象中所需的信息在该类的javadoc中列出。确保该方法永远不会返回null！！
   *
   * @return
   */
  PIIDecision getPIIDecision();

  void setSerialNumber(String serialNumber);

  String getSerialNumber();

  ProvisioningMessage getProvisioningMessage();

  Long getStartupTmsForSession();
}
